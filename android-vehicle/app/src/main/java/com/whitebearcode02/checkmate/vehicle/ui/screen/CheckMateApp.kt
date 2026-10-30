package com.whitebearcode02.checkmate.vehicle.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.whitebearcode02.checkmate.vehicle.domain.model.DataSource
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleAction
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleCard
import com.whitebearcode02.checkmate.vehicle.ui.navigation.CheckMateScreen
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateActions
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateUiState

@Composable
fun CheckMateApp(
    state: CheckMateUiState,
    actions: CheckMateActions,
    developerMode: Boolean,
) {
    BackHandler(enabled = state.screen != CheckMateScreen.HOME) { actions.home() }
    Scaffold { insets ->
        Box(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 1120.dp).fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CheckMate", style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.semantics { heading() })
                        Text(
                            if (state.vehicle.source == DataSource.MOCK)
                                "MOCK · 모의 차량 데이터 / 실제 사고 감지 아님"
                            else "PLEOS · 차량 데이터 연결 상태를 확인하세요",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (state.notice != null) {
                    item {
                        VehicleCard("상태 안내") {
                            Text(state.notice, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                }
                when (state.screen) {
                    CheckMateScreen.HOME -> dashboardItems(state, actions, developerMode)
                    CheckMateScreen.SAFETY_GUIDE -> safetyGuideItems(actions)
                    CheckMateScreen.CONFIRMATION -> confirmationItems(state, actions)
                    CheckMateScreen.ASSISTANCE -> assistanceItems(state, actions, developerMode)
                    CheckMateScreen.RECORDS -> recordItems(state, actions)
                    CheckMateScreen.ANALYSIS -> analysisItems(state, actions)
                }
                if (state.screen !in setOf(CheckMateScreen.HOME, CheckMateScreen.SAFETY_GUIDE) && state.activeEvent == null) {
                    item { VehicleAction("홈으로", actions.home, secondary = true) }
                }
                item {
                    Text(
                        "Phase 3A · ${state.storageStatus.label()} · 클라우드·스마트폰·실제 AI 미연동",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
