package com.whitebearcode02.checkmate.vehicle.data.sync

import com.whitebearcode02.checkmate.vehicle.data.auth.Authentication
import com.whitebearcode02.checkmate.vehicle.data.remote.RemoteAccidentDataSource
import com.whitebearcode02.checkmate.vehicle.data.remote.RemoteMode
import com.whitebearcode02.checkmate.vehicle.data.remote.RemoteReadResult
import com.whitebearcode02.checkmate.vehicle.data.remote.RemoteWriteResult
import com.whitebearcode02.checkmate.vehicle.data.remote.sameContent
import com.whitebearcode02.checkmate.vehicle.data.remote.canAdvanceRecord
import com.whitebearcode02.checkmate.vehicle.data.remote.hasConsistentProgress
import com.whitebearcode02.checkmate.vehicle.data.repository.AccidentSyncStore
import com.whitebearcode02.checkmate.vehicle.domain.model.AccidentRecord
import com.whitebearcode02.checkmate.vehicle.domain.model.SyncStatus
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncRunStatus { AUTH_REQUIRED, COMPLETE, RETRY, CONFLICT, BLOCKED }

data class SyncSummary(
    val status: SyncRunStatus,
    val pushed: Int = 0,
    val pulled: Int = 0,
    val conflicts: Int = 0,
)

/**
 * Explicit offline simulation, never started by the UI or a background worker.
 * External adapters remain disabled until authentication, consent, backend rules and cost are reviewed.
 * Concurrent differences are retained as conflicts; timestamps never choose a winner.
 * Call from Dispatchers.IO when the store is backed by Room's blocking local API.
 */
