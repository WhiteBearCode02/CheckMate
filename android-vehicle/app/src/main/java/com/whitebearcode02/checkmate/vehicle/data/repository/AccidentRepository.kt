package com.whitebearcode02.checkmate.vehicle.data.repository

import com.whitebearcode02.checkmate.vehicle.domain.AccidentStateMachine
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccidentStoreState(
    val events: List<AccidentEvent> = emptyList(),
    val assistance: List<AssistanceAction> = emptyList(),
    val predictions: Map<UUID, AccidentPrediction> = emptyMap(),
    val activeEventId: UUID? = null,
    val syncMetadata: Map<UUID, AccidentSyncMetadata> = emptyMap(),
    val evidence: Map<UUID, List<EvidenceMetadata>> = emptyMap(),
)

/** Local API. A persistent implementation can replace this without changing the screens. */
interface AccidentRepository {
    val state: StateFlow<AccidentStoreState>
    fun suspect(vehicle: VehicleState, phone: ConnectionState): AccidentEvent
    fun suspect(vehicle: VehicleState, phone: ConnectionState, origin: EventOrigin): AccidentEvent = suspect(vehicle, phone)
    fun cancelFalseAlarm(eventId: UUID)
    fun confirm(eventId: UUID)
    fun acknowledge(eventId: UUID, step: AssistanceStep)
    fun beginAnalysis(eventId: UUID)
    fun finishAnalysis(eventId: UUID, prediction: AccidentPrediction)
}

/** Phase 1 stores demo records in memory only; nothing is uploaded or written to shared storage. */
class InMemoryAccidentRepository(
    private val clock: Clock = Clock.systemUTC(),
    initialState: AccidentStoreState = AccidentStoreState(),
    private val eventOrigin: EventOrigin = EventOrigin.DEMO,
    private val newId: () -> UUID = UUID::randomUUID,
) : AccidentRepository {
    private val mutableState = MutableStateFlow(initialState)
    override val state = mutableState.asStateFlow()

    @Synchronized
    override fun suspect(vehicle: VehicleState, phone: ConnectionState): AccidentEvent = suspect(vehicle, phone, eventOrigin)

    @Synchronized
    override fun suspect(vehicle: VehicleState, phone: ConnectionState, origin: EventOrigin): AccidentEvent {
        val snapshot = state.value
        snapshot.events.firstOrNull { it.eventId == snapshot.activeEventId }?.let { return it }
        val eventId = (1..16).asSequence().map { newId() }
            .firstOrNull { id -> snapshot.events.none { it.eventId == id } }
            ?: error("Unable to allocate a unique event ID")
        val event = AccidentEvent(
            eventId = eventId,
            occurredAt = Instant.now(clock),
            vehicleSnapshot = vehicle,
            phoneConnection = phone,
            source = if (origin == EventOrigin.DEMO) DataSource.MOCK else vehicle.source,
            origin = origin,
        )
        mutableState.value = snapshot.copy(events = listOf(event) + snapshot.events, activeEventId = eventId)
        return event
    }

    @Synchronized
    override fun cancelFalseAlarm(eventId: UUID) {
        updateEvent(eventId, AccidentStatus.FALSE_ALARM)
        mutableState.value = state.value.copy(activeEventId = null)
    }

    @Synchronized
    override fun confirm(eventId: UUID) {
        updateEvent(eventId, AccidentStatus.CONFIRMED)
        updateEvent(eventId, AccidentStatus.ASSISTANCE)
    }

    @Synchronized
    override fun acknowledge(eventId: UUID, step: AssistanceStep) {
        val snapshot = state.value
        require(event(eventId).status == AccidentStatus.ASSISTANCE) { "Assistance is not active" }
        val completed = snapshot.assistance.filter { it.eventId == eventId }.map { it.step }.toSet()
        if (step in completed) return
        require(AssistanceStep.entries.take(step.ordinal).all { it in completed }) { "Follow the guide in order" }
        mutableState.value = snapshot.copy(
            assistance = snapshot.assistance + AssistanceAction(eventId, step, Instant.now(clock)),
        )
    }

    @Synchronized
    override fun beginAnalysis(eventId: UUID) {
        val completed = state.value.assistance.filter { it.eventId == eventId }.map { it.step }.toSet()
        require(completed.containsAll(AssistanceStep.entries)) { "Complete the safety guide first" }
        updateEvent(eventId, AccidentStatus.ANALYSIS_PENDING)
        mutableState.value = state.value.copy(
            predictions = state.value.predictions + (eventId to AccidentPrediction(eventId, PredictionStatus.PENDING)),
        )
    }

    @Synchronized
    override fun finishAnalysis(eventId: UUID, prediction: AccidentPrediction) {
        require(prediction.eventId == eventId) { "Prediction belongs to another event" }
        require(prediction.status in setOf(PredictionStatus.AVAILABLE, PredictionStatus.UNAVAILABLE, PredictionStatus.FAILED)) {
            "Analysis result must be terminal"
        }
        updateEvent(eventId, if (prediction.status == PredictionStatus.AVAILABLE) AccidentStatus.COMPLETED else AccidentStatus.UNAVAILABLE)
        mutableState.value = state.value.copy(
            predictions = state.value.predictions + (eventId to prediction),
            activeEventId = null,
        )
    }

    private fun event(id: UUID) = state.value.events.firstOrNull { it.eventId == id }
        ?: throw IllegalArgumentException("Unknown event")

    private fun updateEvent(id: UUID, status: AccidentStatus) {
        val updated = AccidentStateMachine.transition(event(id), status)
        mutableState.value = state.value.copy(events = state.value.events.map { if (it.eventId == id) updated else it })
    }
}
