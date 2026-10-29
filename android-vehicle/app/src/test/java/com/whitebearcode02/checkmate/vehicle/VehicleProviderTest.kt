package com.whitebearcode02.checkmate.vehicle

import com.whitebearcode02.checkmate.vehicle.data.provider.MockVehicleProvider
import com.whitebearcode02.checkmate.vehicle.data.provider.MockVehicleScenario
import com.whitebearcode02.checkmate.vehicle.data.provider.PleosSdkSession
import com.whitebearcode02.checkmate.vehicle.data.provider.PleosVehicleProvider
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class VehicleProviderTest {
    @Test fun successfulMockIsExplicitlyMockAndParked() {
        val provider = MockVehicleProvider()
        provider.connect()
        assertEquals(ConnectionState.CONNECTED, provider.state.value.connection)
        assertEquals(DataSource.MOCK, provider.state.value.source)
        assertEquals(0.0, provider.state.value.speedKph)
        assertEquals(VehicleMovement.PARKED, provider.state.value.movement)
        assertTrue(provider.state.value.permitsDetailedInteraction)
    }

    @Test fun failureClearsPreviouslyAvailableSensorValues() {
        val provider = MockVehicleProvider()
        provider.connect()
        provider.setScenario(MockVehicleScenario.FAILURE)
        assertEquals(ConnectionState.FAILED, provider.state.value.connection)
        assertEquals(VehicleError.CONNECTION_FAILED, provider.state.value.error)
        assertNull(provider.state.value.speedKph)
        assertFalse(provider.state.value.permitsDetailedInteraction)
    }

    @Test fun noDataAndUnsupportedAreDistinctAndNeverZeroSpeed() {
        val provider = MockVehicleProvider(MockVehicleScenario.NO_DATA)
        provider.connect()
        assertEquals(VehicleError.DATA_UNAVAILABLE, provider.state.value.error)
        assertTrue(VehicleCapability.SPEED in provider.state.value.capabilities)
        assertNull(provider.state.value.speedKph)
        assertEquals(VehicleMovement.UNKNOWN, provider.state.value.movement)
        assertFalse(provider.state.value.permitsDetailedInteraction)
        provider.setScenario(MockVehicleScenario.UNSUPPORTED)
        assertTrue(provider.state.value.capabilities.isEmpty())
        assertNull(provider.state.value.speedKph)
        assertEquals(VehicleMovement.UNKNOWN, provider.state.value.movement)
        assertFalse(provider.state.value.permitsDetailedInteraction)
    }

    @Test fun movingMockRestrictsDetails() {
        val provider = MockVehicleProvider(MockVehicleScenario.MOVING)
        provider.connect()
        assertEquals(45.0, provider.state.value.speedKph)
        assertFalse(provider.state.value.permitsDetailedInteraction)
    }

    @Test fun closedMockCannotReconnectOrChangeScenario() {
        val provider = MockVehicleProvider()
        provider.connect()
        provider.close()
        provider.close()
        provider.connect()
        provider.setScenario(MockVehicleScenario.MOVING)
        assertEquals(ConnectionState.DISCONNECTED, provider.state.value.connection)
        assertNull(provider.state.value.speedKph)
    }

    @Test fun pleosWithoutVerifiedAdapterReportsUnavailable() {
        val provider = PleosVehicleProvider()
        provider.connect()
        assertEquals(ConnectionState.UNAVAILABLE, provider.state.value.connection)
        assertEquals(VehicleError.SDK_UNAVAILABLE, provider.state.value.error)
        assertEquals(DataSource.PLEOS, provider.state.value.source)
        assertNull(provider.state.value.speedKph)
    }

    @Test fun pleosPermissionFailureIsSafeAndReleasesSession() {
        val session = FakeSession(SecurityException("private vehicle identifier"))
        val provider = PleosVehicleProvider(session)
        provider.connect()
        assertEquals(VehicleError.PERMISSION_DENIED, provider.state.value.error)
        assertEquals(ConnectionState.FAILED, provider.state.value.connection)
        assertEquals(1, session.closeCount)
        assertFalse(provider.state.value.toString().contains("private vehicle identifier"))
    }

    @Test fun pleosStartAndCleanupFailuresDoNotEscape() {
        val session = FakeSession(IllegalStateException("service down"), failOnClose = true)
        val provider = PleosVehicleProvider(session)
        provider.connect()
        assertEquals(VehicleError.CONNECTION_FAILED, provider.state.value.error)
        provider.close()
        assertEquals(ConnectionState.DISCONNECTED, provider.state.value.connection)
        assertEquals(1, session.closeCount)
    }

    @Test fun pleosIgnoresCallbacksFromDisconnectedOrClosedSession() {
        val session = FakeSession()
        val provider = PleosVehicleProvider(session)
        provider.connect()
        val staleCallback = session.callback!!
        provider.disconnect()
        staleCallback(parkedState())
        assertEquals(ConnectionState.DISCONNECTED, provider.state.value.connection)
        provider.connect()
        staleCallback(parkedState())
        assertEquals(ConnectionState.CONNECTING, provider.state.value.connection)
        session.callback!!(parkedState())
        assertEquals(ConnectionState.CONNECTED, provider.state.value.connection)
        assertEquals(DataSource.PLEOS, provider.state.value.source)
        provider.close()
        session.callback!!(parkedState())
        provider.connect()
        assertEquals(ConnectionState.DISCONNECTED, provider.state.value.connection)
        assertEquals(2, session.startCount)
        assertEquals(2, session.closeCount)
    }

    @Test fun repeatedPleosConnectDoesNotDuplicateSubscriptions() {
        val session = FakeSession()
        val provider = PleosVehicleProvider(session)
        provider.connect()
        provider.connect()
        provider.close()
        provider.close()
        assertEquals(1, session.startCount)
        assertEquals(1, session.closeCount)
    }

    private fun parkedState() = VehicleState(
        connection = ConnectionState.CONNECTED, speedKph = 0.0, movement = VehicleMovement.PARKED,
        capabilities = setOf(VehicleCapability.SPEED, VehicleCapability.MOVEMENT),
    )

    private class FakeSession(
        private val startFailure: Exception? = null,
        private val failOnClose: Boolean = false,
    ) : PleosSdkSession {
        var callback: ((VehicleState) -> Unit)? = null
        var startCount = 0
        var closeCount = 0
        override fun start(onState: (VehicleState) -> Unit) {
            startCount++
            callback = onState
            startFailure?.let { throw it }
        }
        override fun close() {
            closeCount++
            if (failOnClose) error("private SDK cleanup message")
        }
    }
}