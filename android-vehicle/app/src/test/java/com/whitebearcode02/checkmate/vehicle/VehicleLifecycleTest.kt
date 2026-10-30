package com.whitebearcode02.checkmate.vehicle

import com.whitebearcode02.checkmate.vehicle.data.provider.*
import com.whitebearcode02.checkmate.vehicle.domain.DrivingSafetyPolicy
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleLifecycleTest {
    private val policy = VehicleTimingPolicy(
        connectTimeoutMillis = 1_000, staleAfterMillis = 2_000,
        retryDelaysMillis = listOf(100, 200), freshnessCheckMillis = 100,
    )

    @Test fun initializationWithoutServiceResponseTimesOutAndRetryBudgetIsBounded() = runTest {
        val session = Session()
        val provider = PleosVehicleProvider(session, backgroundScope, { testScheduler.currentTime }, policy)
        provider.connect()
        session.emit(VehicleState(
            connection = ConnectionState.CONNECTING, source = DataSource.PLEOS,
            sdkInitialization = SdkInitializationState.INITIALIZED,
        ))
        assertFalse(provider.state.value.serviceObserved)
        assertFalse(provider.state.value.permitsDetailedInteraction)
        advanceTimeBy(3_400)
        runCurrent()
        assertEquals(3, session.starts)
        assertEquals(3, session.closes)
        assertEquals(VehicleError.CONNECTION_TIMEOUT, provider.state.value.error)
        assertEquals(2, provider.state.value.retryAttempt)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, session.starts)
        provider.close()
    }

    @Test fun initializedFlagAloneCannotCompleteConnectionOrDisableTimeout() = runTest {
        val session = Session()
        val provider = PleosVehicleProvider(session, backgroundScope, { testScheduler.currentTime }, policy)
        provider.connect()
        session.emit(parked(0).copy(serviceObserved = false))
        assertEquals(ConnectionState.CONNECTING, provider.state.value.connection)
        assertEquals(VehicleMovement.UNKNOWN, provider.state.value.movement)
        assertNull(provider.state.value.speedKph)
        assertTrue(provider.state.value.readings.isEmpty())
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(VehicleError.CONNECTION_TIMEOUT, provider.state.value.error)
        provider.close()
    }

    @Test fun mockSnapshotIsRejectedInsteadOfRelabeledAsRealVehicleData() = runTest {
        val session = Session()
        val provider = PleosVehicleProvider(session, backgroundScope, { testScheduler.currentTime }, policy)
        val actual = parked(0)
        val mock = actual.copy(
            source = DataSource.MOCK,
            readings = actual.readings.mapValues { (_, datum) -> datum.copy(source = DataSource.MOCK) },
        )
        provider.connect()
        session.emit(mock)
        assertEquals(ConnectionState.ERROR, provider.state.value.connection)
        assertEquals(VehicleError.DATA_UNAVAILABLE, provider.state.value.error)
        assertTrue(provider.state.value.readings.isEmpty())
        assertNull(provider.state.value.speedKph)
        assertFalse(provider.state.value.permitsDetailedInteraction)
        runCurrent()
        assertEquals(1, session.closes)
        provider.close()
    }

    @Test fun successAfterTerminalCallbackCannotOvertakeQueuedFailureCleanup() = runTest {
        val session = Session()
        val provider = PleosVehicleProvider(session, backgroundScope, { testScheduler.currentTime }, policy)
        provider.connect()
        session.emit(VehicleState(
            connection = ConnectionState.PERMISSION_DENIED, source = DataSource.PLEOS,
            error = VehicleError.PERMISSION_DENIED,
        ))
        session.emit(parked(0))
        assertEquals(ConnectionState.PERMISSION_DENIED, provider.state.value.connection)
        assertFalse(provider.state.value.permitsDetailedInteraction)
        runCurrent()
        provider.close()
    }

    @Test fun permissionDeniedStopsAutomaticRetryAndSanitizesException() = runTest {
        val session = Session { throw SecurityException("sensitive vehicle identifier") }
        val provider = PleosVehicleProvider(session, backgroundScope, { testScheduler.currentTime }, policy)
        provider.connect()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(ConnectionState.PERMISSION_DENIED, provider.state.value.connection)
        assertEquals(1, session.starts)
        assertEquals(1, session.closes)
        assertFalse(provider.state.value.toString().contains("sensitive"))
        assertFalse(provider.state.value.permitsDetailedInteraction)
        provider.close()
    }

    @Test fun terminalFailureRetainsTrustedPermissionAndSupportMetadataButClearsSamples() = runTest {
        val session = Session()
        val provider = PleosVehicleProvider(session, backgroundScope, { testScheduler.currentTime }, policy)
        provider.connect()
        session.emit(VehicleState(
            connection = ConnectionState.PERMISSION_DENIED, source = DataSource.PLEOS,
            error = VehicleError.PERMISSION_DENIED, sdkInitialization = SdkInitializationState.INITIALIZED,
            readings = mapOf(VehicleDataKind.DOOR to VehicleDatum(
                kind = VehicleDataKind.DOOR, source = DataSource.PLEOS, unit = VehicleUnit.STATE,
                support = FeatureSupport.SUPPORTED, permission = DataPermission.DENIED,
                value = VehicleValue.NumberValue(10.0), collectedAtMillis = 0,
                validity = DataValidity.VALID, updateState = DataUpdateState.UPDATING,
            )),
        ))
        runCurrent()
        val datum = provider.state.value.readings.getValue(VehicleDataKind.DOOR)
        assertEquals(DataPermission.DENIED, datum.permission)
        assertEquals(FeatureSupport.SUPPORTED, datum.support)
        assertEquals(VehicleUnit.STATE, datum.unit)
        assertEquals(DataSource.PLEOS, datum.source)
        assertNull(datum.value)
        assertNull(datum.collectedAtMillis)
        assertEquals(DataValidity.UNKNOWN, datum.validity)
        assertEquals(DataUpdateState.STOPPED, datum.updateState)
        assertFalse(provider.state.value.permitsDetailedInteraction)
        provider.close()
    }

    @Test fun missingPlatformClassReportsUnsupportedWithoutThrowingOrRetrying() = runTest {
        val session = Session { throw NoClassDefFoundError("android/car/Car") }
        val provider = PleosVehicleProvider(session, backgroundScope, { testScheduler.currentTime }, policy)
        provider.connect()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(ConnectionState.UNSUPPORTED, provider.state.value.connection)
        assertEquals(VehicleError.SDK_UNAVAILABLE, provider.state.value.error)
        assertEquals(1, session.starts)
        provider.close()
    }

    @Test fun initializationFailureRetriesAndManualReconnectStartsANewBoundedBudget() = runTest {
        val session = Session { throw IllegalStateException("private sdk diagnostic") }
        val provider = PleosVehicleProvider(session, backgroundScope, { testScheduler.currentTime }, policy)
        provider.connect()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(3, session.starts)
        assertEquals(SdkInitializationState.FAILED, provider.state.value.sdkInitialization)
        provider.reconnect()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(6, session.starts)
        assertEquals(6, session.closes)
        provider.close()
    }

    @Test fun duplicateConnectDoesNotDuplicateSessionAndCloseCancelsPendingRetry() = runTest {
        val session = Session()
        val provider = PleosVehicleProvider(session, backgroundScope, { testScheduler.currentTime }, policy)
        provider.connect()
        provider.connect()
        assertEquals(1, session.starts)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, session.closes)
        provider.close()
        provider.close()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, session.starts)
        assertEquals(1, session.closes)
        assertEquals(ConnectionState.DISCONNECTED, provider.state.value.connection)
    }

    @Test fun staleCallbackCannotRestoreParkedStateDuringReconnectOrAfterClose() = runTest {
        val session = Session()
        val provider = PleosVehicleProvider(session, backgroundScope, { testScheduler.currentTime }, policy)
        provider.connect()
        val oldCallback = session.callbacks.last()
        provider.reconnect()
        oldCallback(parked(testScheduler.currentTime))
        assertEquals(ConnectionState.CONNECTING, provider.state.value.connection)
        session.emit(parked(testScheduler.currentTime))
        assertTrue(provider.state.value.permitsDetailedInteraction)
        provider.close()
        session.emit(parked(testScheduler.currentTime))
        assertEquals(ConnectionState.DISCONNECTED, provider.state.value.connection)
        assertFalse(provider.state.value.permitsDetailedInteraction)
    }

    @Test fun stoppedSdkUpdatesExpireAndRemoveCurrentSpeedAndMovement() = runTest {
        val session = Session()
        val provider = PleosVehicleProvider(session, backgroundScope, { testScheduler.currentTime }, policy)
        provider.connect()
        session.emit(parked(testScheduler.currentTime))
        assertTrue(DrivingSafetyPolicy.canShowDetails(provider.state.value))
        advanceTimeBy(2_000)
        runCurrent()
        val state = provider.state.value
        assertEquals(ConnectionState.CONNECTED, state.connection)
        assertEquals(DataValidity.STALE, state.readings[VehicleDataKind.MOVEMENT]?.validity)
        assertEquals(DataUpdateState.STOPPED, state.readings[VehicleDataKind.MOVEMENT]?.updateState)
        assertEquals(VehicleError.DATA_STALE, state.error)
        assertNull(state.speedKph)
        assertEquals(VehicleMovement.UNKNOWN, state.movement)
        assertFalse(DrivingSafetyPolicy.canShowDetails(state))
        provider.close()
    }

    @Test fun futureTimestampFailsClosedImmediately() = runTest {
        val session = Session()
        val provider = PleosVehicleProvider(session, backgroundScope, { testScheduler.currentTime }, policy)
        provider.connect()
        session.emit(parked(50_000))
        assertFalse(provider.state.value.permitsDetailedInteraction)
        assertEquals(DataValidity.STALE, provider.state.value.readings[VehicleDataKind.MOVEMENT]?.validity)
        provider.close()
    }

    @Test fun freshSampleAfterStaleRestoresPolicyWithoutInventingParkedData() = runTest {
        val session = Session()
        val provider = PleosVehicleProvider(session, backgroundScope, { testScheduler.currentTime }, policy)
        provider.connect()
        session.emit(parked(0))
        advanceTimeBy(2_000)
        runCurrent()
        assertFalse(provider.state.value.permitsDetailedInteraction)
        session.emit(parked(testScheduler.currentTime))
        assertTrue(provider.state.value.permitsDetailedInteraction)
        assertNull(provider.state.value.error)
        provider.close()
    }

    @Test fun capabilityResponseDoesNotInventMovementOrStationaryState() = runTest {
        val session = Session()
        val provider = PleosVehicleProvider(session, backgroundScope, { testScheduler.currentTime }, policy)
        provider.connect()
        session.emit(VehicleState(
            connection = ConnectionState.CONNECTED, source = DataSource.PLEOS,
            capabilities = setOf(VehicleCapability.DOOR), serviceObserved = true,
            sdkInitialization = SdkInitializationState.INITIALIZED,
            readings = mapOf(VehicleDataKind.DOOR to VehicleDatum(
                kind = VehicleDataKind.DOOR, source = DataSource.PLEOS, support = FeatureSupport.SUPPORTED,
            )),
        ))
        assertEquals(ConnectionState.CONNECTED, provider.state.value.connection)
        assertEquals(VehicleMovement.UNKNOWN, provider.state.value.movement)
        assertNull(provider.state.value.speedKph)
        assertFalse(provider.state.value.permitsDetailedInteraction)
        provider.close()
    }

    @Test fun mockDelayedConnectionAndDisconnectionNeverReuseParkedValues() = runTest {
        val provider = MockVehicleProvider(MockVehicleScenario.DELAYED_CONNECTION, backgroundScope,
            { testScheduler.currentTime }, policy)
        provider.connect()
        assertEquals(ConnectionState.CONNECTING, provider.state.value.connection)
        assertFalse(provider.state.value.permitsDetailedInteraction)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(provider.state.value.permitsDetailedInteraction)
        provider.setScenario(MockVehicleScenario.DISCONNECT_DURING_CONNECT)
        assertNull(provider.state.value.speedKph)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(ConnectionState.DISCONNECTED, provider.state.value.connection)
        assertFalse(provider.state.value.permitsDetailedInteraction)
        provider.close()
    }

    @Test fun mockUpdateStoppedAndUnknownAlwaysRestrictDetails() = runTest {
        val provider = MockVehicleProvider(MockVehicleScenario.UPDATE_STOPPED, backgroundScope,
            { testScheduler.currentTime }, policy)
        provider.connect()
        assertTrue(provider.state.value.permitsDetailedInteraction)
        advanceTimeBy(2_000)
        runCurrent()
        assertFalse(provider.state.value.permitsDetailedInteraction)
        assertEquals(VehicleError.DATA_STALE, provider.state.value.error)
        provider.setScenario(MockVehicleScenario.UNKNOWN)
        assertEquals(ConnectionState.CONNECTED, provider.state.value.connection)
        assertEquals(VehicleMovement.UNKNOWN, provider.state.value.movement)
        assertFalse(provider.state.value.permitsDetailedInteraction)
        provider.close()
    }

    @Test fun mockNormalUpdatesStayFreshAndRetryScenarioRemainsExplicitlyMock() = runTest {
        val provider = MockVehicleProvider(MockVehicleScenario.SUCCESS, backgroundScope,
            { testScheduler.currentTime }, policy)
        provider.connect()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(provider.state.value.permitsDetailedInteraction)
        assertEquals(10_000L, provider.state.value.lastUpdatedAtMillis)
        provider.setScenario(MockVehicleScenario.RECONNECT_SUCCESS)
        assertEquals(ConnectionState.FAILED, provider.state.value.connection)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(ConnectionState.CONNECTED, provider.state.value.connection)
        assertEquals(1, provider.state.value.retryAttempt)
        assertEquals(DataSource.MOCK, provider.state.value.source)
        assertTrue(provider.state.value.readings.values.all { it.source == DataSource.MOCK })
        provider.close()
    }

    @Test fun mockCloseCancelsDelayedConnectionAndNeverMutatesAfterClose() = runTest {
        val provider = MockVehicleProvider(MockVehicleScenario.DELAYED_CONNECTION, backgroundScope,
            { testScheduler.currentTime }, policy)
        provider.connect()
        provider.close()
        advanceTimeBy(10_000)
        runCurrent()
        provider.connect()
        provider.setScenario(MockVehicleScenario.SUCCESS)
        assertEquals(ConnectionState.DISCONNECTED, provider.state.value.connection)
        assertNull(provider.state.value.lastUpdatedAtMillis)
    }

    private fun parked(at: Long): VehicleState {
        val values = mapOf(
            VehicleDataKind.MOVEMENT to VehicleValue.StateValue(VehicleMovement.PARKED.name),
            VehicleDataKind.SPEED to VehicleValue.NumberValue(0.0),
        )
        return VehicleState(
            connection = ConnectionState.CONNECTED, source = DataSource.PLEOS,
            speedKph = 0.0, movement = VehicleMovement.PARKED,
            capabilities = setOf(VehicleCapability.MOVEMENT, VehicleCapability.SPEED),
            readings = values.mapValues { (kind, value) -> VehicleDatum(
                kind = kind, value = value, source = DataSource.PLEOS, collectedAtMillis = at,
                validity = DataValidity.VALID, support = FeatureSupport.SUPPORTED,
                permission = DataPermission.GRANTED, updateState = DataUpdateState.UPDATING,
            ) },
            lastUpdatedAtMillis = at, sdkInitialization = SdkInitializationState.INITIALIZED,
            serviceObserved = true,
        )
    }

    private class Session(private val startFailure: (() -> Unit)? = null) : PleosSdkSession {
        val callbacks = mutableListOf<(VehicleState) -> Unit>()
        var starts = 0
        var closes = 0
        override fun start(onState: (VehicleState) -> Unit) {
            starts++
            callbacks += onState
            startFailure?.invoke()
        }
        override fun close() { closes++ }
        fun emit(state: VehicleState) = callbacks.last()(state)
    }
}
