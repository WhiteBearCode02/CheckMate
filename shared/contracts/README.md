# CheckMate 공통 사고 데이터 계약 — Phase 3A

계약 `schemaVersion`: **1** · 기준일: 2026-10-31. 차량·스마트폰·AI 담당자가 공유할 Android SDK 비의존 JSON 계약이다. 실제 서버 API·Firebase 인증·모바일 연동을 완료했다는 뜻이 아니다.

- [사고 JSON Schema](accident-event.schema.json): JSON Schema draft 2020-12, `AccidentRecordJsonCodec`의 wire 필드·enum·필수값을 기술한다.
- [사고 예제](accident-event.example.json): 가상 UUID와 UTC 시각으로 만든 수동 차량 사고 메타데이터다. 실제 사고·차량 측정·영상 분석 결과가 아니다.
- [기존 Phase 1·2 계약 이력](../../android-vehicle/shared/README.md): 삭제하지 않고 보존한다. 현재 사고 envelope는 이 문서를 기준으로 한다.
- [저장 구조](../../docs/architecture/database-design.md), [동기화 정책](../../docs/architecture/sync-policy.md), [Phase 3 검증 보고서](../../android-vehicle/docs/PHASE3_REPORT.md).

## Envelope와 식별자

| 필드 | 의미 | 수신 규칙 |
| --- | --- | --- |
| `schemaVersion` | 공통 사고 envelope 버전 | 현재 `1` 필수 |
| `event` | 기존 `AccidentEvent` 확장 | 필수. 사고 키는 `event.eventId` UUID |
| `ownerUserId` | 사고 소유 사용자 UUID | 필수. 이 값만으로 접근 권한을 부여하지 않음 |
| `createdByDeviceId` | 생성 기기 UUID | 필수 |
| `sync` | 로컬·원격 버전, 수정 시각·기기·상태 | 필수 |
| `assistance` | 안내 확인 이력 | 생략·null이면 빈 목록 |
| `prediction` | 분석 상태·결과 메타데이터 | 생략·null 허용 |
| `evidence` | 파일 원본과 분리한 증거 메타데이터 | 생략·null이면 빈 목록 |
| `deletedAt` | 삭제 tombstone의 UTC 시각 | 생략·null이면 미삭제 |

UUID는 소문자 canonical `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` 문자열이다. 기존 `eventId`를 유지하며 별도 `accidentId`를 도입하지 않는다. 같은 사건의 상태 전환·동기화·보고서에서 같은 UUID를 사용한다. 새 사고에는 새 UUID를 할당한다.

시각은 UTC ISO 8601 `Z` 문자열이다. 예: `2026-10-31T00:00:00Z`. `event.occurredAt`, `sync.modifiedAt`, `completedAt`, `createdAt`, `deletedAt`, 차량 샘플 `collectedAt`, 차량 `lastUpdatedAt`에 적용한다. 내부 Kotlin의 `collectedAtMillis`·`lastUpdatedAtMillis`는 codec에서 ISO 문자열로 변환하며 wire에 같은 이름의 epoch 숫자를 사용하지 않는다. 샘플 시각은 내부 epoch milliseconds로 변환 가능해야 한다. 재시도 횟수·버전·과실비율은 시각이 아닌 숫자다.

## 출처·생성 경로·차량 상태

`DataSource`는 기존 `MOCK`, `PLEOS`를 유지한다. `EventOrigin`은 `VEHICLE_MANUAL`, `MOBILE_MANUAL`, `MOBILE_SENSOR`, `DEMO`다. `event.origin` 생략·null은 기존 모델 호환을 위해 `DEMO`로 읽으며 새 송신자는 명시한다. 수동 사고라는 이유로 차량 출처를 `MOCK`나 `MOBILE`로 바꾸지 않는다.

`event.vehicleSnapshot`은 발생 당시 관측·연결 상태다. 현재 주행 안전 판단으로 재사용하지 않는다. 예제의 `source=PLEOS`, `origin=VEHICLE_MANUAL`, `movement=UNKNOWN`, `speedKph=null`은 실제 SDK가 속도·정차를 제공하지 않았음을 뜻한다. 값을 0·PARKED로 채워 상세 화면을 허용하지 않는다.

`vehicleSnapshot.readings`는 kind별 map의 wire 배열이다. 각 항목은 `kind`, `value`, `unit`, `source`, `collectedAt`, `validity`, `support`, `permission`, `updateState`를 가진다. `value`는 null 또는 아래 태그 객체다.

```json
{"type":"NUMBER","number":0.0}
```

```json
{"type":"STATE","state":"PARKED"}
```

```json
{"type":"BOOLEAN","enabled":false}
```

샘플 값·시각·지원·권한·신선도는 함께 해석한다. `CONNECTED` 또는 숫자 `0`만으로 정차가 증명되지 않는다. `readings`의 kind 중복을 거부하고 샘플 출처는 부모 차량 출처와 일치해야 한다. `speedKph`가 있으면 `SPEED`, `movement`가 `UNKNOWN`이 아니면 `MOVEMENT` capability가 있어야 한다.

## 안내·증거·분석

`assistance[].eventId`, `evidence[].eventId`, `prediction.eventId`는 부모 `event.eventId`와 같아야 한다. 안내 단계는 중복할 수 없으며 같은 증거 UUID도 목록에 한 번만 존재해야 한다. 안내 확인은 사용자에게 가이드를 보여주고 확인한 이력이며 통화·보험 접수·영상 전송 완료 증명이 아니다.

