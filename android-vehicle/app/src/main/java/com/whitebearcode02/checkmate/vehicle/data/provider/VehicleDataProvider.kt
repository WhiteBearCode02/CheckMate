package com.whitebearcode02.checkmate.vehicle.data.provider

import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleState
import kotlinx.coroutines.flow.StateFlow

interface VehicleDataProvider : AutoCloseable {
    val state: StateFlow<VehicleState>
    fun connect()
    fun disconnect()
    override fun close()
}
