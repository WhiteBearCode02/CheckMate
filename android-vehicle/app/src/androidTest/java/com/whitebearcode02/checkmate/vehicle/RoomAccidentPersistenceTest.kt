package com.whitebearcode02.checkmate.vehicle

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whitebearcode02.checkmate.vehicle.data.local.AccidentDatabase
import com.whitebearcode02.checkmate.vehicle.data.local.RoomAccidentLocalDataSource
import com.whitebearcode02.checkmate.vehicle.data.repository.PersistentAccidentRepository
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Requires an Android device/emulator: closes and reopens the actual SQLite/Room file. */
@RunWith(AndroidJUnit4::class)
class RoomAccidentPersistenceTest {
    @Test fun reopeningRoomRestoresProgressAndFiltersUserScope() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "checkmate-room-test-${UUID.randomUUID()}.db"
        val user = UUID.randomUUID()
        val device = UUID.randomUUID()
        fun open() = Room.databaseBuilder(context, AccidentDatabase::class.java, name).build()
        try {
            val first = RoomAccidentLocalDataSource(open(), user)
            val original = try {
                val repository = PersistentAccidentRepository(first, user, device)
                val event = repository.suspect(VehicleState(), ConnectionState.DISCONNECTED)
                repository.confirm(event.eventId)
                repository.acknowledge(event.eventId, AssistanceStep.OCCUPANT_SAFETY)
                repository.records().single()
            } finally { first.close() }
            val reopened = RoomAccidentLocalDataSource(open(), user)
            try {
                assertEquals(listOf(original), reopened.load())
                val repository = PersistentAccidentRepository(reopened, user, device)
                assertEquals(original.event.eventId, repository.state.value.activeEventId)
                assertEquals(1, repository.state.value.assistance.size)
            } finally { reopened.close() }
            val otherUser = RoomAccidentLocalDataSource(open(), UUID.randomUUID())
            try { assertTrue(otherUser.load().isEmpty()) } finally { otherUser.close() }
        } finally { context.deleteDatabase(name) }
    }
}
