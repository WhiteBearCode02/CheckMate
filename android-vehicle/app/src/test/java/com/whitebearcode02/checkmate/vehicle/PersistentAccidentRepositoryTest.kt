package com.whitebearcode02.checkmate.vehicle

import com.whitebearcode02.checkmate.vehicle.data.contract.AccidentRecordJsonCodec
import com.whitebearcode02.checkmate.vehicle.data.local.AccidentLocalDataSource
import com.whitebearcode02.checkmate.vehicle.data.local.InMemoryAccidentLocalDataSource
import com.whitebearcode02.checkmate.vehicle.data.local.LocalStorageException
import com.whitebearcode02.checkmate.vehicle.data.repository.PersistentAccidentRepository
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import java.io.File
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersistentAccidentRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val clock = Clock.fixed(persistenceTime, ZoneOffset.UTC)
    private fun repository(local: AccidentLocalDataSource, user: UUID = persistenceUser) =
        PersistentAccidentRepository(local, user, persistenceDevice, clock)
    private fun suspect(repository: PersistentAccidentRepository) = repository.suspect(VehicleState(), ConnectionState.UNKNOWN)

    /** JVM durable-boundary test double. Actual Room reopen is separately covered by instrumentation. */
    private class FileLocal(private val directory: File) : AccidentLocalDataSource {
        private val codec = AccidentRecordJsonCodec()
        override fun load() = directory.listFiles()!!.filter { it.extension == "json" }.map { codec.decode(it.readText()) }
        override fun save(record: AccidentRecord) { File(directory, "${record.event.eventId}.json").writeText(codec.encode(record)) }
    }

    @Test fun diskRoundTripRestoresAccidentProgressPredictionAndSyncMetadata() {
        val directory = temporary.newFolder()
        val first = repository(FileLocal(directory))
        val event = suspect(first)
        first.confirm(event.eventId)
        AssistanceStep.entries.forEach { first.acknowledge(event.eventId, it) }
        first.beginAnalysis(event.eventId)
        first.finishAnalysis(event.eventId, AccidentPrediction(event.eventId, PredictionStatus.UNAVAILABLE))
        val reopened = repository(FileLocal(directory))
        assertEquals(first.state.value, reopened.state.value)
        assertEquals(first.records(), reopened.records())
        assertEquals(AccidentStatus.UNAVAILABLE, reopened.state.value.events.single().status)
        assertNull(reopened.state.value.activeEventId)
    }
    @Test fun restoredActiveAccidentPreventsDuplicateCreation() {
        val local = InMemoryAccidentLocalDataSource(persistenceUser)
        val first = repository(local)
        val event = suspect(first)
        val reopened = repository(local)
        assertEquals(event, suspect(reopened))
        assertEquals(1, local.load().size)
        assertEquals(1L, local.load().single().sync.localVersion)
    }
    @Test fun restoredGuideContinuesFromSavedStepAndNoOpDoesNotChangeVersion() {
        val local = InMemoryAccidentLocalDataSource(persistenceUser)
        val first = repository(local)
        val event = suspect(first)
        first.confirm(event.eventId)
        first.acknowledge(event.eventId, AssistanceStep.OCCUPANT_SAFETY)
        val reopened = repository(local)
        reopened.acknowledge(event.eventId, AssistanceStep.OCCUPANT_SAFETY)
        assertEquals(3L, reopened.records().single().sync.localVersion)
        reopened.acknowledge(event.eventId, AssistanceStep.SECONDARY_COLLISION)
        assertEquals(4L, reopened.records().single().sync.localVersion)
        assertEquals(2, reopened.state.value.assistance.size)
    }
    @Test fun failedWriteDoesNotPublishOrLoseExistingRecord() {
        val delegate = InMemoryAccidentLocalDataSource(persistenceUser)
        var fail = false
        val local = object : AccidentLocalDataSource {
            override fun load() = delegate.load()
            override fun save(record: AccidentRecord) { if (fail) error("private-path"); delegate.save(record) }
        }
        val repository = repository(local)
        val event = suspect(repository)
        val before = repository.state.value
        fail = true
        val error = assertThrows(LocalStorageException::class.java) { repository.confirm(event.eventId) }
        assertEquals(before, repository.state.value)
        assertEquals(AccidentStatus.SUSPECTED, delegate.load().single().event.status)
        assertFalse(error.toString().contains("private-path")); assertNull(error.cause)
        fail = false
        repository.confirm(event.eventId)
        assertEquals(AccidentStatus.ASSISTANCE, repository.state.value.events.single().status)
    }
    @Test fun damagedStorageFailsClosedWithoutWritingEmptyState() {
        var saves = 0
        val local = object : AccidentLocalDataSource {
            override fun load(): List<AccidentRecord> = error("private-corrupt-payload")
            override fun save(record: AccidentRecord) { saves++ }
        }
        assertThrows(LocalStorageException::class.java) { repository(local) }
        assertEquals(0, saves)
    }
    @Test fun corruptOnDiskJsonIsPreservedForRecovery() {
        val directory = temporary.newFolder()
        val original = File(directory, "damaged.json").apply { writeText("{invalid") }
        assertThrows(LocalStorageException::class.java) { repository(FileLocal(directory)) }
        assertEquals("{invalid", original.readText())
    }
    @Test fun ownerIsolationAppliesAtLoadWriteAndSyncBoundaries() {
        val record = persistenceFixture()
        val other = UUID.randomUUID()
        val local = InMemoryAccidentLocalDataSource(other)
        assertThrows(IllegalArgumentException::class.java) { local.save(record) }
        val wrongLoader = object : AccidentLocalDataSource {
            override fun load() = listOf(record)
            override fun save(record: AccidentRecord) { fail("must not save") }
        }
        assertThrows(LocalStorageException::class.java) { repository(wrongLoader, other) }
        assertThrows(IllegalArgumentException::class.java) { repository(local, other).applySync(0, record) }
    }
    @Test fun compareAndSetRejectsSyncResultAfterNewerLocalEdit() {
        val local = InMemoryAccidentLocalDataSource(persistenceUser)
        val repository = repository(local)
        val event = suspect(repository)
        val uploaded = repository.records().single()
        repository.confirm(event.eventId)
        assertFalse(repository.applySync(uploaded.sync.localVersion,
            uploaded.copy(sync = uploaded.sync.copy(remoteVersion = 1, status = SyncStatus.SYNCED))))
        assertEquals(AccidentStatus.ASSISTANCE, repository.state.value.events.single().status)
        assertEquals(2L, repository.records().single().sync.localVersion)
    }
    @Test fun repeatedRemoteDeliveryUpdatesOneRecordAndKeepsEvidenceMetadata() {
        val local = InMemoryAccidentLocalDataSource(persistenceUser)
        val repository = repository(local)
        val record = persistenceFixture()
        assertTrue(repository.applySync(0, record))
        assertFalse(repository.applySync(0, record))
        assertTrue(repository.applySync(record.sync.localVersion, record))
        assertEquals(listOf(record), repository.records())
        assertEquals(record.evidence, repository.state.value.evidence[record.event.eventId])
    }
    @Test fun secondRepositoryRefreshesBeforeCasAndCannotOverwriteNewerVersion() {
        val local = InMemoryAccidentLocalDataSource(persistenceUser)
        val first = repository(local)
        val second = repository(local)
        val event = suspect(first)
        val uploaded = first.records().single()
        second.confirm(event.eventId)
        assertFalse(first.applySync(uploaded.sync.localVersion, uploaded))
        assertEquals(AccidentStatus.ASSISTANCE, first.state.value.events.single().status)
    }
    @Test fun tombstonesPersistWithoutDisplayingDeletedAccident() {
        val local = InMemoryAccidentLocalDataSource(persistenceUser)
        val repository = repository(local)
        val deleted = persistenceFixture().copy(deletedAt = persistenceTime)
        assertTrue(repository.applySync(0, deleted))
        val reopened = repository(local)
        assertEquals(listOf(deleted), reopened.records())
        assertTrue(reopened.state.value.events.isEmpty())
    }
    @Test fun demoOriginRemainsIndependentOfPleosDataProvenance() {
        val repository = repository(InMemoryAccidentLocalDataSource(persistenceUser))
        val event = repository.suspect(VehicleState(source = DataSource.PLEOS), ConnectionState.UNKNOWN, EventOrigin.DEMO)
        assertEquals(EventOrigin.DEMO, event.origin)
        assertEquals(DataSource.MOCK, event.source)
        assertEquals(DataSource.PLEOS, event.vehicleSnapshot.source)
    }
    @Test fun manualOriginAndNoNetworkPreserveLocalOperation() {
        val repository = repository(InMemoryAccidentLocalDataSource(persistenceUser))
        val event = repository.suspect(VehicleState(source = DataSource.PLEOS), ConnectionState.DISCONNECTED)
        assertEquals(EventOrigin.VEHICLE_MANUAL, event.origin)
        assertEquals(DataSource.PLEOS, event.source)
        assertEquals(SyncStatus.LOCAL_ONLY, repository.records().single().sync.status)
        repository.confirm(event.eventId)
        assertEquals(AccidentStatus.ASSISTANCE, repository.state.value.events.single().status)
    }
}
