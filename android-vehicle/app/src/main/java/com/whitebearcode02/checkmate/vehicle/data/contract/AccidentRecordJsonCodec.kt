package com.whitebearcode02.checkmate.vehicle.data.contract

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import java.io.StringReader
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class AccidentContractException : IllegalArgumentException("Invalid accident contract")

/** Explicit DOM mapping calls validated constructors; Gson never reflects into domain classes. */
class AccidentRecordJsonCodec {
    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()

    fun encode(record: AccidentRecord): String = gson.toJson(obj(
        "schemaVersion" to record.schemaVersion, "event" to event(record.event),
        "ownerUserId" to record.ownerUserId, "createdByDeviceId" to record.createdByDeviceId,
        "sync" to obj("localVersion" to record.sync.localVersion, "remoteVersion" to record.sync.remoteVersion,
            "modifiedAt" to record.sync.modifiedAt, "modifiedByDeviceId" to record.sync.modifiedByDeviceId,
            "status" to record.sync.status),
        "assistance" to record.assistance.map { obj("eventId" to it.eventId, "step" to it.step, "completedAt" to it.completedAt) },
        "prediction" to record.prediction?.let { prediction(it) },
        "evidence" to record.evidence.map { obj("evidenceId" to it.evidenceId, "eventId" to it.eventId,
            "kind" to it.kind, "ownerDeviceId" to it.ownerDeviceId, "createdAt" to it.createdAt,
            "mimeType" to it.mimeType, "transferState" to it.transferState) },
        "deletedAt" to record.deletedAt,
    )).also {
        require(it.length <= MAX_DOCUMENT_CHARS) { "Accident record too large" }
        // A successful write must always be readable under the exact same validation/bounds.
        decode(it)
    }

    /** Unknown keys are additive. Missing required keys, unknown enum/version and duplicates fail closed. */
    fun decode(json: String): AccidentRecord = try {
        require(json.length <= MAX_DOCUMENT_CHARS)
        val root = JsonReader(StringReader(json)).use { reader ->
            reader.strictness = Strictness.STRICT
            readValue(reader, 0).also { require(reader.peek() == JsonToken.END_DOCUMENT) }.asJsonObject
        }
        require(root.int("schemaVersion") == ACCIDENT_SCHEMA_VERSION)
        val sync = root.objectValue("sync")
        AccidentRecord(
            schemaVersion = root.int("schemaVersion"),
            event = readEvent(root.objectValue("event")),
            ownerUserId = root.uuid("ownerUserId"), createdByDeviceId = root.uuid("createdByDeviceId"),
            sync = AccidentSyncMetadata(sync.long("localVersion"), sync.long("remoteVersion"),
                sync.instant("modifiedAt"), sync.uuid("modifiedByDeviceId"), sync.enum("status")),
            assistance = root.array("assistance").map { element -> element.asJsonObject.let {
                AssistanceAction(it.uuid("eventId"), it.enum("step"), it.instant("completedAt"))
            } },
            prediction = root.optional("prediction")?.asJsonObject?.let(::readPrediction),
            evidence = root.array("evidence").map { element -> element.asJsonObject.let {
                EvidenceMetadata(it.uuid("evidenceId"), it.uuid("eventId"), it.enum("kind"),
                    it.uuid("ownerDeviceId"), it.instant("createdAt"), it.text("mimeType"), it.enum("transferState"))
            } },
            deletedAt = root.optionalInstant("deletedAt"),
        )
    } catch (_: Exception) {
        // Neither caller UI nor logs receive the rejected payload or private field values.
        throw AccidentContractException()
    }

    private fun event(value: AccidentEvent): JsonObject = obj("eventId" to value.eventId, "occurredAt" to value.occurredAt,
        "status" to value.status, "vehicleSnapshot" to vehicle(value.vehicleSnapshot),
        "phoneConnection" to value.phoneConnection, "source" to value.source,
        "sessionId" to value.sessionId, "origin" to value.origin)

    private fun vehicle(value: VehicleState): JsonObject = obj("connection" to value.connection, "source" to value.source,
        "speedKph" to value.speedKph, "movement" to value.movement, "capabilities" to value.capabilities.toList(),
        "error" to value.error, "readings" to value.readings.values.map { datum ->
            obj("kind" to datum.kind, "value" to datum.value?.let {
                when (it) {
                    is VehicleValue.NumberValue -> obj("type" to "NUMBER", "number" to it.number)
                    is VehicleValue.StateValue -> obj("type" to "STATE", "state" to it.state)
                    is VehicleValue.BooleanValue -> obj("type" to "BOOLEAN", "enabled" to it.enabled)
                }
            }, "unit" to datum.unit, "source" to datum.source, "collectedAt" to datum.collectedAtMillis?.let(Instant::ofEpochMilli),
                "validity" to datum.validity, "support" to datum.support, "permission" to datum.permission,
                "updateState" to datum.updateState)
        }, "lastUpdatedAt" to value.lastUpdatedAtMillis?.let(Instant::ofEpochMilli), "retryAttempt" to value.retryAttempt,
        "sdkInitialization" to value.sdkInitialization, "serviceObserved" to value.serviceObserved)

    private fun prediction(value: AccidentPrediction): JsonObject = obj("eventId" to value.eventId, "status" to value.status,
        "accidentType" to value.accidentType, "ownFaultPercent" to value.ownFaultPercent,
        "otherFaultPercent" to value.otherFaultPercent, "rationale" to value.rationale,
        "modelLabel" to value.modelLabel, "source" to value.source)

