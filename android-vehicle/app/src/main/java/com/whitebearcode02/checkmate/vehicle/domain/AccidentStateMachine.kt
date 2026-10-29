package com.whitebearcode02.checkmate.vehicle.domain

import com.whitebearcode02.checkmate.vehicle.domain.model.AccidentEvent
import com.whitebearcode02.checkmate.vehicle.domain.model.AccidentStatus

/** The event ledger retains FALSE_ALARM while the dashboard returns to NORMAL. */
object AccidentStateMachine {
    fun canTransition(from: AccidentStatus, to: AccidentStatus): Boolean = when (from) {
        AccidentStatus.NORMAL -> to == AccidentStatus.SUSPECTED
        AccidentStatus.SUSPECTED -> to == AccidentStatus.FALSE_ALARM || to == AccidentStatus.CONFIRMED
        AccidentStatus.CONFIRMED -> to == AccidentStatus.ASSISTANCE
        AccidentStatus.ASSISTANCE -> to == AccidentStatus.ANALYSIS_PENDING
        AccidentStatus.ANALYSIS_PENDING -> to == AccidentStatus.COMPLETED || to == AccidentStatus.UNAVAILABLE
        AccidentStatus.FALSE_ALARM, AccidentStatus.COMPLETED, AccidentStatus.UNAVAILABLE -> false
    }

    fun transition(event: AccidentEvent, next: AccidentStatus): AccidentEvent {
        require(canTransition(event.status, next)) { "Invalid accident state transition" }
        return event.copy(status = next)
    }
}
