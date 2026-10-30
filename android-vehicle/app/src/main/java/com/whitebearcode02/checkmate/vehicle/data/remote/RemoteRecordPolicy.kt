package com.whitebearcode02.checkmate.vehicle.data.remote

import com.whitebearcode02.checkmate.vehicle.domain.AccidentStateMachine
import com.whitebearcode02.checkmate.vehicle.domain.model.AccidentRecord
import com.whitebearcode02.checkmate.vehicle.domain.model.AccidentStatus
import com.whitebearcode02.checkmate.vehicle.domain.model.AssistanceStep
import com.whitebearcode02.checkmate.vehicle.domain.model.PredictionStatus

/** A current CAS revision is necessary, but does not authorize destructive field replacements. */
internal fun canAdvanceRecord(current: AccidentRecord, next: AccidentRecord): Boolean {
    if (!hasConsistentProgress(next)) return false
    if (current.ownerUserId != next.ownerUserId || current.createdByDeviceId != next.createdByDeviceId ||
        current.schemaVersion != next.schemaVersion || current.event != next.event.copy(status = current.event.status)) return false
    if (current.deletedAt != null) return current.sameContent(next)
    if (!canReach(current.event.status, next.event.status)) return false
    val actions = next.assistance.associateBy { it.step }
    if (current.assistance.any { actions[it.step] != it }) return false
    val evidence = next.evidence.associateBy { it.evidenceId }
    if (current.evidence.any { evidence[it.evidenceId] != it }) return false
    val prediction = current.prediction
    if (prediction != null) {
        val result = next.prediction ?: return false
        when (prediction.status) {
            // Request and completion may both have happened offline between two sync revisions.
            PredictionStatus.NOT_REQUESTED -> Unit
            PredictionStatus.PENDING -> if (result.status == PredictionStatus.NOT_REQUESTED) return false
            PredictionStatus.AVAILABLE, PredictionStatus.UNAVAILABLE, PredictionStatus.FAILED -> if (result != prediction) return false
        }
    }
    return true
}

/** A state label alone cannot assert that the ordered safety guide and analysis have finished. */
internal fun hasConsistentProgress(record: AccidentRecord): Boolean {
    val steps = record.assistance.map { it.step }.toSet()
    if (record.assistance.any { action -> AssistanceStep.entries.take(action.step.ordinal).any { it !in steps } }) return false
    val prediction = record.prediction?.status
    return when (record.event.status) {
        AccidentStatus.NORMAL, AccidentStatus.SUSPECTED, AccidentStatus.FALSE_ALARM, AccidentStatus.CONFIRMED ->
            steps.isEmpty() && (prediction == null || prediction == PredictionStatus.NOT_REQUESTED)
        AccidentStatus.ASSISTANCE -> prediction == null || prediction == PredictionStatus.NOT_REQUESTED
        AccidentStatus.ANALYSIS_PENDING -> steps.containsAll(AssistanceStep.entries) && prediction == PredictionStatus.PENDING
        AccidentStatus.COMPLETED -> steps.containsAll(AssistanceStep.entries) && prediction == PredictionStatus.AVAILABLE
        AccidentStatus.UNAVAILABLE -> steps.containsAll(AssistanceStep.entries) &&
            prediction in setOf(PredictionStatus.UNAVAILABLE, PredictionStatus.FAILED)
    }
}

/** Several ordered local steps may have occurred while offline, without permitting any regression. */
private fun canReach(from: AccidentStatus, to: AccidentStatus): Boolean {
    val pending = ArrayDeque<AccidentStatus>()
    val visited = mutableSetOf<AccidentStatus>()
    pending.add(from)
    while (pending.isNotEmpty()) {
        val status = pending.removeFirst()
        if (status == to) return true
        if (visited.add(status)) AccidentStatus.entries.filter {
            AccidentStateMachine.canTransition(status, it)
        }.forEach(pending::addLast)
    }
    return false
}
