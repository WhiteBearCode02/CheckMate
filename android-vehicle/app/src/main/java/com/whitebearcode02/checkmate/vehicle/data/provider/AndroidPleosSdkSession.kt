package com.whitebearcode02.checkmate.vehicle.data.provider

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Door
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import com.whitebearcode02.checkmate.vehicle.data.mapper.VehicleStateMapper
import com.whitebearcode02.checkmate.vehicle.domain.model.ConnectionState
import com.whitebearcode02.checkmate.vehicle.domain.model.SdkInitializationState
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleError
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeoutException

/**
 * Typed, read-only Vehicle SDK 2.3.2 integration verified against the official Door sample
 * and the locally resolved artifact. initialize() returning is never a service connection.
 * Each start creates a new Vehicle; close invalidates UI callbacks immediately and cleans up off-main.
 */
class AndroidPleosSdkSession(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PleosSdkSession {
    private val applicationContext = context.applicationContext
    private var activeRun: Run? = null
    private var retiringRun: Run? = null

    @Synchronized
    override fun start(onState: (VehicleState) -> Unit) {
        if (activeRun != null) return
        val run = Run(onState, retiringRun?.finished)
        activeRun = run
        run.post { run.initialize() }
    }

    @Synchronized
    override fun close() {
        val run = activeRun ?: return
        activeRun = null
        retiringRun = run
        run.accepting = false
        run.post { run.beginClose() }
    }

    /** One channel serializes SDK operations and binder callbacks without blocking the caller. */
    private inner class Run(
        private val onState: (VehicleState) -> Unit,
        private val previousCleanup: CompletableDeferred<Unit>?,
    ) {
        val finished = CompletableDeferred<Unit>()
        @Volatile var accepting = true
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val actions = Channel<() -> Unit>(Channel.UNLIMITED)
        private var vehicle: Vehicle? = null
        private var door: Door? = null
        private val area = DoorArea(DoorValue.ROW_1_LEFT)
        private var registered = false
        private val requests = SdkRequestTracker()
        private var requestTimeout: Job? = null
        private var closing = false
        private var released = false
        private var initialized = false
        private var serviceObserved = false
        private var minimum: Int? = null
        private var maximum: Int? = null
        private var poll: Job? = null
        private var closeGrace: Job? = null
        private val listener = object : DoorPositionListener {
            override fun onPositionUpdated(pos: Int?) = post { publishPosition(pos) }
            override fun onFailed(e: Exception) = post { fail(e) }
        }

        init {
            scope.launch {
                // A quick reconnect may be requested while the prior close is awaiting a callback.
                // Reuse the boundary, but never initialize overlapping Vehicle instances.
                previousCleanup?.await()
                for (action in actions) {
                    try {
                        action()
                    } catch (exception: Exception) {
                        fail(exception)
                    } catch (_: LinkageError) {
                        emit(VehicleStateMapper.failure(
                            ConnectionState.UNSUPPORTED, VehicleError.SDK_UNAVAILABLE,
                            SdkInitializationState.UNSUPPORTED,
                        ))
                        beginClose()
                    }
                }
            }
        }

        fun post(action: () -> Unit) { actions.trySend(action) }

        fun initialize() {
            if (!accepting || closing) return
            emit(VehicleStateMapper.initializing())
            // Component and action are the exact SDK 2.3.2 binding target and merged queries entry.
            val intent = Intent(VEHICLE_ACTION).setComponent(ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
            if (applicationContext.packageManager.resolveService(intent, 0) == null) {
                emit(VehicleStateMapper.failure(
                    ConnectionState.UNSUPPORTED, VehicleError.SDK_UNAVAILABLE,
                    SdkInitializationState.UNSUPPORTED,
                ))
                beginClose()
                return
            }
            if (!hasDoorPermission()) {
                fail(SecurityException())
                return
            }
            val session = Vehicle(applicationContext)
            vehicle = session // Retain even if initialize throws, so failure cleanup can release it.
            session.initialize()
            initialized = true
            emit(VehicleStateMapper.initialized())
            val doorApi = session.getDoor()
            door = doorApi
            val ticket = beginRequest()
            doorApi.checkDoorPositionCapabilities(
                onComplete = { config, boundsByArea -> post {
                    if (!completeRequest(ticket)) return@post
                    if (closing || !accepting) {
                        beginClose()
                        return@post
                    }
                    serviceObserved = true
                    val bounds = boundsByArea[area]
                    val min = bounds?.minValue
                    val max = bounds?.maxValue
                    val readable = (config.accessType == AccessType.READ ||
                        config.accessType == AccessType.READ_WRITE) && min != null && max != null && min <= max
                    emit(VehicleStateMapper.doorCapability(readable))
                    if (readable) {
                        minimum = min
                        maximum = max
                        readPosition()
                    }
                } },
                onFailed = { exception -> post {
                    if (!completeRequest(ticket)) return@post
                    if (closing || !accepting) beginClose() else fail(exception)
                } },
            )
        }

        private fun readPosition() {
            if (closing || !accepting || requests.isPending || minimum == null || maximum == null) return
            val doorApi = door ?: return
            val ticket = beginRequest()
            try {
                doorApi.getCurrentDoorPosition(
                    area = area,
                    onComplete = { position -> post {
                        if (!completeRequest(ticket)) return@post
                        if (closing || !accepting) {
                            beginClose()
                        } else {
                            publishPosition(position)
                            subscribeAndPoll()
                        }
                    } },
                    onFailed = { exception -> post {
                        if (!completeRequest(ticket)) return@post
                        if (closing || !accepting) beginClose() else fail(exception)
                    } },
                )
            } catch (exception: Exception) {
                completeRequest(ticket)
                fail(exception)
            }
        }

        private fun beginRequest(): Long {
            val ticket = requests.begin()
            requestTimeout = scope.launch {
                delay(REQUEST_TIMEOUT_MILLIS)
                post {
                    if (completeRequest(ticket) && !closing && accepting) fail(TimeoutException())
                }
            }
            return ticket
        }

        private fun completeRequest(ticket: Long): Boolean {
            if (!requests.complete(ticket)) return false
            requestTimeout?.cancel()
            requestTimeout = null
            return true
        }

        private fun subscribeAndPoll() {
            if (closing || !accepting) return
            if (!registered) {
                // Mark before the call: a partial/throwing registration is still cleaned up.
                registered = true
                door?.registerDoorPosition(area, listener)
            }
            if (poll != null) return
            // Door notifications are on-change. Re-read unchanged values so freshness is observed.
            poll = scope.launch {
                while (true) {
                    delay(READ_INTERVAL_MILLIS)
                    post { readPosition() }
                }
            }
        }

        private fun publishPosition(position: Int?) {
            if (closing || !accepting) return
            val min = minimum ?: return
            val max = maximum ?: return
            serviceObserved = true
            emit(VehicleStateMapper.doorPosition(position, min, max, nowMillis()))
        }

        private fun fail(exception: Exception) {
            if (closing || !accepting) return
            // The published 2.3.2 artifact obfuscates VehicleException; do not depend on its name,
            // reflect on internals, parse arbitrary messages, or expose those messages to users/logs.
            val denied = exception is SecurityException || !hasDoorPermission()
            val unsupported = exception is UnsupportedOperationException
            emit(VehicleStateMapper.failure(
                connection = when {
                    denied -> ConnectionState.PERMISSION_DENIED
                    unsupported -> ConnectionState.UNSUPPORTED
                    else -> ConnectionState.ERROR
                },
                error = when {
                    denied -> VehicleError.PERMISSION_DENIED
                    unsupported -> VehicleError.UNSUPPORTED_FEATURE
                    exception is TimeoutException -> VehicleError.CONNECTION_TIMEOUT
                    !initialized -> VehicleError.INITIALIZATION_FAILED
                    else -> VehicleError.CONNECTION_FAILED
                },
                initialization = if (initialized) SdkInitializationState.INITIALIZED else SdkInitializationState.FAILED,
                serviceObserved = serviceObserved,
            ))
            beginClose()
        }

        private fun emit(state: VehicleState) {
            if (accepting && !closing) onState(state)
        }

        fun beginClose() {
            if (released) return
            if (!closing) {
                closing = true
                poll?.cancel()
                poll = null
                requestTimeout?.cancel()
                requestTimeout = null
                if (registered) {
                    registered = false
                    try {
                        door?.unregisterDoorPosition(area, listener)
                    } catch (_: Exception) {
                        // Resource release still runs; never log an SDK exception's message.
                    } catch (_: LinkageError) {
                        // A damaged/incompatible SDK must not crash the app during teardown.
                    }
                }
            }
            if (!requests.isPending) {
                release()
            } else if (closeGrace == null) {
                // No cancellation API exists. Let in-flight callbacks finish before release when
                // possible, but bound cleanup if a broken/disconnected service never replies.
                closeGrace = scope.launch {
                    delay(CLOSE_GRACE_MILLIS)
                    post { release() }
                }
            }
        }

        private fun release() {
            if (released) return
            released = true
            try {
                vehicle?.release()
            } catch (_: Exception) {
                // Teardown is best effort and never exposes raw SDK exceptions.
            } catch (_: LinkageError) {
                // Keep unsupported platforms usable for the basic accident guide.
            } finally {
                vehicle = null
                door = null
                actions.close()
                finished.complete(Unit)
                scope.cancel()
            }
        }
    }

    private fun hasDoorPermission(): Boolean =
        applicationContext.checkSelfPermission(DOOR_PERMISSION) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val DOOR_PERMISSION = "pleos.car.permission.CAR_DOORS"
        private const val VEHICLE_ACTION = "ai.pleos.playground.vehicle.VEHICLE_START"
        private const val SERVICE_PACKAGE = "ai.pleos.playground.service.vehicle"
        private const val SERVICE_CLASS = "$SERVICE_PACKAGE.VehicleService"
        private const val READ_INTERVAL_MILLIS = 2_000L
        private const val REQUEST_TIMEOUT_MILLIS = 5_000L
        private const val CLOSE_GRACE_MILLIS = 1_500L
    }
}
