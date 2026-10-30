package com.whitebearcode02.checkmate.vehicle.data.provider

/** Actor-owned request tickets prevent duplicate/late callbacks from completing a newer read. */
internal class SdkRequestTracker {
    private var sequence = 0L
    private var activeTicket: Long? = null
    val isPending: Boolean get() = activeTicket != null

    fun begin(): Long {
        check(!isPending)
        return (++sequence).also { activeTicket = it }
    }

    fun complete(ticket: Long): Boolean {
        if (activeTicket != ticket) return false
        activeTicket = null
        return true
    }
}
