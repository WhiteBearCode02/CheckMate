package com.whitebearcode02.checkmate.vehicle.data

import com.whitebearcode02.checkmate.vehicle.data.mapper.VehicleStateMapper
import com.whitebearcode02.checkmate.vehicle.domain.DrivingSafetyPolicy
import com.whitebearcode02.checkmate.vehicle.domain.model.ConnectionState
import com.whitebearcode02.checkmate.vehicle.domain.model.DataPermission
import com.whitebearcode02.checkmate.vehicle.domain.model.DataSource
import com.whitebearcode02.checkmate.vehicle.domain.model.DataValidity
import com.whitebearcode02.checkmate.vehicle.domain.model.FeatureSupport
import com.whitebearcode02.checkmate.vehicle.domain.model.SdkInitializationState
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleDataKind
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleError
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleMovement
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleStateMapperTest {
    @Test
    fun initializationAloneDoesNotConfirmAServiceConnection() {
        val state = VehicleStateMapper.initialized()
        assertEquals(SdkInitializationState.INITIALIZED, state.sdkInitialization)
        assertEquals(ConnectionState.CONNECTING, state.connection)
        assertFalse(state.serviceObserved)
        assertFalse(DrivingSafetyPolicy.canShowDetails(state))
    }

    @Test
    fun capabilityResponseConfirmsServiceButDoesNotInventAValue() {
        val state = VehicleStateMapper.doorCapability(readable = true)
        assertEquals(ConnectionState.CONNECTED, state.connection)
        assertTrue(state.serviceObserved)
        assertEquals(FeatureSupport.SUPPORTED, state.readings.getValue(VehicleDataKind.DOOR).support)
        assertNull(state.readings.getValue(VehicleDataKind.DOOR).value)
        assertNull(state.lastUpdatedAtMillis)
        assertFalse(DrivingSafetyPolicy.canShowDetails(state))
    }

    @Test
    fun zeroDoorPositionIsARealPleosSampleAndNeverAZeroSpeedOrParkingAssertion() {
        val state = VehicleStateMapper.doorPosition(0, 0, 1, 12_000L)
        val datum = state.readings.getValue(VehicleDataKind.DOOR)
        assertEquals(VehicleValue.NumberValue(0.0), datum.value)
        assertEquals(DataSource.PLEOS, datum.source)
        assertEquals(DataValidity.VALID, datum.validity)
        assertTrue(datum.isUsable)
        assertEquals(12_000L, datum.collectedAtMillis)
        assertEquals(VehicleMovement.UNKNOWN, state.movement)
        assertNull(state.speedKph)
        assertFalse(DrivingSafetyPolicy.canShowDetails(state))
    }

    @Test
    fun doorBoundsComeFromTheCapabilityRatherThanAUniversalZeroOneAssumption() {
        val state = VehicleStateMapper.doorPosition(7, 0, 10, 100L)
        assertEquals(VehicleValue.NumberValue(7.0), state.readings.getValue(VehicleDataKind.DOOR).value)
        assertNull(state.error)
    }

    @Test
    fun nullOutsideRangeAndReversedBoundsNeverProduceUsableVehicleData() {
        listOf(
            VehicleStateMapper.doorPosition(null, 0, 1, 100L),
            VehicleStateMapper.doorPosition(2, 0, 1, 100L),
            VehicleStateMapper.doorPosition(0, 1, 0, 100L),
        ).forEach { state ->
            val datum = state.readings.getValue(VehicleDataKind.DOOR)
            assertFalse(datum.isUsable)
            assertNull(datum.value)
            assertNull(datum.collectedAtMillis)
            assertEquals(DataValidity.INVALID, datum.validity)
            assertEquals(VehicleError.DATA_UNAVAILABLE, state.error)
            assertFalse(DrivingSafetyPolicy.canShowDetails(state))
        }
    }

    @Test
    fun unsupportedAreaDoesNotMeanTheSdkFailedToInitialize() {
        val state = VehicleStateMapper.doorCapability(readable = false)
        assertEquals(SdkInitializationState.INITIALIZED, state.sdkInitialization)
        assertTrue(state.serviceObserved)
        assertEquals(FeatureSupport.UNSUPPORTED, state.readings.getValue(VehicleDataKind.DOOR).support)
        assertEquals(VehicleError.UNSUPPORTED_FEATURE, state.error)
        assertFalse(DrivingSafetyPolicy.canShowDetails(state))
    }

    @Test
    fun permissionFailureIsSanitizedAndHasNoCurrentVehicleValue() {
        val state = VehicleStateMapper.failure(
            ConnectionState.PERMISSION_DENIED, VehicleError.PERMISSION_DENIED,
            SdkInitializationState.INITIALIZED, serviceObserved = true,
        )
        val datum = state.readings.getValue(VehicleDataKind.DOOR)
        assertEquals(DataPermission.DENIED, datum.permission)
        assertNull(datum.value)
        assertFalse(datum.isUsable)
        assertFalse(DrivingSafetyPolicy.canShowDetails(state))
    }
}
