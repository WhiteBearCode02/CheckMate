package com.whitebearcode02.checkmate.vehicle.data.provider

import com.whitebearcode02.checkmate.vehicle.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MockVehicleScenario {
    SUCCESS, FAILURE, NO_DATA, UNSUPPORTED, MOVING, DELAYED_CONNECTION, PERMISSION_DENIED,
    UPDATE_STOPPED, DISCONNECT_DURING_CONNECT, RECONNECT_SUCCESS, UNKNOWN,
}

/** Explicit, deterministic development scenarios. No live vehicle service is used. */
class MockVehicleProvider(
    private var scenario: MockVehicleScenario = MockVehicleScenario.SUCCESS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: () -> Long = System::currentTimeMillis,
    private val policy: VehicleTimingPolicy = VehicleTimingPolicy(),
) : VehicleDataProvider {
    private val mutableState = MutableStateFlow(VehicleState(source = DataSource.MOCK))
    override val state: StateFlow<VehicleState> = mutableState.asStateFlow()
    private var closed = false
    private var active = false
    private var generation = 0
    private var transitionJob: Job? = null
    private var updateJob: Job? = null

    @Synchronized
    override fun connect() {
        if (closed || active) return
        active = true
        val current = ++generation
        mutableState.value = VehicleState(
            connection = ConnectionState.CONNECTING, source = DataSource.MOCK,
            sdkInitialization = SdkInitializationState.INITIALIZING,
        )
        mutableState.value = when (scenario) {
            MockVehicleScenario.SUCCESS -> connected(0.0, VehicleMovement.PARKED)
            MockVehicleScenario.MOVING -> connected(45.0, VehicleMovement.MOVING)
            MockVehicleScenario.FAILURE, MockVehicleScenario.RECONNECT_SUCCESS -> VehicleState(
                connection = ConnectionState.FAILED, source = DataSource.MOCK, error = VehicleError.CONNECTION_FAILED,
                sdkInitialization = SdkInitializationState.FAILED,
            )
            MockVehicleScenario.NO_DATA -> VehicleState(
                connection = ConnectionState.CONNECTED, source = DataSource.MOCK,
                capabilities = setOf(VehicleCapability.SPEED, VehicleCapability.MOVEMENT),
                error = VehicleError.DATA_UNAVAILABLE,
                readings = unavailableReadings(FeatureSupport.SUPPORTED),
                sdkInitialization = SdkInitializationState.INITIALIZED, serviceObserved = true,
            )
            MockVehicleScenario.UNSUPPORTED -> VehicleState(
                connection = ConnectionState.CONNECTED, source = DataSource.MOCK,
                readings = unavailableReadings(FeatureSupport.UNSUPPORTED),
                sdkInitialization = SdkInitializationState.INITIALIZED, serviceObserved = true,
            )
            MockVehicleScenario.PERMISSION_DENIED -> VehicleState(
                connection = ConnectionState.PERMISSION_DENIED, source = DataSource.MOCK,
                error = VehicleError.PERMISSION_DENIED,
                readings = unavailableReadings(FeatureSupport.UNKNOWN, DataPermission.DENIED),
                sdkInitialization = SdkInitializationState.INITIALIZED,
            )
            MockVehicleScenario.UPDATE_STOPPED -> connected(0.0, VehicleMovement.PARKED)
            MockVehicleScenario.UNKNOWN -> connected(null, VehicleMovement.UNKNOWN)
            MockVehicleScenario.DELAYED_CONNECTION, MockVehicleScenario.DISCONNECT_DURING_CONNECT -> mutableState.value
        }
        if (scenario in setOf(MockVehicleScenario.DELAYED_CONNECTION, MockVehicleScenario.DISCONNECT_DURING_CONNECT,
                MockVehicleScenario.RECONNECT_SUCCESS)) {
            val selected = scenario
            transitionJob = scope.launch {
                delay(if (selected == MockVehicleScenario.RECONNECT_SUCCESS) policy.retryDelaysMillis.firstOrNull() ?: 500 else 1_000)
                synchronized(this@MockVehicleProvider) {
                    if (!isCurrent(current)) return@launch
                    mutableState.value = if (selected == MockVehicleScenario.DISCONNECT_DURING_CONNECT) {
                        active = false
                        VehicleState(source = DataSource.MOCK)
                    } else connected(0.0, VehicleMovement.PARKED).copy(
                        retryAttempt = if (selected == MockVehicleScenario.RECONNECT_SUCCESS) 1 else 0,
                    )
                    if (active) startUpdates(current, refresh = true)
                }
            }
        } else if (mutableState.value.connection == ConnectionState.CONNECTED) {
            startUpdates(current, refresh = scenario != MockVehicleScenario.UPDATE_STOPPED)
        }
    }

    @Synchronized
    fun setScenario(scenario: MockVehicleScenario) {
        if (closed) return
        this.scenario = scenario
        disconnect()
        connect()
    }

    @Synchronized
    override fun disconnect() {
        generation++
        active = false
        transitionJob?.cancel()
        updateJob?.cancel()
        mutableState.value = VehicleState(source = DataSource.MOCK)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        disconnect()
    }

    private fun connected(speed: Double?, movement: VehicleMovement): VehicleState {
        val now = clock()
        val values = buildMap {
            put(VehicleDataKind.MOVEMENT, VehicleDatum(
                kind = VehicleDataKind.MOVEMENT, value = VehicleValue.StateValue(movement.name), unit = VehicleUnit.STATE,
                source = DataSource.MOCK, collectedAtMillis = now,
                validity = if (movement == VehicleMovement.UNKNOWN) DataValidity.UNKNOWN else DataValidity.VALID,
                support = FeatureSupport.SUPPORTED, permission = DataPermission.GRANTED,
                updateState = DataUpdateState.UPDATING,
            ))
            put(VehicleDataKind.SPEED, VehicleDatum(
                kind = VehicleDataKind.SPEED, value = speed?.let { VehicleValue.NumberValue(it) }, unit = VehicleUnit.KPH,
                source = DataSource.MOCK, collectedAtMillis = now,
                validity = if (speed == null) DataValidity.UNKNOWN else DataValidity.VALID,
                support = FeatureSupport.SUPPORTED, permission = DataPermission.GRANTED,
                updateState = DataUpdateState.UPDATING,
            ))
            VehicleDataKind.entries.filter { it !in keys }.forEach { kind ->
                put(kind, VehicleDatum(kind = kind, source = DataSource.MOCK, support = FeatureSupport.UNSUPPORTED))
            }
        }
        return VehicleState(
        connection = ConnectionState.CONNECTED, source = DataSource.MOCK, speedKph = speed, movement = movement,
        capabilities = setOf(VehicleCapability.SPEED, VehicleCapability.MOVEMENT),
        readings = values, lastUpdatedAtMillis = now, sdkInitialization = SdkInitializationState.INITIALIZED,
        serviceObserved = true,
        )
    }

    private fun unavailableReadings(support: FeatureSupport, permission: DataPermission = DataPermission.UNKNOWN) =
        VehicleDataKind.entries.associateWith { kind ->
            VehicleDatum(kind = kind, source = DataSource.MOCK, support = support, permission = permission)
        }

    private fun startUpdates(current: Int, refresh: Boolean) {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive) {
                delay(policy.freshnessCheckMillis)
                synchronized(this@MockVehicleProvider) {
                    if (!isCurrent(current)) return@launch
                    val state = mutableState.value
                    mutableState.value = if (refresh && state.movement != VehicleMovement.UNKNOWN && state.error == null) {
                        connected(state.speedKph, state.movement).copy(retryAttempt = state.retryAttempt)
                    } else state.expire(clock(), policy.staleAfterMillis)
                }
            }
        }
    }

    private fun isCurrent(current: Int) = !closed && active && generation == current
}
