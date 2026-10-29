package com.whitebearcode02.checkmate.vehicle.ui.screen

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleAction
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleCard
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateActions
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateUiState

internal fun LazyListScope.recordItems(state: CheckMateUiState, actions: CheckMateActions) {
    item {
        VehicleCard("사고 기록 · Mock") {
            Text("현재 실행 중 생성된 모의 이벤트만 표시합니다. 실제 사고 증거를 보존하는 저장소가 아닙니다.")
            if (state.events.isEmpty()) Text("아직 기록된 사고가 없습니다.")
        }
    }
    if (state.canShowDetails) {
        items(state.events, key = { it.eventId.toString() }) { event ->
            VehicleCard(event.status.label()) {
                Text("사고 ID: ${event.eventId}", style = MaterialTheme.typography.bodySmall)
                Text("발생 시각: ${event.occurredAt.displayTime()}")
                Text("발생 당시 차량 연결: ${event.vehicleSnapshot.connection.label()}", style = MaterialTheme.typography.bodySmall)
                Text("발생 당시 블랙박스 연결: ${event.phoneConnection.label()}", style = MaterialTheme.typography.bodySmall)
                VehicleAction("분석 결과 상태 보기", { actions.openAnalysis(event.eventId) }, secondary = true)
            }
        }
    } else {
        item { Text("정차 여부를 확인할 수 없어 상세 기록을 표시하지 않습니다.") }
    }
}
