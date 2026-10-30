# CheckMate Phase 3A 구현·검증 보고서

기준일: 2026-10-31 (Asia/Seoul). 차량용 앱의 기존 기능을 보존하면서 공통 사고 계약, 로컬 영속 저장과 향후 동기화 경계를 구현했다. 실제 클라우드·모바일·AI 연동 완료 보고서가 아니다.

## A. 기존 코드 분석 결과

### Git 기준과 기존 변경 보존

| 항목 | 실제 확인 결과 |
| --- | --- |
| 로컬 및 원격 `vehicle-app` HEAD | `54f7164b1723d5ee78f218fede3b1299977f9cfb` |
| 원격 `main` HEAD | `c9a5ed6053308624ce47b93877a84fbbed61fcd7` |
| 공통 조상 | Phase 2 `b21967e3f6d55aa686229aa496388074d77dd0b2` |
| 원격 `main...vehicle-app` | vehicle 쪽 ahead 1 / behind 3, diverged |
| 오래된 로컬 `origin/main` | `3b12435935dce4eb9e940f884e5e9a49818578c8` |
| 작업 시작 시 변경 | 루트 README의 공식 아이콘·브랜드 설명 수정, 원본 `docs/assets/checkmate-icon.png` 미추적 |
| 병합 여부 | Phase 2 [PR #3](https://github.com/WhiteBearCode02/CheckMate/pull/3)는 2026-10-30 14:22:41 KST 병합, 열린 PR 0개 |

GitHub connector로 원격 브랜치·compare·PR·전체 트리를 읽기 전용 조회했다. main 쪽 세 커밋은 병합 이력이며 비교 파일 변경은 없었다. 작업 트리가 깨끗하지 않으므로 fetch·main 병합을 수행하지 않았다. reset·브랜치 변경·commit·push·PR 생성·merge도 하지 않았다. 기존 README 브랜딩과 런처 리소스 및 원본 이미지를 보존했다.

### 확인한 기존 구현

`AccidentRepository`, `InMemoryAccidentRepository`, `AccidentModels`, `AccidentStateMachine`, `DrivingSafetyPolicy`, `CheckMateViewModel`, `VehicleDataProvider`, `PleosVehicleProvider`, `AndroidPleosSdkSession`, `MockVehicleProvider`, Compose 화면·Navigation, 기존 shared 계약과 Phase 1·2 보고서·테스트를 확인했다.

- Phase 1: UUID 사고 생성·확인·오탐, 순서 있는 6단계 안전 안내, Mock 분석·결과 없음, 메모리 저장소, MVVM.
- Phase 2: 읽기 전용 Door API 초기화·조회·구독, 연결 시간 제한·재시도·해제, 데이터 신선도·권한·지원·출처 표시.
- 유지한 기능: 상태 전이, Mock 개발 모드, 실제 SDK 실패 시 자동 Mock 전환 금지, 안전 안내 오프라인 접근, 주행·정차 미확인 시 상세 기록·분석 차단.
- 개정 설계와 차이: 메모리 전용 기록, 생성 주체와 데이터 출처의 혼동 가능성, 블랙박스를 중심으로 한 스마트폰 설명, DB·인증·동기화 경계 부재를 필요한 범위에서 개선했다.

### 접근하지 못한 문서

요청한 데이터구조·기능명세서 v2.0, 기능개발·브랜치 워크플로우 v2.0, Pleos 개발준비 가이드 v2.0, GitHub·PR·코드리뷰 가이드 v2.1, 공모전 참가자 통합가이드 v1.1, 기획내용 개정안 v3.0, 차량·스마트폰 DB 연동설계서 v1.0의 **7개 원문은 찾지 못했다**. 로컬과 원격 main·vehicle-app·mobile-app 트리를 조사했으며 원격 응답의 truncated=false를 확인했다. 해당 원문을 읽거나 반영 완료했다고 주장하지 않는다. 이번 요구사항과 확인 가능한 기존 코드·문서를 기준으로 구현했다.

## B. 수정한 내용

### 모델과 계약

- 공통 식별자는 기존 UUID `eventId`를 유지한다. `accidentId`를 별도로 도입하지 않았다.
- `UserProfile`, `RegisteredDevice`, `AccidentSyncMetadata`, `EvidenceMetadata`, `InsuranceContact`, `AccidentRecord`를 추가했다.
- `EventOrigin`은 `VEHICLE_MANUAL`, `MOBILE_MANUAL`, `MOBILE_SENSOR`, `DEMO`이며 `DataSource.MOCK/PLEOS`와 별개다. 기존 enum 값·기존 API는 보존하고 `PHOTO`만 추가했다.
- `AccidentRecord.schemaVersion=1`, UUID, UTC ISO 8601, 원본 파일 없는 메타데이터를 공통 JSON으로 정의했다. 차량 샘플의 내부 epoch milliseconds는 wire에서 `collectedAt`·`lastUpdatedAt` UTC 문자열로 변환한다.
- 명시적 Gson DOM codec은 생성자 검증을 수행한다. 중복 키·잘못된 타입·숫자 범위·ID 참조·알 수 없는 enum/스키마 버전·과도한 깊이/크기를 거부한다. 알 수 없는 추가 키는 무시하며 재인코딩 시 보존하지 않는다. 버전 없는 이전 계약을 자동 정상 데이터로 간주하지 않는다.
- 원시 영상·사진·위치·기기 파일 URI·보험사 개인정보·인증정보를 사고 envelope에 추가하지 않았다. `EvidenceMetadata`만으로 파일 존재나 업로드 동의를 확정하지 않는다.

정식 계약: [shared/contracts](../../shared/contracts/README.md), [예제](../../shared/contracts/accident-event.example.json), [Schema](../../shared/contracts/accident-event.schema.json). 기존 [android-vehicle/shared](../shared/README.md)는 역사적 계약과 차량 표현 문서로 보존한다.

### 저장소·DB·동기화

```text
Compose → ViewModel → AccidentRepository → AccidentLocalDataSource → Room
                                   ↕ AccidentSyncStore (local version CAS)
                              SyncCoordinator
                                   ↕ Authentication / RemoteAccidentDataSource
                              Fake 구현·테스트만 제공
```

Room을 우선 검토해 기존 환경과 호환되는 2.6.1 Java Entity/DAO/Database 및 javac annotationProcessor로 적용했다. KSP·Kotlin·AGP·SDK 버전을 변경하지 않았다. Gson 2.11.0은 명시적 직렬화에만 사용하며 도메인 클래스를 reflection으로 역직렬화하지 않는다.

- `(owner_user_id,event_id)` 복합 키, 사용자 범위 조회, JSON payload에 이벤트·안내·분석 메타데이터·동기화 버전을 원자적으로 저장한다. 실제 미디어는 저장하지 않는다.
- Persistent Repository는 기존 InMemory 상태 머신을 재사용한다. 저장 완료 후 StateFlow를 발행하며 실패 시 이전 내용이 유지된다. 다른 Repository 인스턴스와 sync CAS도 직렬화하고 최신 저장 상태를 다시 확인한다.
- 손상·스키마 불일치·프로필 오류는 저장소 오류로 처리한다. 빈 DB로 초기화하거나 파괴적 migration fallback을 사용하지 않는다.
- 현재 앱의 사용자 UUID는 앱 내부 기록을 묶는 로컬 namespace이며 실제 로그인 계정이 아니다. 기기 UUID와 함께 비공개 설정에 유지하고 백업은 차단한다. 실제 계정 이관·페어링은 구현하지 않았다.
- Fake 인증은 발급자가 검증한 세션을 사용한다. Remote는 본문의 userId만 믿지 않고 세션 사용자 범위만 읽고 쓴다. Fake는 운영 인증·보안 규칙의 대체가 아니다.
- Remote revision CAS·payload와 연결된 멱등 operation ID·localVersion CAS로 재전송과 최신 편집을 보호한다. 일시 실패는 RETRY, 상충 내용은 CONFLICT, 접근·설정 오류는 BLOCKED로 구분한다.
- 시각 기반 덮어쓰기를 하지 않는다. 사고 확인/오탐·동시 증거·삭제 tombstone 충돌은 보존하며 자동 해결을 하지 않는다. 정상적인 여러 오프라인 단계의 전진은 허용하고 상태 역행·기존 안내/증거 삭제·최종 분석 교체·tombstone 부활은 거부한다.
- 외부 Remote 모드는 Coordinator에서 차단된다. 실제 앱에서 Fake sync를 자동 실행하지 않으며 Firebase·INTERNET 권한·WorkManager·파일 전송을 추가하지 않았다.

세부 사항: [연동 구조](../../docs/architecture/integration-overview.md) · [DB 설계](../../docs/architecture/database-design.md) · [동기화 정책](../../docs/architecture/sync-policy.md).

### 차량 UI

- Dashboard: 수동 사고 대응 진입, 기기 저장 상태·외부 동기화 미설정 표시, 선택적 블랙박스와 스마트폰 연동 역할 구분.
- 확인 화면: 수동 기록과 Demo 기록 문구 분리, 저장 중 중복 조작 제한.
- 기록 화면: eventId별 한 항목, 출처·생성 주체·동기화 상태·메타데이터 표시, 영상 원본의 실제 존재 여부는 미확인으로 표시.
- 분석 화면: 요청 상태·분석 가능 여부·유형·비율·근거와 Mock 표시 유지. 과실비율은 참고용이며 법적 확정 결과가 아님을 명시. 재시작 후 남은 미연동 분석 요청도 결과 없음으로 마무리할 수 있다.
- 실제 저장소 로드·쓰기는 IO dispatcher에서 실행한다. 로딩·실패 중 기록 변경을 막고 기본 안전 안내는 계속 허용한다. 복구된 사고의 과거 정차 스냅샷을 현재 정차 증거로 사용하지 않는다.

### 보안·비용 검사

민감 원문 로그·공개 미디어 URL·토큰/서비스 계정 파일·클라우드 설정을 추가하지 않았다. Room은 앱 전용 저장소를 사용하며 백업·기기 이전·평문 통신 차단을 유지한다. 별도 DB 암호화는 추가하지 않았다. 루팅·기기 탈취까지 보호하는 암호화 저장이라고 주장하지 않는다. 보험사 연락 정보는 모델만 정의하며 입력·전화·전송 기능은 구현하지 않았다.

이번 작업은 로컬 코드·무료 공개 의존성·Fake 테스트 범위다. 유료 API·Firebase 프로젝트·과금 설정·외부 개인정보 전송·배포를 활성화하지 않았다. 실제 인증·DB 보안 규칙·이용조건·비용은 Phase 3B 착수 전에 확인하고 별도 동의를 받아야 한다.

## C. 테스트 결과

수정 전: `testDebugUnitTest` **93개 통과**, 실패·오류·건너뜀 0, 2분 48초. 원본 XML을 로컬 `build/phase3-verification/baseline/`에 보관했다.

최종 검증: 
| 검증 | 결과 |
| --- | --- |
| testDebugUnitTest | **150개 통과**: 기존 93개 + 신규 57개, 실패·오류·건너뜀 0 |
| 신규 테스트 구성 | JSON codec 15개, Persistent Repository 13개, Fake sync·인증·통합 24개, ViewModel·안전·로딩 5개 |
| assembleDebug | 성공, `app/build/outputs/apk/debug/app-debug.apk` |
| lintDebug | **오류 0개, 경고 18개** |
| assembleDebugAndroidTest | 성공. 실제 Room 재오픈 테스트 소스의 APK 컴파일 확인 |
| 전체 Gradle 실행 | 10분 19초, 85개 task 중 64개 실행·21개 up-to-date |
| Room schema | javac processor가 `app/schemas/...AccidentDatabase/1.json`을 생성, version 1·accident_records 확인 |
| 공통 JSON | Draft 2020-12 정식 validator로 Schema·예제 통과, 잘못된 버전·UUID·비UTC 시각 거부. 실제 예제의 Kotlin codec 왕복도 JVM 테스트로 확인 |
| 안전·보안 | 패키징된 Manifest의 기존 권한·아이콘 참조, 백업/평문 차단, INTERNET 미추가, 민감 원문 로그·자격증명·업로드 구현 미추가 확인 |
| 아이콘 | 원본·런처 리소스 16개 SHA-256 동일 |
| 문서 | README 링크·앵커·Mermaid 2개 로컬 렌더링 검증, 최종 상대 링크 검사 |
| 실제 기기 | adb 기기 목록 0개. Room/Pleos/Compose 기기 실행 미수행 |

Lint 경고는 EmptySuperCall 1, OldTargetApi 1, RedundantLabel 1, NewerVersionAvailable 3, ObsoleteSdkInt 1, UnusedResources 7, MonochromeLauncherIcon 2, UseKtx 1, UseTomlInstead 1이다. 기존 SDK·아이콘 보존과 불필요한 버전 변경 금지 범위를 지켰다. 경고를 오류 없이 통과한 것으로 기록하며 경고가 없는 빌드라고 표현하지 않는다. 로컬 보고서는 `app/build/reports/lint-results-debug.html`, JVM 결과는 `app/build/reports/tests/testDebugUnitTest/index.html`에 있다.


검증 명령:

```powershell
cd D:\CheckMate\android-vehicle
$env:GRADLE_USER_HOME='C:\Users\dlgus\.gradle'
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest --offline --no-configuration-cache '-Pkotlin.compiler.execution.strategy=in-process' --max-workers=2
```

요청한 세 Gradle task와 추가 기기 테스트 APK 컴파일을 한 실행에서 수행했다. 초기 offline 실행은 `error_prone_annotations:2.27.0` jar가 캐시에 없어 컴파일 전에 중단됐다. 공개 Maven 의존성을 받는 실행 후, codec 타입 추론·ViewModel 참조 오류를 수정하고 최종 offline 실행에서 모든 검증을 통과했다. SDK·Java·Kotlin 버전은 유지했다.

JVM 디스크 재오픈 테스트는 파일 기반 LocalDataSource 테스트 대역과 실제 codec·Repository를 사용한다. **실제 Android Room/SQLite 재오픈 테스트와 구분한다.** `RoomAccidentPersistenceTest`는 소스만 추가했으며 실제 기기에서는 실행하지 않았다. Pleos·Compose 화면 런타임·기기 간 클라우드 통신도 검증하지 않았다.

## D. 미구현 및 후속 작업

Phase 3B는 인프라·운영 인증·모바일 연동 환경이 준비되지 않아 진행하지 않았다.

- Firebase 프로젝트·차량 환경 지원·계정 연결 방식·인증 약관 및 비용 검증. Firebase는 후보이며 확정하지 않았다.
- 서버가 검증한 인증 주체에 연결된 사용자별 DB·파일 접근 규칙, 원격 트랜잭션·계약 검증·보안 테스트.
- 실제 모바일 앱의 수동 신고·현장 증거 추가·동일 기록 조회·양방향 변경, 무선망 장애와 프로세스 종료 복구 테스트.
- 충돌 해결 UI·서버 중재, 보존/삭제·계정 이관·프로필 전환 정책, 백그라운드 동기화 스케줄과 재시도 예산.
- 원본 파일 보존·존재 확인·사용자 동의·비공개 업로드와 선택적 녹화. QR 페어링은 검토 단계다.
- 실제 AI 모델·입력 권한·분석 작업 ID·결과 버전·성능/한계 검증, 사고 보고서 생성·보관·내보내기.
- 실제 Room 재오픈 instrumentation, Pleos 전용 에뮬레이터·실기기, 운전 중 UX 검증. Door 데이터로 정차 판정을 추가하지 않는다.

## E. 팀원별 작업 안내

| 담당 | 전달 사항 |
| --- | --- |
| 차량용 | 공통 UUID eventId·schemaVersion·UTC 유지, 정차 미확인 제한 보존. Room 재오픈/저장 실패·SDK 재진입 실제 검증. 서버 연결은 승인 후 별도 어댑터 구현 |
| 스마트폰 | 루트 JSON Schema·예제를 기준으로 동일 사건을 구현. 수동 신고·증거 메타데이터·진행 상태·분석 상세를 우선 지원하고 녹화는 선택 기능으로 분리. ownerUserId만으로 권한을 부여하지 말고 인증·파일 소유권·동의를 확인 |
| AI | eventId를 유지한 입력·결과·오류 계약 합의. 미디어 원본을 JSON에 넣지 말고 허가된 파일 접근 경계를 사용. Mock/실제 결과·모델 버전·근거·판단 불가를 구분하며 비율 미제공을 임의 0:100으로 채우지 않음 |

다른 담당자의 브랜치와 프로젝트는 수정하지 않았다. 위 내용은 전달용 문서이며 도구로 팀원에게 메시지·PR을 보내지 않았다.

## F. GitHub 협업

변경 파일은 [PHASE3_CHANGED_FILES.txt](PHASE3_CHANGED_FILES.txt)에 정리한다. 기존 미커밋 README·아이콘 원본은 이 작업에서 생성한 것처럼 취급하지 않으며 커밋 검토 대상에 함께 포함한다. 원본 PNG가 저장소에 포함되지 않으면 GitHub README 이미지가 표시되지 않는다.

추천 커밋 메시지:

```text
feat(vehicle): prepare persistent accident records and cross-device sync
```

추천 PR 제목:

```text
feat(vehicle): establish CheckMate accident data persistence and sync foundation
```

PR 설명 초안:

```markdown
차량 사고 기록이 프로세스 종료 시 사라지던 문제를 앱 전용 Room 저장소로 해결하고,
기존 eventId를 유지하는 SDK 독립 JSON 계약과 향후 스마트폰 연동 경계를 추가합니다.
수동 사고 대응·오프라인 안전 안내와 기존 Pleos/Mock 및 정차 제한을 유지합니다.

Fake Auth/Remote와 Sync Coordinator는 사용자 격리·버전 CAS·멱등 재전송·충돌 보존을
검증합니다. 실제 클라우드·모바일·AI·영상 업로드는 활성화하지 않습니다.

검증: PHASE3_REPORT.md의 최종 테스트·Debug 빌드·Lint 결과 참조.
Room 실제 기기 재오픈 및 Pleos/Compose 런타임은 후속 검증입니다.
```

리뷰 확인 항목:

- 기존 93개 회귀 및 신규 테스트, Room Java annotationProcessor·내보낸 schema, SDK/Java 버전 유지.
- 저장 실패 시 이전 기록 보존, 프로세스 재생성 복구, 사용자/기기 식별의 로컬·운영 경계.
- 같은 eventId·operation ID 재수신, local/remote revision CAS, 상태 역행·삭제·증거 충돌 정책.
- 정차 불명 상태 상세 화면 차단, 안전 안내·수동 대응의 오프라인 접근, Mock/실제 출처 구분.
- 개인정보 로그·미디어 공개 URL·자격증명·자동 외부 전송 없음, 아이콘·README 원본 설명/기여 보존.

commit·push·PR 생성·merge는 수행하지 않았다.
