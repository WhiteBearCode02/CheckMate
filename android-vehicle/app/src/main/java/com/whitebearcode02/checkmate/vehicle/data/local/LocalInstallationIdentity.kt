package com.whitebearcode02.checkmate.vehicle.data.local

import android.content.Context
import java.util.UUID

/** Local ownership namespace only. This is never an authenticated cloud account or pairing proof. */
data class LocalInstallationIdentity(val userId: UUID, val deviceId: UUID) {
    companion object {
        @Synchronized
        fun load(context: Context): LocalInstallationIdentity {
            val preferences = context.getSharedPreferences("checkmate_local_identity", Context.MODE_PRIVATE)
            val existingUser = preferences.getString("user_id", null)
            val existingDevice = preferences.getString("device_id", null)
            if (existingUser != null || existingDevice != null) {
                // Incomplete or malformed identity must not silently switch to a new user's empty namespace.
                check(existingUser != null && existingDevice != null) { "Local identity unavailable" }
                return try { LocalInstallationIdentity(UUID.fromString(existingUser), UUID.fromString(existingDevice)) }
                catch (_: IllegalArgumentException) { throw IllegalStateException("Local identity unavailable") }
            }
            val identity = LocalInstallationIdentity(UUID.randomUUID(), UUID.randomUUID())
            check(preferences.edit().putString("user_id", identity.userId.toString())
                .putString("device_id", identity.deviceId.toString()).commit()) { "Local identity unavailable" }
            return identity
        }
    }
}
