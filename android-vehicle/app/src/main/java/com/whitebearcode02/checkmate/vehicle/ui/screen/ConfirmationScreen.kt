package com.whitebearcode02.checkmate.vehicle.ui.screen

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleAction
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleCard
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateActions
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateUiState

internal fun LazyListScope.confirmationItems(state: CheckMateUiState, actions: CheckMateActions) {
    item {
        VehicleCard("Mock 사고 의심 상황", warning = true) {
            Text("안전한 곳에 정차하고 본인과 탑승자의 상태를 확인하세요.", style = MaterialTheme.typography.titleLarge)
            Text("개발용 버튼으로 생성된 모의 이벤트입니다. 실제 센서가 사고를 감지한 것이 아닙니다.")
            state.activeEvent?.let { event ->
                Text("발생 시각: ${event.occurredAt.displayTime()}", style = MaterialTheme.typography.bodySmall)
                Text("발생 당시 차량 연결: ${event.vehicleSnapshot.connection.label()}", style = MaterialTheme.typography.bodySmall)
                Text("발생 당시 블랙박스 연결: ${event.phoneConnection.label()}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    item { VehicleAction("사고 확인 · 안전 대응 안내", actions.confirmAccident, warning = true) }
    item { VehicleAction("오탐 취소 · 대기로 복귀", actions.cancelFalseAlarm, secondary = true) }
}
