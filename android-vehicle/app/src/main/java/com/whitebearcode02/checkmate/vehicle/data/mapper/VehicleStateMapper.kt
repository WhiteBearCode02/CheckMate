package com.whitebearcode02.checkmate.vehicle.data.mapper

import com.whitebearcode02.checkmate.vehicle.domain.model.ConnectionState
import com.whitebearcode02.checkmate.vehicle.domain.model.DataPermission
import com.whitebearcode02.checkmate.vehicle.domain.model.DataSource
import com.whitebearcode02.checkmate.vehicle.domain.model.DataUpdateState
import com.whitebearcode02.checkmate.vehicle.domain.model.DataValidity
import com.whitebearcode02.checkmate.vehicle.domain.model.FeatureSupport
import com.whitebearcode02.checkmate.vehicle.domain.model.SdkInitializationState
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleCapability
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleDataKind
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleDatum
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleError
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleState
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleUnit
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleValue

/** SDK-free conversion policy. Door information never establishes movement or parking. */
object VehicleStateMapper {
    fun initializing() = VehicleState(
        connection = ConnectionState.CONNECTING,
        source = DataSource.PLEOS,
        sdkInitialization = SdkInitializationState.INITIALIZING,
    )

    fun initialized() = initializing().copy(sdkInitialization = SdkInitializationState.INITIALIZED)

    fun doorCapability(readable: Boolean): VehicleState = initialized().copy(
        connection = ConnectionState.CONNECTED,
        serviceObserved = true,
        capabilities = if (readable) setOf(VehicleCapability.DOOR) else emptySet(),
        error = if (readable) null else VehicleError.UNSUPPORTED_FEATURE,
        readings = mapOf(
            VehicleDataKind.DOOR to VehicleDatum(
                kind = VehicleDataKind.DOOR,
                source = DataSource.PLEOS,
                unit = VehicleUnit.STATE,
                support = if (readable) FeatureSupport.SUPPORTED else FeatureSupport.UNSUPPORTED,
                permission = DataPermission.GRANTED,
            ),
        ),
    )

    fun doorPosition(position: Int?, minimum: Int, maximum: Int, nowMillis: Long): VehicleState {
        val valid = position != null && minimum <= maximum && position in minimum..maximum
        val capability = doorCapability(readable = true)
        return capability.copy(
            error = if (valid) null else VehicleError.DATA_UNAVAILABLE,
            lastUpdatedAtMillis = nowMillis.takeIf { valid },
            readings = mapOf(
                VehicleDataKind.DOOR to capability.readings.getValue(VehicleDataKind.DOOR).copy(
                    value = position?.takeIf { valid }?.let { VehicleValue.NumberValue(it.toDouble()) },
                    collectedAtMillis = nowMillis.takeIf { valid },
                    validity = if (valid) DataValidity.VALID else DataValidity.INVALID,
                    updateState = DataUpdateState.UPDATING,
                ),
            ),
        )
    }

    fun failure(
        connection: ConnectionState,
        error: VehicleError,
        initialization: SdkInitializationState,
        serviceObserved: Boolean = false,
    ) = VehicleState(
        connection = connection,
        source = DataSource.PLEOS,
        error = error,
        sdkInitialization = initialization,
        serviceObserved = serviceObserved,
        readings = mapOf(
            VehicleDataKind.DOOR to VehicleDatum(
                kind = VehicleDataKind.DOOR,
                source = DataSource.PLEOS,
                unit = VehicleUnit.STATE,
                support = if (connection == ConnectionState.UNSUPPORTED) {
                    FeatureSupport.UNSUPPORTED
                } else FeatureSupport.UNKNOWN,
                permission = if (connection == ConnectionState.PERMISSION_DENIED) {
                    DataPermission.DENIED
                } else DataPermission.UNKNOWN,
                updateState = DataUpdateState.STOPPED,
            ),
        ),
    )
}
