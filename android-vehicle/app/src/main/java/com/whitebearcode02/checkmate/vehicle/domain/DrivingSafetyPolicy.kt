package com.whitebearcode02.checkmate.vehicle.domain

import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleState

object DrivingSafetyPolicy {
    /** Unknown, contradictory and faulty data never grants access to detailed screens. */
    fun canShowDetails(vehicle: VehicleState): Boolean = vehicle.permitsDetailedInteraction
}