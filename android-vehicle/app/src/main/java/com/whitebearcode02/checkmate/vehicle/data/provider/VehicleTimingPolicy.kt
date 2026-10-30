package com.whitebearcode02.checkmate.vehicle.data.provider

/** Bounded local work only. No server, network requests or paid service is involved. */
data class VehicleTimingPolicy(
    val connectTimeoutMillis: Long = 5_000,
    val staleAfterMillis: Long = 5_000,
    val retryDelaysMillis: List<Long> = listOf(500, 1_000),
    val freshnessCheckMillis: Long = 500,
) {
    init {
        require(connectTimeoutMillis > 0 && staleAfterMillis > 0 && freshnessCheckMillis > 0)
        require(retryDelaysMillis.size <= 3 && retryDelaysMillis.all { it > 0 })
    }
}
