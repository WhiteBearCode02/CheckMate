# 사고 메타데이터 저장 설계 — Phase 3A

기준일: 2026-10-31. 차량 앱은 Room 기반 로컬 저장을 사용하며 실제 공유 DB는 연결하지 않는다. 현재 코드의 `eventId` UUID와 기존 사고 상태 전이·Repository API를 보존한다.

## 저장 모델

| 모델 | 목적 | 저장·전송 경계 |
| --- | --- | --- |
| `UserProfile` | 개발용 사용자 식별·설정 | 실제 계정 가입·클라우드 인증 아님 |
| `RegisteredDevice` | 사용자 소유 기기의 종류·식별자 | `VEHICLE` / `MOBILE`; 실제 기기 페어링 증명이 아님 |
| `AccidentEvent` | 발생 시각·상태·차량 스냅샷·출처·생성 경로 | 공통 키 `eventId`; 생성 경로 `EventOrigin`과 관측 `DataSource` 분리 |
| `AccidentRecord` | 소유권·수정 버전·안내 단계·분석 메타데이터·증거 메타데이터·삭제 표식의 집계 | schema version 1 JSON envelope |
| `AccidentSyncMetadata` | 로컬/원격 버전·수정 시각·기기·상태 | 원격 성공 여부를 로컬 저장 완료와 구분 |
| `EvidenceMetadata` | 종류·소유 기기·생성 시각·MIME·전송 가능 상태 | 파일 URI·파일 원본·GPS 없음. 메타데이터만으로 실제 파일 보유를 판단하지 않음 |
| `InsuranceContact` | 사용자가 등록할 보험사 연락처의 검증 가능한 모델 | 사고 JSON envelope·로그·Fake Remote에 포함하지 않음. 실제 연락처 등록·통화·공유는 별도 구현 범위 |

스마트폰 블랙박스는 선택 기능이며 `EvidenceKind.VIDEO`가 존재한다는 이유만으로 녹화·파일 생성·업로드가 구현되지는 않는다. `PHOTO`를 추가하되 기존 `VIDEO`, `LOCATION`, `VEHICLE_SNAPSHOT` 값을 유지한다. 위치 유형도 원시 좌표 전송을 허용하지 않는다.

## 계층과 API

```text
Compose UI → ViewModel → AccidentRepository
                            ├─ InMemoryAccidentRepository (개발·기존 테스트)
                            └─ PersistentAccidentRepository
                                → AccidentLocalDataSource
                                → Room (AccidentDatabase / AccidentDao / AccidentEntity)

SyncCoordinator → AccidentSyncStore (localVersion 비교 후 반영)
                → RemoteAccidentDataSource (개발용 Fake)
```

기존 메모리 Repository를 삭제하지 않고 API 호환성을 유지한다. 영속 Repository는 로컬 쓰기 완료 후에만 StateFlow의 성공 상태를 발행한다. 쓰기 실패를 성공으로 표시하거나 기존 메모리 상태를 새 값으로 교체하지 않는다. UI는 Room DAO나 JSON 파일을 직접 다루지 않는다.

## Room 선택과 스키마

Room 2.6.1을 사용하고 기존 Android Gradle Plugin·Kotlin·SDK 설정은 유지한다. Java Entity·DAO·Database와 javac annotation processor를 사용해 별도 KSP/Kotlin 플러그인 버전 교체를 피한다. Room 의존성은 무료 라이브러리이며 Firebase 프로젝트나 청구 계정을 생성하지 않는다.

테이블 `accident_records`의 복합 기본 키는 `(owner_user_id, event_id)`다. 각 행의 `payload`에는 검증된 `AccidentRecord` JSON을 저장한다. 안내 단계·분석·증거 목록·동기화 버전·tombstone이 같은 행에 있으므로 개별 사고 변경은 SQLite의 단일 행 교체로 원자적으로 반영된다. `eventId` 기준으로 같은 사고가 중복 행으로 늘어나지 않는다.

