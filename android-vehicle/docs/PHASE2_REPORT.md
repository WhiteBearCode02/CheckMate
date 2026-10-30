# CheckMate Phase 2 구현 보고서

기준일: 2026-10-30 (Asia/Seoul) · 작업 브랜치: `vehicle-app` · 시작 커밋: `3481218`

## 1. 작업 전 확인과 보존

- Phase 1 모델·Provider·안전 정책·기존 테스트 49개와 세 문서를 확인했다. SDK 2.3.2, min/compile/target SDK 28/37/35, Java 17, Gradle 9.5.0, AGP 9.3.3을 유지했다.
- GitHub에서 Phase 1 PR #2의 병합과 최신 `main` `aa3ce2f141bb380ad133d727618253b474c19b37`을 확인했다. `vehicle-app...main` 비교는 merge commit 2개 차이, 변경 파일 0개로 소스 트리가 같았다. 로컬의 오래된 remote ref만 보고 최신 상태라고 판단하지 않았다.
- 작업 시작 시 `MainActivity.kt`가 미커밋 상태였으나 Git 필터를 적용한 내용은 HEAD와 동일했다(blob `1843dcd5dba01046f58552ff49021b7cd5657b0d`). 원본 바이트를 Git에서 제외되는 로컬 검증 폴더에 보존한 후 필요한 Factory·생명주기 연결만 변경했다.
- 작업 트리 보존과 동일 소스 비교 결과에 따라 자동 Merge·Reset을 하지 않았다. `main`·`mobile-app`·`ai-model` 브랜치, Push·PR·Release·원격 설정은 변경하지 않았다.

## 2. 구현 기능과 데이터 흐름

```text
MainActivity → ViewModel Factory → 명시적 Provider Factory
                                 ├─ PLEOS (Debug·Release 기본)
                                 │   └─ AndroidPleosSdkSession → Vehicle SDK → Door 읽기
                                 └─ MOCK (Debug에서 사용자가 직접 선택)

SDK capability / GET / listener → VehicleStateMapper → PleosVehicleProvider
                                  ↓                      ↓
                     출처·지원·권한·수집 시각·유효성 → StateFlow → ViewModel → Compose
```

### SDK 읽기 기능

`Vehicle(applicationContext).initialize()`, `getDoor()`, Door Position capability·GET·listener와 `Vehicle.release()`를 실제 의존성에 대해 컴파일한다. `ROW_1_LEFT` 영역, `AccessType.READ/READ_WRITE`, 해당 영역의 최소·최대 범위가 확인된 경우만 읽는다. 제어용 write API는 사용하지 않는다.

초기화 반환은 연결 완료가 아니다. SDK에는 사용할 수 있는 공식 public 서비스 연결 listener를 확인하지 못해, capability/read 응답을 서비스 응답 관측 근거로 분리했다. 이 증거 없이 `CONNECTED`를 전달하는 잘못된 세션은 `CONNECTING`으로 제한한다.

Door 알림은 변화가 있을 때만 올 수 있으므로 2초 간격 GET도 수행한다. 동시에 한 요청만 진행하고, capability와 GET 각각 5초 제한·요청 ticket으로 중복/지연 응답을 막는다. `null`·범위 밖 값은 현재값이 아니며 Door 원시 위치를 개폐율·속도·정차·안전 판정으로 바꾸지 않는다.

### 연결·해제·장애 대응

- SDK 초기화·capability·GET·listener 작업은 IO actor로 순서를 보장한다. 등록과 해제에는 같은 listener·area를 사용한다.
- 종료는 UI 콜백을 즉시 무효화하고 재조회·구독을 정리한다. 진행 중 응답은 제한된 유예 후 최선으로 해제하며 다음 초기화는 이전 Run의 해제 완료를 기다린다. 메인 스레드는 기다리지 않는다.
- 초기 시도와 자동 재시도 2회, 총 3회로 제한하며 간격은 500ms·1초다. 권한 거부·미지원은 자동 반복하지 않는다. 수동 재시도만 새 예산을 시작한다.
- Provider generation, 터미널 콜백 가드, 요청 ticket과 Run 수명으로 이전 연결·중복·종료 후 응답이 현재 상태를 덮어쓰지 못하게 한다.
- 실제 Provider가 `MOCK` 출처의 값을 전달하면 거부한다. 실제 연결 실패를 Mock 성공으로 바꾸지 않는다.

### 상태·대시보드·안전

데이터 종류·값·단위·출처·수집 시각·유효성·지원·권한·갱신 상태, SDK 초기화와 서비스 관측을 각각 표현한다. 기존 연결 enum은 유지하면서 `UNSUPPORTED`, `PERMISSION_DENIED`, `ERROR`, `UNKNOWN`을 추가했다.