    private fun readEvent(value: JsonObject): AccidentEvent = AccidentEvent(
        eventId = value.uuid("eventId"), occurredAt = value.instant("occurredAt"), status = value.enum("status"),
        vehicleSnapshot = readVehicle(value.objectValue("vehicleSnapshot")),
        phoneConnection = value.enum("phoneConnection"), source = value.enum("source"),
        sessionId = value.optionalText("sessionId")?.let(::parseUuid),
        origin = value.optionalText("origin")?.let { enumValueOf<EventOrigin>(it) } ?: EventOrigin.DEMO,
    )

    private fun readVehicle(value: JsonObject): VehicleState {
        val readings = value.array("readings").map { element -> element.asJsonObject.let { datum ->
            VehicleDatum(kind = datum.enum("kind"), value = datum.optional("value")?.asJsonObject?.let { sample ->
                when (sample.text("type")) {
                    "NUMBER" -> VehicleValue.NumberValue(sample.number("number"))
                    "STATE" -> VehicleValue.StateValue(sample.text("state"))
                    "BOOLEAN" -> VehicleValue.BooleanValue(sample.boolean("enabled"))
                    else -> throw AccidentContractException()
                }
            }, unit = datum.enum("unit"), source = datum.enum("source"),
                collectedAtMillis = datum.optionalInstant("collectedAt")?.toEpochMilli(), validity = datum.enum("validity"),
                support = datum.enum("support"), permission = datum.enum("permission"), updateState = datum.enum("updateState"))
        } }
        require(readings.map { it.kind }.distinct().size == readings.size)
        return VehicleState(connection = value.enum("connection"), source = value.enum("source"),
            speedKph = value.optional("speedKph")?.let { numeric(it).toDouble().also { number -> require(number.isFinite()) } },
            movement = value.enum("movement"),
            capabilities = value.array("capabilities").map { enumValueOf<VehicleCapability>(string(it)) }.toSet(),
            error = value.optionalText("error")?.let { enumValueOf<VehicleError>(it) },
            readings = readings.associateBy { it.kind }, lastUpdatedAtMillis = value.optionalInstant("lastUpdatedAt")?.toEpochMilli(),
            retryAttempt = value.int("retryAttempt"), sdkInitialization = value.enum("sdkInitialization"),
            serviceObserved = value.boolean("serviceObserved"))
    }

    private fun readPrediction(value: JsonObject): AccidentPrediction = AccidentPrediction(
        eventId = value.uuid("eventId"), status = value.enum("status"), accidentType = value.optionalText("accidentType"),
        ownFaultPercent = value.optional("ownFaultPercent")?.let { numeric(it).intValueExact() },
        otherFaultPercent = value.optional("otherFaultPercent")?.let { numeric(it).intValueExact() },
        rationale = value.optionalText("rationale"), modelLabel = value.text("modelLabel"), source = value.enum("source"),
    )

    private fun obj(vararg fields: Pair<String, Any?>): JsonObject = JsonObject().apply {
        fields.forEach { (key, value) -> add(key, element(value)) }
    }
    private fun element(value: Any?): JsonElement = when (value) {
        null -> JsonNull.INSTANCE
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is UUID, is Instant, is Enum<*> -> JsonPrimitive(value.toString())
        is Iterable<*> -> JsonArray().apply { value.forEach { add(element(it)) } }
        else -> error("Unsupported contract value")
    }

    /** Bound complexity and reject duplicate keys before DOM conversion can hide an ambiguity. */
    private fun readValue(reader: JsonReader, depth: Int): JsonElement {
        require(depth <= 32)
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    require(name.length <= 128 && !has(name) && size() < 256)
                    add(name, readValue(reader, depth + 1))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                reader.beginArray()
                while (reader.hasNext()) { require(size() < 10_000); add(readValue(reader, depth + 1)) }
                reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString().also { require(it.length <= 16_384) })
            JsonToken.NUMBER -> JsonPrimitive(reader.nextString().also { require(it.length <= 128) }.toBigDecimal())
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
            else -> throw AccidentContractException()
        }
    }

    private fun JsonObject.required(key: String): JsonElement = get(key)?.takeUnless { it.isJsonNull } ?: throw AccidentContractException()
    private fun JsonObject.optional(key: String): JsonElement? = get(key)?.takeUnless { it.isJsonNull }
    private fun JsonObject.objectValue(key: String): JsonObject = required(key).asJsonObject
    private fun JsonObject.array(key: String): JsonArray = optional(key)?.asJsonArray ?: JsonArray()
    private fun JsonObject.text(key: String): String = string(required(key))
    private fun JsonObject.optionalText(key: String): String? = optional(key)?.let(::string)
    private fun JsonObject.long(key: String): Long = numeric(required(key)).longValueExact()
    private fun JsonObject.int(key: String): Int = numeric(required(key)).intValueExact()
    private fun JsonObject.number(key: String): Double = numeric(required(key)).toDouble().also { require(it.isFinite()) }
    private fun JsonObject.boolean(key: String): Boolean = required(key).let { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean); it.asBoolean }
    private fun JsonObject.uuid(key: String): UUID = parseUuid(text(key))
    private fun JsonObject.instant(key: String): Instant = parseInstant(text(key))
    private fun JsonObject.optionalInstant(key: String): Instant? = optionalText(key)?.let(::parseInstant)
    private inline fun <reified T : Enum<T>> JsonObject.enum(key: String): T = enumValueOf(text(key))
    private fun string(value: JsonElement): String { require(value.isJsonPrimitive && value.asJsonPrimitive.isString); return value.asString }
    private fun numeric(value: JsonElement): BigDecimal { require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber); return value.asBigDecimal }
    private fun parseUuid(text: String): UUID = UUID.fromString(text).also { require(it.toString() == text) }
    private fun parseInstant(text: String): Instant = Instant.parse(text).also { require(text.endsWith("Z")) }

    companion object { private const val MAX_DOCUMENT_CHARS = 1_048_576 }
}