JSON 내부의 UUID·소유권과 행 키가 다르면 오류로 처리한다. 한 행이라도 파싱·버전·소유권 검증에 실패하면 전체 로드를 실패시키며 정상 빈 DB로 조용히 초기화하지 않는다. 잘못된 입력이나 저장 오류 메시지는 사고 payload·파일 URI·개인정보를 노출하지 않는다.

DB 스키마는 version 1이며 export한 Room schema를 저장소에서 관리한다. destructive migration은 사용하지 않는다. 이후 DB version이나 `schemaVersion`을 올릴 때 명시적인 마이그레이션·손상 보존·롤백 가능성·기존 fixture 검증을 추가해야 한다. Phase 1·2는 메모리 저장이므로 기존 영속 사고 DB의 자동 이관 대상이 없다.

## JSON 호환성과 검증

정확한 wire 필드·예제·JSON Schema는 [공통 계약](../../shared/contracts/README.md)을 기준으로 한다. `AccidentRecordJsonCodec`는 명시적으로 필드를 매핑하고 검증된 모델 생성자를 호출한다. Gson의 reflection으로 검증을 우회하여 도메인 객체를 만들지 않는다.

- `schemaVersion=1`만 읽는다. 버전 없음·구버전·미지원 미래 버전을 성공으로 간주하지 않는다.
- 알 수 없는 추가 필드는 읽을 때 무시한다. 현재 모델로 재저장할 때 보존되지 않으므로 확장 필드의 무손실 중계 용도로 사용하지 않는다.
- 알 수 없는 enum, 잘못된 타입·범위·UUID·UTC 시각, 중복 JSON 키·잘못된 JSON은 거부한다.
- 문서 최대 길이 1,048,576자, 중첩 깊이 32, 배열 원소 10,000개, 객체 필드 256개, 문자열 16,384자 등의 한계를 적용한다. 문서 길이 제한은 바이트 수 기준과 다르다.
- 하위 안내·예측·증거의 `eventId`는 부모와 일치해야 한다. 안내 단계와 `evidenceId` 중복을 거부한다. 가용 분석 과실비율의 각 범위·합계와 차량 스냅샷 정합성도 확인한다.

## 재시작·오프라인·삭제

프로세스 재생성 시 로컬 사고·안내 단계·분석 메타데이터·동기화 상태를 복원한다. 기록 스냅샷은 과거 관측이다. 현재 차량 연결이나 정차 상태를 복원하는 자료로 사용하지 않으며 Provider에서 새로 확인한다.

Remote가 준비되지 않아도 사고 생성·기록 조회·대응 단계 저장·기본 안전 안내는 사용할 수 있다. 동기화 실패는 로컬 기록을 삭제하지 않는다. `deletedAt`이 있는 기록은 삭제 tombstone으로 보존하며 목록에서 정상 사고처럼 다시 나타나지 않도록 취급한다. 영구 삭제·장기 보존 기간·원본 파일 제거 정책은 실제 서버·사용자 동의 범위와 함께 후속 합의한다.

## 보안과 한계

앱 전용 내부 DB를 사용하며 공용 외부 저장소나 공개 URL을 만들지 않는다. 기존 앱 백업·평문 통신 제한을 보존한다. 원본 영상·사진·기기 파일 URI·원시 위치·API 키·토큰은 사고 envelope에 넣지 않는다. 앱 전용 저장소는 SQLCipher 등 별도 암호화 구현을 뜻하지 않으며 손상·분실·루팅 기기까지 보호한다는 보장은 하지 않는다.

개발용 installation identity는 실제 사용자의 검증된 클라우드 신원이 아니다. 실제 계정 연결·전환·사용자별 DB 관리·로그아웃 데이터 제거·보존 기간은 Phase 3B 전에 결정한다. 상세 화면의 주행 안전 제한을 저장소 복원이나 Fake 연결로 우회하지 않는다.

검증 명령과 실제 실행 결과, Room 기기 재오픈 검증 여부는 [Phase 3 보고서](../../android-vehicle/docs/PHASE3_REPORT.md)에 기록한다. JVM 저장소 테스트와 Android 실제 Room 실행을 구분한다.