`EvidenceMetadata`는 `evidenceId`, `eventId`, `kind`, `ownerDeviceId`, `createdAt`, `mimeType`, `transferState`를 포함한다. `kind`는 기존 `VIDEO`, `LOCATION`, `VEHICLE_SNAPSHOT`에 `PHOTO`를 추가한다. 전송 상태는 `LOCAL_ONLY`, `METADATA_ONLY`, `READY_WITH_CONSENT`, `UNAVAILABLE`다. 파일 경로나 URI·공개 URL·GPS·원본 바이트는 포함하지 않는다. `READY_WITH_CONSENT`도 실제 전송 시점의 권한·동의 검사를 대체하지 않으며 해당 파일이 현재 기기에 존재한다고 보장하지 않는다.

분석 상태는 `NOT_REQUESTED`, `PENDING`, `AVAILABLE`, `UNAVAILABLE`, `FAILED`다. `AVAILABLE`일 때 사고 유형·근거는 비어 있지 않아야 하며 두 과실비율은 각각 0~100 정수·합계 100이어야 한다. 다른 상태에서 과실비율은 생략 또는 null이다. 결과 없음·실패를 50:50 등 임의 결과로 바꾸지 않는다.

현재 실제 AI 모델은 연결하지 않았다. `source=MOCK` 결과는 데모로 표시하고 법적 확정 과실비율로 설명하지 않는다. `PLEOS`를 실제 AI 결과 출처로 임의 재사용하지 않는다. 실제 AI 연동 전 모델 식별자·버전·작업 ID·입력 권한·출처·판단 불가·근거·한계를 추가 합의하고 필요한 계약 버전을 올린다. 스마트폰 블랙박스는 선택 기능이다.

## 버전·잘못된 입력

송신 codec은 정의한 필드를 모두 내보내며 nullable 필드는 JSON null로 표현한다. 수신 codec은 Schema의 required 필드를 요구하고 나머지는 위 기본값으로 읽는다. 필드명이 같아도 문자열 숫자·문자열 boolean을 올바른 타입으로 변환해 주지 않는다.

- 버전 없음·schema 0·미지원 미래 버전·알 수 없는 enum은 거부한다. 이전 단계에 영속 사고 JSON이 없었으므로 구버전 자동 마이그레이션은 제공하지 않는다.
- 알 수 없는 추가 필드는 읽을 때 무시한다. 재직렬화 시 버려지므로 미지원 확장을 무손실 보존하는 중계 계층으로 사용하지 않는다.
- 중복 JSON 키·주석·후행 데이터·잘못된 UUID·UTC 시각·범위·타입·참조 관계는 거부한다. 잘못된 원문을 로그·예외 메시지로 내보내지 않는다.
- codec는 최대 문서 길이 1,048,576자·중첩 깊이 32·배열 10,000개·객체 256필드·문자열 16,384자의 경계를 둔다. JSON Schema만으로 파서의 중복 키·깊이·전체 길이 제한을 대신할 수 없다.
- JSON Schema만으로 부모/하위 ID 일치, 과실비율 합계 100, kind별 중복, 목록의 특정 필드 기준 중복, 차량 출처 일치·capability 정합성을 모두 표현하지 않는다. Kotlin/Python 등 각 수신 구현에서 의미 검증을 추가한다. `format` 검증을 지원·활성화한 검증기를 사용한다.

## 사용자·기기·연락처와 Remote API

`UserProfile`, `RegisteredDevice`, `InsuranceContact`는 도메인 모델로 추가됐지만 사고 envelope의 중첩 필드는 아니다. 사용자·기기 등록 프로토콜과 보험사 연락처 공유 API는 아직 구현하지 않았다. installation identity는 로컬 소유권 namespace이며 실제 로그인·페어링 증명이 아니다.

Remote 호출에는 인증된 세션·기대 revision·멱등 작업 식별자가 별도로 필요하다. 해당 값은 예제 사고 JSON 본문만으로 신뢰하지 않는다. Fake Auth·Fake Remote는 개발용 검증이며 실제 Firebase 토큰·보안 규칙을 구현한 것이 아니다. `sync.status=SYNCED`도 어떤 Remote 구현인지 확인해야 하며 Fake 성공을 실제 기기 연동 성공으로 표시하지 않는다.

동시 다른 내용은 `CONFLICT`로 보존하고 최신 시각만으로 덮어쓰지 않는다. 삭제 `deletedAt`은 tombstone이며 재수신된 오래된 기록으로 자동 부활시키지 않는다. 증거·확인/오탐·삭제/편집의 구체적인 보수적 정책은 [동기화 정책](../../docs/architecture/sync-policy.md)을 따른다.

## Phase 3B 전제

현재 기본값은 로컬 저장이며 외부 전송·유료 API·클라우드 프로젝트·원본 영상 업로드는 활성화하지 않는다. 실제 연동 전 인증 주체 기반 사용자 격리·서버 원자성·DB 보안 규칙·차량 환경 지원·동의·보존/삭제·이용 및 비용 조건을 검증하고 소유자 승인을 받는다. 최종 빌드·테스트 결과는 [Phase 3 보고서](../../android-vehicle/docs/PHASE3_REPORT.md)에 기록한다.
