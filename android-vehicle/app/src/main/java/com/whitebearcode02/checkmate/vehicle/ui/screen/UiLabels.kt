package com.whitebearcode02.checkmate.vehicle.ui.screen

import com.whitebearcode02.checkmate.vehicle.domain.model.AccidentStatus
import com.whitebearcode02.checkmate.vehicle.domain.model.ConnectionState
import com.whitebearcode02.checkmate.vehicle.domain.model.VehicleMovement
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun ConnectionState.label(): String = when (this) {
    ConnectionState.DISCONNECTED -> "미연결"
    ConnectionState.CONNECTING -> "연결 중"
    ConnectionState.CONNECTED -> "연결됨"
    ConnectionState.FAILED -> "연결 실패"
    ConnectionState.UNAVAILABLE -> "사용 불가"
}

internal fun AccidentStatus.label(): String = when (this) {
    AccidentStatus.NORMAL -> "대기"
    AccidentStatus.SUSPECTED -> "사고 의심 · 확인 필요"
    AccidentStatus.FALSE_ALARM -> "오탐으로 취소"
    AccidentStatus.CONFIRMED -> "사고 확인됨"
    AccidentStatus.ASSISTANCE -> "안전 대응 안내 중"
    AccidentStatus.ANALYSIS_PENDING -> "분석 대기"
    AccidentStatus.COMPLETED -> "Mock 분석 완료"
    AccidentStatus.UNAVAILABLE -> "분석 결과 없음"
}

internal fun VehicleMovement.label(): String = when (this) {
    VehicleMovement.UNKNOWN -> "정차 여부 확인 불가"
    VehicleMovement.PARKED -> "정차 상태"
    VehicleMovement.MOVING -> "주행 상태"
}

internal fun Instant.displayTime(): String = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())
    .format(this)
