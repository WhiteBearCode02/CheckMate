package com.whitebearcode02.checkmate.vehicle.ui.screen

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.whitebearcode02.checkmate.vehicle.domain.model.AssistanceStep
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleAction
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleCard
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateActions
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateUiState

internal fun LazyListScope.assistanceItems(
    state: CheckMateUiState,
    actions: CheckMateActions,
    developerMode: Boolean,
) {
    val steps = AssistanceStep.entries
    val next = steps.firstOrNull { it !in state.completedSteps }
    item {
        VehicleCard("사고 후 안전 대응 안내", warning = true) {
            Text("본인과 탑승자의 안전을 먼저 확인하세요.", style = MaterialTheme.typography.titleLarge)
            Text("안내 확인 ${state.completedSteps.size} / ${steps.size}")
            Text("버튼은 안내를 읽었다는 표시입니다. 실제 신고·영상 보존·보험 접수를 실행하거나 완료 여부를 확인하지 않습니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (next != null) {
        item {
            VehicleCard("${next.ordinal + 1}. ${next.title()}") {
                Text(next.guidance())
                VehicleAction("안내 확인 · 다음", { actions.completeAssistanceStep(next) })
            }
        }
    } else {
        item {
            VehicleCard("안전 대응 안내 확인 완료") {
                Text("영상과 위치 데이터는 아직 수집되지 않았습니다. 실제 사고 대응과 기록 보존 여부를 직접 확인하세요.")
                VehicleAction("분석 연결 상태 확인", actions.requestUnavailableAnalysis, enabled = state.canShowDetails)
                if (developerMode) {
                    VehicleAction("개발용 Mock 분석 예시 보기", actions.requestMockAnalysis, enabled = state.canShowDetails, secondary = true)
                }
                if (!state.canShowDetails) Text("분석 화면은 정차가 확인된 후 열 수 있습니다.")
            }
        }
    }
}

private fun AssistanceStep.title(): String = when (this) {
    AssistanceStep.OCCUPANT_SAFETY -> "본인과 탑승자의 안전"
    AssistanceStep.SECONDARY_COLLISION -> "주변 교통과 2차 사고 위험"
    AssistanceStep.EMERGENCY_GUIDANCE -> "긴급 신고 안내"
    AssistanceStep.EVIDENCE_CHECK -> "영상과 위치 기록"
    AssistanceStep.INSURANCE_GUIDANCE -> "보험사 접수 안내"
    AssistanceStep.RECORD_REVIEW -> "사고 기록 확인"
}

private fun AssistanceStep.guidance(): String = when (this) {
    AssistanceStep.OCCUPANT_SAFETY -> "본인과 탑승자의 부상 여부를 확인하세요. 위험한 상황에서는 안전 확보를 우선하세요."
    AssistanceStep.SECONDARY_COLLISION -> "주변 차량과 도로 상황을 확인하고 2차 사고 위험에 주의하세요."
    AssistanceStep.EMERGENCY_GUIDANCE -> "부상자나 화재 등 긴급 위험이 있으면 119, 경찰 도움이 필요하면 112로 직접 신고하세요. 이 앱은 전화를 걸거나 신고를 전송하지 않습니다."
    AssistanceStep.EVIDENCE_CHECK -> "안전이 확보된 후 블랙박스 영상과 사고 위치가 보존되었는지 직접 확인하세요. 현재 앱은 영상과 위치를 저장하지 않습니다."
    AssistanceStep.INSURANCE_GUIDANCE -> "안전이 확보된 후 가입한 보험사의 연락처를 통해 사고 접수 절차를 확인하세요. 현재 보험사 연결과 자동 전송은 제공하지 않습니다."
    AssistanceStep.RECORD_REVIEW -> "정차 후 사고 ID, 발생 시간과 저장 상태를 확인하세요. 기록은 기기 내부에 저장되며 스마트폰 공유는 아직 연결되지 않았습니다. 영상 원본 보존 여부는 직접 확인하세요."
}
