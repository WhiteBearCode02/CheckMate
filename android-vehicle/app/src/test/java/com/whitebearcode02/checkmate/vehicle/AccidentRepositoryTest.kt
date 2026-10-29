package com.whitebearcode02.checkmate.vehicle

import com.whitebearcode02.checkmate.vehicle.data.repository.InMemoryAccidentRepository
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class AccidentRepositoryTest {
    private val timestamp = Instant.parse("2026-10-29T15:00:00Z")
    private val fixedClock = Clock.fixed(timestamp, ZoneOffset.UTC)
    private fun repository() = InMemoryAccidentRepository(clock = fixedClock)
    private fun vehicle() = VehicleState(connection = ConnectionState.FAILED, error = VehicleError.CONNECTION_FAILED)
    private fun suspect(repository: InMemoryAccidentRepository) = repository.suspect(vehicle(), ConnectionState.UNAVAILABLE)
    private fun pending(repository: InMemoryAccidentRepository): AccidentEvent {
        val event = suspect(repository)
        repository.confirm(event.eventId)
        AssistanceStep.entries.forEach { repository.acknowledge(event.eventId, it) }
        repository.beginAnalysis(event.eventId)
        return event
    }

    @Test fun suspicionCapturesIdTimestampAndConnectionSnapshot() {
        val repository = repository()
        val event = suspect(repository)
        assertEquals(AccidentStatus.SUSPECTED, event.status)
        assertEquals(timestamp, event.occurredAt)
        assertEquals(vehicle(), event.vehicleSnapshot)
        assertEquals(ConnectionState.UNAVAILABLE, event.phoneConnection)
        assertEquals(DataSource.MOCK, event.source)
        assertEquals(event.eventId, repository.state.value.activeEventId)
        assertEquals(listOf(event), repository.state.value.events)
        UUID.fromString(event.eventId.toString())
    }

    @Test fun duplicateSuspicionDoesNotReplaceOriginalSnapshotOrAddRecords() {
        val repository = repository()
        val first = suspect(repository)
        val repeated = repository.suspect(VehicleState(), ConnectionState.CONNECTED)
        assertEquals(first, repeated)
        assertEquals(1, repository.state.value.events.size)
        repository.confirm(first.eventId)
        assertEquals(first.eventId, suspect(repository).eventId)
        assertEquals(1, repository.state.value.events.size)
    }

    @Test fun cancelRetainsFalseAlarmAndNextEventGetsDifferentId() {
        val repository = repository()
        val first = suspect(repository)
        repository.cancelFalseAlarm(first.eventId)
        assertNull(repository.state.value.activeEventId)
        assertEquals(AccidentStatus.FALSE_ALARM, repository.state.value.events.single().status)
        val second = suspect(repository)
        assertNotEquals(first.eventId, second.eventId)
        assertEquals(2, repository.state.value.events.size)
    }

    @Test fun duplicateInjectedIdsAreRetriedWithoutOverwritingRecords() {
        val firstId = UUID.randomUUID()
        val nextId = UUID.randomUUID()
        val candidates = ArrayDeque(listOf(firstId, firstId, firstId, nextId))
        val repository = InMemoryAccidentRepository(fixedClock) { candidates.removeFirst() }
        val first = suspect(repository)
        repository.cancelFalseAlarm(first.eventId)
        assertEquals(nextId, suspect(repository).eventId)
        assertEquals(setOf(firstId, nextId), repository.state.value.events.map { it.eventId }.toSet())
    }

    @Test fun exhaustingDuplicateIdsFailsWithoutChangingStore() {
        val id = UUID.randomUUID()
        val repository = InMemoryAccidentRepository(fixedClock) { id }
        val event = suspect(repository)
        repository.cancelFalseAlarm(event.eventId)
        val before = repository.state.value
        assertThrows(IllegalStateException::class.java) { suspect(repository) }
        assertEquals(before, repository.state.value)
    }

    @Test fun confirmationEntersAssistanceAndGuideMustFollowOrder() {
        val repository = repository()
        val event = suspect(repository)
        repository.confirm(event.eventId)
        assertEquals(AccidentStatus.ASSISTANCE, repository.state.value.events.single().status)
        assertThrows(IllegalArgumentException::class.java) {
            repository.acknowledge(event.eventId, AssistanceStep.SECONDARY_COLLISION)
        }
        repository.acknowledge(event.eventId, AssistanceStep.OCCUPANT_SAFETY)
        repository.acknowledge(event.eventId, AssistanceStep.OCCUPANT_SAFETY)
        val action = repository.state.value.assistance.single()
        assertEquals(event.eventId, action.eventId)
        assertEquals(timestamp, action.completedAt)
        assertEquals(AssistanceStep.OCCUPANT_SAFETY, action.step)
    }

    @Test fun allSixGuideStepsAreRequiredBeforeAnalysis() {
        val repository = repository()
        val event = suspect(repository)
        repository.confirm(event.eventId)
        AssistanceStep.entries.dropLast(1).forEach { repository.acknowledge(event.eventId, it) }
        assertThrows(IllegalArgumentException::class.java) { repository.beginAnalysis(event.eventId) }
        assertTrue(repository.state.value.predictions.isEmpty())
        repository.acknowledge(event.eventId, AssistanceStep.RECORD_REVIEW)
        repository.beginAnalysis(event.eventId)
        assertEquals(AccidentStatus.ANALYSIS_PENDING, repository.state.value.events.single().status)
        val result = repository.state.value.predictions.getValue(event.eventId)
        assertEquals(PredictionStatus.PENDING, result.status)
        assertNull(result.ownFaultPercent)
        assertNull(result.otherFaultPercent)
    }

    @Test fun noResultEndsUnavailableWithoutInventingPercentages() {
        val repository = repository()
        val event = pending(repository)
        repository.finishAnalysis(event.eventId, AccidentPrediction(event.eventId, PredictionStatus.UNAVAILABLE))
        assertEquals(AccidentStatus.UNAVAILABLE, repository.state.value.events.single().status)
        assertNull(repository.state.value.activeEventId)
        assertNull(repository.state.value.predictions.getValue(event.eventId).ownFaultPercent)
        assertNull(repository.state.value.predictions.getValue(event.eventId).otherFaultPercent)
    }

    @Test fun failedAnalysisEndsUnavailableAndRetainsFailureStatus() {
        val repository = repository()
        val event = pending(repository)
        repository.finishAnalysis(event.eventId, AccidentPrediction(event.eventId, PredictionStatus.FAILED))
        assertEquals(AccidentStatus.UNAVAILABLE, repository.state.value.events.single().status)
        assertEquals(PredictionStatus.FAILED, repository.state.value.predictions.getValue(event.eventId).status)
    }

    @Test fun explicitMockPredictionCompletesOnlyItsEvent() {
        val repository = repository()
        val event = pending(repository)
        val prediction = AccidentPrediction(
            eventId = event.eventId, status = PredictionStatus.AVAILABLE, accidentType = "모의 추돌",
            ownFaultPercent = 20, otherFaultPercent = 80, rationale = "UI 검증용 고정 예시", modelLabel = "Mock fixture",
        )
        repository.finishAnalysis(event.eventId, prediction)
        assertEquals(AccidentStatus.COMPLETED, repository.state.value.events.single().status)
        assertEquals(prediction, repository.state.value.predictions[event.eventId])
        assertNull(repository.state.value.activeEventId)
    }

    @Test fun foreignOrNonterminalPredictionDoesNotMutateStore() {
        val repository = repository()
        val event = pending(repository)
        val before = repository.state.value
        assertThrows(IllegalArgumentException::class.java) {
            repository.finishAnalysis(event.eventId, AccidentPrediction(UUID.randomUUID(), PredictionStatus.UNAVAILABLE))
        }
        assertEquals(before, repository.state.value)
        assertThrows(IllegalArgumentException::class.java) {
            repository.finishAnalysis(event.eventId, AccidentPrediction(event.eventId, PredictionStatus.PENDING))
        }
        assertEquals(before, repository.state.value)
    }

    @Test fun terminalEventsAndUnknownIdsCannotBeReconfirmedOrOverwritten() {
        val repository = repository()
        val event = suspect(repository)
        repository.cancelFalseAlarm(event.eventId)
        val before = repository.state.value
        assertThrows(IllegalArgumentException::class.java) { repository.confirm(event.eventId) }
        assertThrows(IllegalArgumentException::class.java) { repository.confirm(UUID.randomUUID()) }
        assertThrows(IllegalArgumentException::class.java) { repository.acknowledge(event.eventId, AssistanceStep.OCCUPANT_SAFETY) }
        assertThrows(IllegalArgumentException::class.java) { repository.beginAnalysis(event.eventId) }
        assertEquals(before, repository.state.value)
    }
}