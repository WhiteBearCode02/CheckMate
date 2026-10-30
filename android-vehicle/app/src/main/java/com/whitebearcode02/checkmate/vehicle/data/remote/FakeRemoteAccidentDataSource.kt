package com.whitebearcode02.checkmate.vehicle.data.remote

import com.whitebearcode02.checkmate.vehicle.data.auth.AuthenticatedSession
import com.whitebearcode02.checkmate.vehicle.data.auth.FakeAuthAuthority
import com.whitebearcode02.checkmate.vehicle.domain.model.AccidentRecord
import com.whitebearcode02.checkmate.vehicle.domain.model.SyncStatus
import java.util.UUID

/** In-memory, offline server simulator. No Firebase project, networking, or file transfer. */
class FakeRemoteAccidentDataSource(private val authority: FakeAuthAuthority) : RemoteAccidentDataSource {
    override val mode = RemoteMode.FAKE
    @Volatile var available: Boolean = true
    private val users = mutableMapOf<UUID, MutableMap<UUID, AccidentRecord>>()
    private data class Receipt(val request: AccidentRecord, val expected: Long, val applied: AccidentRecord)
    private val receipts = mutableMapOf<Pair<UUID, String>, Receipt>()

    override suspend fun records(session: AuthenticatedSession): RemoteReadResult = read(session)

    @Synchronized
    private fun read(session: AuthenticatedSession): RemoteReadResult {
        val userId = authority.verifiedUserId(session) ?: return RemoteReadResult.AccessDenied
        if (!available) return RemoteReadResult.Unavailable
        return RemoteReadResult.Success(users[userId]?.values?.map(::snapshot).orEmpty())
    }

    override suspend fun upsert(
        session: AuthenticatedSession,
        record: AccidentRecord,
        expectedRemoteVersion: Long,
        operationId: String,
    ): RemoteWriteResult = write(session, record, expectedRemoteVersion, operationId)

    @Synchronized
    private fun write(
        session: AuthenticatedSession,
        record: AccidentRecord,
        expectedRemoteVersion: Long,
        operationId: String,
    ): RemoteWriteResult {
        val userId = authority.verifiedUserId(session) ?: return RemoteWriteResult.AccessDenied
        if (record.ownerUserId != userId) return RemoteWriteResult.AccessDenied
        if (!available) return RemoteWriteResult.Unavailable
        if (operationId.isBlank() || operationId.length > 256 || expectedRemoteVersion < 0) {
            return RemoteWriteResult.InvalidOperation
        }
        if (!hasConsistentProgress(record)) return RemoteWriteResult.InvalidOperation
        val key = userId to operationId
        receipts[key]?.let { receipt ->
            // Replay is permitted only for the exact operation, never a different payload.
            val normalized = record.copy(sync = record.sync.copy(status = SyncStatus.LOCAL_ONLY))
            return if (receipt.request == normalized && receipt.expected == expectedRemoteVersion) {
                RemoteWriteResult.Applied(snapshot(receipt.applied))
            } else RemoteWriteResult.InvalidOperation
        }
        val bucket = users.getOrPut(userId) { mutableMapOf() }
        val current = bucket[record.event.eventId]
        if ((current?.sync?.remoteVersion ?: 0L) != expectedRemoteVersion) {
            return current?.let { RemoteWriteResult.Conflict(snapshot(it)) } ?: RemoteWriteResult.InvalidOperation
        }
        if (expectedRemoteVersion == Long.MAX_VALUE) return RemoteWriteResult.InvalidOperation
        if (current != null && !canAdvanceRecord(current, record)) return RemoteWriteResult.Conflict(snapshot(current))
        val applied = snapshot(record).copy(sync = record.sync.copy(
            remoteVersion = expectedRemoteVersion + 1,
            status = SyncStatus.SYNCED,
        ))
        bucket[record.event.eventId] = applied
        receipts[key] = Receipt(snapshot(record).copy(sync = record.sync.copy(status = SyncStatus.LOCAL_ONLY)), expectedRemoteVersion, applied)
        return RemoteWriteResult.Applied(snapshot(applied))
    }

    private fun snapshot(record: AccidentRecord): AccidentRecord = record.copy(
        event = record.event.copy(vehicleSnapshot = record.event.vehicleSnapshot.copy(
            capabilities = record.event.vehicleSnapshot.capabilities.toSet(),
            readings = record.event.vehicleSnapshot.readings.toMap(),
        )),
        assistance = record.assistance.toList(),
        evidence = record.evidence.toList(),
    )
}
