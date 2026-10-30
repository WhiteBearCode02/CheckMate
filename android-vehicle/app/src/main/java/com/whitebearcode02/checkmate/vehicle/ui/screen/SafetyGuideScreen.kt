package com.whitebearcode02.checkmate.vehicle.ui.screen

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleAction
import com.whitebearcode02.checkmate.vehicle.ui.component.VehicleCard
import com.whitebearcode02.checkmate.vehicle.viewmodel.CheckMateActions

/** Basic guidance is independent of SDK availability and never creates an accident record. */
internal fun LazyListScope.safetyGuideItems(actions: CheckMateActions) {
    item {
        VehicleCard("기본 안전 안내", warning = true) {
            Text("안전한 곳에 정차하고 본인과 탑승자의 상태를 확인하세요.", style = MaterialTheme.typography.titleLarge)
            Text("주변 차량과 도로 상황을 확인하고 2차 사고 위험에 주의하세요.")
            Text("긴급 위험이 있으면 직접 긴급 신고를 요청하세요. 이 앱은 신고나 보험 접수를 전송하지 않습니다.")
            Text("이 안내를 열어도 사고 이벤트가 생성되거나 차량 연결이 확인되지는 않습니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
    item { VehicleAction("홈으로", actions.home, secondary = true) }
}
