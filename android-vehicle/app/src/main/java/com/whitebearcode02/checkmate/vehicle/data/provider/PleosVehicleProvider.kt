package com.whitebearcode02.checkmate.vehicle.data.provider

import com.whitebearcode02.checkmate.vehicle.domain.model.ConnectionState
import com.whitebearcode02.checkmate.vehicle.domain.model.DataSource
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleError
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-owned boundary, not Pleos SDK signatures. start registers callbacks without blocking. */
interface PleosSdkSession : AutoCloseable {
    fun start(onState: (VehicleState) -> Unit)
    /** Unregister asynchronously; never wait for a callback thread while closing this boundary. */
    override fun close()
}

/** Fail-closed until the official SDK adapter is verified on the Pleos emulator. */
class PleosVehicleProvider(private val session: PleosSdkSession? = null) : VehicleDataProvider {
    private val mutableState = MutableStateFlow(VehicleState(source = DataSource.PLEOS))
    override val state: StateFlow<VehicleState> = mutableState.asStateFlow()
    private var closed = false
    private var generation = 0
    private var sessionStarted = false

    @Synchronized
    override fun connect() {
        if (closed || sessionStarted) return
        val sdk = session
        if (sdk == null) {
            mutableState.value = VehicleState(
                connection = ConnectionState.UNAVAILABLE, source = DataSource.PLEOS,
                error = VehicleError.SDK_UNAVAILABLE,
            )
            return
        }
        val currentGeneration = ++generation
        mutableState.value = VehicleState(connection = ConnectionState.CONNECTING, source = DataSource.PLEOS)
        sessionStarted = true
        try {
            sdk.start { snapshot ->
                synchronized(this) {
                    if (!closed && sessionStarted && generation == currentGeneration) {
                        mutableState.value = snapshot.copy(source = DataSource.PLEOS)
                    }
                }
            }
        } catch (_: SecurityException) {
            releaseSession()
            mutableState.value = failed(VehicleError.PERMISSION_DENIED)
        } catch (_: Exception) {
            releaseSession()
            mutableState.value = failed(VehicleError.CONNECTION_FAILED)
        }
    }

    @Synchronized
    override fun disconnect() {
        releaseSession()
        mutableState.value = VehicleState(source = DataSource.PLEOS)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        disconnect()
    }

    private fun releaseSession() {
        generation++
        if (!sessionStarted) return
        sessionStarted = false
        try {
            session?.close()
        } catch (_: Exception) {
            // SDK messages are intentionally neither logged nor exposed to the UI.
        }
    }

    private fun failed(error: VehicleError) = VehicleState(
        connection = ConnectionState.FAILED, source = DataSource.PLEOS, error = error,
    )
}
