package com.whitebearcode02.checkmate.vehicle

import com.whitebearcode02.checkmate.vehicle.domain.DrivingSafetyPolicy
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class DrivingSafetyPolicyTest {
    private fun state(connection: ConnectionState, movement: VehicleMovement) = VehicleState(
        connection = connection, movement = movement,
        capabilities = setOf(VehicleCapability.MOVEMENT),
    )

    @Test fun contradictoryMotionOrErrorNeverPermitsDetails() {
        val parked = VehicleState(
            connection = ConnectionState.CONNECTED, movement = VehicleMovement.PARKED,
            capabilities = setOf(VehicleCapability.MOVEMENT, VehicleCapability.SPEED),
        )
        assertFalse(DrivingSafetyPolicy.canShowDetails(parked.copy(speedKph = 1.0)))
        assertFalse(DrivingSafetyPolicy.canShowDetails(parked.copy(error = VehicleError.DATA_UNAVAILABLE)))
        assertTrue(DrivingSafetyPolicy.canShowDetails(parked.copy(speedKph = 0.0)))
    }
    @Test fun connectedParkedPermitsDetails() {
        assertTrue(DrivingSafetyPolicy.canShowDetails(state(ConnectionState.CONNECTED, VehicleMovement.PARKED)))
    }

    @Test fun movingNeverPermitsDetails() {
        ConnectionState.entries.forEach {
            assertFalse(DrivingSafetyPolicy.canShowDetails(state(it, VehicleMovement.MOVING)))
        }
    }

    @Test fun unknownNeverPermitsDetailsEvenWhenConnected() {
        ConnectionState.entries.forEach {
            assertFalse(DrivingSafetyPolicy.canShowDetails(state(it, VehicleMovement.UNKNOWN)))
        }
        assertFalse(DrivingSafetyPolicy.canShowDetails(VehicleState(connection = ConnectionState.CONNECTED)))
    }

    @Test fun failedOrDisconnectedCannotUseStaleParkedValue() {
        ConnectionState.entries.filter { it != ConnectionState.CONNECTED }.forEach {
            assertFalse(DrivingSafetyPolicy.canShowDetails(state(it, VehicleMovement.PARKED)))
        }
    }
}