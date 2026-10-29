package com.whitebearcode02.checkmate.vehicle.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.whitebearcode02.checkmate.vehicle.data.provider.MockVehicleScenario
import com.whitebearcode02.checkmate.vehicle.domain.model.DataSource
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleCapability
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleAction
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleCard
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateActions
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateUiState

internal fun LazyListScope.dashboardItems(
    state: CheckMateUiState,
    actions: CheckMateActions,
    developerMode: Boolean,
) {
    item {
        VehicleCard("차량 안전 및 사고 대응") {
            Text(state.activeEvent?.status?.label() ?: "사고 이벤트 대기", style = MaterialTheme.typography.headlineSmall)
            Text("현재 실제 사고 감지 기능은 연결되어 있지 않습니다.")
        }
    }
    item { ConnectionPanels(state) }
    item {
        VehicleCard("최근 사고 이벤트") {
            val event = state.events.firstOrNull()
            if (event == null) {
                Text("아직 기록된 사고가 없습니다.")
            } else {
                Text(event.status.label())
                Text("발생 시각: ${event.occurredAt.displayTime()}", style = MaterialTheme.typography.bodySmall)
            }
            VehicleAction("사고 기록 보기 (${state.events.size})", actions.openRecords, enabled = state.canShowDetails)
            if (!state.canShowDetails) Text("사고 기록과 상세 AI 결과는 정차 확인 후 볼 수 있습니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (developerMode && state.vehicle.source == DataSource.MOCK) {
        item { DeveloperPanel(actions, state.canShowDetails) }
    }
}

@Composable
private fun ConnectionPanels(state: CheckMateUiState) {
    BoxWithConstraints {
        if (maxWidth >= 760.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                VehicleConnectionPanel(state, Modifier.weight(1f))
                PhoneConnectionPanel(state, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                VehicleConnectionPanel(state)
                PhoneConnectionPanel(state)
            }
        }
    }
}

@Composable
private fun VehicleConnectionPanel(state: CheckMateUiState, modifier: Modifier = Modifier) {
    VehicleCard("차량 데이터", modifier) {
        Text("${if (state.vehicle.source == DataSource.MOCK) "모의" else "Pleos"} 연결: ${state.vehicle.connection.label()}")
        Text(state.vehicle.movement.label())
        Text(
            when {
                VehicleCapability.SPEED !in state.vehicle.capabilities -> "속도 데이터: 지원되지 않음"
                state.vehicle.speedKph == null -> "속도 데이터: 수신되지 않음"
                else -> "${if (state.vehicle.source == DataSource.MOCK) "모의 " else ""}속도: ${state.vehicle.speedKph} km/h"
            },
        )
        if (state.vehicle.error != null) {
            Text("차량 데이터를 사용할 수 없습니다. 연결 및 지원 기능을 확인하세요.", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PhoneConnectionPanel(state: CheckMateUiState, modifier: Modifier = Modifier) {
    VehicleCard("스마트폰 블랙박스", modifier) {
        Text("연결: ${state.phoneConnection.label()}")
        Text("녹화: 미구현 · 녹화하지 않음")
        Text("영상·위치 수집 및 보험사 전송: 미연동", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DeveloperPanel(actions: CheckMateActions, canTrigger: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    VehicleCard("개발용 모의 상황") {
        Text("아래 버튼은 테스트 데이터를 생성합니다. 실제 사고 신호나 차량 제어를 사용하지 않습니다.", style = MaterialTheme.typography.bodySmall)
        VehicleAction("Mock 사고 의심 이벤트 발생", actions.triggerMockAccident, enabled = canTrigger, warning = true)
        if (!canTrigger) Text("모의 이벤트 테스트는 연결 성공·정차 시나리오에서 실행할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
        VehicleAction(
            if (expanded) "연결 테스트 접기" else "연결 테스트 펼치기",
            { expanded = !expanded },
            secondary = true,
        )
        if (expanded) {
            MockVehicleScenario.entries.forEach { scenario ->
                VehicleAction(
                    label = when (scenario) {
                        MockVehicleScenario.SUCCESS -> "모의 연결 성공 · 정차"
                        MockVehicleScenario.FAILURE -> "모의 연결 실패"
                        MockVehicleScenario.NO_DATA -> "모의 연결 · 데이터 없음"
                        MockVehicleScenario.UNSUPPORTED -> "모의 연결 · 데이터 미지원"
                        MockVehicleScenario.MOVING -> "모의 주행 · 상세 화면 제한"
                    },
                    onClick = { actions.changeScenario(scenario) },
                    secondary = true,
                )
            }
        }
    }
}
