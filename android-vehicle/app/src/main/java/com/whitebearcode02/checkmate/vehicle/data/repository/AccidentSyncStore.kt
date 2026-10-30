package com.whitebearcode02.checkmate.vehicle.data.repository

import com.whitebearcode02.checkmate.vehicle.domain.model.AccidentRecord
import java.util.UUID

/** Atomic compare-and-set prevents in-flight sync from erasing a newer local edit. */
interface AccidentSyncStore {
    /** Null only for explicitly multi-user test stores; persistent stores bind one local profile. */
    val ownerUserId: UUID? get() = null
    fun records(): List<AccidentRecord>
    /** expectedLocalVersion=0 inserts an unseen event. False means retry from a fresh snapshot. */
    fun applySync(expectedLocalVersion: Long, record: AccidentRecord): Boolean
}
