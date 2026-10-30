package com.whitebearcode02.checkmate.vehicle.data.local

import android.content.Context
import androidx.room.Room
import com.whitebearcode02.checkmate.vehicle.data.contract.AccidentRecordJsonCodec
import com.whitebearcode02.checkmate.vehicle.domain.model.AccidentRecord
import java.util.UUID

/** Blocking, scoped API. Call on IO, never directly from Compose or the main thread. */
interface AccidentLocalDataSource {
    fun load(): List<AccidentRecord>
    fun save(record: AccidentRecord)
}

/** Safe public error; database paths, JSON and exception messages must not reach logs/UI. */
class LocalStorageException : IllegalStateException("Local accident storage unavailable")

class InMemoryAccidentLocalDataSource(private val userId: UUID) : AccidentLocalDataSource {
    private val values = linkedMapOf<UUID, AccidentRecord>()
    @Synchronized override fun load() = values.values.toList()
    @Synchronized override fun save(record: AccidentRecord) {
        require(record.ownerUserId == userId) { "Accident owner mismatch" }
        values[record.event.eventId] = record
    }
}

/** Room default thread checks remain enabled. There is no destructive migration fallback. */
class RoomAccidentLocalDataSource(
    private val database: AccidentDatabase,
    private val userId: UUID,
    private val codec: AccidentRecordJsonCodec = AccidentRecordJsonCodec(),
) : AccidentLocalDataSource, AutoCloseable {
    override fun load(): List<AccidentRecord> = storageOperation {
        database.accidents().load(userId.toString()).map { entity ->
            val record = codec.decode(entity.payload)
            check(record.ownerUserId == userId && record.ownerUserId.toString() == entity.ownerUserId)
            check(record.event.eventId.toString() == entity.eventId)
            record
        }
    }

    override fun save(record: AccidentRecord) {
        require(record.ownerUserId == userId) { "Accident owner mismatch" }
        storageOperation {
            database.accidents().save(AccidentEntity(userId.toString(), record.event.eventId.toString(), codec.encode(record)))
        }
    }

    override fun close() = database.close()

    private inline fun <T> storageOperation(operation: () -> T): T = try {
        operation()
    } catch (_: Exception) {
        throw LocalStorageException()
    }

    companion object {
        @Volatile private var sharedDatabase: AccidentDatabase? = null
        /** Database bytes stay in the application's private directory; backup is disabled in Manifest. */
        fun create(context: Context, userId: UUID): RoomAccidentLocalDataSource =
            RoomAccidentLocalDataSource(
                sharedDatabase ?: synchronized(this) {
                    sharedDatabase ?: Room.databaseBuilder(context.applicationContext, AccidentDatabase::class.java,
                        "checkmate-accidents.db").build().also { sharedDatabase = it }
                },
                userId,
            )
    }
}
