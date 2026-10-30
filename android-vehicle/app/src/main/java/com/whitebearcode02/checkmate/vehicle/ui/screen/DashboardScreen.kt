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
import com.whitebearcode02.checkmate.vehicle.domain.model.*
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleAction
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleCard
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateActions
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateUiState
import com.whitebearcode02.checkmate.vehicle.viewmodel.LocalStorageStatus

internal fun LazyListScope.dashboardItems(
    state: CheckMateUiState,
    actions: CheckMateActions,
    developerMode: Boolean,
) {
    item {
        VehicleCard("차량 안전 및 사고 대응") {
            Text(state.activeEvent?.status?.label() ?: "사고 이벤트 대기", style = MaterialTheme.typography.headlineSmall)
            state.activeEvent?.let { Text(it.origin.label(), color = MaterialTheme.colorScheme.tertiary) }
            Text("현재 실제 사고 감지 기능은 연결되어 있지 않습니다.")
            VehicleAction("직접 사고 대응 시작", actions.startAccidentResponse,
                enabled = !state.storageBusy && state.storageStatus !in setOf(LocalStorageStatus.LOADING, LocalStorageStatus.ERROR), warning = true)
            Text("연결·정차를 확인할 수 없어도 사고 여부 확인과 기본 안전 안내를 이용할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
            VehicleAction("기본 사고 대응 안전 안내", actions.openSafetyGuide, warning = true)
        }
    }
    item { ConnectionPanels(state, actions) }
    item {
        VehicleCard("최근 사고 이벤트") {
            Text(state.storageStatus.label())
            if (state.storageBusy) Text("기기에 저장 중 · 저장 완료 후 진행할 수 있습니다.")
            Text("동기화: 클라우드·계정 연결 미설정 · 외부 전송 없음", style = MaterialTheme.typography.bodySmall)
            val event = state.events.firstOrNull()
            if (event == null) {
                Text("아직 기록된 사고가 없습니다.")
            } else {
                Text(event.origin.label(), style = MaterialTheme.typography.bodySmall)
                state.syncMetadata[event.eventId]?.let { Text(it.status.label(), style = MaterialTheme.typography.bodySmall) }
                Text(event.status.label())
                Text("발생 시각: ${event.occurredAt.displayTime()}", style = MaterialTheme.typography.bodySmall)
            }
            VehicleAction("사고 기록 보기 (${state.events.size})", actions.openRecords, enabled = state.canShowDetails)
            if (!state.canShowDetails) Text("사고 기록과 상세 AI 결과는 정차 확인 후 볼 수 있습니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (developerMode) {
        item { ProviderModePanel(state, actions) }
        if (state.vehicle.source == DataSource.MOCK && !state.vehicle.hasMovementSignal()) {
            item { DeveloperPanel(actions, state.canShowDetails) }
        }
    }
}

@Composable
private fun ConnectionPanels(state: CheckMateUiState, actions: CheckMateActions) {
    BoxWithConstraints {
        if (maxWidth >= 760.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                VehicleConnectionPanel(state, actions, Modifier.weight(1f))
                PhoneConnectionPanel(state, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                VehicleConnectionPanel(state, actions)
                PhoneConnectionPanel(state)
            }
        }
    }
}

@Composable
private fun VehicleConnectionPanel(state: CheckMateUiState, actions: CheckMateActions, modifier: Modifier = Modifier) {
    VehicleCard("차량 데이터", modifier) {
        val mock = state.vehicle.source == DataSource.MOCK
        Text(if (mock) "MOCK · 사용자가 선택한 테스트 데이터" else "PLEOS · 실제 SDK 연결 시도")
        Text("${if (mock) "모의 SDK 상태" else "Vehicle SDK 초기화"}: ${state.vehicle.sdkInitialization.label()}")
        Text("${if (mock) "모의 서비스 상태" else "Vehicle Service 연결"}: ${state.vehicle.connection.label()}")
        if (!mock && !state.vehicle.serviceObserved) {
            Text("Vehicle Service 연결은 아직 확인되지 않았습니다.", style = MaterialTheme.typography.bodySmall)
        }
        Text(state.vehicle.movement.label())
        val speed = state.vehicle.readings[VehicleDataKind.SPEED]
        Text(if (speed?.isUsable == true) "${if (mock) "모의 " else ""}속도: ${speed.displayValue()}" else "현재 속도: 확인 불가")
        Text("마지막 수신: ${state.vehicle.lastUpdatedAtMillis?.displayTimestamp() ?: "수신 이력 없음"}", style = MaterialTheme.typography.bodySmall)
        if (state.vehicle.error != null) {
            Text(state.vehicle.error.label(), color = MaterialTheme.colorScheme.error)
        }
        if (!state.canShowDetails) {
            Text("정차를 확인할 수 없으면 기록·분석과 상세 화면을 제한합니다.", color = MaterialTheme.colorScheme.tertiary)
        }
        if (state.vehicle.connection != ConnectionState.CONNECTING &&
            (state.vehicle.connection != ConnectionState.CONNECTED || state.vehicle.error != null)) {
            VehicleAction("차량 연결 재시도", actions.retryVehicleConnection, secondary = true)
            Text("재시도 ${state.vehicle.retryAttempt}회 · 자동 Mock 전환 없음", style = MaterialTheme.typography.bodySmall)
        }
        if (!state.vehicle.hasMovementSignal()) {
            VehicleDataKind.entries.forEach { kind ->
                VehicleReading(kind, state.vehicle.readings[kind], mock)
            }
        }
    }
}

@Composable
private fun VehicleReading(kind: VehicleDataKind, datum: VehicleDatum?, mock: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(kind.label(), style = MaterialTheme.typography.titleMedium)
        if (datum == null) {
            Text("지원·권한 미확인 · 수신값 없음", style = MaterialTheme.typography.bodySmall)
        } else {
            val valueLabel = when {
                datum.isUsable -> "${if (mock) "모의 " else ""}수신값: ${datum.displayValue()}"
                datum.value != null -> "이전 수신값: ${datum.displayValue()} · 현재값으로 사용하지 않음"
                else -> "수신값 없음"
            }
            Text(valueLabel)
            if (kind == VehicleDataKind.DOOR && datum.isUsable) {
                Text("SDK 원시 위치값 · 개폐율/안전 판정이 아님", style = MaterialTheme.typography.bodySmall)
            }
            Text("${datum.support.label()} · ${datum.permission.label()}", style = MaterialTheme.typography.bodySmall)
            Text("${datum.validity.label()} · ${datum.updateState.label()}", style = MaterialTheme.typography.bodySmall)
            Text("수신 시각: ${datum.collectedAtMillis?.displayTimestamp() ?: "없음"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProviderModePanel(state: CheckMateUiState, actions: CheckMateActions) {
    VehicleCard("개발용 데이터 출처 선택") {
        Text("Debug 전용 · 실제 SDK 연결에 실패해도 Mock으로 자동 전환하지 않습니다.", style = MaterialTheme.typography.bodySmall)
        if (state.vehicle.source == DataSource.MOCK) {
            VehicleAction("실제 Pleos SDK 연결 시도로 전환", { actions.selectVehicleSource(DataSource.PLEOS) }, secondary = true)
        } else {
            VehicleAction("테스트용 Mock 모드 직접 선택", { actions.selectVehicleSource(DataSource.MOCK) }, enabled = state.canSelectMock, secondary = true)
        }
        if (state.vehicle.hasMovementSignal()) {
            Text("주행 중에는 Mock 모드 전환과 시나리오 설정을 제한합니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun VehicleState.hasMovementSignal(): Boolean = movement == VehicleMovement.MOVING || (speedKph ?: 0.0) > 0.0

@Composable
private fun PhoneConnectionPanel(state: CheckMateUiState, modifier: Modifier = Modifier) {
    VehicleCard("스마트폰 연동", modifier) {
        Text("연결: ${state.phoneConnection.label()}")
        Text("사고 진행 상태 공유: 미연동 · 공통 데이터 계약 준비")
        Text("블랙박스: 선택 기능 · 현재 녹화하지 않음")
        Text("스마트폰 녹화 없이도 수동 사고 대응 안내를 이용할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
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
                        MockVehicleScenario.DELAYED_CONNECTION -> "모의 연결 지연"
                        MockVehicleScenario.PERMISSION_DENIED -> "모의 읽기 권한 거부"
                        MockVehicleScenario.UPDATE_STOPPED -> "모의 데이터 갱신 중단"
                        MockVehicleScenario.DISCONNECT_DURING_CONNECT -> "모의 연결 도중 해제"
                        MockVehicleScenario.RECONNECT_SUCCESS -> "모의 재연결 성공"
                        MockVehicleScenario.UNKNOWN -> "모의 정차 여부 미확인"
                    },
                    onClick = { actions.changeScenario(scenario) },
                    secondary = true,
                )
            }
        }
    }
}
