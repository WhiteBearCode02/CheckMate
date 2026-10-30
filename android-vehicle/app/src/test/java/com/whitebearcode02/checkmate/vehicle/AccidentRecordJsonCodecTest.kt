package com.whitebearcode02.checkmate.vehicle

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.whitebearcode02.checkmate.vehicle.data.contract.AccidentContractException
import com.whitebearcode02.checkmate.vehicle.data.contract.AccidentRecordJsonCodec
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AccidentRecordJsonCodecTest {
    private val codec = AccidentRecordJsonCodec()
    private fun modified(change: (JsonObject) -> Unit) = JsonParser.parseString(codec.encode(persistenceFixture())).asJsonObject
        .apply(change).toString()
    private fun rejects(json: String) { assertThrows(AccidentContractException::class.java) { codec.decode(json) } }

    @Test fun completeAggregateRoundTripsThroughValidatedConstructors() {
        val record = persistenceFixture()
        assertEquals(record, codec.decode(codec.encode(record)))
    }
    @Test fun sharedContractExampleRoundTripsThroughProductionCodec() {
        val checkout = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "shared/contracts/accident-event.example.json").isFile }
            ?: error("Shared accident contract example missing")
        val record = codec.decode(File(checkout, "shared/contracts/accident-event.example.json").readText())
        assertEquals(record, codec.decode(codec.encode(record)))
        assertEquals(EventOrigin.VEHICLE_MANUAL, record.event.origin)
        assertEquals(DataSource.PLEOS, record.event.vehicleSnapshot.source)
        assertFalse(record.event.vehicleSnapshot.permitsDetailedInteraction)
    }
    @Test fun emptyOptionalMetadataAndNullUnknownVehicleValuesRoundTrip() {
        val record = persistenceFixture().let { it.copy(event = it.event.copy(vehicleSnapshot = VehicleState()),
            assistance = emptyList(), prediction = null, evidence = emptyList(), deletedAt = persistenceTime) }
        assertEquals(record, codec.decode(codec.encode(record)))
    }
    @Test fun wireTimestampsAreUtcIsoStringsIncludingVehicleSamples() {
        val root = JsonParser.parseString(codec.encode(persistenceFixture())).asJsonObject
        assertEquals(persistenceTime.toString(), root["event"].asJsonObject["occurredAt"].asString)
        val vehicle = root["event"].asJsonObject["vehicleSnapshot"].asJsonObject
        assertEquals(persistenceTime.toString(), vehicle["lastUpdatedAt"].asString)
        assertEquals(persistenceTime.toString(), vehicle["readings"].asJsonArray[0].asJsonObject["collectedAt"].asString)
        assertFalse(vehicle.has("lastUpdatedAtMillis"))
    }
    @Test fun additiveFieldsAreIgnoredAndAbsentOriginDefaultsToDemo() {
        val record = persistenceFixture()
        val root = JsonParser.parseString(codec.encode(record)).asJsonObject
        root.addProperty("futureField", "ignored")
        root["event"].asJsonObject.remove("origin")
        assertEquals(record, codec.decode(root.toString()))
    }
    @Test fun optionalListsAndPredictionMayBeAbsent() {
        val root = JsonParser.parseString(codec.encode(persistenceFixture())).asJsonObject
        listOf("assistance", "prediction", "evidence", "deletedAt").forEach(root::remove)
        val result = codec.decode(root.toString())
        assertTrue(result.assistance.isEmpty()); assertTrue(result.evidence.isEmpty()); assertNull(result.prediction)
    }
    @Test fun oldAndFutureSchemasAreRejected() {
        rejects(modified { it.addProperty("schemaVersion", 0) })
        rejects(modified { it.addProperty("schemaVersion", 2) })
    }
    @Test fun malformedDuplicateAndTrailingJsonAreRejectedWithoutEchoingContent() {
        listOf("{", "{}", "{\"schemaVersion\":1,\"schemaVersion\":1}", codec.encode(persistenceFixture()) + " {}",
            "/*comment*/" + codec.encode(persistenceFixture())).forEach(::rejects)
        val error = assertThrows(AccidentContractException::class.java) { codec.decode("private-value") }
        assertFalse(error.toString().contains("private-value")); assertNull(error.cause)
    }
    @Test fun invalidUuidTimestampAndUnknownEnumAreRejected() {
        rejects(modified { it.addProperty("ownerUserId", "1-1-1-1-1") })
        rejects(modified { it["event"].asJsonObject.addProperty("occurredAt", "2026-10-31T00:00:00+09:00") })
        rejects(modified { it["event"].asJsonObject.addProperty("status", "UNRECOGNIZED") })
    }
    @Test fun invalidPredictionCannotBypassConstructorValidation() {
        rejects(modified { it["prediction"].asJsonObject.addProperty("ownFaultPercent", 99) })
        rejects(modified { it["prediction"].asJsonObject.addProperty("ownFaultPercent", "20") })
        rejects(modified { it["prediction"].asJsonObject.addProperty("ownFaultPercent", 20.5) })
    }
    @Test fun inconsistentReferencesAndDuplicateEvidenceAreRejected() {
        rejects(modified { it["evidence"].asJsonArray[0].asJsonObject.addProperty("eventId", persistenceUser.toString()) })
        rejects(modified { it["evidence"].asJsonArray.add(it["evidence"].asJsonArray[0].deepCopy()) })
    }
    @Test fun invalidVehicleProvenanceAndDuplicateReadingsAreRejected() {
        rejects(modified { it["event"].asJsonObject["vehicleSnapshot"].asJsonObject["readings"].asJsonArray[0]
            .asJsonObject.addProperty("source", "PLEOS") })
        rejects(modified { val readings = it["event"].asJsonObject["vehicleSnapshot"].asJsonObject["readings"].asJsonArray
            readings.add(readings[0].deepCopy()) })
    }
    @Test fun oversizedAndExcessivelyNestedPayloadsAreRejected() {
        rejects(" ".repeat(1_048_577))
        rejects("[".repeat(34) + "0" + "]".repeat(34))
    }
    @Test fun locallyConstructedOversizedFieldsCannotCreateUnreadableStoredRecords() {
        val record = persistenceFixture().let { it.copy(prediction = it.prediction!!.copy(rationale = "x".repeat(16_385))) }
        assertThrows(AccidentContractException::class.java) { codec.encode(record) }
    }
    @Test fun contractContainsNoMediaLocationOrCredentialsFields() {
        val json = codec.encode(persistenceFixture())
        listOf("fileReference", "content://", "latitude", "longitude", "accessToken", "apiKey").forEach { assertFalse(json.contains(it)) }
        assertEquals(DataSource.MOCK, codec.decode(json).prediction!!.source)
    }
}
