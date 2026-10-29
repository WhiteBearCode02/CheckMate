# CheckMate 공통 데이터 계약 — Phase 1

계약 초안 버전: `phase1/1` · 작성일: 2026-10-30

모바일·AI·차량 담당자가 함께 검토할 전송 형식이다. 이 파일은 저장소의 `android-vehicle/shared/`에 있으며 Android SDK에 의존하지 않는다. 팀 간 합의가 끝난 서버 API나 이미 구현된 네트워크 직렬화 기능을 의미하지 않는다. Kotlin의 원본 타입은 `app/src/main/java/com/whitebearcode02/checkmate/vehicle/domain/model/`에 있다.

## 공통 규칙

- 모든 사고 연결 키는 UUID 문자열 `eventId`다. `AccidentEvent`, `Evidence`, `AccidentPrediction`, `AssistanceAction`, `AccidentReport`에서 같은 사건의 ID가 일치해야 한다.
- `sessionId`, `evidenceId`도 UUID 문자열이다. Kotlin에서는 `UUID`, 전송할 때는 `UUID.toString()` 형식을 사용한다.
- 시각은 UTC ISO 8601 문자열이다. 예: `2026-10-30T00:00:00Z`. Kotlin에서는 `Instant`, 전송할 때는 `Instant.toString()`을 사용한다. 단위가 표시되지 않은 epoch 숫자로 바꾸지 않는다.
- 열거형은 아래의 대문자 이름을 그대로 사용한다. 선택 필드의 값이 없으면 JSON `null`로 표현한다. 후속 수신 구현은 알 수 없는 값·잘못된 범위·누락된 필수 필드를 성공으로 취급하지 않아야 한다.
- 수치 `0`과 값 없음 `null`은 다르다. 연결됨과 데이터 수신됨, 기능 미지원과 일시적 값 없음도 다르다.
- `source=MOCK`는 합성 개발 데이터다. 이를 실제 차량·영상·AI 관측 결과로 전환하거나 표시하지 않는다. 실제 어댑터는 검증 후 `PLEOS`를 사용한다. AI·모바일 소스가 추가되면 계약을 버전업한다.
- 영상·위치 원문은 JSON에 넣지 않는다. `Evidence`의 파일 참조 및 메타데이터만 공유한다. 이 계약에는 원시 위도·경도 필드를 정의하지 않는다.
- 이벤트·증거·파일 참조·위치·연결 대상 정보를 로그, 분석 서비스, 예외 문자열에 기록하지 않는다. 예시는 모두 가상 데이터다.
- Phase 1 저장소는 메모리에만 보관하며 앱 프로세스 종료 시 사라진다. 외부 전송·파일 저장·실제 녹화·보고서 내보내기는 구현하지 않는다.

## 모델

| 모델 | 필수 필드 | 선택 필드 / 제약 |
| --- | --- | --- |
| `DriveSession` | `sessionId`, `startedAt` | `endedAt=null`; 종료 시각은 시작보다 빠를 수 없음 |
| `AccidentEvent` | `eventId`, `occurredAt`, `vehicleSnapshot` | `status=SUSPECTED`, `phoneConnection=DISCONNECTED`, `source=MOCK`, `sessionId=null` |
| `Evidence` | `evidenceId`, `eventId`, `kind`, `capturedAt`, `fileReference`, `mimeType` | `durationMillis=null`; 있을 때 0 이상의 밀리초 |
| `AccidentPrediction` | `eventId`, `status` | `accidentType`, `ownFaultPercent`, `otherFaultPercent`, `rationale`은 기본 null; `modelLabel=미연동`, `source=MOCK` |
| `AssistanceAction` | `eventId`, `step`, `completedAt` | 가이드 확인 이력이며 신고·전송 완료 증명이 아님 |
| `AccidentReport` | `eventId`, `generatedAt` | `evidence=[]`, `assistance=[]`, `prediction=null`; 모든 하위 `eventId` 일치 필수 |
| `VehicleState` | 기본값으로 생성 가능 | `connection=DISCONNECTED`, `source=MOCK`, `speedKph=null`, `movement=UNKNOWN`, `capabilities=[]`, `error=null` |
| `ConnectionState` | 열거형 | 차량 및 스마트폰 연결 상태에 공통 사용 |

## 상태 및 열거형

| 타입 | 값 |
| --- | --- |
| `ConnectionState` | `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `FAILED`, `UNAVAILABLE` |
| `DataSource` | `MOCK`, `PLEOS` |
| `VehicleMovement` | `UNKNOWN`, `PARKED`, `MOVING` |
| `VehicleCapability` | `SPEED`, `MOVEMENT` |
| `VehicleError` | `CONNECTION_FAILED`, `PERMISSION_DENIED`, `SDK_UNAVAILABLE`, `DATA_UNAVAILABLE` |
| `AccidentStatus` | `NORMAL`, `SUSPECTED`, `FALSE_ALARM`, `CONFIRMED`, `ASSISTANCE`, `ANALYSIS_PENDING`, `COMPLETED`, `UNAVAILABLE` |
| `PredictionStatus` | `NOT_REQUESTED`, `PENDING`, `AVAILABLE`, `UNAVAILABLE`, `FAILED` |
| `EvidenceKind` | `VIDEO`, `LOCATION`, `VEHICLE_SNAPSHOT` |
| `AssistanceStep` | `OCCUPANT_SAFETY`, `SECONDARY_COLLISION`, `EMERGENCY_GUIDANCE`, `EVIDENCE_CHECK`, `INSURANCE_GUIDANCE`, `RECORD_REVIEW` |

### 사고 상태 전환

```text
새 이벤트 생성 → SUSPECTED
                 ├→ FALSE_ALARM (이력을 보존하고 현재 감시 상태는 NORMAL)
                 └→ CONFIRMED → ASSISTANCE → ANALYSIS_PENDING
                                            ├→ COMPLETED
                                            └→ UNAVAILABLE
