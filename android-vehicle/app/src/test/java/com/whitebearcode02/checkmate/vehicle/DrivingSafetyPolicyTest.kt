package com.whitebearcode02.checkmate.vehicle

import com.whitebearcode02.checkmate.vehicle.domain.DrivingSafetyPolicy
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class DrivingSafetyPolicyTest {
    private fun state(connection: ConnectionState, movement: VehicleMovement) = VehicleState(
        connection = connection, movement = movement,
        capabilities = setOf(VehicleCapability.MOVEMENT),
        readings = freshMovement(movement),
    )

    @Test fun contradictoryMotionOrErrorNeverPermitsDetails() {
        val parked = VehicleState(
            connection = ConnectionState.CONNECTED, movement = VehicleMovement.PARKED,
            capabilities = setOf(VehicleCapability.MOVEMENT, VehicleCapability.SPEED),
            readings = freshMovement(VehicleMovement.PARKED),
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

    @Test fun restoredLegacyParkedFieldsWithoutFreshMetadataNeverPermitDetails() {
        val restored = VehicleState(
            connection = ConnectionState.CONNECTED, movement = VehicleMovement.PARKED,
            capabilities = setOf(VehicleCapability.MOVEMENT),
        )
        assertFalse(DrivingSafetyPolicy.canShowDetails(restored))
    }

    @Test fun everyMissingOrInvalidMovementMetadataStateFailsClosed() {
        val parked = state(ConnectionState.CONNECTED, VehicleMovement.PARKED)
        val movement = parked.readings.getValue(VehicleDataKind.MOVEMENT)
        val invalid = listOf(
            movement.copy(collectedAtMillis = null),
            movement.copy(value = null),
            movement.copy(validity = DataValidity.STALE),
            movement.copy(validity = DataValidity.INVALID),
            movement.copy(support = FeatureSupport.UNSUPPORTED),
            movement.copy(permission = DataPermission.DENIED),
            movement.copy(updateState = DataUpdateState.STOPPED),
            movement.copy(value = VehicleValue.StateValue(VehicleMovement.MOVING.name)),
        )
        invalid.forEach { sample ->
            assertFalse(DrivingSafetyPolicy.canShowDetails(parked.copy(
                readings = mapOf(VehicleDataKind.MOVEMENT to sample),
            )))
        }
    }

    @Test fun pleosParkedMetadataRequiresInitializedSdkAndObservedServiceResponse() {
        val mock = state(ConnectionState.CONNECTED, VehicleMovement.PARKED)
        val pleos = mock.copy(
            source = DataSource.PLEOS,
            readings = mock.readings.mapValues { (_, value) -> value.copy(source = DataSource.PLEOS) },
        )
        assertFalse(DrivingSafetyPolicy.canShowDetails(pleos))
        assertFalse(DrivingSafetyPolicy.canShowDetails(pleos.copy(sdkInitialization = SdkInitializationState.INITIALIZED)))
        assertTrue(DrivingSafetyPolicy.canShowDetails(pleos.copy(
            sdkInitialization = SdkInitializationState.INITIALIZED, serviceObserved = true,
        )))
    }

    @Test fun knownSpeedCannotOverrideExpiredParkedMetadata() {
        val parked = state(ConnectionState.CONNECTED, VehicleMovement.PARKED).copy(
            capabilities = setOf(VehicleCapability.MOVEMENT, VehicleCapability.SPEED), speedKph = 0.0,
        )
        assertTrue(DrivingSafetyPolicy.canShowDetails(parked))
        val expired = parked.expire(nowMillis = 5_001, maxAgeMillis = 5_000)
        assertFalse(DrivingSafetyPolicy.canShowDetails(expired))
        assertEquals(VehicleMovement.UNKNOWN, expired.movement)
    }

    private fun freshMovement(movement: VehicleMovement) = mapOf(
        VehicleDataKind.MOVEMENT to VehicleDatum(
            kind = VehicleDataKind.MOVEMENT, value = VehicleValue.StateValue(movement.name),
            source = DataSource.MOCK, unit = VehicleUnit.STATE, collectedAtMillis = 1,
            validity = DataValidity.VALID, support = FeatureSupport.SUPPORTED,
            permission = DataPermission.GRANTED, updateState = DataUpdateState.UPDATING,
        ),
    )
}
