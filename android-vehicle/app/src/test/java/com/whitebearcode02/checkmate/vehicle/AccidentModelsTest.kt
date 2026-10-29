package com.whitebearcode02.checkmate.vehicle

import com.whitebearcode02.checkmate.vehicle.domain.AccidentStateMachine
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class AccidentModelsTest {
    private val occurredAt = Instant.parse("2026-10-29T15:00:00Z")
    private fun suspected() = AccidentEvent(
        eventId = UUID.randomUUID(), occurredAt = occurredAt, vehicleSnapshot = VehicleState(),
    )

    @Test fun allowedConfirmedFlowRetainsIdentityAndSnapshot() {
        val initial = suspected()
        val confirmed = AccidentStateMachine.transition(initial, AccidentStatus.CONFIRMED)
        val assistance = AccidentStateMachine.transition(confirmed, AccidentStatus.ASSISTANCE)
        val pending = AccidentStateMachine.transition(assistance, AccidentStatus.ANALYSIS_PENDING)
        val completed = AccidentStateMachine.transition(pending, AccidentStatus.COMPLETED)
        assertEquals(initial.eventId, completed.eventId)
        assertEquals(initial.occurredAt, completed.occurredAt)
        assertEquals(initial.vehicleSnapshot, completed.vehicleSnapshot)
        assertEquals(AccidentStatus.COMPLETED, completed.status)
    }

    @Test fun falseAlarmIsRetainedAsTerminalRecord() {
        val canceled = AccidentStateMachine.transition(suspected(), AccidentStatus.FALSE_ALARM)
        assertEquals(AccidentStatus.FALSE_ALARM, canceled.status)
        assertFalse(AccidentStateMachine.canTransition(canceled.status, AccidentStatus.CONFIRMED))
        assertFalse(AccidentStateMachine.canTransition(canceled.status, AccidentStatus.NORMAL))
    }

    @Test fun unavailableIsValidAnalysisOutcome() {
        val pending = suspected().copy(status = AccidentStatus.ANALYSIS_PENDING)
        assertEquals(AccidentStatus.UNAVAILABLE, AccidentStateMachine.transition(pending, AccidentStatus.UNAVAILABLE).status)
        assertFalse(AccidentStateMachine.canTransition(AccidentStatus.UNAVAILABLE, AccidentStatus.COMPLETED))
    }

    @Test fun invalidTransitionCannotSkipConfirmation() {
        assertThrows(IllegalArgumentException::class.java) {
            AccidentStateMachine.transition(suspected(), AccidentStatus.COMPLETED)
        }
    }

    @Test fun predictionAbsentHasNoInventedPercentages() {
        val prediction = AccidentPrediction(suspected().eventId, PredictionStatus.UNAVAILABLE)
        assertNull(prediction.ownFaultPercent)
        assertNull(prediction.otherFaultPercent)
        assertThrows(IllegalArgumentException::class.java) {
            prediction.copy(ownFaultPercent = 0, otherFaultPercent = 100)
        }
    }

    @Test fun availablePredictionRequiresCompleteConsistentData() {
        val prediction = AccidentPrediction(
            eventId = suspected().eventId, status = PredictionStatus.AVAILABLE,
            accidentType = "모의 추돌", ownFaultPercent = 20, otherFaultPercent = 80,
            rationale = "화면 확인을 위한 가상 예시", modelLabel = "Mock fixture",
        )
        assertEquals(DataSource.MOCK, prediction.source)
        assertThrows(IllegalArgumentException::class.java) { prediction.copy(otherFaultPercent = 79) }
        assertThrows(IllegalArgumentException::class.java) { prediction.copy(rationale = null) }
        assertThrows(IllegalArgumentException::class.java) { prediction.copy(ownFaultPercent = null) }
    }

    @Test fun reportRejectsChildrenFromAnotherAccident() {
        val first = suspected()
        val second = suspected()
        assertThrows(IllegalArgumentException::class.java) {
            AccidentReport(first.eventId, occurredAt, prediction = AccidentPrediction(second.eventId, PredictionStatus.UNAVAILABLE))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccidentReport(first.eventId, occurredAt, assistance = listOf(AssistanceAction(second.eventId, AssistanceStep.OCCUPANT_SAFETY, occurredAt)))
        }
    }

    @Test fun referencesAreRedactedAndWireValuesRoundTrip() {
        val event = suspected()
        val evidence = Evidence(UUID.randomUUID(), event.eventId, EvidenceKind.VIDEO, occurredAt, "content://private/clip", "video/mp4")
        assertFalse(evidence.toString().contains("content://private/clip"))
        assertEquals(event.eventId, UUID.fromString(event.eventId.toString()))
        assertEquals(occurredAt, Instant.parse(occurredAt.toString()))
    }

    @Test fun sensorValuesCannotClaimUnsupportedOrInvalidData() {
        assertThrows(IllegalArgumentException::class.java) { VehicleState(speedKph = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { VehicleState(movement = VehicleMovement.PARKED) }
        assertThrows(IllegalArgumentException::class.java) { VehicleState(speedKph = Double.NaN, capabilities = setOf(VehicleCapability.SPEED)) }
        assertThrows(IllegalArgumentException::class.java) { VehicleState(speedKph = -1.0, capabilities = setOf(VehicleCapability.SPEED)) }
        assertFalse(VehicleState().permitsDetailedInteraction)
    }
}