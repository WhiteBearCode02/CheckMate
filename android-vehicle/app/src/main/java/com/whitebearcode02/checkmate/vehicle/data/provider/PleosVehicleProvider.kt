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

/** App-owned boundary, not Pleos SDK signatures. start registers callbacks without blocking. */
interface PleosSdkSession : AutoCloseable {
    fun start(onState: (VehicleState) -> Unit)
    /** Unregister asynchronously; never wait for a callback thread while closing this boundary. */
    override fun close()
}

/** Owns connection attempts and freshness. Initialization alone never establishes a connection. */
class PleosVehicleProvider(
    private val session: PleosSdkSession? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: () -> Long = System::currentTimeMillis,
    private val policy: VehicleTimingPolicy = VehicleTimingPolicy(),
) : VehicleDataProvider {
    private val mutableState = MutableStateFlow(VehicleState(source = DataSource.PLEOS))
    override val state: StateFlow<VehicleState> = mutableState.asStateFlow()
    private val stateLock = Any()
    /** SDK start/close are serialized without holding the callback/state monitor. */
    private val sessionLock = Any()
    private var closed = false
    private var generation = 0
    private var sessionStarted = false
    private var terminalCallbackReceived = false
    private var attempt = 0
    private var timeoutJob: Job? = null
    private var freshnessJob: Job? = null
    private var retryJob: Job? = null

    override fun connect() = startAttempt(resetBudget = true)

    private fun startAttempt(resetBudget: Boolean) {
        val currentGeneration: Int
        synchronized(stateLock) {
            if (closed || sessionStarted || retryJob?.isActive == true && resetBudget) return
            if (resetBudget) attempt = 0
            if (session == null) {
                mutableState.value = VehicleState(
                    connection = ConnectionState.UNAVAILABLE, source = DataSource.PLEOS,
                    error = VehicleError.SDK_UNAVAILABLE,
                    sdkInitialization = SdkInitializationState.UNSUPPORTED,
                )
                return
            }
            currentGeneration = ++generation
            sessionStarted = true
            terminalCallbackReceived = false
            mutableState.value = VehicleState(
                connection = ConnectionState.CONNECTING, source = DataSource.PLEOS,
                retryAttempt = attempt, sdkInitialization = SdkInitializationState.INITIALIZING,
            )
            timeoutJob = scope.launch {
                delay(policy.connectTimeoutMillis)
                fail(currentGeneration, VehicleError.CONNECTION_TIMEOUT, ConnectionState.ERROR, true)
            }
            freshnessJob = scope.launch {
                while (isActive) {
                    delay(policy.freshnessCheckMillis)
                    synchronized(stateLock) {
                        if (isCurrent(currentGeneration)) {
                            mutableState.value = mutableState.value.expire(clock(), policy.staleAfterMillis)
                        }
                    }
                }
            }
        }
        try {
            synchronized(sessionLock) {
                val shouldStart = synchronized(stateLock) { isCurrent(currentGeneration) }
                if (shouldStart) session?.start { accept(currentGeneration, it) }
            }
        } catch (_: SecurityException) {
            fail(currentGeneration, VehicleError.PERMISSION_DENIED, ConnectionState.PERMISSION_DENIED, false)
        } catch (_: LinkageError) {
            fail(currentGeneration, VehicleError.SDK_UNAVAILABLE, ConnectionState.UNSUPPORTED, false)
        } catch (_: Exception) {
            fail(currentGeneration, VehicleError.INITIALIZATION_FAILED, ConnectionState.ERROR, true)
        }
    }

    private fun accept(expectedGeneration: Int, snapshot: VehicleState) {
        var failure: Pair<VehicleError, Boolean>? = null
        synchronized(stateLock) {
            if (!isCurrent(expectedGeneration) || terminalCallbackReceived) return
            val provenanceValid = snapshot.source == DataSource.PLEOS &&
                snapshot.readings.values.all { it.source == DataSource.PLEOS }
            val observed = snapshot.sdkInitialization == SdkInitializationState.INITIALIZED && snapshot.serviceObserved
            val normalized = when {
                !provenanceValid -> VehicleState(
                    connection = ConnectionState.ERROR, source = DataSource.PLEOS,
                    error = VehicleError.DATA_UNAVAILABLE, retryAttempt = attempt,
                    sdkInitialization = mutableState.value.sdkInitialization,
                )
                snapshot.connection == ConnectionState.CONNECTED && !observed -> snapshot.copy(
                    connection = ConnectionState.CONNECTING, speedKph = null, movement = VehicleMovement.UNKNOWN,
                    readings = emptyMap(), lastUpdatedAtMillis = null, retryAttempt = attempt,
                )
                else -> snapshot.copy(retryAttempt = attempt).expire(clock(), policy.staleAfterMillis)
            }
            mutableState.value = normalized
            when (normalized.connection) {
                ConnectionState.CONNECTED -> timeoutJob?.cancel()
                ConnectionState.PERMISSION_DENIED -> failure = VehicleError.PERMISSION_DENIED to false
                ConnectionState.UNSUPPORTED, ConnectionState.UNAVAILABLE ->
                    failure = (normalized.error ?: VehicleError.SDK_UNAVAILABLE) to false
                ConnectionState.FAILED, ConnectionState.ERROR, ConnectionState.DISCONNECTED ->
                    failure = (normalized.error ?: VehicleError.CONNECTION_FAILED) to true
                else -> Unit
            }
            // A delayed success from this SDK attempt cannot overtake a terminal error.
            if (failure != null) terminalCallbackReceived = true
        }
        failure?.let { (error, retryable) ->
            // Callback threads never block waiting for SDK cleanup or another callback.
            val failureConnection = mutableState.value.connection
            scope.launch { fail(expectedGeneration, error, failureConnection, retryable) }
        }
    }

    private fun fail(expectedGeneration: Int, error: VehicleError, connection: ConnectionState, retryable: Boolean) {
        synchronized(sessionLock) {
            val cleanup: Boolean
            val failureGeneration: Int
            val retryDelay: Long?
            synchronized(stateLock) {
                if (!isCurrent(expectedGeneration)) return
                cleanup = sessionStarted
                sessionStarted = false
                failureGeneration = ++generation
                timeoutJob?.cancel()
                freshnessJob?.cancel()
                val previous = mutableState.value
                val safeMetadata = previous.readings.filterValues { it.source == DataSource.PLEOS }
                    .mapValues { (_, datum) -> datum.copy(
                        value = null, collectedAtMillis = null, validity = DataValidity.UNKNOWN,
                        updateState = DataUpdateState.STOPPED,
                    ) }
                mutableState.value = VehicleState(
                    connection = connection, source = DataSource.PLEOS, error = error, retryAttempt = attempt,
                    readings = safeMetadata,
                    sdkInitialization = if (error == VehicleError.INITIALIZATION_FAILED) {
                        SdkInitializationState.FAILED
                    } else previous.sdkInitialization,
                )
                retryDelay = policy.retryDelaysMillis.getOrNull(attempt).takeIf { retryable }
            }
            if (cleanup) closeSession()
            if (retryDelay != null) {
                synchronized(stateLock) {
                    if (closed || generation != failureGeneration) return
                    retryJob = scope.launch {
                        delay(retryDelay)
                        synchronized(stateLock) {
                            if (closed || generation != failureGeneration) return@launch
                            attempt++
                            retryJob = null
                        }
                        startAttempt(resetBudget = false)
                    }
                }
            }
        }
    }

    override fun disconnect() {
        stop(closePermanently = false)
    }

    override fun close() {
        stop(closePermanently = true)
    }

    private fun stop(closePermanently: Boolean) {
        synchronized(sessionLock) {
            val cleanup: Boolean
            synchronized(stateLock) {
                if (closed) return
                if (closePermanently) closed = true
                generation++
                cleanup = sessionStarted
                sessionStarted = false
                timeoutJob?.cancel()
                freshnessJob?.cancel()
                retryJob?.cancel()
                retryJob = null
                mutableState.value = VehicleState(source = DataSource.PLEOS)
            }
            if (cleanup) closeSession()
        }
    }

    private fun isCurrent(expectedGeneration: Int): Boolean =
        !closed && sessionStarted && generation == expectedGeneration

    private fun closeSession() = synchronized(sessionLock) {
        try { session?.close() } catch (_: Exception) {
            // Raw SDK exception messages are intentionally neither logged nor exposed.
        } catch (_: LinkageError) {
            // A non-AAOS runtime may not provide a transitive SDK class even during cleanup.
        }
    }
}