```

`NORMAL`은 활성 사고가 없는 감시 상태다. 오탐 이벤트를 `NORMAL`로 덮어쓰지 않고 `FALSE_ALARM`으로 남긴다. 새 사건은 새 UUID를 사용한다. 같은 사건의 상태 전환에서는 ID를 유지한다. 종료된 사건의 상태를 재사용하여 새 사고로 만들지 않는다.

### 차량 상태 검증

`speedKph`는 유한한 0 이상의 수치이며 `SPEED` 지원이 있어야 한다. `MOVEMENT` 지원이 없으면 `movement`는 `UNKNOWN`이다. 상세 사고 기록·AI 화면은 `connection=CONNECTED`, `movement=PARKED`, `error=null`이며 수신된 속도가 있다면 0일 때만 허용한다. 속도 미지원이어도 검증된 정차 상태가 있을 수 있으므로 속도 수신 자체를 필수로 요구하지 않는다. 미연결·실패·데이터 없음·주행 상태 및 정차 표시와 양의 속도가 충돌하는 상태에서는 상세 화면을 열 수 없다. 정차 판정이 실제 차량에서 검증되기 전에는 속도 0만으로 허용하지 않는다.

### AI 결과 검증

`AVAILABLE`일 때 사고 유형·판단 근거는 비어 있을 수 없다. 두 과실비율은 각각 0~100 정수이며 합계가 100이어야 한다. 그 외 상태에서는 과실비율 둘 다 `null`이어야 한다. 결과 없음·실패·판단 불가를 임의의 0:100 또는 50:50으로 채우지 않는다. Phase 1의 `AVAILABLE` 예시는 개발자가 선택한 **Mock 시나리오**일 뿐 모델의 추론·신뢰도·법적 판단을 나타내지 않는다. 실제 추론 도입 시 신뢰도·모델 버전·분석 한계·입력 출처를 별도 합의한다.

### 증거 참조 검증

`fileReference`와 `mimeType`은 공백 문자열일 수 없다. 참조 예시는 `evidence/<evidenceId>.mp4` 같은 논리적 식별자다. 공개 URL, 인증 토큰, 사용자 기기의 절대 경로를 넣지 않는다. 미래 파일 해석 계층에서 허용된 앱 전용 저장소로 범위를 제한하고 경로 이동(`..`), 임의 URI 및 외부 접근을 검증해야 한다. Phase 1에서는 참조된 파일을 열거나 생성하지 않는다.

## JSON 예시

아래는 전송 형태를 설명하기 위한 예시이며 현재 앱이 생성하거나 전송하는 네트워크 패킷이 아니다.

### 모의 사고 의심 이벤트

```json
{
  "eventId": "5bc24171-907a-4383-a4c8-b76f93695bfd",
  "occurredAt": "2026-10-30T00:00:00Z",
  "status": "SUSPECTED",
  "vehicleSnapshot": {
    "connection": "CONNECTED",
    "source": "MOCK",
    "speedKph": 0.0,
    "movement": "PARKED",
    "capabilities": ["SPEED", "MOVEMENT"],
    "error": null
  },
  "phoneConnection": "DISCONNECTED",
  "source": "MOCK",
  "sessionId": null
}
```

### 분석 불가 결과를 포함한 보고서

```json
{
  "eventId": "5bc24171-907a-4383-a4c8-b76f93695bfd",
  "generatedAt": "2026-10-30T00:03:00Z",
  "evidence": [],
  "assistance": [
    {
      "eventId": "5bc24171-907a-4383-a4c8-b76f93695bfd",
      "step": "OCCUPANT_SAFETY",
      "completedAt": "2026-10-30T00:01:00Z"
    }
  ],
  "prediction": {
    "eventId": "5bc24171-907a-4383-a4c8-b76f93695bfd",
    "status": "UNAVAILABLE",
    "accidentType": null,
    "ownFaultPercent": null,
    "otherFaultPercent": null,
    "rationale": null,
    "modelLabel": "미연동",
    "source": "MOCK"
  }
}
```

### 향후 영상 파일 메타데이터 예시

```json
{
  "evidenceId": "ecdbb8ae-b1e2-4bc4-a338-177f07023bd7",
  "eventId": "5bc24171-907a-4383-a4c8-b76f93695bfd",
  "kind": "VIDEO",
  "capturedAt": "2026-10-30T00:00:00Z",
  "fileReference": "evidence/ecdbb8ae-b1e2-4bc4-a338-177f07023bd7.mp4",
  "mimeType": "video/mp4",
  "durationMillis": 30000
}
```

## 후속 합의 사항

모바일·AI 담당자와 전송 방향, 계약 버전 표기 위치, 파일 전달 권한, 보존·삭제 정책, 중복 수신 처리, 실제 모델 출처와 실패 사유를 합의한 후 직렬화·전송 API를 추가한다. 백엔드·클라우드·유료 서비스·보험사 연결은 이번 계약 초안으로 활성화되지 않는다.
