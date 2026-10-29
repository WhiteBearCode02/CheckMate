package com.whitebearcode02.checkmate.vehicle.ui.screen

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.whitebearcode02.checkmate.vehicle.domain.model.DataSource
import com.whitebearcode02.checkmate.vehicle.domain.model.PredictionStatus
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleCard
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateActions
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateUiState

internal fun LazyListScope.analysisItems(state: CheckMateUiState, actions: CheckMateActions) {
    if (!state.canShowDetails) {
        item { VehicleCard("상세 분석 화면 제한") { Text("정차가 확인된 후 분석 결과를 확인할 수 있습니다.") } }
        return
    }
    val prediction = state.prediction
    val event = state.selectedEvent ?: state.activeEvent
    item {
        VehicleCard("AI 분석 결과") {
            Text("사고 ID: ${event?.eventId ?: "선택된 이벤트 없음"}", style = MaterialTheme.typography.bodySmall)
            Text("실제 AI 모델은 연결되지 않았습니다.")
            Text("AI 결과는 법적 판단이 아닌 참고 정보이며, Mock 예시는 실제 사고의 과실비율을 의미하지 않습니다.", color = MaterialTheme.colorScheme.primary)
        }
    }
    item {
        VehicleCard("모델 및 분석 상태") {
            Text("모델: ${prediction?.modelLabel ?: "미연동"}")
            when (prediction?.status) {
                null, PredictionStatus.NOT_REQUESTED -> Text("분석 결과 없음 · 분석을 요청하지 않았습니다.")
                PredictionStatus.PENDING -> Text("분석 대기 중 · 아직 결과가 없습니다.")
                PredictionStatus.UNAVAILABLE -> Text("판단 불가 · 연결된 AI 모델 또는 신뢰할 수 있는 분석 결과가 없습니다.")
                PredictionStatus.FAILED -> Text("분석 실패 · 과실비율을 표시할 수 없습니다.")
                PredictionStatus.AVAILABLE -> {
                    if (prediction.source == DataSource.MOCK) {
                        Text("MOCK · 개발용 가상 예시", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleLarge)
                    }
                    Text("사고 유형: ${prediction.accidentType ?: "판단 불가"}")
                    if (prediction.ownFaultPercent != null && prediction.otherFaultPercent != null) {
                        Text("내 차량 참고 과실비율: ${prediction.ownFaultPercent}%")
                        Text("상대 차량 참고 과실비율: ${prediction.otherFaultPercent}%")
                    } else {
                        Text("참고 과실비율: 결과 없음")
                    }
                    Text("판단 근거: ${prediction.rationale ?: "제공된 근거 없음"}")
                }
            }
        }
    }
}
