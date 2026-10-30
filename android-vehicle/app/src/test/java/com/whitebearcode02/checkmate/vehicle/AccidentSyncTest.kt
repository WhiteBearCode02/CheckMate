package com.whitebearcode02.checkmate.vehicle

import com.whitebearcode02.checkmate.vehicle.data.auth.*
import com.whitebearcode02.checkmate.vehicle.data.local.InMemoryAccidentLocalDataSource
import com.whitebearcode02.checkmate.vehicle.data.remote.*
import com.whitebearcode02.checkmate.vehicle.data.repository.AccidentSyncStore
import com.whitebearcode02.checkmate.vehicle.data.repository.PersistentAccidentRepository
import com.whitebearcode02.checkmate.vehicle.data.sync.*
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AccidentSyncTest {
    private val owner = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val otherOwner = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val vehicle = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val mobile = UUID.fromString("00000000-0000-0000-0000-000000000004")
    private val time = Instant.parse("2026-10-30T15:00:00Z")
    private val authority = FakeAuthAuthority()
    private val auth = FakeAuthentication(authority).apply { signIn(owner) }
    private val remote = FakeRemoteAccidentDataSource(authority)

    private class Store(initial: List<AccidentRecord> = emptyList()) : AccidentSyncStore {
        private val contents = initial.associateBy { it.ownerUserId to it.event.eventId }.toMutableMap()
        override fun records() = contents.values.toList()
        override fun applySync(expectedLocalVersion: Long, record: AccidentRecord): Boolean {
            val key = record.ownerUserId to record.event.eventId
            if ((contents[key]?.sync?.localVersion ?: 0L) != expectedLocalVersion) return false
            contents[key] = record
            return true
        }
        fun record(eventId: UUID, userId: UUID? = null) = records().single {
            it.event.eventId == eventId && (userId == null || it.ownerUserId == userId)
        }
        fun edit(eventId: UUID, change: (AccidentRecord) -> AccidentRecord) {
            val before = record(eventId)
            val after = change(before)
            check(applySync(before.sync.localVersion, after.copy(sync = after.sync.copy(
                localVersion = before.sync.localVersion + 1,
                modifiedAt = after.sync.modifiedAt.plusSeconds(1),
                status = SyncStatus.PENDING,
            ))))
        }
    }

    private fun record(id: UUID = UUID.randomUUID(), userId: UUID = owner) = AccidentRecord(
        event = AccidentEvent(id, time, vehicleSnapshot = VehicleState()),
        ownerUserId = userId,
        createdByDeviceId = vehicle,
        sync = AccidentSyncMetadata(modifiedAt = time, modifiedByDeviceId = vehicle),
    )
    private fun coordinator(store: Store, adapter: RemoteAccidentDataSource = remote, deviceId: UUID = vehicle,
                            authentication: Authentication = auth) = SyncCoordinator(store, authentication, adapter, deviceId)
    private suspend fun remoteRecords(session: AuthenticatedSession = auth.currentSession()!!) =
        (remote.records(session) as RemoteReadResult.Success).records

    @Test fun `vehicle record reaches mobile once and mobile edits return to vehicle`() = runTest {
        val original = record()
        val vehicleStore = Store(listOf(original))
        val mobileStore = Store()
        assertEquals(SyncRunStatus.COMPLETE, coordinator(vehicleStore).synchronize().status)
        assertEquals(1, coordinator(mobileStore, deviceId = mobile).synchronize().pulled)
        repeat(2) { assertEquals(0, coordinator(mobileStore, deviceId = mobile).synchronize().pulled) }
        assertEquals(original.event.eventId, mobileStore.records().single().event.eventId)

        mobileStore.edit(original.event.eventId) { it.copy(event = it.event.copy(status = AccidentStatus.ASSISTANCE)) }
        assertEquals(SyncRunStatus.COMPLETE, coordinator(mobileStore, deviceId = mobile).synchronize().status)
        assertEquals(1, coordinator(vehicleStore).synchronize().pulled)
        assertEquals(AccidentStatus.ASSISTANCE, vehicleStore.records().single().event.status)
        assertEquals(2L, vehicleStore.records().single().sync.remoteVersion)
    }

    @Test fun `network interruption preserves content and retries without duplicate event`() = runTest {
        val original = record()
        val local = Store(listOf(original))
        remote.available = false
        assertEquals(SyncRunStatus.RETRY, coordinator(local).synchronize().status)
        assertEquals(original.event, local.records().single().event)
        assertEquals(SyncStatus.RETRY, local.records().single().sync.status)
        remote.available = true
        assertEquals(SyncRunStatus.COMPLETE, coordinator(local).synchronize().status)
        repeat(2) { coordinator(local).synchronize() }
        assertEquals(1, remoteRecords().size)
        assertEquals(1L, remoteRecords().single().sync.remoteVersion)
    }

    @Test fun `lost acknowledgment retries same bound operation after queue status changes`() = runTest {
        val local = Store(listOf(record()))
        var failed = false
        val lostAck = object : RemoteAccidentDataSource by remote {
            override suspend fun upsert(session: AuthenticatedSession, record: AccidentRecord,
                                        expectedRemoteVersion: Long, operationId: String): RemoteWriteResult {
                val result = remote.upsert(session, record, expectedRemoteVersion, operationId)
                if (!failed) { failed = true; throw IOException("simulated interruption") }
                return result
            }
            override suspend fun records(session: AuthenticatedSession) = RemoteReadResult.Unavailable
        }
        assertEquals(SyncRunStatus.RETRY, coordinator(local, lostAck).synchronize().status)
        assertEquals(SyncStatus.RETRY, local.records().single().sync.status)
        assertEquals(SyncRunStatus.COMPLETE, coordinator(local).synchronize().status)
        assertEquals(1L, remoteRecords().single().sync.remoteVersion)
    }

    @Test fun `operation replay cannot substitute payload or expected revision`() = runTest {
        val session = auth.currentSession()!!
        val original = record()
        assertTrue(remote.upsert(session, original, 0, "op") is RemoteWriteResult.Applied)
        assertTrue(remote.upsert(session, original, 0, "op") is RemoteWriteResult.Applied)
        val changed = original.copy(event = original.event.copy(status = AccidentStatus.CONFIRMED))
        assertEquals(RemoteWriteResult.InvalidOperation, remote.upsert(session, changed, 0, "op"))
        assertEquals(RemoteWriteResult.InvalidOperation, remote.upsert(session, original, 1, "op"))
        assertEquals(original.event, remoteRecords().single().event)
        assertEquals(1L, remoteRecords().single().sync.remoteVersion)
    }

    @Test fun `server identity rejects forged revoked and foreign issuer sessions`() = runTest {
        val issued = auth.currentSession()!!
        val forged = AuthenticatedSession(issued.userId, issued.credential)
        assertEquals(RemoteReadResult.AccessDenied, remote.records(forged))
        assertEquals(RemoteWriteResult.AccessDenied, remote.upsert(forged, record(), 0, "forged"))
        val otherIssuer = FakeAuthentication(FakeAuthAuthority()).apply { signIn(owner) }
        assertEquals(RemoteReadResult.AccessDenied, remote.records(otherIssuer.currentSession()!!))
        auth.signOut()
        assertEquals(RemoteReadResult.AccessDenied, remote.records(issued))
    }

    @Test fun `identical event UUID remains isolated by authenticated user namespace`() = runTest {
        val id = UUID.randomUUID()
        val first = record(id)
        val secondAuth = FakeAuthentication(authority).apply { signIn(otherOwner) }
        val second = record(id, otherOwner).copy(event = first.event.copy(status = AccidentStatus.FALSE_ALARM))
        assertTrue(remote.upsert(auth.currentSession()!!, first, 0, "same-op") is RemoteWriteResult.Applied)
        assertEquals(RemoteWriteResult.AccessDenied, remote.upsert(auth.currentSession()!!, second, 0, "attack"))
        assertTrue(remote.upsert(secondAuth.currentSession()!!, second, 0, "same-op") is RemoteWriteResult.Applied)
        assertEquals(first.event, remoteRecords().single().event)
        assertEquals(second.event, remoteRecords(secondAuth.currentSession()!!).single().event)
        val local = Store(listOf(first))
        assertEquals(SyncRunStatus.COMPLETE, coordinator(local, authentication = secondAuth).synchronize().status)
        assertEquals(2, local.records().size)
        assertEquals(first, local.record(id, owner))
    }

    @Test fun `simultaneous false alarm and confirmation retains conflicting local decision`() = runTest {
        val original = record()
        val first = Store(listOf(original))
        val second = Store()
        coordinator(first).synchronize()
        coordinator(second, deviceId = mobile).synchronize()
        first.edit(original.event.eventId) { it.copy(event = it.event.copy(status = AccidentStatus.FALSE_ALARM)) }
        second.edit(original.event.eventId) { it.copy(event = it.event.copy(status = AccidentStatus.ASSISTANCE)) }
        coordinator(first).synchronize()
        val result = coordinator(second, deviceId = mobile).synchronize()
        assertEquals(SyncRunStatus.CONFLICT, result.status)
        assertEquals(AccidentStatus.ASSISTANCE, second.records().single().event.status)
        assertEquals(SyncStatus.CONFLICT, second.records().single().sync.status)
        assertEquals(AccidentStatus.FALSE_ALARM, remoteRecords().single().event.status)
    }

    @Test fun `editing during suspended write cannot be overwritten by stale acknowledgment`() = runTest {
        val original = record()
        val local = Store(listOf(original))
        val editDuringWrite = object : RemoteAccidentDataSource by remote {
            override suspend fun upsert(session: AuthenticatedSession, record: AccidentRecord,
                                        expectedRemoteVersion: Long, operationId: String): RemoteWriteResult {
                val response = remote.upsert(session, record, expectedRemoteVersion, operationId)
                local.edit(original.event.eventId) { it.copy(event = it.event.copy(status = AccidentStatus.FALSE_ALARM)) }
                return response
            }
        }
        coordinator(local, editDuringWrite).synchronize()
        assertEquals(2L, local.records().single().sync.localVersion)
        assertEquals(AccidentStatus.FALSE_ALARM, local.records().single().event.status)
        assertEquals(SyncStatus.CONFLICT, local.records().single().sync.status)
        assertEquals(AccidentStatus.SUSPECTED, remoteRecords().single().event.status)
    }

    @Test fun `tombstone conflicts with offline edit and cannot silently resurrect`() = runTest {
        val original = record()
        val deleting = Store(listOf(original))
        val editing = Store()
        coordinator(deleting).synchronize()
        coordinator(editing, deviceId = mobile).synchronize()
        deleting.edit(original.event.eventId) { it.copy(deletedAt = time.plusSeconds(1)) }
        editing.edit(original.event.eventId) { it.copy(event = it.event.copy(status = AccidentStatus.ASSISTANCE)) }
        coordinator(deleting).synchronize()
        assertEquals(SyncRunStatus.CONFLICT, coordinator(editing, deviceId = mobile).synchronize().status)
        assertNull(editing.records().single().deletedAt)
        assertNotNull(remoteRecords().single().deletedAt)
        val tombstone = remoteRecords().single()
        assertTrue(remote.upsert(auth.currentSession()!!, tombstone.copy(deletedAt = null),
            tombstone.sync.remoteVersion, "undelete") is RemoteWriteResult.Conflict)
    }

    @Test fun `clean local record receives remote tombstone without removing sync history`() = runTest {
        val first = Store(listOf(record()))
        val second = Store()
        coordinator(first).synchronize()
        coordinator(second, deviceId = mobile).synchronize()
        val id = first.records().single().event.eventId
        first.edit(id) { it.copy(deletedAt = time.plusSeconds(1)) }
        coordinator(first).synchronize()
        assertEquals(1, coordinator(second, deviceId = mobile).synchronize().pulled)
        assertNotNull(second.records().single().deletedAt)
        assertEquals(SyncStatus.SYNCED, second.records().single().sync.status)
    }

    @Test fun `same evidence UUID with incompatible metadata remains conflict`() = runTest {
        val original = record()
        val first = Store(listOf(original))
        val second = Store()
        coordinator(first).synchronize()
        coordinator(second, deviceId = mobile).synchronize()
        val proof = EvidenceMetadata(UUID.randomUUID(), original.event.eventId, EvidenceKind.VIDEO, vehicle, time, "video/mp4")
        first.edit(original.event.eventId) { it.copy(evidence = listOf(proof)) }
        second.edit(original.event.eventId) { it.copy(evidence = listOf(proof.copy(mimeType = "image/png"))) }
        coordinator(first).synchronize()
        assertEquals(SyncRunStatus.CONFLICT, coordinator(second, deviceId = mobile).synchronize().status)
        assertEquals("image/png", second.records().single().evidence.single().mimeType)
    }

    @Test fun `malicious owner pull and duplicate contradictory revisions are rejected`() = runTest {
        val foreign = record(userId = otherOwner).let { it.copy(sync = it.sync.copy(remoteVersion = 1)) }
        val malicious = object : RemoteAccidentDataSource by remote {
            override suspend fun records(session: AuthenticatedSession) = RemoteReadResult.Success(listOf(foreign))
        }
        val local = Store()
        assertEquals(SyncRunStatus.BLOCKED, coordinator(local, malicious).synchronize().status)
        assertTrue(local.records().isEmpty())
        val own = record().let { it.copy(sync = it.sync.copy(remoteVersion = 1)) }
        val duplicate = object : RemoteAccidentDataSource by remote {
            override suspend fun records(session: AuthenticatedSession) = RemoteReadResult.Success(listOf(
                own, own.copy(event = own.event.copy(status = AccidentStatus.FALSE_ALARM)),
            ))
        }
        assertEquals(SyncRunStatus.BLOCKED, coordinator(local, duplicate).synchronize().status)
        assertTrue(local.records().isEmpty())
    }

    @Test fun `auth absence and external mode never issue remote operations`() = runTest {
        var calls = 0
        val external = object : RemoteAccidentDataSource by remote {
            override val mode = RemoteMode.EXTERNAL
            override suspend fun records(session: AuthenticatedSession): RemoteReadResult { calls++; error("must not call") }
            override suspend fun upsert(session: AuthenticatedSession, record: AccidentRecord,
                                        expectedRemoteVersion: Long, operationId: String): RemoteWriteResult {
                calls++; error("must not call")
            }
        }
        val local = Store(listOf(record()))
        assertEquals(SyncRunStatus.BLOCKED, coordinator(local, external).synchronize().status)
        assertEquals(0, calls)
        auth.signOut()
        assertEquals(SyncRunStatus.AUTH_REQUIRED, coordinator(local, external).synchronize().status)
        assertEquals(0, calls)
    }

    @Test fun `auth switch during remote await does not apply response to another user`() = runTest {
        val local = Store(listOf(record()))
        val switched = object : RemoteAccidentDataSource by remote {
            override suspend fun upsert(session: AuthenticatedSession, record: AccidentRecord,
                                        expectedRemoteVersion: Long, operationId: String): RemoteWriteResult {
                val response = remote.upsert(session, record, expectedRemoteVersion, operationId)
                auth.signIn(otherOwner)
                return response
            }
        }
        assertEquals(SyncRunStatus.AUTH_REQUIRED, coordinator(local, switched).synchronize().status)
        assertEquals(SyncStatus.LOCAL_ONLY, local.records().single().sync.status)
        assertTrue(remoteRecords().isEmpty())
    }

    @Test fun `coroutine cancellation propagates and preserves local contents`() = runTest {
        val original = record()
        val local = Store(listOf(original))
        val cancelled = object : RemoteAccidentDataSource by remote {
            override suspend fun upsert(session: AuthenticatedSession, record: AccidentRecord,
                                        expectedRemoteVersion: Long, operationId: String): RemoteWriteResult {
                throw CancellationException("cancelled")
            }
        }
        try {
            coordinator(local, cancelled).synchronize()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(original, local.records().single())
        }
    }

    @Test fun `valid revision does not authorize status regression or immutable event replacement`() = runTest {
        val original = record().let { it.copy(event = it.event.copy(status = AccidentStatus.ASSISTANCE)) }
        val session = auth.currentSession()!!
        assertTrue(remote.upsert(session, original, 0, "create") is RemoteWriteResult.Applied)
        val attacks = listOf(
            original.copy(event = original.event.copy(status = AccidentStatus.SUSPECTED)),
            original.copy(event = original.event.copy(origin = EventOrigin.MOBILE_MANUAL)),
            original.copy(event = original.event.copy(occurredAt = time.plusSeconds(1))),
            original.copy(event = original.event.copy(source = DataSource.PLEOS)),
            original.copy(createdByDeviceId = mobile),
        )
        attacks.forEachIndexed { index, attack ->
            assertTrue(remote.upsert(session, attack, 1, "attack-$index") is RemoteWriteResult.Conflict)
        }
        assertEquals(original.event, remoteRecords().single().event)
        assertEquals(1L, remoteRecords().single().sync.remoteVersion)
    }

    @Test fun `valid revision permits evidence and guide append but rejects erasure or replacement`() = runTest {
        val original = record()
        val proof = EvidenceMetadata(UUID.randomUUID(), original.event.eventId, EvidenceKind.PHOTO, vehicle, time, "image/png")
        val action = AssistanceAction(original.event.eventId, AssistanceStep.OCCUPANT_SAFETY, time)
        val existing = original.copy(event = original.event.copy(status = AccidentStatus.ASSISTANCE),
            evidence = listOf(proof), assistance = listOf(action))
        val session = auth.currentSession()!!
        remote.upsert(session, existing, 0, "create")
        val attacks = listOf(
            existing.copy(evidence = emptyList()),
            existing.copy(evidence = listOf(proof.copy(mimeType = "video/mp4"))),
            existing.copy(assistance = emptyList()),
            existing.copy(assistance = listOf(action.copy(completedAt = time.plusSeconds(1)))),
        )
        attacks.forEachIndexed { index, attack ->
            assertTrue(remote.upsert(session, attack, 1, "attack-$index") is RemoteWriteResult.Conflict)
        }
        val appended = existing.copy(
            evidence = listOf(proof, proof.copy(evidenceId = UUID.randomUUID())),
            assistance = listOf(action, action.copy(step = AssistanceStep.SECONDARY_COLLISION)),
        )
        assertTrue(remote.upsert(session, appended, 1, "append") is RemoteWriteResult.Applied)
        assertEquals(2, remoteRecords().single().evidence.size)
        assertEquals(2, remoteRecords().single().assistance.size)
    }

    @Test fun `persistent repository sync preserves ordered guide across two devices and reopen`() = runTest {
        val vehicleLocal = InMemoryAccidentLocalDataSource(owner)
        val mobileLocal = InMemoryAccidentLocalDataSource(owner)
        val first = PersistentAccidentRepository(vehicleLocal, owner, vehicle)
        val second = PersistentAccidentRepository(mobileLocal, owner, mobile)
        val event = first.suspect(VehicleState(), ConnectionState.UNKNOWN)
        first.confirm(event.eventId)
        first.acknowledge(event.eventId, AssistanceStep.OCCUPANT_SAFETY)
        assertEquals(SyncRunStatus.COMPLETE, SyncCoordinator(first, auth, remote, vehicle).synchronize().status)
        assertEquals(SyncRunStatus.COMPLETE, SyncCoordinator(second, auth, remote, mobile).synchronize().status)
        second.acknowledge(event.eventId, AssistanceStep.SECONDARY_COLLISION)
        SyncCoordinator(second, auth, remote, mobile).synchronize()
        SyncCoordinator(first, auth, remote, vehicle).synchronize()
        val reopened = PersistentAccidentRepository(vehicleLocal, owner, vehicle)
        assertEquals(1, reopened.records().size)
        assertEquals(2, reopened.state.value.assistance.size)
        assertEquals(SyncStatus.SYNCED, reopened.records().single().sync.status)
        assertEquals(first.state.value, reopened.state.value)
    }

    @Test fun `persistent fixed profile rejects different authenticated user before remote access`() = runTest {
        val local = InMemoryAccidentLocalDataSource(owner)
        val repository = PersistentAccidentRepository(local, owner, vehicle)
        val event = repository.suspect(VehicleState(), ConnectionState.UNKNOWN)
        var calls = 0
        val observed = object : RemoteAccidentDataSource by remote {
            override suspend fun records(session: AuthenticatedSession): RemoteReadResult { calls++; return remote.records(session) }
            override suspend fun upsert(session: AuthenticatedSession, record: AccidentRecord,
                                        expectedRemoteVersion: Long, operationId: String): RemoteWriteResult {
                calls++; return remote.upsert(session, record, expectedRemoteVersion, operationId)
            }
        }
        auth.signIn(otherOwner)
        assertEquals(SyncRunStatus.BLOCKED, SyncCoordinator(repository, auth, observed, vehicle).synchronize().status)
        assertEquals(0, calls)
        assertEquals(event, repository.records().single().event)
    }

    @Test fun `storage failure returns safe blocked result without uploading records`() = runTest {
        val failing = object : AccidentSyncStore {
            override val ownerUserId = owner
            override fun records(): List<AccidentRecord> = error("private damaged payload")
            override fun applySync(expectedLocalVersion: Long, record: AccidentRecord): Boolean = error("private database path")
        }
        val result = SyncCoordinator(failing, auth, remote, vehicle).synchronize()
        assertEquals(SyncRunStatus.BLOCKED, result.status)
        assertFalse(result.toString().contains("private"))
        assertTrue(remoteRecords().isEmpty())
    }

    @Test fun `not requested baseline accepts analysis request and unavailable result completed offline`() = runTest {
        completeOfflineBaseline(PredictionStatus.UNAVAILABLE)
    }

    @Test fun `not requested baseline accepts available mock result completed offline`() = runTest {
        completeOfflineBaseline(PredictionStatus.AVAILABLE)
    }

    private suspend fun completeOfflineBaseline(result: PredictionStatus) {
        val original = record().let { it.copy(
            event = it.event.copy(status = AccidentStatus.ASSISTANCE),
            prediction = AccidentPrediction(it.event.eventId, PredictionStatus.NOT_REQUESTED),
        ) }
        assertTrue(remote.upsert(auth.currentSession()!!, original, 0, "baseline") is RemoteWriteResult.Applied)
        val local = InMemoryAccidentLocalDataSource(owner)
        val repository = PersistentAccidentRepository(local, owner, vehicle)
        val sync = SyncCoordinator(repository, auth, remote, vehicle)
        assertEquals(SyncRunStatus.COMPLETE, sync.synchronize().status)
        AssistanceStep.entries.forEach { repository.acknowledge(original.event.eventId, it) }
        repository.beginAnalysis(original.event.eventId)
        val prediction = if (result == PredictionStatus.AVAILABLE) AccidentPrediction(
            original.event.eventId, result, accidentType = "Mock example", ownFaultPercent = 0,
            otherFaultPercent = 100, rationale = "Offline fixture", modelLabel = "Mock fixture",
        ) else AccidentPrediction(original.event.eventId, result)
        repository.finishAnalysis(original.event.eventId, prediction)
        assertEquals(SyncRunStatus.COMPLETE, sync.synchronize().status)
        assertEquals(2L, remoteRecords().single().sync.remoteVersion)
        assertEquals(prediction, remoteRecords().single().prediction)
        assertEquals(DataSource.MOCK, remoteRecords().single().prediction!!.source)
        assertEquals(AssistanceStep.entries.size, remoteRecords().single().assistance.size)
    }

    @Test fun `inconsistent terminal analysis cannot be inserted or pulled`() = runTest {
        val original = record()
        val unavailable = original.copy(event = original.event.copy(status = AccidentStatus.UNAVAILABLE),
            prediction = AccidentPrediction(original.event.eventId, PredictionStatus.UNAVAILABLE))
        val contradictory = unavailable.copy(
            assistance = AssistanceStep.entries.map { AssistanceAction(original.event.eventId, it, time) },
            prediction = AccidentPrediction(original.event.eventId, PredictionStatus.PENDING),
        )
        val session = auth.currentSession()!!
        assertEquals(RemoteWriteResult.InvalidOperation, remote.upsert(session, unavailable, 0, "missing-guide"))
        assertEquals(RemoteWriteResult.InvalidOperation, remote.upsert(session, contradictory, 0, "wrong-result"))
        val corrupt = object : RemoteAccidentDataSource by remote {
            override suspend fun records(session: AuthenticatedSession) = RemoteReadResult.Success(listOf(
                contradictory.copy(sync = contradictory.sync.copy(remoteVersion = 1)),
            ))
        }
        val local = Store()
        assertEquals(SyncRunStatus.BLOCKED, coordinator(local, corrupt).synchronize().status)
        assertTrue(local.records().isEmpty())
        assertTrue(remoteRecords().isEmpty())
    }

    @Test fun `existing terminal analysis is immutable even when replacement fields are consistent`() = runTest {
        val original = record()
        val completed = original.copy(
            event = original.event.copy(status = AccidentStatus.UNAVAILABLE),
            assistance = AssistanceStep.entries.map { AssistanceAction(original.event.eventId, it, time) },
            prediction = AccidentPrediction(original.event.eventId, PredictionStatus.UNAVAILABLE),
        )
        val session = auth.currentSession()!!
        assertTrue(remote.upsert(session, completed, 0, "terminal") is RemoteWriteResult.Applied)
        val replacement = completed.copy(prediction = AccidentPrediction(original.event.eventId, PredictionStatus.FAILED))
        assertTrue(remote.upsert(session, replacement, 1, "replacement") is RemoteWriteResult.Conflict)
        assertEquals(PredictionStatus.UNAVAILABLE, remoteRecords().single().prediction!!.status)
        assertEquals(1L, remoteRecords().single().sync.remoteVersion)
    }
}
