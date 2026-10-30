package com.whitebearcode02.checkmate.vehicle.ui.screen

import com.whitebearcode02.checkmate.vehicle.domain.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun ConnectionState.label(): String = when (this) {
    ConnectionState.DISCONNECTED -> "미연결"
    ConnectionState.CONNECTING -> "연결 중"
    ConnectionState.CONNECTED -> "연결됨"
    ConnectionState.FAILED -> "연결 실패"
    ConnectionState.UNAVAILABLE -> "사용 불가"
    ConnectionState.UNSUPPORTED -> "지원되지 않음"
    ConnectionState.PERMISSION_DENIED -> "권한 거부"
    ConnectionState.ERROR -> "오류"
    ConnectionState.UNKNOWN -> "확인되지 않음"
}

internal fun SdkInitializationState.label(): String = when (this) {
    SdkInitializationState.NOT_STARTED -> "시작 전"
    SdkInitializationState.INITIALIZING -> "초기화 중"
    SdkInitializationState.INITIALIZED -> "초기화 완료"
    SdkInitializationState.FAILED -> "초기화 실패"
    SdkInitializationState.UNSUPPORTED -> "실행 환경 미지원"
}

internal fun VehicleDataKind.label(): String = when (this) {
    VehicleDataKind.SPEED -> "차량 속도"
    VehicleDataKind.MOVEMENT -> "정차 / 주행 상태"
    VehicleDataKind.BRAKE -> "브레이크"
    VehicleDataKind.TURN_SIGNAL -> "방향지시등"
    VehicleDataKind.DRIVING_MODE -> "주행 모드"
    VehicleDataKind.ODOMETER -> "누적 주행 거리"
    VehicleDataKind.DOOR -> "앞좌석 왼쪽 도어 위치"
}

internal fun FeatureSupport.label(): String = when (this) {
    FeatureSupport.UNKNOWN -> "지원 여부 미확인"
    FeatureSupport.SUPPORTED -> "지원"
    FeatureSupport.UNSUPPORTED -> "미지원"
}

internal fun DataPermission.label(): String = when (this) {
    DataPermission.UNKNOWN -> "권한 미확인"
    DataPermission.GRANTED -> "읽기 권한 확인"
    DataPermission.DENIED -> "읽기 권한 거부"
}

internal fun DataValidity.label(): String = when (this) {
    DataValidity.UNKNOWN -> "유효성 미확인"
    DataValidity.VALID -> "유효"
    DataValidity.STALE -> "유효기간 만료"
    DataValidity.INVALID -> "유효하지 않음"
}

internal fun DataUpdateState.label(): String = when (this) {
    DataUpdateState.WAITING -> "수신 대기"
    DataUpdateState.UPDATING -> "갱신 중"
    DataUpdateState.STOPPED -> "갱신 중단"
}

internal fun VehicleError.label(): String = when (this) {
    VehicleError.CONNECTION_FAILED -> "차량 서비스 연결에 실패했습니다."
    VehicleError.PERMISSION_DENIED -> "차량 데이터 읽기 권한이 없습니다."
    VehicleError.SDK_UNAVAILABLE -> "이 환경에서는 Vehicle SDK를 사용할 수 없습니다."
    VehicleError.DATA_UNAVAILABLE -> "차량 데이터가 수신되지 않았습니다."
    VehicleError.INITIALIZATION_FAILED -> "Vehicle SDK 초기화에 실패했습니다."
    VehicleError.CONNECTION_TIMEOUT -> "차량 서비스 연결 대기 시간이 지났습니다."
    VehicleError.DATA_STALE -> "차량 데이터 갱신이 중단되었습니다. 정차 여부를 다시 확인해야 합니다."
    VehicleError.UNSUPPORTED_FEATURE -> "이 환경에서 해당 차량 정보가 지원되지 않습니다."
}

internal fun VehicleDatum.displayValue(): String {
    val text = when (val sample = value) {
        null -> "수신값 없음"
        is VehicleValue.NumberValue -> sample.number.toString()
        is VehicleValue.BooleanValue -> if (sample.enabled) "켜짐" else "꺼짐"
        is VehicleValue.StateValue -> when (sample.state) {
            VehicleMovement.PARKED.name -> "정차"
            VehicleMovement.MOVING.name -> "주행"
            VehicleMovement.UNKNOWN.name -> "확인 불가"
            else -> sample.state
        }
    }
    val suffix = when (unit) {
        VehicleUnit.KPH -> " km/h"
        VehicleUnit.KM -> " km"
        VehicleUnit.PERCENT -> " %"
        VehicleUnit.UNKNOWN, VehicleUnit.STATE, VehicleUnit.BOOLEAN -> ""
    }
    return text + if (value == null) "" else suffix
}

internal fun Long.displayTimestamp(): String = Instant.ofEpochMilli(this).displayTime()

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
