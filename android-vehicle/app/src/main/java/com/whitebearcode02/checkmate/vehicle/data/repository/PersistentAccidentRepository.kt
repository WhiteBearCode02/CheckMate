package com.whitebearcode02.checkmate.vehicle.data.repository

import com.whitebearcode02.checkmate.vehicle.data.local.AccidentLocalDataSource
import com.whitebearcode02.checkmate.vehicle.data.local.LocalStorageException
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Durable write-before-publish adapter around the existing, tested state machine. Call on IO. */
class PersistentAccidentRepository(
    private val local: AccidentLocalDataSource,
    private val userId: UUID,
    private val deviceId: UUID,
    private val clock: Clock = Clock.systemUTC(),
    private val eventOrigin: EventOrigin = EventOrigin.VEHICLE_MANUAL,
    private val newId: () -> UUID = UUID::randomUUID,
) : AccidentRepository, AccidentSyncStore {
    override val ownerUserId: UUID get() = userId
    private var stored = synchronized(writeLock) { loadSafely() }
    private val mutableState = MutableStateFlow(project(stored))
    override val state = mutableState.asStateFlow()

    override fun suspect(vehicle: VehicleState, phone: ConnectionState): AccidentEvent = suspect(vehicle, phone, eventOrigin)
    override fun suspect(vehicle: VehicleState, phone: ConnectionState, origin: EventOrigin): AccidentEvent =
        mutate { it.suspect(vehicle, phone, origin) }
    override fun cancelFalseAlarm(eventId: UUID) { mutate { it.cancelFalseAlarm(eventId) } }
    override fun confirm(eventId: UUID) { mutate { it.confirm(eventId) } }
    override fun acknowledge(eventId: UUID, step: AssistanceStep) { mutate { it.acknowledge(eventId, step) } }
    override fun beginAnalysis(eventId: UUID) { mutate { it.beginAnalysis(eventId) } }
    override fun finishAnalysis(eventId: UUID, prediction: AccidentPrediction) { mutate { it.finishAnalysis(eventId, prediction) } }

    override fun records(): List<AccidentRecord> = synchronized(writeLock) {
        refresh()
        stored.toList()
    }

    /** Expected zero means an insert. A newer local edit is never silently overwritten by sync. */
    override fun applySync(expectedLocalVersion: Long, record: AccidentRecord): Boolean = synchronized(writeLock) {
        require(record.ownerUserId == userId) { "Accident owner mismatch" }
        require(expectedLocalVersion >= 0)
        refresh()
        val existing = stored.firstOrNull { it.event.eventId == record.event.eventId }
        if ((existing?.sync?.localVersion ?: 0L) != expectedLocalVersion) return@synchronized false
        require(record.sync.localVersion >= expectedLocalVersion) { "Invalid local version" }
        saveSafely(record)
        stored = stored.filterNot { it.event.eventId == record.event.eventId } + record
        mutableState.value = project(stored)
        true
    }

    private fun <T> mutate(action: (InMemoryAccidentRepository) -> T): T = synchronized(writeLock) {
        refresh()
        val before = mutableState.value
        val engine = InMemoryAccidentRepository(clock, initialState = before, eventOrigin = eventOrigin, newId = newId)
        val result = action(engine)
        val after = engine.state.value
        if (before == after) return@synchronized result
        val changed = after.events.mapNotNull { event ->
            val previous = stored.firstOrNull { it.event.eventId == event.eventId }
            val assistance = after.assistance.filter { it.eventId == event.eventId }
            val prediction = after.predictions[event.eventId]
            if (previous != null && previous.event == event && previous.assistance == assistance && previous.prediction == prediction) null
            else if (previous == null) AccidentRecord(event, userId, deviceId,
                AccidentSyncMetadata(modifiedAt = Instant.now(clock), modifiedByDeviceId = deviceId), assistance, prediction)
            else previous.copy(event = event, assistance = assistance, prediction = prediction,
                sync = previous.sync.copy(localVersion = Math.addExact(previous.sync.localVersion, 1),
                    modifiedAt = Instant.now(clock), modifiedByDeviceId = deviceId,
                    status = when {
                        previous.sync.status == SyncStatus.CONFLICT -> SyncStatus.CONFLICT
                        previous.sync.remoteVersion == 0L -> SyncStatus.LOCAL_ONLY
                        else -> SyncStatus.PENDING
                    }))
        }
        // Public operations affect one aggregate, making the DAO's single-row transaction sufficient.
        check(changed.size <= 1) { "Mutation must affect one accident" }
        changed.forEach(::saveSafely)
        val changedIds = changed.map { it.event.eventId }.toSet()
        stored = stored.filterNot { it.event.eventId in changedIds } + changed
        mutableState.value = project(stored)
        result
    }

    private fun loadSafely(): List<AccidentRecord> = try {
        local.load().also { records ->
            check(records.all { it.ownerUserId == userId })
            check(records.map { it.event.eventId }.distinct().size == records.size)
        }
    } catch (_: Exception) { throw LocalStorageException() }

    private fun saveSafely(record: AccidentRecord) = try { local.save(record) }
    catch (_: Exception) { throw LocalStorageException() }

    private fun refresh() {
        stored = loadSafely()
        mutableState.value = project(stored)
    }

    private fun project(records: List<AccidentRecord>): AccidentStoreState {
        val visible = records.filter { it.deletedAt == null }
            .sortedWith(compareByDescending<AccidentRecord> { it.event.occurredAt }.thenBy { it.event.eventId })
        return AccidentStoreState(events = visible.map { it.event }, assistance = visible.flatMap { it.assistance },
            predictions = visible.mapNotNull { record -> record.prediction?.let { record.event.eventId to it } }.toMap(),
            activeEventId = visible.firstOrNull { it.event.status in activeStatuses }?.event?.eventId,
            syncMetadata = visible.associate { it.event.eventId to it.sync },
            evidence = visible.associate { it.event.eventId to it.evidence })
    }

    companion object {
        /** Serializes sync and local mutations even when repositories are opened in parallel. */
        private val writeLock = Any()
        private val activeStatuses = setOf(AccidentStatus.SUSPECTED, AccidentStatus.CONFIRMED,
            AccidentStatus.ASSISTANCE, AccidentStatus.ANALYSIS_PENDING)
    }
}
