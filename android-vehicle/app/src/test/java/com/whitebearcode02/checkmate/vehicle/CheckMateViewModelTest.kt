package com.whitebearcode02.checkmate.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.whitebearcode02.checkmate.vehicle.data.provider.MockVehicleProvider
import com.whitebearcode02.checkmate.vehicle.data.provider.MockVehicleScenario
import com.whitebearcode02.checkmate.vehicle.data.provider.VehicleDataProvider
import com.whitebearcode02.checkmate.vehicle.data.repository.InMemoryAccidentRepository
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import com.whitebearcode02.checkmate.vehicle.ui.navigation.CheckMateScreen
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckMateViewModelTest {
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        stores.forEach(ViewModelStore::clear)
        Dispatchers.resetMain()
    }

    @Test
    fun `developer workflow shows mock analysis and returns home after completion`() = runTest {
        val repository = InMemoryAccidentRepository()
        val viewModel = create(repository = repository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canShowDetails)
        viewModel.actions.triggerMockAccident()
        val id = repository.state.value.activeEventId
        assertNotNull(id)
        assertEquals(CheckMateScreen.CONFIRMATION, viewModel.uiState.value.screen)
        assertEquals(id, viewModel.uiState.value.activeEvent?.eventId)

        viewModel.actions.confirmAccident()
        assertEquals(CheckMateScreen.ASSISTANCE, viewModel.uiState.value.screen)
        AssistanceStep.entries.forEach(viewModel.actions.completeAssistanceStep)
        viewModel.actions.requestMockAnalysis()

        assertEquals(CheckMateScreen.ANALYSIS, viewModel.uiState.value.screen)
        assertEquals(PredictionStatus.PENDING, viewModel.uiState.value.prediction?.status)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AccidentStatus.COMPLETED, state.selectedEvent?.status)
        assertEquals(PredictionStatus.AVAILABLE, state.prediction?.status)
        assertEquals(DataSource.MOCK, state.prediction?.source)
        assertEquals(0, state.prediction?.ownFaultPercent)
        assertEquals(100, state.prediction?.otherFaultPercent)
        assertEquals(id, state.prediction?.eventId)
        assertNull(state.activeEvent)
        assertNull(repository.state.value.activeEventId)

        viewModel.actions.home()
        assertEquals(CheckMateScreen.HOME, viewModel.uiState.value.screen)
    }

    @Test
    fun `unavailable analysis never invents fault percentages`() = runTest {
        val viewModel = create()
        completeGuide(viewModel)
        viewModel.actions.requestUnavailableAnalysis()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AccidentStatus.UNAVAILABLE, state.selectedEvent?.status)
        assertEquals(PredictionStatus.UNAVAILABLE, state.prediction?.status)
        assertNull(state.prediction?.ownFaultPercent)
        assertNull(state.prediction?.otherFaultPercent)
        assertNull(state.activeEvent)
        viewModel.actions.home()
        assertEquals(CheckMateScreen.HOME, viewModel.uiState.value.screen)
    }

    @Test
    fun `back navigation keeps an active accident resumable`() = runTest {
        val repository = InMemoryAccidentRepository()
        val viewModel = create(repository = repository)
        viewModel.actions.triggerMockAccident()
        val id = repository.state.value.activeEventId

        viewModel.actions.home()
        assertEquals(CheckMateScreen.CONFIRMATION, viewModel.uiState.value.screen)
        viewModel.actions.confirmAccident()
        viewModel.actions.home()
        assertEquals(CheckMateScreen.ASSISTANCE, viewModel.uiState.value.screen)
        assertEquals(id, viewModel.uiState.value.activeEvent?.eventId)

        viewModel.actions.triggerMockAccident()
        assertEquals(1, repository.state.value.events.size)
        assertEquals(id, repository.state.value.activeEventId)
        advanceUntilIdle()
    }

    @Test
    fun `false alarm restores home and the next event gets a new ID`() = runTest {
        val repository = InMemoryAccidentRepository()
        val viewModel = create(repository = repository)
        viewModel.actions.triggerMockAccident()
        val firstId = repository.state.value.activeEventId
        viewModel.actions.cancelFalseAlarm()

        assertEquals(CheckMateScreen.HOME, viewModel.uiState.value.screen)
        assertNull(viewModel.uiState.value.activeEvent)
        assertEquals(AccidentStatus.FALSE_ALARM, repository.state.value.events.first().status)
        viewModel.actions.triggerMockAccident()
        assertNotEquals(firstId, repository.state.value.activeEventId)
        assertEquals(2, repository.state.value.events.size)
        advanceUntilIdle()
    }

    @Test
    fun `records close when vehicle starts moving`() = runTest {
        val viewModel = create()
        viewModel.actions.openRecords()
        assertEquals(CheckMateScreen.RECORDS, viewModel.uiState.value.screen)

        viewModel.actions.changeScenario(MockVehicleScenario.MOVING)
        assertEquals(CheckMateScreen.HOME, viewModel.uiState.value.screen)
        assertFalse(viewModel.uiState.value.canShowDetails)
        viewModel.actions.openRecords()
        assertEquals(CheckMateScreen.HOME, viewModel.uiState.value.screen)
        advanceUntilIdle()
    }

    @Test
    fun `records close when movement data is unavailable`() = runTest {
        val viewModel = create()
        viewModel.actions.openRecords()
        viewModel.actions.changeScenario(MockVehicleScenario.NO_DATA)

        assertEquals(CheckMateScreen.HOME, viewModel.uiState.value.screen)
        assertFalse(viewModel.uiState.value.canShowDetails)
        assertNotNull(viewModel.uiState.value.notice)
        advanceUntilIdle()
    }

    @Test
    fun `analysis details close on movement even after a mock result exists`() = runTest {
        val viewModel = create()
        completeGuide(viewModel)
        viewModel.actions.requestMockAnalysis()
        advanceUntilIdle()
        val eventId = requireNotNull(viewModel.uiState.value.selectedEvent).eventId
        assertEquals(CheckMateScreen.ANALYSIS, viewModel.uiState.value.screen)

        viewModel.actions.changeScenario(MockVehicleScenario.MOVING)
        assertEquals(CheckMateScreen.HOME, viewModel.uiState.value.screen)
        assertFalse(viewModel.uiState.value.canShowDetails)
        viewModel.actions.openAnalysis(eventId)
        assertEquals(CheckMateScreen.HOME, viewModel.uiState.value.screen)
        advanceUntilIdle()
    }

    @Test
    fun `nondeveloper commands cannot generate mock events scenarios or sample analyses`() = runTest {
        val provider = MockVehicleProvider()
        val repository = InMemoryAccidentRepository()
        val viewModel = create(provider, repository, developerMode = false)
        viewModel.actions.triggerMockAccident()
        viewModel.actions.changeScenario(MockVehicleScenario.FAILURE)

        assertTrue(repository.state.value.events.isEmpty())
        assertEquals(ConnectionState.CONNECTED, provider.state.value.connection)
        val event = repository.suspect(provider.state.value, ConnectionState.DISCONNECTED)
        repository.confirm(event.eventId)
        AssistanceStep.entries.forEach { repository.acknowledge(event.eventId, it) }
        advanceUntilIdle()
        viewModel.actions.requestMockAnalysis()
        advanceUntilIdle()

        assertTrue(repository.state.value.predictions.isEmpty())
        assertEquals(AccidentStatus.ASSISTANCE, repository.state.value.events.first().status)
    }

    @Test
    fun `default nondeveloper provider is unavailable rather than a connected mock`() = runTest {
        val viewModel = managed { CheckMateViewModel() }
        advanceUntilIdle()

        assertEquals(DataSource.PLEOS, viewModel.uiState.value.vehicle.source)
        assertEquals(ConnectionState.UNAVAILABLE, viewModel.uiState.value.vehicle.connection)
        assertFalse(viewModel.uiState.value.canShowDetails)
        assertTrue(viewModel.uiState.value.events.isEmpty())
    }

    @Test
    fun `unknown movement prevents developer event creation`() = runTest {
        val repository = InMemoryAccidentRepository()
        val viewModel = create(MockVehicleProvider(MockVehicleScenario.UNSUPPORTED), repository)
        viewModel.actions.triggerMockAccident()

        assertTrue(repository.state.value.events.isEmpty())
        assertEquals(CheckMateScreen.HOME, viewModel.uiState.value.screen)
        assertNotNull(viewModel.uiState.value.notice)
        advanceUntilIdle()
    }

    @Test
    fun `analysis is refused until all safety guidance is acknowledged`() = runTest {
        val repository = InMemoryAccidentRepository()
        val viewModel = create(repository = repository)
        viewModel.actions.triggerMockAccident()
        viewModel.actions.confirmAccident()
        viewModel.actions.completeAssistanceStep(AssistanceStep.OCCUPANT_SAFETY)
        viewModel.actions.requestMockAnalysis()
        advanceUntilIdle()

        assertEquals(CheckMateScreen.ASSISTANCE, viewModel.uiState.value.screen)
        assertEquals(AccidentStatus.ASSISTANCE, repository.state.value.events.first().status)
        assertTrue(repository.state.value.predictions.isEmpty())
        assertNotNull(viewModel.uiState.value.notice)
    }

    @Test
    fun `clearing ViewModelStore closes the provider once`() = runTest {
        val provider = TrackingProvider()
        create(provider)
        advanceUntilIdle()
        assertEquals(1, provider.connectCount)

        stores.last().clear()
        stores.last().clear()
        assertEquals(1, provider.closeCount)
        assertEquals(ConnectionState.DISCONNECTED, provider.state.value.connection)
    }

    @Test
    fun `basic safety guidance remains reachable without SDK and creates no event`() = runTest {
        val viewModel = managed { CheckMateViewModel() }
        advanceUntilIdle()

        viewModel.actions.openSafetyGuide()

        assertEquals(CheckMateScreen.SAFETY_GUIDE, viewModel.uiState.value.screen)
        assertTrue(viewModel.uiState.value.events.isEmpty())
        assertNull(viewModel.uiState.value.activeEvent)
        assertEquals(DataSource.PLEOS, viewModel.uiState.value.vehicle.source)
        assertFalse(viewModel.uiState.value.canShowDetails)
        viewModel.actions.home()
        assertEquals(CheckMateScreen.HOME, viewModel.uiState.value.screen)
    }

    @Test
    fun `SDK failure does not invoke factory or switch to mock`() = runTest {
        val real = ControllableProvider(DataSource.PLEOS)
        var factoryCalls = 0
        val viewModel = managed {
            CheckMateViewModel(real, developerMode = true, providerFactory = {
                factoryCalls++
                ControllableProvider(it)
            })
        }
        real.emit(VehicleState(source = DataSource.PLEOS, connection = ConnectionState.ERROR, error = VehicleError.CONNECTION_FAILED))
        advanceUntilIdle()

        assertEquals(0, factoryCalls)
        assertEquals(DataSource.PLEOS, viewModel.uiState.value.vehicle.source)
        assertTrue(viewModel.uiState.value.events.isEmpty())
        viewModel.actions.triggerMockAccident()
        assertTrue(viewModel.uiState.value.events.isEmpty())
    }

    @Test
    fun `explicit provider replacement closes old provider and ignores its late values`() = runTest {
        val real = ControllableProvider(DataSource.PLEOS)
        val mock = ControllableProvider(DataSource.MOCK)
        val viewModel = managed {
            CheckMateViewModel(real, developerMode = true, providerFactory = { mock })
        }
        advanceUntilIdle()
        viewModel.actions.selectVehicleSource(DataSource.MOCK)
        advanceUntilIdle()

        assertEquals(1, real.closeCount)
        assertEquals(1, mock.connectCount)
        assertEquals(DataSource.MOCK, viewModel.uiState.value.vehicle.source)
        real.emit(parkedState(DataSource.PLEOS))
        advanceUntilIdle()
        assertEquals(DataSource.MOCK, viewModel.uiState.value.vehicle.source)
        assertFalse(viewModel.uiState.value.canShowDetails)
        assertTrue(viewModel.uiState.value.events.isEmpty())

        stores.last().clear()
        assertEquals(1, real.closeCount)
        assertEquals(1, mock.closeCount)
    }

    @Test
    fun `moving actual provider cannot switch to mock even through direct action`() = runTest {
        val real = ControllableProvider(DataSource.PLEOS)
        var factoryCalls = 0
        val viewModel = managed {
            CheckMateViewModel(real, developerMode = true, providerFactory = { factoryCalls++; ControllableProvider(it) })
        }
        val movementSignals = listOf(
            VehicleState(source = DataSource.PLEOS, connection = ConnectionState.CONNECTED,
                movement = VehicleMovement.MOVING, capabilities = setOf(VehicleCapability.MOVEMENT)),
            VehicleState(source = DataSource.PLEOS, connection = ConnectionState.CONNECTED,
                speedKph = 30.0, capabilities = setOf(VehicleCapability.SPEED)),
        )
        movementSignals.forEach { state ->
            real.emit(state)
            advanceUntilIdle()
            viewModel.actions.selectVehicleSource(DataSource.MOCK)

            assertEquals(0, factoryCalls)
            assertEquals(DataSource.PLEOS, viewModel.uiState.value.vehicle.source)
            assertFalse(viewModel.uiState.value.canSelectMock)
        }
    }

    @Test
    fun `direct developer scenario action cannot override moving data with parked mock`() = runTest {
        val provider = MockVehicleProvider()
        val viewModel = create(provider)
        advanceUntilIdle()
        viewModel.actions.changeScenario(MockVehicleScenario.MOVING)
        assertFalse(viewModel.uiState.value.canShowDetails)

        viewModel.actions.changeScenario(MockVehicleScenario.SUCCESS)
        advanceUntilIdle()

        assertEquals(VehicleMovement.MOVING, provider.state.value.movement)
        assertEquals(VehicleMovement.MOVING, viewModel.uiState.value.vehicle.movement)
        assertFalse(viewModel.uiState.value.canShowDetails)
        assertNotNull(viewModel.uiState.value.notice)
        assertTrue(viewModel.uiState.value.events.isEmpty())
    }

    @Test
    fun `late developer scenario action cannot reconnect a background provider`() = runTest {
        val provider = MockVehicleProvider()
        val viewModel = create(provider)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canShowDetails)
        viewModel.onBackground()

        viewModel.actions.changeScenario(MockVehicleScenario.MOVING)
        advanceUntilIdle()

        assertEquals(ConnectionState.DISCONNECTED, provider.state.value.connection)
        assertEquals(VehicleMovement.UNKNOWN, viewModel.uiState.value.vehicle.movement)
        assertFalse(viewModel.uiState.value.canShowDetails)
        viewModel.onForeground()
        advanceUntilIdle()
        assertEquals(ConnectionState.CONNECTED, provider.state.value.connection)
        assertEquals(VehicleMovement.PARKED, viewModel.uiState.value.vehicle.movement)
        assertTrue(viewModel.uiState.value.canShowDetails)
    }

    @Test
    fun `foreground lifecycle is idempotent and never restores old parked trust`() = runTest {
        val provider = ControllableProvider(DataSource.PLEOS)
        val viewModel = create(provider)
        provider.emit(parkedState(DataSource.PLEOS))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canShowDetails)
        viewModel.actions.openRecords()
        viewModel.onForeground()
        viewModel.onForeground()
        assertEquals(1, provider.connectCount)

        viewModel.onBackground()
        viewModel.onBackground()
        assertEquals(1, provider.disconnectCount)
        assertEquals(CheckMateScreen.HOME, viewModel.uiState.value.screen)
        assertFalse(viewModel.uiState.value.canShowDetails)
        viewModel.onForeground()
        advanceUntilIdle()
        assertEquals(2, provider.connectCount)
        assertFalse(viewModel.uiState.value.canShowDetails)
        provider.emit(parkedState(DataSource.PLEOS))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canShowDetails)
    }

    @Test
    fun `stale parked state closes records and blocks detail actions`() = runTest {
        val provider = ControllableProvider(DataSource.MOCK)
        val viewModel = create(provider)
        provider.emit(parkedState(DataSource.MOCK))
        advanceUntilIdle()
        viewModel.actions.openRecords()
        assertEquals(CheckMateScreen.RECORDS, viewModel.uiState.value.screen)
        provider.emit(parkedState(DataSource.MOCK).expire(20_000L, 5_000L))
        advanceUntilIdle()

        assertEquals(CheckMateScreen.HOME, viewModel.uiState.value.screen)
        assertFalse(viewModel.uiState.value.canShowDetails)
        viewModel.actions.openRecords()
        assertEquals(CheckMateScreen.HOME, viewModel.uiState.value.screen)
    }

    private fun completeGuide(viewModel: CheckMateViewModel) {
        viewModel.actions.triggerMockAccident()
        viewModel.actions.confirmAccident()
        AssistanceStep.entries.forEach(viewModel.actions.completeAssistanceStep)
    }

    private fun create(
        provider: VehicleDataProvider = MockVehicleProvider(),
        repository: InMemoryAccidentRepository = InMemoryAccidentRepository(),
        developerMode: Boolean = true,
    ): CheckMateViewModel = managed { CheckMateViewModel(provider, repository, developerMode) }

    private fun managed(factory: () -> CheckMateViewModel): CheckMateViewModel {
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore().also(stores::add)
        }
        val provider = ViewModelProvider(owner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == CheckMateViewModel::class.java)
                return factory() as T
            }
        })
        return provider[CheckMateViewModel::class.java]
    }

    private class TrackingProvider : VehicleDataProvider {
        private val delegate = MockVehicleProvider()
        override val state = delegate.state
        var connectCount = 0
        var closeCount = 0

        override fun connect() { connectCount++; delegate.connect() }
        override fun disconnect() = delegate.disconnect()
        override fun close() { closeCount++; delegate.close() }
    }

    private class ControllableProvider(private val source: DataSource) : VehicleDataProvider {
        private val mutableState = MutableStateFlow(VehicleState(source = source))
        override val state = mutableState.asStateFlow()
        var connectCount = 0
        var disconnectCount = 0
        var closeCount = 0
        override fun connect() { connectCount++ }
        override fun disconnect() {
            disconnectCount++
            mutableState.value = VehicleState(source = source, connection = ConnectionState.DISCONNECTED)
        }
        override fun close() { closeCount++ }
        fun emit(state: VehicleState) { mutableState.value = state }
    }

    private fun parkedState(source: DataSource): VehicleState = VehicleState(
        source = source,
        connection = ConnectionState.CONNECTED,
        sdkInitialization = SdkInitializationState.INITIALIZED,
        serviceObserved = true,
        movement = VehicleMovement.PARKED,
        capabilities = setOf(VehicleCapability.MOVEMENT),
        readings = mapOf(VehicleDataKind.MOVEMENT to VehicleDatum(
            kind = VehicleDataKind.MOVEMENT,
            value = VehicleValue.StateValue(VehicleMovement.PARKED.name),
            source = source,
            unit = VehicleUnit.STATE,
            collectedAtMillis = 10_000L,
            validity = DataValidity.VALID,
            support = FeatureSupport.SUPPORTED,
            permission = DataPermission.GRANTED,
            updateState = DataUpdateState.UPDATING,
        )),
    )
}
