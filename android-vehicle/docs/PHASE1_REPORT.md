# CheckMate 차량 앱 Phase 1 구현 보고

작업 브랜치: `vehicle-app` · 기준일: 2026-10-30

## 작업 전 확인

- Git 작업 트리는 깨끗했고, 기존 코드는 Activity 내부의 단순 모의 사고 화면이었다.
- 기존 compileSdk 37, targetSdk 35, minSdk 28, Java 17, Pleos SDK 2.3.2 설정을 유지했다.
- 기존 공통 `shared/` 계약은 없었다. 이번 계약 초안을 `android-vehicle/shared/README.md`에 추가했다.
- GitHub에서 열린 PR 조회 결과는 0건이었다. 다른 브랜치 변경, Push, PR 생성·Merge는 수행하지 않는다.

## 변경 파일 및 기능

| 파일 / 경로 | 변경 내용 |
| --- | --- |
| `app/src/main/java/.../MainActivity.kt` | Compose 진입점, ViewModel 생성 및 상태 구독 |
| `app/src/main/java/.../domain/model/AccidentModels.kt` | 주행 세션·사고·증거·예측·대응·보고서 및 ID/값 검증 |
| `app/src/main/java/.../domain/model/VehicleModels.kt` | 차량값·연결·출처·지원 기능·안전 오류 코드 |
| `app/src/main/java/.../domain/AccidentStateMachine.kt` | 허용된 사고 상태 전환 검증 |
| `app/src/main/java/.../domain/DrivingSafetyPolicy.kt` | 확인된 정차 상태의 상세 화면 접근 정책 |
| `app/src/main/java/.../data/provider/VehicleDataProvider.kt` | 차량 데이터 및 생명주기 공통 API |
| `app/src/main/java/.../data/provider/MockVehicleProvider.kt` | 연결 성공·실패·값 없음·미지원·주행 시나리오 |
| `app/src/main/java/.../data/provider/PleosVehicleProvider.kt` | SDK 세션 경계, 미연동 기본값, 실패·구독 종료 처리 |
| `app/src/main/java/.../data/repository/AccidentRepository.kt` | 사건 ID 생성·스냅샷·오탐 이력·대응 순서·분석 결과 관리 |
| `app/src/main/java/.../viewmodel/` | 화면 상태, 사용자 명령, provider/repository 연결 |
| `app/src/main/java/.../ui/navigation/CheckMateScreen.kt` | 화면 구분 |
| `app/src/main/java/.../ui/component/VehicleComponents.kt` | 공통 카드·버튼 |
| `app/src/main/java/.../ui/screen/` | 홈·확인·안전 대응·기록·AI 결과·표시 문구 |
| `app/src/main/java/.../ui/theme/` | 다크 네이비·블루·주황 경고, 큰 글자 스타일 |
| `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/` | 최소 권한, 백업·기기 이전·평문 통신 차단 |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | ViewModel/Compose 생명주기 의존성 및 개발 빌드 구분 |
| `app/src/test/java/.../` | provider·모델·상태 전환·저장소·ViewModel 단위 테스트 |
| `shared/README.md` | 공통 타입·UUID·ISO 8601·JSON 예시·검증 규칙 |
| `docs/PLEOS_INTEGRATION.md` | 공식 SDK 조사 근거와 실제 어댑터 후속 작업 |
| `docs/PHASE1_RUNTIME_CHECKLIST.md` | 별도 PC의 미실행 화면·런타임 검증표 |

위 표의 Java 경로 `...`는 `com/whitebearcode02/checkmate/vehicle`이다. 최종 변경 파일 전체는 `git status --short`로 확인한다.

## 아키텍처 및 데이터 흐름

```text
VehicleDataProvider → StateFlow<VehicleState> ─┐
                                             ├→ CheckMateViewModel → CheckMateUiState → Compose 화면
AccidentRepository → StateFlow<StoreState> ───┘         ↑
       ↑                                               │
상태 머신·공통 모델 ← 사용자 명령 API ← CheckMateActions ┘
```

사고 버튼은 UUID·UTC 시각·현재 연결 스냅샷을 생성한다. 같은 활성 사건에 대한 중복 입력은 ID를 새로 만들지 않는다. 오탐은 종료된 이력으로 보존하며 현재 감시 상태는 대기로 돌아간다. 확인된 사건은 안전 안내를 순서대로 확인한 뒤 분석 대기·완료/사용 불가로 진행한다. 예측값은 개발자가 선택한 가상 예시에서만 생성하며 실제 AI 결과로 표시하지 않는다.

## 검증 결과

