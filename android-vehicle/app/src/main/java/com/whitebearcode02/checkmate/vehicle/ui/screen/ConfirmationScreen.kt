package com.whitebearcode02.checkmate.vehicle.ui.screen

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleAction
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleCard
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateActions
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateUiState
import com.whitebearcode02.checkmate.vehicle.domain.model.EventOrigin

internal fun LazyListScope.confirmationItems(state: CheckMateUiState, actions: CheckMateActions) {
    item {
        VehicleCard(if (state.activeEvent?.origin == EventOrigin.DEMO) "Mock 사고 의심 상황" else "사고 발생 여부 확인", warning = true) {
            Text("안전한 곳에 정차하고 본인과 탑승자의 상태를 확인하세요.", style = MaterialTheme.typography.titleLarge)
            Text(if (state.activeEvent?.origin == EventOrigin.DEMO)
                "개발용 버튼으로 생성된 모의 이벤트입니다. 실제 센서가 사고를 감지한 것이 아닙니다."
                else "사용자가 시작한 사고 대응입니다. 사고 발생 여부를 직접 확인해 주세요. 자동 사고 감지 결과가 아닙니다.")
            state.activeEvent?.let { event ->
                Text("발생 시각: ${event.occurredAt.displayTime()}", style = MaterialTheme.typography.bodySmall)
                Text("발생 당시 차량 연결: ${event.vehicleSnapshot.connection.label()}", style = MaterialTheme.typography.bodySmall)
                Text("발생 당시 스마트폰 연결: ${event.phoneConnection.label()}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    item { VehicleAction("사고 확인 · 안전 대응 안내", actions.confirmAccident, enabled = !state.storageBusy, warning = true) }
    item { VehicleAction("오탐 취소 · 대기로 복귀", actions.cancelFalseAlarm, enabled = !state.storageBusy, secondary = true) }
}
