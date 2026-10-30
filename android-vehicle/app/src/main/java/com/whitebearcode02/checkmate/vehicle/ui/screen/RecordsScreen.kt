package com.whitebearcode02.checkmate.vehicle.ui.screen

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleAction
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleCard
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateActions
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateUiState
import com.whitebearcode02.checkmate.vehicle.domain.model.DataSource

internal fun LazyListScope.recordItems(state: CheckMateUiState, actions: CheckMateActions) {
    item {
        VehicleCard("사고 기록") {
            Text(state.storageStatus.label())
            Text("사고 정보와 대응 단계의 기록입니다. 사진·영상 원본이나 위치가 저장되었다는 의미는 아닙니다.")
            if (state.events.isEmpty()) Text("아직 기록된 사고가 없습니다.")
        }
    }
    if (state.canShowDetails) {
        items(state.events, key = { it.eventId.toString() }) { event ->
            VehicleCard(event.status.label()) {
                Text("사고 ID: ${event.eventId}", style = MaterialTheme.typography.bodySmall)
                Text("발생 시각: ${event.occurredAt.displayTime()}")
                Text(event.origin.label())
                Text(if (event.source == DataSource.MOCK) "차량 데이터 출처: MOCK · 모의 데이터" else "차량 데이터 출처: PLEOS")
                Text(state.syncMetadata[event.eventId]?.status?.label() ?: "개발용 메모리 기록")
                Text("증거 메타데이터: ${state.evidence[event.eventId]?.size ?: 0}건 · 이 기기의 영상 원본 존재 여부 미확인",
                    style = MaterialTheme.typography.bodySmall)
                Text("발생 당시 차량 연결: ${event.vehicleSnapshot.connection.label()}", style = MaterialTheme.typography.bodySmall)
                Text("발생 당시 스마트폰 연결: ${event.phoneConnection.label()}", style = MaterialTheme.typography.bodySmall)
                VehicleAction("분석 결과 상태 보기", { actions.openAnalysis(event.eventId) }, secondary = true)
            }
        }
    } else {
        item { Text("정차 여부를 확인할 수 없어 상세 기록을 표시하지 않습니다.") }
    }
}
