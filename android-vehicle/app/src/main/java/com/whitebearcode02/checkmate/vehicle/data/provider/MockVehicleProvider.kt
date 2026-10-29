package com.whitebearcode02.checkmate.vehicle.data.provider

import com.whitebearcode02.checkmate.vehicle.domain.model.ConnectionState
import com.whitebearcode02.checkmate.vehicle.domain.model.DataSource
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleCapability
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleError
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleMovement
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MockVehicleScenario { SUCCESS, FAILURE, NO_DATA, UNSUPPORTED, MOVING }

/** Explicit, deterministic development scenarios. No live vehicle service is used. */
class MockVehicleProvider(
    private var scenario: MockVehicleScenario = MockVehicleScenario.SUCCESS,
) : VehicleDataProvider {
    private val mutableState = MutableStateFlow(VehicleState(source = DataSource.MOCK))
    override val state: StateFlow<VehicleState> = mutableState.asStateFlow()
    private var closed = false

    override fun connect() {
        if (closed) return
        mutableState.value = VehicleState(connection = ConnectionState.CONNECTING, source = DataSource.MOCK)
        mutableState.value = when (scenario) {
            MockVehicleScenario.SUCCESS -> connected(0.0, VehicleMovement.PARKED)
            MockVehicleScenario.MOVING -> connected(45.0, VehicleMovement.MOVING)
            MockVehicleScenario.FAILURE -> VehicleState(
                connection = ConnectionState.FAILED, source = DataSource.MOCK, error = VehicleError.CONNECTION_FAILED,
            )
            MockVehicleScenario.NO_DATA -> VehicleState(
                connection = ConnectionState.CONNECTED, source = DataSource.MOCK,
                capabilities = setOf(VehicleCapability.SPEED, VehicleCapability.MOVEMENT),
                error = VehicleError.DATA_UNAVAILABLE,
            )
            MockVehicleScenario.UNSUPPORTED -> VehicleState(
                connection = ConnectionState.CONNECTED, source = DataSource.MOCK,
            )
        }
    }

    fun setScenario(scenario: MockVehicleScenario) {
        if (closed) return
        this.scenario = scenario
        connect()
    }

    override fun disconnect() {
        mutableState.value = VehicleState(source = DataSource.MOCK)
    }

    override fun close() {
        if (closed) return
        closed = true
        disconnect()
    }

    private fun connected(speed: Double, movement: VehicleMovement) = VehicleState(
        connection = ConnectionState.CONNECTED, source = DataSource.MOCK, speedKph = speed, movement = movement,
        capabilities = setOf(VehicleCapability.SPEED, VehicleCapability.MOVEMENT),
    )
}