class SyncCoordinator(
    private val store: AccidentSyncStore,
    private val authentication: Authentication,
    private val remote: RemoteAccidentDataSource,
    private val deviceId: UUID,
) {
    private val mutex = Mutex()
    private val dirty = setOf(SyncStatus.LOCAL_ONLY, SyncStatus.PENDING, SyncStatus.RETRY)

    suspend fun synchronize(): SyncSummary = mutex.withLock {
        try {
            synchronizeSession()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Storage failures have no unsafe fallback and must not expose record content in errors.
            SyncSummary(SyncRunStatus.BLOCKED)
        }
    }

    private suspend fun synchronizeSession(): SyncSummary {
        val session = authentication.currentSession() ?: return SyncSummary(SyncRunStatus.AUTH_REQUIRED)
        if (store.ownerUserId?.let { it != session.userId } == true || remote.mode != RemoteMode.FAKE) {
            return SyncSummary(SyncRunStatus.BLOCKED)
        }
        var pushed = 0
        var pulled = 0
        var conflicts = 0
        var retry = false
        var blocked = false

        fun currentSession() = authentication.currentSession() === session
        fun owned() = store.records().filter { it.ownerUserId == session.userId }
        fun mark(record: AccidentRecord, status: SyncStatus): Boolean =
            store.applySync(record.sync.localVersion, record.copy(sync = record.sync.copy(status = status)))
        fun validResponse(record: AccidentRecord, eventId: UUID): Boolean =
            record.ownerUserId == session.userId && record.event.eventId == eventId &&
                record.sync.remoteVersion > 0 && hasConsistentProgress(record)
        fun accept(local: AccidentRecord, received: AccidentRecord): Boolean = store.applySync(
            local.sync.localVersion,
            local.copy(sync = local.sync.copy(remoteVersion = received.sync.remoteVersion, status = SyncStatus.SYNCED)),
        )

        for (local in owned().filter { it.sync.status in dirty }) {
            if (!currentSession()) return SyncSummary(SyncRunStatus.AUTH_REQUIRED, pushed, pulled, conflicts)
            val operationId = "$deviceId:${local.event.eventId}:${local.sync.localVersion}"
            val response = try {
                remote.upsert(session, local, local.sync.remoteVersion, operationId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                RemoteWriteResult.Unavailable
            }
            if (!currentSession()) return SyncSummary(SyncRunStatus.AUTH_REQUIRED, pushed, pulled, conflicts)
            when (response) {
                is RemoteWriteResult.Applied -> {
                    if (!validResponse(response.record, local.event.eventId) || !local.sameContent(response.record) ||
                        response.record.sync.remoteVersion <= local.sync.remoteVersion) {
                        mark(local, SyncStatus.BLOCKED)
                        blocked = true
                    } else if (accept(local, response.record)) pushed++ else retry = true
                }
                is RemoteWriteResult.Conflict -> {
                    if (!validResponse(response.record, local.event.eventId)) {
                        mark(local, SyncStatus.BLOCKED)
                        blocked = true
                    } else if (response.record.sync.remoteVersion >= local.sync.remoteVersion && local.sameContent(response.record)) {
                        if (accept(local, response.record)) pushed++ else retry = true
                    } else {
                        if (mark(local, SyncStatus.CONFLICT)) conflicts++ else retry = true
                    }
                }
                RemoteWriteResult.Unavailable -> {
                    mark(local, SyncStatus.RETRY)
                    retry = true
                }
                RemoteWriteResult.AccessDenied, RemoteWriteResult.InvalidOperation -> {
                    mark(local, SyncStatus.BLOCKED)
                    blocked = true
                }
            }
        }

        if (!currentSession()) return SyncSummary(SyncRunStatus.AUTH_REQUIRED, pushed, pulled, conflicts)
        val read = try {
            remote.records(session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            RemoteReadResult.Unavailable
        }
        if (!currentSession()) return SyncSummary(SyncRunStatus.AUTH_REQUIRED, pushed, pulled, conflicts)
        when (read) {
            RemoteReadResult.AccessDenied -> blocked = true
            RemoteReadResult.Unavailable -> retry = true
            is RemoteReadResult.Success -> {
                // Reject forged owners before looking up an event, including identical UUIDs across users.
                if (read.records.any { it.ownerUserId != session.userId || it.sync.remoteVersion <= 0 || !hasConsistentProgress(it) }) {
                    blocked = true
                } else for ((_, duplicates) in read.records.groupBy { it.event.eventId }) {
                    val received = duplicates.first()
                    if (duplicates.any { !it.sameContent(received) || it.sync.remoteVersion != received.sync.remoteVersion }) {
                        blocked = true
                        continue
                    }
                    val local = owned().firstOrNull { it.event.eventId == received.event.eventId }
                    if (local == null) {
                        val inserted = received.copy(sync = received.sync.copy(localVersion = 1, status = SyncStatus.SYNCED))
                        if (store.applySync(0, inserted)) pulled++ else retry = true
                        continue
                    }
                    if (local.sync.status == SyncStatus.CONFLICT || local.sync.status == SyncStatus.BLOCKED) continue
                    if (received.sync.remoteVersion < local.sync.remoteVersion) continue
                    if (local.sameContent(received)) {
                        if (received.sync.remoteVersion != local.sync.remoteVersion || local.sync.status != SyncStatus.SYNCED) {
                            if (!accept(local, received)) retry = true
                        }
                    } else if (received.sync.remoteVersion == local.sync.remoteVersion) {
                        // Dirty local content may legitimately differ from its baseline. A clean mismatch cannot.
                        if (local.sync.status == SyncStatus.SYNCED) {
                            if (mark(local, SyncStatus.CONFLICT)) conflicts++ else retry = true
                        }
                    } else if (local.sync.status in dirty || !canAdvanceRecord(local, received)) {
                        if (mark(local, SyncStatus.CONFLICT)) conflicts++ else retry = true
                    } else if (local.sync.localVersion == Long.MAX_VALUE) {
                        mark(local, SyncStatus.BLOCKED)
                        blocked = true
                    } else {
                        val updated = received.copy(sync = received.sync.copy(
                            localVersion = local.sync.localVersion + 1,
                            status = SyncStatus.SYNCED,
                        ))
                        if (store.applySync(local.sync.localVersion, updated)) pulled++ else retry = true
                    }
                }
            }
        }
        val finalRecords = owned()
        val status = when {
            blocked || finalRecords.any { it.sync.status == SyncStatus.BLOCKED } -> SyncRunStatus.BLOCKED
            conflicts > 0 || finalRecords.any { it.sync.status == SyncStatus.CONFLICT } -> SyncRunStatus.CONFLICT
            retry || finalRecords.any { it.sync.status in dirty } -> SyncRunStatus.RETRY
            else -> SyncRunStatus.COMPLETE
        }
        return SyncSummary(status, pushed, pulled, conflicts)
    }
}
