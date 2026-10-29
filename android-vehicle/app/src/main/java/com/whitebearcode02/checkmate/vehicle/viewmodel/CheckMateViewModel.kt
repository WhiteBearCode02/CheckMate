package com.whitebearcode02.checkmate.vehicle.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitebearcode02.checkmate.vehicle.data.provider.MockVehicleProvider
import com.whitebearcode02.checkmate.vehicle.data.provider.PleosVehicleProvider
import com.whitebearcode02.checkmate.vehicle.data.provider.VehicleDataProvider
import com.whitebearcode02.checkmate.vehicle.data.repository.AccidentRepository
import com.whitebearcode02.checkmate.vehicle.data.repository.InMemoryAccidentRepository
import com.whitebearcode02.checkmate.vehicle.domain.DrivingSafetyPolicy
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import com.whitebearcode02.checkmate.vehicle.ui.navigation.CheckMateScreen
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

class CheckMateViewModel(
    private val provider: VehicleDataProvider = PleosVehicleProvider(),
    private val repository: AccidentRepository = InMemoryAccidentRepository(),
    private val developerMode: Boolean = false,
) : ViewModel() {
    private var screen = CheckMateScreen.HOME
    private var selectedId: UUID? = null
    private var notice: String? = null
    private val mutableUiState = MutableStateFlow(CheckMateUiState())
    val uiState = mutableUiState.asStateFlow()

    val actions = CheckMateActions(
        home = ::home,
        triggerMockAccident = {
            if (developerMode && allowDetails()) runAction {
                val event = repository.suspect(provider.state.value, ConnectionState.DISCONNECTED)
                selectedId = event.eventId
                screen = screenFor(event.status)
            }
        },
        cancelFalseAlarm = {
            activeId()?.let { id -> runAction { repository.cancelFalseAlarm(id); screen = CheckMateScreen.HOME } }
        },
        confirmAccident = {
            activeId()?.let { id -> runAction { repository.confirm(id); screen = CheckMateScreen.ASSISTANCE } }
        },
        openRecords = { if (allowDetails()) runAction { screen = CheckMateScreen.RECORDS } },
        requestMockAnalysis = { if (developerMode) analyze(useMock = true) },
        requestUnavailableAnalysis = { analyze(useMock = false) },
        openAnalysis = { id ->
            if (allowDetails()) runAction {
                require(repository.state.value.events.any { it.eventId == id })
                selectedId = id
                screen = CheckMateScreen.ANALYSIS
            }
        },
        completeAssistanceStep = { step -> activeId()?.let { id -> runAction { repository.acknowledge(id, step) } } },
        changeScenario = { scenario ->
            if (developerMode) runAction { (provider as? MockVehicleProvider)?.setScenario(scenario) }
        },
    )

    init {
        viewModelScope.launch { provider.state.collect { publish() } }
        viewModelScope.launch { repository.state.collect { publish() } }
        provider.connect()
        publish()
    }

    private fun home() = runAction {
        screen = repository.state.value.events.firstOrNull { it.eventId == activeId() }
            ?.let { screenFor(it.status) } ?: CheckMateScreen.HOME
    }

    private fun screenFor(status: AccidentStatus) = when (status) {
        AccidentStatus.SUSPECTED -> CheckMateScreen.CONFIRMATION
        AccidentStatus.CONFIRMED, AccidentStatus.ASSISTANCE -> CheckMateScreen.ASSISTANCE
        AccidentStatus.ANALYSIS_PENDING -> CheckMateScreen.ANALYSIS
        else -> CheckMateScreen.HOME
    }

    private fun activeId() = repository.state.value.activeEventId

    private fun allowDetails(): Boolean {
        if (DrivingSafetyPolicy.canShowDetails(provider.state.value)) return true
        notice = "정차가 확인된 뒤 사고 기록과 상세 분석을 확인할 수 있습니다."
        publish()
        return false
    }

    private fun analyze(useMock: Boolean) {
        if (!allowDetails()) return
        val id = activeId() ?: return
        runAction {
            repository.beginAnalysis(id)
            selectedId = id
            screen = CheckMateScreen.ANALYSIS
            // Only a demonstration result is constructed; no model or paid service is invoked.
            viewModelScope.launch {
                yield()
                runAction {
                    val result = if (useMock) AccidentPrediction(
                        eventId = id,
                        status = PredictionStatus.AVAILABLE,
                        accidentType = "후방 추돌 (화면 확인용 예시)",
                        ownFaultPercent = 0,
                        otherFaultPercent = 100,
                        rationale = "개발 화면을 확인하기 위한 고정 예시입니다. 실제 영상이나 차량 데이터를 분석하지 않았습니다.",
                        modelLabel = "Mock 예시 · 실제 AI 미연동",
                        source = DataSource.MOCK,
                    ) else AccidentPrediction(
                        eventId = id,
                        status = PredictionStatus.UNAVAILABLE,
                        modelLabel = "실제 AI 미연동 · 분석 결과 없음",
                    )
                    repository.finishAnalysis(id, result)
                }
            }
        }
    }

    private fun runAction(block: () -> Unit) {
        try {
            notice = null
            block()
        } catch (_: IllegalArgumentException) {
            // Do not log event objects, file references, locations or exception payloads.
            notice = "현재 상태에서 진행할 수 없습니다. 안전 안내 순서를 확인해 주세요."
        } catch (_: IllegalStateException) {
            notice = "처리할 수 없습니다. 현재 사고 기록을 확인해 주세요."
        }
        publish()
    }

    private fun publish() {
        val vehicle = provider.state.value
        val store = repository.state.value
        val detailsAllowed = DrivingSafetyPolicy.canShowDetails(vehicle)
        if (!detailsAllowed && screen in setOf(CheckMateScreen.RECORDS, CheckMateScreen.ANALYSIS)) {
            screen = CheckMateScreen.HOME
            notice = "주행 중이거나 정차 여부를 확인할 수 없어 상세 화면을 닫았습니다."
        }
        val selected = store.events.firstOrNull { it.eventId == selectedId }
        mutableUiState.update {
            CheckMateUiState(
                vehicle = vehicle,
                screen = screen,
                events = store.events,
                activeEvent = store.events.firstOrNull { it.eventId == store.activeEventId },
                selectedEvent = selected,
                prediction = store.predictions[selectedId],
                completedSteps = store.assistance.filter { it.eventId == selectedId }.map { it.step }.toSet(),
                canShowDetails = detailsAllowed,
                notice = notice,
            )
        }
    }

    override fun onCleared() {
        provider.close()
        super.onCleared()
    }
}
