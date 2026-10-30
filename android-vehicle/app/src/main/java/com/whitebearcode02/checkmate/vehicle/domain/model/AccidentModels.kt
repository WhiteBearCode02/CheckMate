package com.whitebearcode02.checkmate.vehicle.domain.model

import java.time.Instant
import java.util.UUID

enum class AccidentStatus {
    NORMAL, SUSPECTED, FALSE_ALARM, CONFIRMED, ASSISTANCE, ANALYSIS_PENDING, COMPLETED, UNAVAILABLE,
}

data class DriveSession(
    val sessionId: UUID,
    val startedAt: Instant,
    val endedAt: Instant? = null,
) {
    init { require(endedAt == null || !endedAt.isBefore(startedAt)) }
}

/** UUID.toString and Instant.toString are the shared UUID/ISO-8601 wire forms. */
data class AccidentEvent(
    val eventId: UUID,
    val occurredAt: Instant,
    val status: AccidentStatus = AccidentStatus.SUSPECTED,
    val vehicleSnapshot: VehicleState,
    val phoneConnection: ConnectionState = ConnectionState.DISCONNECTED,
    val source: DataSource = DataSource.MOCK,
    val sessionId: UUID? = null,
    val origin: EventOrigin = EventOrigin.DEMO,
)

enum class EvidenceKind { VIDEO, LOCATION, VEHICLE_SNAPSHOT, PHOTO }

/** A private file reference and metadata, never encoded video or raw location coordinates. */
data class Evidence(
    val evidenceId: UUID,
    val eventId: UUID,
    val kind: EvidenceKind,
    val capturedAt: Instant,
    val fileReference: String,
    val mimeType: String,
    val durationMillis: Long? = null,
) {
    init {
        require(fileReference.isNotBlank())
        require(mimeType.isNotBlank())
        require(durationMillis == null || durationMillis >= 0)
    }

    override fun toString(): String = "Evidence(evidenceId=$evidenceId, eventId=$eventId, kind=$kind, fileReference=[redacted])"
}

enum class PredictionStatus { NOT_REQUESTED, PENDING, AVAILABLE, UNAVAILABLE, FAILED }

data class AccidentPrediction(
    val eventId: UUID,
    val status: PredictionStatus,
    val accidentType: String? = null,
    val ownFaultPercent: Int? = null,
    val otherFaultPercent: Int? = null,
    val rationale: String? = null,
    val modelLabel: String = "미연동",
    val source: DataSource = DataSource.MOCK,
) {
    init {
        if (status == PredictionStatus.AVAILABLE) {
            require(!accidentType.isNullOrBlank())
            require(!rationale.isNullOrBlank())
            require(ownFaultPercent != null && ownFaultPercent in 0..100)
            require(otherFaultPercent != null && otherFaultPercent in 0..100)
            require(ownFaultPercent + otherFaultPercent == 100)
        } else {
            require(ownFaultPercent == null && otherFaultPercent == null)
        }
    }
}

enum class AssistanceStep {
    OCCUPANT_SAFETY, SECONDARY_COLLISION, EMERGENCY_GUIDANCE, EVIDENCE_CHECK, INSURANCE_GUIDANCE, RECORD_REVIEW,
}

data class AssistanceAction(
    val eventId: UUID,
    val step: AssistanceStep,
    val completedAt: Instant,
)

data class AccidentReport(
    val eventId: UUID,
    val generatedAt: Instant,
    val evidence: List<Evidence> = emptyList(),
    val assistance: List<AssistanceAction> = emptyList(),
    val prediction: AccidentPrediction? = null,
) {
    init {
        require(evidence.all { it.eventId == eventId })
        require(assistance.all { it.eventId == eventId })
        require(prediction == null || prediction.eventId == eventId)
    }
}
