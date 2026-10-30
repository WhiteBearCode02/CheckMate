package com.whitebearcode02.checkmate.vehicle.data.provider

import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleState
import kotlinx.coroutines.flow.StateFlow

interface VehicleDataProvider : AutoCloseable {
    val state: StateFlow<VehicleState>
    fun connect()
    fun disconnect()
    /** Explicit retry is allowed after the bounded automatic retry budget is exhausted. */
    fun reconnect() {
        disconnect()
        connect()
    }
    override fun close()
}
