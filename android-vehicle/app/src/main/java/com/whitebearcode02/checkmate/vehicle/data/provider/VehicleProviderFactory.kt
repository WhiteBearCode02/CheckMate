package com.whitebearcode02.checkmate.vehicle.data.provider

import com.whitebearcode02.checkmate.vehicle.domain.model.DataSource

/** Selection is explicit. A failed real provider is never replaced with fabricated vehicle values. */
class VehicleProviderFactory(
    private val createPleos: () -> VehicleDataProvider,
    private val allowMock: Boolean = false,
    private val createMock: () -> VehicleDataProvider = { MockVehicleProvider() },
) {
    fun create(source: DataSource): VehicleDataProvider = when (source) {
        DataSource.PLEOS -> createPleos()
        DataSource.MOCK -> {
            require(allowMock) { "Mock mode is only available in development builds" }
            createMock()
        }
    }
}
