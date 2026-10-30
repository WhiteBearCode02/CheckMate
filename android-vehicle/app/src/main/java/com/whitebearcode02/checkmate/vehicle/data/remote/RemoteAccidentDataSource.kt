package com.whitebearcode02.checkmate.vehicle.data.remote

import com.whitebearcode02.checkmate.vehicle.data.auth.AuthenticatedSession
import com.whitebearcode02.checkmate.vehicle.domain.model.AccidentRecord

enum class RemoteMode { FAKE, EXTERNAL }

sealed interface RemoteReadResult {
    data class Success(val records: List<AccidentRecord>) : RemoteReadResult
    data object AccessDenied : RemoteReadResult
    data object Unavailable : RemoteReadResult
}

sealed interface RemoteWriteResult {
    data class Applied(val record: AccidentRecord) : RemoteWriteResult
    data class Conflict(val record: AccidentRecord) : RemoteWriteResult
    data object AccessDenied : RemoteWriteResult
    data object InvalidOperation : RemoteWriteResult
    data object Unavailable : RemoteWriteResult
}

/** Metadata only. This API cannot upload files or accept a caller-supplied access userId. */
interface RemoteAccidentDataSource {
    val mode: RemoteMode
    suspend fun records(session: AuthenticatedSession): RemoteReadResult
    suspend fun upsert(
        session: AuthenticatedSession,
        record: AccidentRecord,
        expectedRemoteVersion: Long,
        operationId: String,
    ): RemoteWriteResult
}

/** Logical content excludes device-local queue/revision bookkeeping. */
internal fun AccidentRecord.sameContent(other: AccidentRecord): Boolean = copy(sync = other.sync) == other
