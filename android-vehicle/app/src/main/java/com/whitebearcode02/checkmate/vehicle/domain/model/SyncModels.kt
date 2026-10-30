package com.whitebearcode02.checkmate.vehicle.domain.model

import java.time.Instant
import java.util.UUID

const val ACCIDENT_SCHEMA_VERSION = 1

enum class EventOrigin { VEHICLE_MANUAL, MOBILE_MANUAL, MOBILE_SENSOR, DEMO }
enum class DeviceKind { VEHICLE, MOBILE }
enum class SyncStatus { LOCAL_ONLY, PENDING, SYNCED, RETRY, CONFLICT, BLOCKED }
enum class EvidenceTransferState { LOCAL_ONLY, METADATA_ONLY, READY_WITH_CONSENT, UNAVAILABLE }

data class UserProfile(val userId: UUID, val createdAt: Instant, val recordingEnabled: Boolean = false)

data class RegisteredDevice(
    val deviceId: UUID,
    val userId: UUID,
    val kind: DeviceKind,
    val registeredAt: Instant,
)

data class AccidentSyncMetadata(
    val localVersion: Long = 1,
    val remoteVersion: Long = 0,
    val modifiedAt: Instant,
    val modifiedByDeviceId: UUID,
    val status: SyncStatus = SyncStatus.LOCAL_ONLY,
) {
    init { require(localVersion > 0 && remoteVersion >= 0) }
}

/** Metadata never proves that bytes exist on this device or that transfer is authorized. */
data class EvidenceMetadata(
    val evidenceId: UUID,
    val eventId: UUID,
    val kind: EvidenceKind,
    val ownerDeviceId: UUID,
    val createdAt: Instant,
    val mimeType: String,
    val transferState: EvidenceTransferState = EvidenceTransferState.METADATA_ONLY,
) {
    init { require(mimeType.isNotBlank() && mimeType.length <= 128) }
}

data class InsuranceContact(val userId: UUID, val companyName: String, val phoneNumber: String) {
    init {
        require(companyName.isNotBlank() && companyName.length <= 100)
        require(phoneNumber.matches(Regex("[+0-9() -]{3,32}")))
    }
    override fun toString() = "InsuranceContact([redacted])"
}

/** Shared aggregate. No file URI, raw video, GPS or credentials may be added to this envelope. */
data class AccidentRecord(
    val event: AccidentEvent,
    val ownerUserId: UUID,
    val createdByDeviceId: UUID,
    val sync: AccidentSyncMetadata,
    val assistance: List<AssistanceAction> = emptyList(),
    val prediction: AccidentPrediction? = null,
    val evidence: List<EvidenceMetadata> = emptyList(),
    val deletedAt: Instant? = null,
    val schemaVersion: Int = ACCIDENT_SCHEMA_VERSION,
) {
    init {
        require(schemaVersion == ACCIDENT_SCHEMA_VERSION)
        require(assistance.all { it.eventId == event.eventId })
        require(assistance.map { it.step }.distinct().size == assistance.size)
        require(prediction == null || prediction.eventId == event.eventId)
        require(evidence.all { it.eventId == event.eventId })
        require(evidence.map { it.evidenceId }.distinct().size == evidence.size)
    }
    override fun toString() = "AccidentRecord(eventId=${event.eventId}, version=${sync.localVersion}, contents=[redacted])"
}
