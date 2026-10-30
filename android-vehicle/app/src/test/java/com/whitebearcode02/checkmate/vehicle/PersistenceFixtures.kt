package com.whitebearcode02.checkmate.vehicle

import com.whitebearcode02.checkmate.vehicle.domain.model.*
import java.time.Instant
import java.util.UUID

internal val persistenceTime: Instant = Instant.parse("2026-10-30T15:00:00Z")
internal val persistenceUser: UUID = UUID.fromString("10000000-0000-4000-8000-000000000001")
internal val persistenceDevice: UUID = UUID.fromString("20000000-0000-4000-8000-000000000001")

internal fun persistenceFixture(): AccidentRecord {
    val id = UUID.fromString("30000000-0000-4000-8000-000000000001")
    val data = mapOf(
        VehicleDataKind.SPEED to VehicleDatum(VehicleDataKind.SPEED, VehicleValue.NumberValue(0.0),
            VehicleUnit.KPH, DataSource.MOCK, persistenceTime.toEpochMilli(), DataValidity.VALID,
            FeatureSupport.SUPPORTED, DataPermission.GRANTED, DataUpdateState.UPDATING),
        VehicleDataKind.MOVEMENT to VehicleDatum(VehicleDataKind.MOVEMENT, VehicleValue.StateValue("PARKED"),
            VehicleUnit.STATE, DataSource.MOCK, persistenceTime.toEpochMilli(), DataValidity.VALID,
            FeatureSupport.SUPPORTED, DataPermission.GRANTED, DataUpdateState.UPDATING),
        VehicleDataKind.BRAKE to VehicleDatum(VehicleDataKind.BRAKE, VehicleValue.BooleanValue(true),
            VehicleUnit.BOOLEAN, DataSource.MOCK, persistenceTime.toEpochMilli(), DataValidity.VALID,
            FeatureSupport.SUPPORTED, DataPermission.GRANTED, DataUpdateState.UPDATING),
    )
    val vehicle = VehicleState(ConnectionState.CONNECTED, DataSource.MOCK, 0.0, VehicleMovement.PARKED,
        setOf(VehicleCapability.SPEED, VehicleCapability.MOVEMENT, VehicleCapability.BRAKE), readings = data,
        lastUpdatedAtMillis = persistenceTime.toEpochMilli(), sdkInitialization = SdkInitializationState.INITIALIZED,
        serviceObserved = true)
    return AccidentRecord(
        event = AccidentEvent(id, persistenceTime, AccidentStatus.COMPLETED, vehicle,
            ConnectionState.DISCONNECTED, DataSource.MOCK, UUID.randomUUID(), EventOrigin.DEMO),
        ownerUserId = persistenceUser, createdByDeviceId = persistenceDevice,
        sync = AccidentSyncMetadata(8, 3, persistenceTime, persistenceDevice, SyncStatus.PENDING),
        assistance = AssistanceStep.entries.map { AssistanceAction(id, it, persistenceTime) },
        prediction = AccidentPrediction(id, PredictionStatus.AVAILABLE, "모의 추돌", 20, 80,
            "테스트용 참고 결과", "Mock fixture", DataSource.MOCK),
        evidence = listOf(EvidenceMetadata(UUID.randomUUID(), id, EvidenceKind.PHOTO, persistenceDevice,
            persistenceTime, "image/jpeg", EvidenceTransferState.METADATA_ONLY)),
    )
}
