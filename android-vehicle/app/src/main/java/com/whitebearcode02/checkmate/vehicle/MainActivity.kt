
package com.whitebearcode02.checkmate.vehicle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.whitebearcode02.checkmate.vehicle.ui.theme.CheckMateVehicleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CheckMateVehicleTheme {
                CheckMateHome()
            }
        }
    }
}

@Composable
fun CheckMateHome() {
    var accidentDetected by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "CheckMate",
                style = MaterialTheme.typography.headlineLarge
            )

            Text("차량 안전 및 사고 대응 시스템")

            HorizontalDivider()

            Text("차량 연결 상태: 미검증")
            Text("블랙박스 연결 상태: 미연결")

            if (accidentDetected) {
                Text(
                    text = "사고 의심 상황이 감지되었습니다.",
                    color = MaterialTheme.colorScheme.error
                )

                Text("안전한 곳에 정차하고 부상 여부를 확인하세요.")

                Button(
                    onClick = { accidentDetected = false }
                ) {
                    Text("모의 상황 종료")
                }
            } else {
                Button(
                    onClick = { accidentDetected = true }
                ) {
                    Text("사고 상황 테스트")
                }
            }

            Text(
                text = "현재 화면은 Mock 데이터 기반 개발용입니다.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