5초 이상 오래된 값과 시간 역행은 만료로 취급하며 활성 상태에서 500ms 간격으로 검사한다. 만료 시 속도·정차 신뢰를 폐기하고 이전 값은 현재값과 구분한다. 오류에서는 실제 값과 수집 시각을 지우고 지원·권한 등 안전한 메타데이터만 유지한다.

정차 미확인·주행·양수 속도·오류·만료·백그라운드에서는 상세 기록·분석을 차단한다. SDK 초기화나 Door 정상 수신만으로 정차를 인정하지 않는다. 기본 안전 안내는 SDK 상태와 관계없이 사용할 수 있고 사고 이벤트를 임의로 생성하지 않는다.

Provider 생성은 Activity에서 분리했다. 화면 회전 시 ViewModel을 유지하고, 백그라운드 진입 시 연결·타이머를 정리하며 재진입 시 새 값을 기다린다. 프로세스 재생성 시 이전 정차 신뢰를 복원하지 않는다. 기존 사고 기록은 메모리 전용이므로 프로세스 종료 후 사라진다.

## 3. 공식 근거와 확인 범위

공식 [초기화·종료 문서](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object), [Vehicle Sample 가이드](https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk-sample-app), [typed Door Sample](https://github.com/Pleos-Playground/pleos-sample-vehicle-app/tree/959981d960ec71144accb27af5ae5d341e72a644)와 캐시된 SDK AAR의 공개 `javap` 시그니처·권한 annotation을 대조했다. 정확한 API·권한·6종 후보 비교·버전·참고 링크는 [별도 검증 가이드](PHASE2_PLEOS_TEST_GUIDE.md)에 있다.

SDK 의존성은 2.3.2, 공식 소개 문서는 Service 2.3.21, Sample은 Service 2.3.22와 AVD v2.1 기준이다. AAR 최소 Service 2.3.13은 런타임 기능 보증이 아니다. 실제 AVD·서비스 버전과 기능 지원은 별도 검증해야 한다.

## 4. 미구현·미지원·런타임 미검증

실제 SDK 호출 코드를 작성하고 컴파일한 것과 실제 Vehicle Service 연결·값 수신 성공은 구분한다. **이 PC에서는 Pleos Emulator, 실기기, 서비스 수신, 실제 Compose 화면을 실행하지 않았다.**

Brake·Turn Signal·Driving Mode·Odometer는 읽기 API·권한을 조사했지만 제품 어댑터에 연결하지 않았다. 현재 속도의 Pleos typed API는 확인되지 않았고, 크루즈 목표 속도를 현재 속도로 대체하지 않았다. AAOS 속도·`CarUxRestrictions` 실제 연동은 라이브러리·권한·환경 검증 전이므로 추가하지 않았다.

실제 충돌 감지, 카메라, 위치·영상 수집/보존, AI 학습·추론, 보험 접수, 긴급 신고, 서버·유료 API·차량 제어는 구현하지 않았다. 루트 README의 Phase 1 검증 수치는 과거 기준이며 이번 결과는 이 보고서로 관리한다.

## 5. 테스트·빌드·Lint 결과

최종 통합 실행은 **성공(1분 43초)**이며 아래 수치는 XML 10개와 최종 APK·Lint 산출물을 직접 확인한 결과다. 기존 Phase 1의 테스트 49개를 유지하고 새 테스트 44개를 추가했다. SDK typed API의 Kotlin 컴파일도 실제 의존성으로 통과했다.

| 항목 | 최종 결과 |
| --- | --- |
| `testDebugUnitTest` | **93개 통과**, 실패·오류·건너뜀 모두 0 |
| 기존 모델·저장소·예제 | 모델 9, 사고 저장소 12, 기존 예제 1 |
| ViewModel / 안전 정책 | 20 / 9 |
| Provider / 수명주기 / Factory | 10 / 18 / 4 |
| SDK 매핑 / 요청 ticket | 7 / 3 |
| `assembleDebug` | 성공, `app/build/outputs/apk/debug/app-debug.apk` |
| APK 크기 / SHA-256 | 35,246,763 bytes / `F59E27A9001FE1D88410BB802A16124052C0CDFA612D75D42AB1877A8ECE87B4` |
| `lintDebug` | **오류 0, 경고 17**, 검사 비활성화·버전 변경 없음 |
| 최종 packaged Manifest | `CAR_DOORS`, SDK `VEHICLE_BINDING`, 앱 전용 AndroidX signature 권한만 확인. `allowBackup=false`, `usesCleartextTraffic=false` |
| 정적 변경 검사 | `git diff --check` 통과, Gradle·SDK 설정 파일 변경 없음 |
| 문서·계약 검사 | Phase 2 문서·공통 계약 3개, 상대 경로·앵커 5개, JSON 예시 3개 통과 |
| 실제 Pleos·실기기·Compose 화면 | **미검증**, 별도 PC의 체크리스트로 관리 |

Lint 경고 17개는 빈 상위 함수 호출 1, 기존 target API 1, 중복 레이블 1, 기존 AGP·의존성 업데이트 권고 6, 기존 미사용 색상 7, 기존 Pleos 의존성의 Version Catalog 이동 권고 1이다. 검증 결과 파일은 `app/build/test-results/testDebugUnitTest/TEST-*.xml`, `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/lint-results-debug.html` 및 XML/SARIF다.

초기 통합 검사에서는 새 안전 조건을 만족하지 않는 재진입 테스트의 fake 입력 1개가 실패했다. 서비스 응답·초기화가 확인된 모의 입력으로 보완하고 원래의 안전 검증과 assertion은 유지했다. 별도로 Lint의 `ExperimentalDetector`가 Kotlin FIR 심볼 분석 중 내부 오류로 중단됐으나 최종 전체 재실행에서는 통과했다. 검사를 끄거나 Gradle·라이브러리를 업그레이드하지 않았으며, 이 도구 오류가 재현되면 분석기 로그를 기준으로 별도 조사해야 한다.

```powershell
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --offline --no-configuration-cache '-Pkotlin.compiler.execution.strategy=in-process' --max-workers=2
```

위 옵션은 검증 프로세스에만 적용하며 저장소 Gradle 설정을 변경하지 않는다. 실제 에뮬레이터 검증은 [체크리스트](PHASE2_PLEOS_TEST_GUIDE.md#9-실행-결과-기록표--현재-모두-미검증)에 별도로 남긴다.

## 6. 보안·개인정보·비용

추가 권한은 공식 Door 읽기의 `pleos.car.permission.CAR_DOORS` 하나다. 기존 SDK `VEHICLE_BINDING`과 AndroidX signature 권한은 유지하며 인터넷·차량 제어·비공개·위치·카메라 권한은 추가하지 않는다. 기존 백업·기기 이전·평문 통신 차단을 유지한다.

현재 실제 차량 데이터 처리 목적은 연결·지원·최신 값 표시 검증이다. 최신 값은 Provider/ViewModel 메모리에만 존재하고 백그라운드·교체·종료 때 폐기한다. 디스크·외부 서버·분석 서비스로 저장/전송하지 않는다. 앱은 SDK 예외 원문·실제 차량값·식별자·위치·이벤트를 로그에 기록하지 않는다. SDK 자체나 시스템 로그는 별도 검증·비식별 검토가 필요하다.

서버·유료 API·자동 신고·보험사 접수·배포·릴리스는 실행하지 않았다. 별도 비용이나 외부 데이터 전송을 발생시키는 기능을 활성화하지 않았다.

## 7. 발견한 위험과 후속 권장사항

1. SDK 초기화·서비스 응답·기능 지원·값 수신은 다른 상태다. 실제 환경에서 각각 증거를 남겨야 한다.
2. 공식 문서의 `VehicleException.errorCode`가 로컬 AAR에서는 난독화되어 있다. 내부명·reflection·메시지 파싱에 의존하지 않으므로 일부 SDK 오류는 상세 분류 대신 안전한 일반 오류가 된다.
3. SDK에는 조회 취소 API를 확인하지 못했다. 진행 중 요청에 5초 제한, 종료 시 1.5초 유예와 최선 해제를 적용했지만 SDK 내부의 binder·해제 자체가 멈추는 상황은 실환경 검증이 필요하다.
4. 실제 read-only Door 데이터는 정차 판단 근거가 아니다. 별도로 검증한 주행/UX 제한 입력이 생길 때까지 실제 모드의 상세 화면 차단을 유지해야 한다.
5. 벽시계 변경·프로세스 종료·화면 회전·권한 변경·서비스 중단·느린 콜백을 별도 AVD에서 확인한다. 동시 상태 처리는 fake session/JVM 검사만으로 실차 보증이 되지 않는다.
6. 모바일·AI와 확장 enum·메타데이터·전송 시각의 ISO 8601 표현·계약 버전을 합의하고, 영속 저장·접근 제어·보유 기간은 별도 Phase에서 구현한다.

## 8. 변경 파일

주요 변경은 실제 SDK 세션·순수 매퍼·요청 ticket, Provider 수명주기·재시도·신선도, 명시적 Factory, Dashboard·기본 안내·ViewModel, 회귀 테스트와 문서다. 시작 시 MainActivity의 미커밋 상태도 함께 보존했다. 아래의 전체 목록은 저장소 상태를 기준으로 기록한다.

## 9. 커밋·PR 초안

추천 커밋: `feat(vehicle): implement Pleos SDK integration and vehicle state management`

커밋 내용: 공식 Door 읽기 어댑터·매퍼, 연결·조회·구독·해제·재시도, 시각·유효성·출처·권한 메타데이터, 안전 제한과 대시보드, 명시적 Mock 선택, 회귀 테스트와 별도 환경 검증 문서.

PR 제목: `feat(vehicle): add Pleos vehicle state integration and safety handling`

PR 본문 초안: SDK 2.3.2의 공식 Door Position 읽기 API를 연결하고 초기화·서비스 관측·지원·권한·최신값을 분리합니다. 실제 연결 실패 시 자동 Mock 전환을 하지 않으며, 데이터 만료·주행·미확인 상태에서 상세 화면을 제한하고 기본 안전 안내는 유지합니다. 기존 49개를 포함한 JVM 테스트 93개, Debug APK 빌드, Android Lint(오류 0·경고 17)가 통과했습니다. Pleos Emulator·실차 연결 및 값 수신은 별도 PC에서 미검증입니다.

## 10. Phase 2 완료 기준

아래 체크는 코드·로컬 검사 완료를 뜻하며 실제 차량 환경 인증이나 수신 성공을 뜻하지 않는다.

- [x] Phase 1 기존 49개 테스트를 포함한 회귀 검사
- [x] SDK 초기화·종료·구독·요청·해제·재시도 계층
- [x] 공식 Door Position 읽기 API 연결 및 실제 SDK 시그니처 컴파일
- [x] 지원·권한·연결·데이터 없음·만료·오류 구분
- [x] 명시적 Mock/PLEOS 선택, 실데이터 가장·자동 Mock 전환 차단
- [x] Dashboard·마지막 수신·단위·출처·재시도·기본 안내
- [x] 정차 미확인·주행·오류·만료·백그라운드 안전 제한
- [x] 테스트 93개·Debug APK·Lint 결과 확인
- [x] 별도 PC 시험 가이드·공통 계약 확장·본 보고서
- [ ] Pleos 전용 Emulator에서 실제 서비스 연결·capability·GET·알림 수신 검증
- [ ] 실제 화면·권한·서비스 단절·회전·재진입·해제·UX 제한 검증

## 전체 변경 파일 목록

경로는 `android-vehicle/` 기준이다. M은 수정, ??는 신규이며 총 29개다.

```text
 M app/src/main/AndroidManifest.xml
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/MainActivity.kt
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/data/provider/MockVehicleProvider.kt
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/data/provider/PleosVehicleProvider.kt
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/data/provider/VehicleDataProvider.kt
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/domain/model/VehicleModels.kt
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/navigation/CheckMateScreen.kt
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/screen/CheckMateApp.kt
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/screen/DashboardScreen.kt
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/screen/UiLabels.kt
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/viewmodel/CheckMateUiState.kt
 M app/src/main/java/com/whitebearcode02/checkmate/vehicle/viewmodel/CheckMateViewModel.kt
 M app/src/test/java/com/whitebearcode02/checkmate/vehicle/CheckMateViewModelTest.kt
 M app/src/test/java/com/whitebearcode02/checkmate/vehicle/DrivingSafetyPolicyTest.kt
 M app/src/test/java/com/whitebearcode02/checkmate/vehicle/VehicleProviderTest.kt
 M shared/README.md
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/data/mapper/VehicleStateMapper.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/data/provider/AndroidPleosSdkSession.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/data/provider/SdkRequestTracker.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/data/provider/VehicleProviderFactory.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/data/provider/VehicleTimingPolicy.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/ui/screen/SafetyGuideScreen.kt
?? app/src/main/java/com/whitebearcode02/checkmate/vehicle/viewmodel/CheckMateViewModelFactory.kt
?? app/src/test/java/com/whitebearcode02/checkmate/vehicle/VehicleLifecycleTest.kt
?? app/src/test/java/com/whitebearcode02/checkmate/vehicle/VehicleProviderFactoryTest.kt
?? app/src/test/java/com/whitebearcode02/checkmate/vehicle/data/SdkRequestTrackerTest.kt
?? app/src/test/java/com/whitebearcode02/checkmate/vehicle/data/VehicleStateMapperTest.kt
?? docs/PHASE2_PLEOS_TEST_GUIDE.md
?? docs/PHASE2_REPORT.md
```