2026-10-30 최종 소스 기준으로 다음 검증을 완료했다. JVM 결과는 XML 6개를 합산했으며 새 테스트 48개와 기존 테스트 1개가 포함된다.

| 항목 | 현재 보고 상태 |
| --- | --- |
| `./gradlew.bat testDebugUnitTest` | 성공 — 모델 9, Provider 10, 저장소 12, 안전 정책 5, ViewModel 12, 기존 예제 1 |
| `./gradlew.bat assembleDebug` | 성공 — 테스트와 함께 4분 12초, 44개 작업 실행 |
| `./gradlew.bat lintDebug` | 성공 — 오류 0개, 경고 17개, 2분 |
| JVM 테스트 수 / 실패 수 | 49 / 0 — 오류·건너뜀도 0 |
| 공통 계약 JSON 예시 | 3개 모두 파싱 성공 |
| `git diff --check` | 통과 |
| 최종 packaged Manifest | 인터넷·CAR_DRIVE·CAR_LIGHTS·CONTROL/PRIVATE 권한 없음, 백업 및 평문 통신 비허용 확인 |
| APK | `app/build/outputs/apk/debug/app-debug.apk`, 35,030,122 bytes, 2026-10-30 02:01:50 KST 생성 |
| Pleos Emulator·실기기·Compose 화면 검증 | 미실행 — 별도 PC에서 수행 |

실행 명령은 다음과 같다. 기존 프로젝트 설정 파일의 컴파일 전략이나 캐시 설정은 바꾸지 않고 검증 프로세스에만 옵션을 적용했다. Lint는 도구가 로컬 캐시에 없어 무료 Maven 저장소에서 의존성을 받은 후 실행했다.

```powershell
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
.\gradlew.bat testDebugUnitTest assembleDebug --offline '-Pkotlin.compiler.execution.strategy=in-process' --max-workers=2
.\gradlew.bat lintDebug --no-configuration-cache '-Pkotlin.compiler.execution.strategy=in-process' --max-workers=2
```

APK SHA-256: `B467888E52C599F2EAFC5492036C71E22842FAC864FB598D710B43A6196C91C8`

검증 파일: `app/build/test-results/testDebugUnitTest/TEST-*.xml`, `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html` 및 XML/SARIF 보고서.

Lint 경고 17개는 빈 상위 함수 호출 1개, 기존 targetSdk 관련 1개, 중복 앱 레이블 1개, AGP/의존성 업데이트 권고 6개, 기존 미사용 색상 7개, 기존 Pleos 의존성의 버전 카탈로그 이동 권고 1개다. 요청에 따라 SDK·Gradle·Pleos 버전을 일괄 변경하거나 기존 리소스를 삭제하지 않았다. Debug 패키징에는 `libandroidx.graphics.path.so`의 심볼 제거 불가 경고도 있었으며 라이브러리를 그대로 포함해 빌드는 성공했다.

초기 검증에서는 Kotlin daemon 세션 오류가 발생해 컴파일 프로세스 옵션을 조정했다. PowerShell이 따옴표 없는 `-P` 인자를 분리한 실행과 Lint 도구가 캐시에 없는 오프라인 실행은 실패했으며, 올바른 인자 및 별도 Lint 실행으로 해결했다. 정차 정책 강화 중의 중간 테스트 실패는 최종 전수 검사에서 재현되지 않았다. 위 표는 최종 성공 실행의 실제 결과다.

## 보안·비용 및 발견한 위험

사용하지 않는 차량 권한을 제거하고 앱 백업·기기 이전·평문 통신을 차단한다. 인터넷 권한, 유료 API, 클라우드, 외부 데이터 전송, 자동 신고·보험 접수 기능은 추가하지 않는다. SDK 예외 원문과 증거 파일 참조를 로그나 안내 문구에 노출하지 않는다. 증거·대응·AI 결과는 부모 사건 ID와 일치하도록 검증한다.

