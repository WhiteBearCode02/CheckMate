package com.whitebearcode02.checkmate.vehicle.domain.model

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, FAILED, UNAVAILABLE }

enum class DataSource { MOCK, PLEOS }

enum class VehicleMovement { UNKNOWN, PARKED, MOVING }

enum class VehicleCapability { SPEED, MOVEMENT }

/** Safe error codes only: SDK exceptions may contain private vehicle information. */
enum class VehicleError { CONNECTION_FAILED, PERMISSION_DENIED, SDK_UNAVAILABLE, DATA_UNAVAILABLE }

data class VehicleState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val source: DataSource = DataSource.MOCK,
    val speedKph: Double? = null,
    val movement: VehicleMovement = VehicleMovement.UNKNOWN,
    val capabilities: Set<VehicleCapability> = emptySet(),
    val error: VehicleError? = null,
) {
    init {
        require(speedKph == null || (speedKph.isFinite() && speedKph >= 0))
        require(speedKph == null || VehicleCapability.SPEED in capabilities)
        require(movement == VehicleMovement.UNKNOWN || VehicleCapability.MOVEMENT in capabilities)
    }

    /** Unknown movement never grants access to detailed driver-facing screens. */
    val permitsDetailedInteraction: Boolean
        get() = connection == ConnectionState.CONNECTED && movement == VehicleMovement.PARKED &&
            error == null && (speedKph == null || speedKph == 0.0)
}
