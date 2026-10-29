package com.whitebearcode02.checkmate.vehicle.viewmodel

import com.whitebearcode02.checkmate.vehicle.data.provider.MockVehicleScenario
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import com.whitebearcode02.checkmate.vehicle.ui.navigation.CheckMateScreen
import java.util.UUID

data class CheckMateUiState(
    val vehicle: VehicleState = VehicleState(),
    val phoneConnection: ConnectionState = ConnectionState.DISCONNECTED,
    val screen: CheckMateScreen = CheckMateScreen.HOME,
    val events: List<AccidentEvent> = emptyList(),
    val activeEvent: AccidentEvent? = null,
    val selectedEvent: AccidentEvent? = null,
    val prediction: AccidentPrediction? = null,
    val completedSteps: Set<AssistanceStep> = emptySet(),
    val canShowDetails: Boolean = false,
    val notice: String? = null,
)

data class CheckMateActions(
    val home: () -> Unit,
    val triggerMockAccident: () -> Unit,
    val cancelFalseAlarm: () -> Unit,
    val confirmAccident: () -> Unit,
    val openRecords: () -> Unit,
    val requestMockAnalysis: () -> Unit,
    val requestUnavailableAnalysis: () -> Unit,
    val openAnalysis: (UUID) -> Unit,
    val completeAssistanceStep: (AssistanceStep) -> Unit,
    val changeScenario: (MockVehicleScenario) -> Unit,
)
