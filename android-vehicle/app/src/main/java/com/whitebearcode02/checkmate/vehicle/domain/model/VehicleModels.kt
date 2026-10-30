package com.whitebearcode02.checkmate.vehicle.domain.model

/** FAILED and UNAVAILABLE remain for the Phase 1 shared accident contract. */
enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, FAILED, UNAVAILABLE, UNSUPPORTED, PERMISSION_DENIED, ERROR, UNKNOWN,
}
enum class SdkInitializationState { NOT_STARTED, INITIALIZING, INITIALIZED, FAILED, UNSUPPORTED }
enum class DataSource { MOCK, PLEOS }
enum class VehicleMovement { UNKNOWN, PARKED, MOVING }
enum class VehicleCapability { SPEED, MOVEMENT, BRAKE, TURN_SIGNAL, DRIVING_MODE, ODOMETER, DOOR }
enum class VehicleDataKind { SPEED, MOVEMENT, BRAKE, TURN_SIGNAL, DRIVING_MODE, ODOMETER, DOOR }
enum class VehicleUnit { UNKNOWN, KPH, KM, PERCENT, STATE, BOOLEAN }
enum class DataValidity { UNKNOWN, VALID, STALE, INVALID }
enum class FeatureSupport { UNKNOWN, SUPPORTED, UNSUPPORTED }
enum class DataPermission { UNKNOWN, GRANTED, DENIED }
enum class DataUpdateState { WAITING, UPDATING, STOPPED }

sealed interface VehicleValue {
    data class NumberValue(val number: Double) : VehicleValue {
        init { require(number.isFinite()) }
    }
    /** App-owned labels only; never place raw SDK errors or vehicle identifiers here. */
    data class StateValue(val state: String) : VehicleValue
    data class BooleanValue(val enabled: Boolean) : VehicleValue
}

/** A sample and its provenance travel together. Missing data is never represented by zero. */
data class VehicleDatum(
    val kind: VehicleDataKind,
    val value: VehicleValue? = null,
    val unit: VehicleUnit = VehicleUnit.UNKNOWN,
    val source: DataSource,
    val collectedAtMillis: Long? = null,
    val validity: DataValidity = DataValidity.UNKNOWN,
    val support: FeatureSupport = FeatureSupport.UNKNOWN,
    val permission: DataPermission = DataPermission.UNKNOWN,
    val updateState: DataUpdateState = DataUpdateState.WAITING,
) {
    init { require(collectedAtMillis == null || collectedAtMillis >= 0L) }

    val isUsable: Boolean
        get() = value != null && collectedAtMillis != null && validity == DataValidity.VALID &&
            support == FeatureSupport.SUPPORTED && permission == DataPermission.GRANTED &&
            updateState == DataUpdateState.UPDATING

    fun expire(nowMillis: Long, maxAgeMillis: Long): VehicleDatum {
        val collected = collectedAtMillis ?: return this
        if (validity != DataValidity.VALID) return this
        return if (nowMillis < collected || nowMillis - collected >= maxAgeMillis) {
            copy(validity = DataValidity.STALE, updateState = DataUpdateState.STOPPED)
        } else this
    }
}

/** Safe error codes only: SDK exceptions may contain private vehicle information. */
enum class VehicleError {
    CONNECTION_FAILED, PERMISSION_DENIED, SDK_UNAVAILABLE, DATA_UNAVAILABLE, INITIALIZATION_FAILED,
    CONNECTION_TIMEOUT, DATA_STALE, UNSUPPORTED_FEATURE,
}

data class VehicleState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val source: DataSource = DataSource.MOCK,
    val speedKph: Double? = null,
    val movement: VehicleMovement = VehicleMovement.UNKNOWN,
    val capabilities: Set<VehicleCapability> = emptySet(),
    val error: VehicleError? = null,
    val readings: Map<VehicleDataKind, VehicleDatum> = emptyMap(),
    val lastUpdatedAtMillis: Long? = null,
    val retryAttempt: Int = 0,
    val sdkInitialization: SdkInitializationState = SdkInitializationState.NOT_STARTED,
    val serviceObserved: Boolean = false,
) {
    init {
        require(speedKph == null || (speedKph.isFinite() && speedKph >= 0))
        require(speedKph == null || VehicleCapability.SPEED in capabilities)
        require(movement == VehicleMovement.UNKNOWN || VehicleCapability.MOVEMENT in capabilities)
        require(readings.all { (kind, datum) -> kind == datum.kind && datum.source == source })
        require(retryAttempt >= 0)
        require(lastUpdatedAtMillis == null || lastUpdatedAtMillis >= 0L)
    }

    /** A fresh explicit parked sample is required, including after process recreation. */
    val permitsDetailedInteraction: Boolean
        get() {
            val movementDatum = readings[VehicleDataKind.MOVEMENT] ?: return false
            val speedDatum = readings[VehicleDataKind.SPEED]
            return connection == ConnectionState.CONNECTED && error == null &&
                (source != DataSource.PLEOS ||
                    sdkInitialization == SdkInitializationState.INITIALIZED && serviceObserved) &&
                movement == VehicleMovement.PARKED && movementDatum.isUsable &&
                (movementDatum.value as? VehicleValue.StateValue)?.state == VehicleMovement.PARKED.name &&
                (speedKph == null || speedKph == 0.0) &&
                (speedDatum == null || speedDatum.isUsable &&
                    (speedDatum.value as? VehicleValue.NumberValue)?.number == 0.0)
        }

    /** Providers call this on a monotonic schedule; wall-clock rollback also fails closed. */
    fun expire(nowMillis: Long, maxAgeMillis: Long): VehicleState {
        val aged = readings.mapValues { (_, datum) -> datum.expire(nowMillis, maxAgeMillis) }
        if (aged == readings) return this
        val movementFresh = aged[VehicleDataKind.MOVEMENT]?.isUsable == true
        val speedFresh = aged[VehicleDataKind.SPEED]?.isUsable == true
        return copy(
            readings = aged,
            speedKph = speedKph.takeIf { speedFresh },
            movement = movement.takeIf { movementFresh } ?: VehicleMovement.UNKNOWN,
            error = error ?: VehicleError.DATA_STALE,
        )
    }
}
