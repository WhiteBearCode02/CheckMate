package com.whitebearcode02.checkmate.vehicle.ui.screen

import com.whitebearcode02.checkmate.vehicle.domain.model.EventOrigin
import com.whitebearcode02.checkmate.vehicle.domain.model.SyncStatus
import com.whitebearcode02.checkmate.vehicle.viewmodel.LocalStorageStatus

internal fun LocalStorageStatus.label(): String = when (this) {
    LocalStorageStatus.MEMORY_ONLY -> "개발용 메모리 저장 · 종료 시 삭제"
    LocalStorageStatus.LOADING -> "기기 저장소 불러오는 중"
    LocalStorageStatus.READY -> "기기 내부 저장소 사용 · 앱 재시작 후 복구"
    LocalStorageStatus.ERROR -> "저장소 오류 · 기록 변경 제한"
}

internal fun SyncStatus.label(): String = when (this) {
    SyncStatus.LOCAL_ONLY -> "로컬 저장 · 외부 동기화 미설정"
    SyncStatus.PENDING -> "기기 저장 완료 · 동기화 대기"
    SyncStatus.SYNCED -> "연결된 저장소와 버전 일치"
    SyncStatus.RETRY -> "기기 기록 유지 · 동기화 재시도 필요"
    SyncStatus.CONFLICT -> "기기 기록 유지 · 변경 충돌 확인 필요"
    SyncStatus.BLOCKED -> "기기 기록 유지 · 접근 또는 전송 제한"
}

internal fun EventOrigin.label(): String = when (this) {
    EventOrigin.VEHICLE_MANUAL -> "차량에서 사용자가 시작한 기록"
    EventOrigin.MOBILE_MANUAL -> "스마트폰 수동 기록"
    EventOrigin.MOBILE_SENSOR -> "스마트폰 센서 기반 기록"
    EventOrigin.DEMO -> "DEMO · 개발용 가상 사고 기록"
}