최종 Manifest에는 기존 SDK의 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING`과 AndroidX의 앱 전용 signature 권한이 남는다. 권한이 전혀 없는 APK라는 의미는 아니다. 실제 SDK 세션 어댑터는 `start/close`를 비차단 방식으로 구현하고 종료 시 콜백 스레드를 기다리지 않아야 한다. 해당 실제 어댑터는 이번 단계에서 연결하지 않았다.

기존 화면의 Activity 내부 상태는 화면 재생성 시 사건 진행을 유지하기 어려웠으며 ViewModel로 이전했다. 실제 정차 여부 미확인 상태를 허용으로 해석하지 않도록 상세 화면 정책을 분리했다. 실제 SDK를 검증하지 않은 상태에서 연결 성공이나 지원 기능을 표시하는 위험을 피하도록 기본 Pleos 세션을 미연동으로 처리했다.

## 미구현 및 제한

실제 차량 SDK 어댑터·스마트폰 연결·카메라 녹화·사고 감지·영상/위치 수집·실제 AI 추론·보험 접수·긴급 신고·서버/인증은 구현하지 않았다. 기록은 앱 프로세스 메모리에만 존재하며 프로세스 종료 후 복구되지 않는다. JSON 문서는 계약 초안이며 네트워크 직렬화·서버 API·팀 합의가 완료된 상태는 아니다. 실제 SDK 예제의 권한·API·서비스 버전을 별도 검증 없이 제품에 적용하지 않는다.

## 다음 Phase

1. 별도 고사양 PC에서 Compose 화면과 Pleos AVD 런타임을 검증하고, 필요한 읽기 전용 SDK 어댑터를 구현한다.
2. 모바일·AI 담당자와 계약 버전·실제 데이터 출처·파일 참조 전달·중복 수신·실패 의미를 합의한다.
3. 앱 전용 영속 저장소, 증거 파일 접근 제어·보관 기간·삭제/복원 정책을 구현한다.
4. 실제 데이터의 신뢰도·부족한 입력·모델 버전·판단 불가 규칙을 포함한 AI 연동을 검증한다.
5. 외부 배포·외부 전송·유료 서비스·법적 책임이 생길 수 있는 실제 접수 기능은 사전 설명과 사용자 동의를 받은 후 진행한다.

## 추천 커밋 및 PR 설명

추천 커밋 제목: `feat(vehicle): implement phase 1 accident assistance foundation`

커밋에 포함할 내용: MVVM 구조와 공통 사고 모델, Mock/Pleos provider 경계, 모의 사고 상태 전환, 안전 대응·기록·AI 화면, 주행 제한, 최소 권한 및 백업 차단, 단위 테스트와 계약·검증 문서.

PR 제목: `feat(vehicle): add phase 1 vehicle dashboard and accident flow`

PR 본문 초안: 기존 단일 모의 화면을 MVVM 구조로 분리하고, 차량 연결 시나리오·사고 확인/오탐·6단계 안전 안내·사고 기록·Mock/미연동 AI 결과 화면을 추가합니다. 상세 화면은 정차 확인 시에만 허용하고, 실제 SDK·녹화·AI·외부 전송은 미연동으로 표시합니다. JVM 테스트 49개, Debug APK 빌드, Android Lint(오류 0·경고 17)가 통과했습니다. 기록은 메모리 전용이며 Pleos Emulator 및 실기기 런타임은 별도 PC에서 검증할 예정입니다.

## 전체 변경 파일

작업 전 변경은 없었다. 아래 경로는 `android-vehicle/` 기준이며 M은 수정, ??는 신규 파일이다.

```text
 M app/build.gradle.kts
 M app/src/main/AndroidManifest.xml
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/MainActivity.kt
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/theme/Color.kt
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/theme/Theme.kt
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/theme/Type.kt
 M app/src/main/res/xml/backup_rules.xml
 M app/src/main/res/xml/data_extraction_rules.xml
 M gradle/libs.versions.toml
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/data/provider/MockVehicleProvider.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/data/provider/PleosVehicleProvider.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/data/provider/VehicleDataProvider.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/data/repository/AccidentRepository.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/domain/AccidentStateMachine.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/domain/DrivingSafetyPolicy.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/domain/model/AccidentModels.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/domain/model/VehicleModels.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/component/VehicleComponents.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/navigation/CheckMateScreen.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/screen/AnalysisScreen.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/screen/AssistanceScreen.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/screen/CheckMateApp.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/screen/ConfirmationScreen.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/screen/DashboardScreen.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/screen/RecordsScreen.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/screen/UiLabels.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/viewmodel/CheckMateUiState.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/viewmodel/CheckMateViewModel.kt
?? app/src/test/java/com/whitebearcode02/checkmate/vehicle/AccidentModelsTest.kt
?? app/src/test/java/com/whitebearcode02/checkmate/vehicle/AccidentRepositoryTest.kt
?? app/src/test/java/com/whitebearcode02/checkmate/vehicle/CheckMateViewModelTest.kt
?? app/src/test/java/com/whitebearcode02/checkmate/vehicle/DrivingSafetyPolicyTest.kt
?? app/src/test/java/com/whitebearcode02/checkmate/vehicle/VehicleProviderTest.kt
?? docs/PHASE1_REPORT.md
?? docs/PHASE1_RUNTIME_CHECKLIST.md
?? docs/PLEOS_INTEGRATION.md
?? shared/README.md
```
