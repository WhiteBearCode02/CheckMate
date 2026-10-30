package com.whitebearcode02.checkmate.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.whitebearcode02.checkmate.vehicle.data.provider.VehicleDataProvider
import com.whitebearcode02.checkmate.vehicle.data.repository.InMemoryAccidentRepository
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import com.whitebearcode02.checkmate.vehicle.ui.navigation.CheckMateScreen
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateViewModel
import com.whitebearcode02.checkmate.vehicle.viewmodel.LocalStorageStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase3ViewModelTest {
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { stores.forEach(ViewModelStore::clear); Dispatchers.resetMain() }

    @Test fun manualResponseWorksOfflineWithoutGrantingDetailAccess() = runTest {
        val repository = InMemoryAccidentRepository()
        val vm = own(CheckMateViewModel(provider = OfflineProvider(), repository = repository))
        vm.actions.startAccidentResponse()
        assertEquals(CheckMateScreen.CONFIRMATION, vm.uiState.value.screen)
        assertEquals(EventOrigin.VEHICLE_MANUAL, vm.uiState.value.activeEvent?.origin)
        assertEquals(DataSource.PLEOS, vm.uiState.value.activeEvent?.source)
        assertFalse(vm.uiState.value.canShowDetails)
        vm.actions.confirmAccident()
        assertEquals(CheckMateScreen.ASSISTANCE, vm.uiState.value.screen)
        vm.actions.completeAssistanceStep(AssistanceStep.OCCUPANT_SAFETY)
        vm.actions.openRecords()
        assertFalse(vm.uiState.value.screen == CheckMateScreen.RECORDS)
        vm.actions.openSafetyGuide()
        assertEquals(CheckMateScreen.SAFETY_GUIDE, vm.uiState.value.screen)
        advanceUntilIdle()
        assertEquals(1, repository.state.value.events.size)
    }

    @Test fun loadFailureDoesNotFallBackToWritableEmptyMemoryStore() = runTest {
        val repository = InMemoryAccidentRepository()
        val vm = own(CheckMateViewModel(provider = OfflineProvider(), repository = repository,
            repositoryDispatcher = StandardTestDispatcher(testScheduler),
            repositoryLoader = { throw IllegalStateException("private storage detail") }))
        advanceUntilIdle()
        assertEquals(LocalStorageStatus.ERROR, vm.uiState.value.storageStatus)
        assertFalse(vm.uiState.value.notice.orEmpty().contains("private storage detail"))
        vm.actions.startAccidentResponse()
        assertTrue(repository.state.value.events.isEmpty())
        vm.actions.openSafetyGuide()
        assertEquals(CheckMateScreen.SAFETY_GUIDE, vm.uiState.value.screen)
    }

    @Test fun loadingBlocksRecordMutationButKeepsOfflineGuideAvailable() = runTest {
        val ready = CompletableDeferred<Unit>()
        val repository = InMemoryAccidentRepository()
        val vm = own(CheckMateViewModel(provider = OfflineProvider(),
            repositoryDispatcher = StandardTestDispatcher(testScheduler), repositoryLoader = { ready.await(); repository }))
        vm.actions.startAccidentResponse()
        assertEquals(LocalStorageStatus.LOADING, vm.uiState.value.storageStatus)
        assertTrue(repository.state.value.events.isEmpty())
        vm.actions.openSafetyGuide()
        assertEquals(CheckMateScreen.SAFETY_GUIDE, vm.uiState.value.screen)
        ready.complete(Unit)
        advanceUntilIdle()
        assertEquals(LocalStorageStatus.READY, vm.uiState.value.storageStatus)
        assertEquals(CheckMateScreen.SAFETY_GUIDE, vm.uiState.value.screen)
        vm.actions.startAccidentResponse()
        advanceUntilIdle()
        assertEquals(1, repository.state.value.events.size)
    }

    @Test fun restoredRecordResumesGuideWithoutRestoringParkedTrust() = runTest {
        val repository = InMemoryAccidentRepository()
        val event = repository.suspect(VehicleState(), ConnectionState.DISCONNECTED, EventOrigin.VEHICLE_MANUAL)
        repository.confirm(event.eventId)
        repository.acknowledge(event.eventId, AssistanceStep.OCCUPANT_SAFETY)
        val vm = own(CheckMateViewModel(provider = OfflineProvider(),
            repositoryDispatcher = StandardTestDispatcher(testScheduler), repositoryLoader = { repository }))
        advanceUntilIdle()
        assertEquals(event.eventId, vm.uiState.value.activeEvent?.eventId)
        assertEquals(setOf(AssistanceStep.OCCUPANT_SAFETY), vm.uiState.value.completedSteps)
        assertEquals(CheckMateScreen.ASSISTANCE, vm.uiState.value.screen)
        assertFalse(vm.uiState.value.canShowDetails)
        vm.actions.openAnalysis(event.eventId)
        assertEquals(CheckMateScreen.ASSISTANCE, vm.uiState.value.screen)
    }

    @Test fun rapidRepeatedManualStartIsSerializedAndCreatesOneEvent() = runTest {
        val repository = InMemoryAccidentRepository()
        val vm = own(CheckMateViewModel(provider = OfflineProvider(), repository = repository,
            repositoryDispatcher = StandardTestDispatcher(testScheduler)))
        repeat(10) { vm.actions.startAccidentResponse() }
        assertTrue(vm.uiState.value.storageBusy)
        advanceUntilIdle()
        assertEquals(1, repository.state.value.events.size)
        assertFalse(vm.uiState.value.storageBusy)
        assertEquals(CheckMateScreen.CONFIRMATION, vm.uiState.value.screen)
    }

    private fun own(vm: CheckMateViewModel): CheckMateViewModel {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore().also(stores::add) }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
        }
        return ViewModelProvider(owner, factory)[CheckMateViewModel::class.java]
    }

    private class OfflineProvider : VehicleDataProvider {
        private val mutable = MutableStateFlow(VehicleState(source = DataSource.PLEOS, connection = ConnectionState.FAILED))
        override val state = mutable.asStateFlow()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun close() = Unit
    }
}
