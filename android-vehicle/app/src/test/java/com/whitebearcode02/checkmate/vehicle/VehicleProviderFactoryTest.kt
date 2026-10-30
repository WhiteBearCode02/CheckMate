package com.whitebearcode02.checkmate.vehicle

import com.whitebearcode02.checkmate.vehicle.data.provider.VehicleDataProvider
import com.whitebearcode02.checkmate.vehicle.data.provider.VehicleProviderFactory
import com.whitebearcode02.checkmate.vehicle.domain.model.ConnectionState
import com.whitebearcode02.checkmate.vehicle.domain.model.DataSource
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleError
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class VehicleProviderFactoryTest {
    @Test
    fun productionFactoryRejectsMockWithoutConstructingIt() {
        var mockCreations = 0
        val factory = VehicleProviderFactory(
            createPleos = { FailingRealProvider() },
            createMock = { mockCreations++; FailingRealProvider() },
        )
        assertThrows(IllegalArgumentException::class.java) { factory.create(DataSource.MOCK) }
        assertEquals(0, mockCreations)
    }

    @Test
    fun realConnectionFailureNeverConstructsMock() {
        var mockCreations = 0
        val real = FailingRealProvider()
        val factory = VehicleProviderFactory(
            createPleos = { real }, allowMock = true,
            createMock = { mockCreations++; FailingRealProvider() },
        )
        val selected = factory.create(DataSource.PLEOS)
        selected.connect()
        assertSame(real, selected)
        assertEquals(DataSource.PLEOS, selected.state.value.source)
        assertEquals(ConnectionState.FAILED, selected.state.value.connection)
        assertEquals(0, mockCreations)
    }

    @Test
    fun developmentMockRequiresExplicitSelection() {
        var realCreations = 0
        var mockCreations = 0
        val mock = FailingRealProvider()
        val factory = VehicleProviderFactory(
            createPleos = { realCreations++; FailingRealProvider() }, allowMock = true,
            createMock = { mockCreations++; mock },
        )
        assertSame(mock, factory.create(DataSource.MOCK))
        assertEquals(0, realCreations)
        assertEquals(1, mockCreations)
    }

    @Test
    fun constructionFailureIsNotHiddenByMockFallback() {
        var mockCreations = 0
        val factory = VehicleProviderFactory(
            createPleos = { throw UnsupportedOperationException("Unavailable") }, allowMock = true,
            createMock = { mockCreations++; FailingRealProvider() },
        )
        assertThrows(UnsupportedOperationException::class.java) { factory.create(DataSource.PLEOS) }
        assertEquals(0, mockCreations)
    }

    private class FailingRealProvider : VehicleDataProvider {
        override val state = MutableStateFlow(VehicleState(source = DataSource.PLEOS))
        override fun connect() {
            state.value = VehicleState(
                source = DataSource.PLEOS, connection = ConnectionState.FAILED,
                error = VehicleError.CONNECTION_FAILED,
            )
        }
        override fun disconnect() { state.value = VehicleState(source = DataSource.PLEOS) }
        override fun close() = disconnect()
    }
}
