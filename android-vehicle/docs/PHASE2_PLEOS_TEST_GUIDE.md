# Phase 2 Pleos 전용 환경 검증 가이드

작성일: 2026-10-30 (Asia/Seoul)

**이 문서는 별도 검증 PC에서 실행할 절차다. 현재 개발 PC에서는 Pleos Emulator 실행, Vehicle Service 연결, 차량값 수신, Compose 화면을 검증하지 않았다.** JVM 테스트·APK 빌드·Lint 결과는 [PHASE2_REPORT.md](PHASE2_REPORT.md)에 별도로 기록한다. 아래 런타임 체크 항목은 전부 미검증 상태다.

## 1. 버전과 검증 근거

| 항목 | 기준과 확인 범위 |
| --- | --- |
| CheckMate | minSdk 28 / compileSdk 37 / targetSdk 35 / Java 17, 기존 Gradle 설정 유지 |
| 실제 의존성 | `ai.pleos.playground:Vehicle:2.3.2`, 전이 의존성 `vehicle-data:1.1.11`, `vehicle-aidl:1.1.1` |
| SDK 소개 문서 | SDK 2.3.2 / Vehicle Service 2.3.21 기준 |
| 공식 Sample 가이드 | SDK 2.3.2 / Vehicle Service 2.3.22 / `Pleos_Connect_v2.1`, Android 14 기준 |
| SDK AAR Manifest | 최소 Service 2.3.13, versionCode 65. 이 최소값은 기능별 지원·호환성 보증이 아니다 |
| Virtual Console | 공식 안내의 최소 Pleos Connect Emulator 버전 2.1.0 |
| 실제 검증 PC | 실행 전에 설치된 AVD·Android·Service·SDK·APK 버전을 아래 양식에 기록 |

SDK AAR 파일의 로컬 SHA-256은 `E11FDFE498CAD3ECC0D5A2A6992B0C4A4D135195F1455018127BC069FD5EF937`이다. 공식 문서·Sample과 캐시된 SDK의 `javap` 공개 시그니처 및 `@RequiresPermission`을 대조했다. 실제 연결 성공을 확인한 것은 아니다. Sample 확인 기준 커밋은 [`959981d960ec71144accb27af5ae5d341e72a644`](https://github.com/Pleos-Playground/pleos-sample-vehicle-app/commit/959981d960ec71144accb27af5ae5d341e72a644)이다.

공식 자료:

- [Vehicle SDK 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
- [초기화·도메인 객체·종료](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object)
- [권한 선언](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)
- [오류 전달과 ErrorCode](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code)
- [공식 Vehicle SDK Sample 가이드](https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk-sample-app)
- [공식 Sample의 typed Door 예제](https://github.com/Pleos-Playground/pleos-sample-vehicle-app/blob/959981d960ec71144accb27af5ae5d341e72a644/README.md#how-to-use-the-typed-door-api)
- [Emulator 설치·실행](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [AVD 설정·GPU 요구사항](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)
- [Virtual Console 연결](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
- [Virtual Console 기능별 사용법](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide)

## 2. 우선 검토한 차량 정보 6종

다음 시그니처는 공식 API 목록과 로컬 SDK 2.3.2에서 존재를 확인했다. API 존재·Console 입력 카드 존재·CheckMate 구현·실제 수신 성공은 서로 다른 근거다. 권한 열은 확인한 **해당 Pleos API의 SDK annotation**을 기준으로 한다. Sample 전체 권한 목록을 복사하지 않는다.

| 차량 정보 | 확인한 읽기·지원 API / 갱신 | 확인한 권한 | Emulator 문서와 현재 구현·품질 한계 |
| --- | --- | --- | --- |
| Brake State | `Brake.checkBrakePedalStatusCapabilities`, `getCurrentBrakePedalStatus`; `registerBrakePedalStatus` / `unregisterBrakePedalStatus`; 콜백 `Boolean?` | `pleos.car.permission.CAR_DRIVE` | Console Motion에 Brake Pedal 카드가 있다. CheckMate 실제 연동 제외, 수신 미검증. 페달 상태는 정차·주차나 충돌의 증거가 아니다 |
| Turn Signal | `TurnSignal.checkTurnSignalStateCapabilities`, `getCurrentTurnSignalState`; `registerTurnSignalState` / `unregisterTurnSignalState`; `TurnSignalValue?` | `pleos.car.permission.CAR_LIGHTS` | Console Convenience에 Turn Signal State 카드가 있다. 실제 연동 제외·미검증. 의도나 사고 책임을 확정하지 않는다 |
| Driving Mode | `DrivingMode.checkDrivingModeStateCapabilities`, `getCurrentDrivingModeState`; `registerDrivingModeState` / `unregisterDrivingModeState`; `DrivingModeType?` | `pleos.car.permission.CAR_DRIVE` | 타입은 NORMAL / ECO / SPORT다. 주행 모드가 주행·정차 상태를 뜻하지 않는다. Console의 현재 Driving Mode 입력 지원은 확인하지 못함. 실제 연동 제외·미검증 |
| Odometer | `Odometer.checkCurrentDistanceDrivenCapabilities`, `getCurrentDistanceDriven`; `registerDistanceDriven` / `unregisterDistanceDriven`; `Float?` | `pleos.car.permission.CAR_DRIVE` | Console Motion에 Odometer 카드가 있다. 실제 연동 제외·미검증. Console의 km 표기만으로 SDK 결과 단위를 확정하지 않았으며, 사고 시점 고해상도 움직임 정보로 사용하지 않는다 |
| Vehicle Speed | 조사한 공개 Pleos SDK·Sample 목록에서 현재 속도를 읽는 제품용 typed API를 확인하지 못함. `getCruiseControlTargetSpeed`는 목표 속도이므로 대체하지 않음 | Pleos 권한 미선정·미추가. AAOS 후보는 별도 `Car.PERMISSION_SPEED` | [AAOS `PERF_VEHICLE_SPEED`](https://developer.android.com/reference/android/car/VehiclePropertyIds#PERF_VEHICLE_SPEED)는 m/s Float의 연속 갱신 속성이나 이 프로젝트에 AAOS Car 라이브러리·권한·어댑터를 추가하지 않았다. 실제 연동 제외·미검증 |
| Door / Safety State | **Door Position만 구현:** `Door.checkDoorPositionCapabilities`, `getCurrentDoorPosition`; `registerDoorPosition` / `unregisterDoorPosition`; `Int?`, 영역별 범위 | **`pleos.car.permission.CAR_DOORS`만 추가** | Console Convenience의 Door Position으로 검증할 예정. ROW_1_LEFT만 사용하고 capability의 읽기 접근·해당 영역·범위를 확인한다. Door 닫힘은 탑승자 안전·정차·주차 보증이 아니다. 기타 Safety API는 이번에 연결하지 않음 |

공식 도메인 목록: [Brake](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/brake/entry), [TurnSignal](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/turn-signal/entry), [DrivingMode](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/driving-mode/entry), [Odometer](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/odometer/entry), [Door](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/entry).

AAOS [`CarUxRestrictionsManager`](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager)의 현재 제한 조회·listener 구조도 조사했으나, 실제 AAOS 라이브러리와 권한·환경 검증이 없어 런타임 연동은 보류했다. 현재 앱의 `DrivingSafetyPolicy`가 미확인·만료·오류 상태에서 상세 화면을 차단한다.

## 3. 별도 검증 PC 준비

1. 공식 GPU 조건을 만족하는 PC에서 Android Studio와 SDK를 준비한다. 이 가이드는 현재 PC에서 AVD를 실행하도록 요구하지 않는다.
2. Android Studio의 `Tools > SDK Manager > SDK Update Sites`에서 공식 설치 안내의 Pleos Connect System Image 저장소를 추가한다. `SDK Platforms`에서 API 34 Pleos 이미지를 설치하고 Android Studio를 재시작한다.
3. `Tools > Device Manager > Create device > Automotive`에서 Pleos Connect v2.1.0을 선택하고 API 34 이미지를 지정한다. 부팅 옵션은 `Cold`, RAM은 공식 안내의 4 GB로 설정한다. 일반 Android AVD는 Vehicle Service 검증 환경이 아니다.
4. AVD를 실행한 뒤 부팅 완료와 ADB 연결을 확인한다. 버전과 기기 식별자는 로컬 검증 기록에만 보관하고, 공유 보고서에는 식별자를 제거한다.

```powershell
adb devices
adb -s <검증기기_SERIAL> shell dumpsys package ai.pleos.playground.service.vehicle
```

두 번째 명령의 `versionName`·`versionCode`와 AVD 설정을 로컬에서 확인한다. `dumpsys` 전체 출력을 저장소에 커밋하지 않는다. SDK AAR의 최소 서비스 버전만 보고 동작을 보증하지 말고, 설치된 버전으로 capability와 실제 값을 확인한다.

## 4. APK 빌드와 설치

```powershell
cd D:\CheckMate\android-vehicle
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
adb -s <검증기기_SERIAL> install -r .\app\build\outputs\apk\debug\app-debug.apk
adb -s <검증기기_SERIAL> shell am start -n com.whitebearcode02.checkmate.vehicle/.MainActivity
```

APK 위치는 `app/build/outputs/apk/debug/app-debug.apk`다. 실행한 빌드의 Git 기준, APK 생성 시각·SHA-256을 기록한다. SDK·Gradle 버전을 Sample 설정으로 바꾸지 않는다. `CAR_DOORS`는 Pleos 서비스의 normal 권한이므로 일반적인 위험 권한 팝업이 나타나는 방식이 아니다. 팝업 유무로 권한을 판단하지 말고 설치 APK의 Manifest·서비스 설치·실제 접근 결과를 함께 확인한다. 인터넷·차량 제어·비공개·위치·카메라 권한은 추가하지 않는다.

## 5. Virtual Console 연결과 Door 조회

공식 [Console 연결 안내](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)에 따라 로컬 포트를 연결한다.

```powershell
adb -s <검증기기_SERIAL> forward tcp:8088 tcp:8088
```

PC 브라우저에서 `http://127.0.0.1:8088/`을 열고 `Emulation Controls > Convenience > Door Position`에서 왼쪽 앞문 영역을 선택한다. Console은 별도 에뮬레이터 시험 도구이며 CheckMate의 네트워크 기능이 아니다. CheckMate는 차량 제어·write API를 호출하지 않는다.

1. CheckMate 대시보드가 `PLEOS · 실제 SDK 연결 시도`임을 확인한다. 기본 모드는 Debug·Release 모두 PLEOS다.
2. 초기화 완료 표시만으로 연결 성공을 기록하지 않는다. `initialize()` 뒤 `checkDoorPositionCapabilities` 응답을 받아야 서비스 응답이 관찰된 상태다. SDK 2.3.2에는 이 앱이 사용할 공식 public 서비스 연결 listener를 확인하지 못했으므로, 독립적인 binder 연결 완료 이벤트를 받았다고 주장하지 않는다.
3. capability 결과에서 `AccessType.READ` 또는 `READ_WRITE`, `DoorArea(DoorValue.ROW_1_LEFT)`, 정상적인 최소·최대 범위를 확인한다. 선택 영역·읽기 접근·범위가 없으면 해당 Door 데이터를 미지원 상태로 둔다.
4. 이후 `getCurrentDoorPosition(area, onComplete, onFailed)`의 값 수신과 대시보드의 마지막 수신 시각을 확인한다. `null`·범위 밖 값은 현재 차량값으로 표시하지 않는다. 숫자 0은 실제 수신 값일 수 있지만 속도 0이나 정차 판정으로 사용하지 않는다. 표시 단위는 SDK Door 위치 단계이며 임의의 %로 변환하지 않는다.
5. Console에서 해당 문을 열고 닫으며 값 변경을 관찰한다. listener 등록 호출 반환은 알림 수신의 증거가 아니므로 실제 변경 알림과 GET 결과를 구분해 기록한다. 범위를 `0..1`로 고정하거나 다른 영역에서도 같다고 가정하지 않는다.

구현된 비동기 호출 순서는 다음과 같다.

```text
서비스 존재·읽기 권한 확인
→ Vehicle(applicationContext).initialize()
→ getDoor().checkDoorPositionCapabilities(onComplete = { config, areaMap -> ... })
→ getCurrentDoorPosition(ROW_1_LEFT, onComplete = { Int? -> ... })
→ registerDoorPosition(동일 area, 동일 DoorPositionListener)
→ 2초마다 GET 재조회(동시에 한 요청만 진행)
→ unregisterDoorPosition(등록 당시 area와 listener)
→ Vehicle.release()
```

Door 알림은 상태가 바뀔 때만 올 수 있으므로, 변화가 없어도 2초 간격 GET으로 수신 시각을 갱신한다. capability·각 GET에는 5초 응답 제한이 있다. 이전 요청·중복 콜백은 ticket으로 무시한다. 데이터는 5초 이상 오래되면 만료 처리하며 500ms 주기로 검사한다. 시간 역행도 안전하게 만료로 처리한다.

종료 시 UI 콜백을 즉시 무효화하고 재조회·listener를 정리한다. 진행 중 응답을 기다려 종료하되, 끊어진 서비스가 응답하지 않으면 최대 1.5초 뒤 SDK를 최선으로 해제한다. 새 연결의 초기화는 이전 세션의 정리가 끝난 뒤 시작하므로 빠른 재시도에서도 Vehicle 인스턴스 초기화가 겹치지 않는다. 이는 정상 응답 전에 release하지 말라는 공식 원칙에 더해, 무한히 남는 세션을 막기 위한 장애 시 종료 제한이다. 실제 해제·늦은 콜백 동작은 별도 AVD에서 확인해야 한다.

## 6. 실제 모드와 명시적 Mock 모드

| 상황 | 확인할 동작 |
| --- | --- |
| SDK 서비스 없음 | 미지원 또는 연결 불가 표시, 값 없음, 자동 Mock 전환 없음 |
| PLEOS 연결 실패 | PLEOS 출처 유지, 정차로 추정하지 않음, 기본 사고 대응 안전 안내 사용 가능 |
| Debug에서 직접 선택 | `테스트용 Mock 모드 직접 선택`으로 전환, `MOCK · 사용자가 선택한 테스트 데이터` 표시 |
| 실제 모드로 복귀 | `실제 Pleos SDK 연결 시도로 전환` 선택, Mock 값과 수신 시각 제거 후 새 SDK 세션 |
| Release | Mock 선택과 개발 시나리오 버튼 없음 |
| 주행 중 | Mock 전환·복잡한 시나리오 조작 제한 |

연결·SDK 일시 오류와 응답 제한은 최초 시도와 자동 재시도 2회, 총 3회까지 처리한다. 자동 재시도 간격은 500ms와 1초다. 미지원·권한 거부는 자동으로 반복하지 않는다. `차량 연결 재시도`를 직접 누르면 예산을 다시 시작한다. 이 과정에는 서버·유료 API·외부 전송이 없다.

## 7. 주행 제한·중단·복구 시험

실제 SDK 어댑터는 Door만 읽으므로 정상 수신 중에도 속도·정차는 확인 불가이며 상세 기록·분석 화면은 차단된다. Console의 Gear P, 문 닫힘 또는 크루즈 목표 속도를 근거로 제한이 풀리면 실패다. 현재 구현은 차량 사고 감지·보험 전송·자동 신고를 수행하지 않는다.

Debug에서 명시적으로 Mock을 선택한 다음 연결 성공, 지연, 실패, 권한 거부, 미지원, 갱신 중단, 연결 도중 해제, 재연결 성공, 주행, 정차 미확인 시나리오를 확인한다. Mock 성공이 실제 서비스 성공의 근거가 되지 않도록 출처를 계속 확인한다.

별도 AVD에서는 서비스 재시작·연결 중단을 검증 운영자가 허용한 에뮬레이터 시험 방법으로 재현하고, 연결 실패·5초 제한·오래된 값 차단·수동 재시도를 확인한다. 실제 차량의 서비스를 중단하거나 권한을 바꾸지 않는다. 서비스 단절 재현이 불가능한 환경은 미검증으로 남기고 fake session/JVM 결과와 구분한다.

백그라운드 전환·화면 회전·앱 종료·재시작도 각각 시험한다. 화면 회전으로 listener가 중복 등록되지 않아야 하고, 백그라운드·Provider 교체 시 SDK 작업이 정리되어야 한다. 프로세스 재생성 시 이전 정차 표시를 복원해 상세 화면을 허용하면 안 된다. 사고 이력은 현재 메모리 저장이므로 프로세스 종료 후 사라지는 것이 현재 제한이다.

## 8. 오류 진단과 Logcat

| 증상 | 점검·조치 |
| --- | --- |
| 서비스 미지원 | Pleos 전용 API 34 AVD와 Vehicle Service 설치 여부 확인. 일반 AVD에서 값이 없는 것은 성공이 아님 |
| 권한 거부 | 빌드 APK의 `pleos.car.permission.CAR_DOORS` 확인, 서비스 설치 상태 점검 후 다시 설치. write·비공개 권한 추가로 우회하지 않음 |
| 초기화 실패 | 실제 SDK·서비스 버전, AVD 부팅 완료, 클래스 호환성을 확인하고 수동 재시도 |
| 지원 여부 응답은 있으나 읽기 불가 | ROW_1_LEFT의 실제 범위·읽기 접근·현재 값 응답을 따로 확인. 다른 문·Door Lock 성공을 대신 증거로 사용하지 않음 |
| 5초 응답 제한·반복 실패 | 앱·AVD 상태와 서비스 버전 확인. 재시도 3회 종료 후 무한 반복이 없는지 확인 |
| 값 없음·오래된 데이터 | 수신 시각, 2초 GET, listener 수신을 구분. 화면의 값·상태를 현재 정보로 표시하지 않아야 함 |
| Console 접속 불가 | ADB 대상, AVD 2.1.0 이상 여부, 8088 포트 충돌과 forward 상태 확인 |

Android Studio `Logcat`에서 해당 앱 패키지(`com.whitebearcode02.checkmate.vehicle`)와 ERROR 수준을 필터링하여 크래시·서비스 오류의 존재를 로컬에서 확인한다. CheckMate 어댑터는 SDK 예외 원문·차량 식별자·사건·위치·영상·실제 차량값을 로그에 추가하지 않는다. SDK 자체 로그·시스템 크래시에는 원문이 섞일 수 있으므로 로그 전체를 커밋·업로드하지 않고, 공유 전 민감값을 제거한 오류 분류와 재현 단계만 기록한다.

공식 문서는 `VehicleException.errorCode`를 설명하지만 로컬 SDK 2.3.2는 해당 클래스·접근자가 난독화되어 있다. 제품 코드는 난독화된 내부명이나 reflection, 임의의 메시지 파싱에 의존하지 않는다. `SecurityException`·현재 권한 확인·`UnsupportedOperationException`·요청 제한을 공통 오류로 변환하며 분류 불가능한 SDK 오류는 안전한 일반 오류로 표시한다. Manifest 권한이 허용되어도 서비스가 반환한 오류의 상세 분류를 확정할 수 없는 경우가 있음을 기록한다.

## 9. 실행 결과 기록표 — 현재 모두 미검증

| 기록 항목 | 실행 후 작성 |
| --- | --- |
| 검증 날짜·검증자 / 로컬 기준 커밋 | 미검증 |
| PC GPU / AVD·Android 버전 / 화면 크기·밀도 | 미검증 |
| SDK / Service versionName·versionCode / APK SHA-256 | 미검증 |
| capability 접근·선택 영역·실제 최소·최대 | 미검증 |
| 최초 실제 GET 값 수신·시각 / 변경 알림 수신 | 미검증 |
| 중단·재시도·해제·복구 결과 / 민감값 제거한 증거 위치 | 미검증 |

- [ ] PLEOS가 기본이며 초기화 성공만으로 연결됨을 표시하지 않는다.
- [ ] capability 응답, 읽기 접근, ROW_1_LEFT 범위, GET 실제 수신을 따로 확인한다.
- [ ] Console 문 상태 변경과 실제 GET/알림 값을 대조한다.
- [ ] 2초 재조회 중 동일 값의 수신 시각 갱신, 단일 요청, 5초 응답 제한을 확인한다.
- [ ] 값 없음·범위 오류·5초 만료·시간 변경에서 현재값과 상세 진입이 안전하게 차단된다.
- [ ] 권한 거부·SDK 미지원·연결 오류가 앱을 종료시키지 않는다.
- [ ] 자동 시도는 총 3회, 수동 재시도 후에만 새 예산으로 시작한다.
- [ ] 실제 연결 실패가 Mock 출처나 가짜 정차·속도를 생성하지 않는다.
- [ ] Debug 명시적 Mock 선택과 Release 개발 도구 제거를 확인한다.
- [ ] Mock의 11개 차량 시나리오와 상세 화면에서 주행·미확인 상태로 바뀌는 경우를 확인한다.
- [ ] 회전 시 중복 초기화·listener 등록이 없고 백그라운드·Provider 교체·종료 시 정리된다.
- [ ] 동일 listener·area로 해제되며 종료·제한 시간 이후의 늦은 콜백이 UI에 반영되지 않는다.
- [ ] Door 정상 수신이어도 정차가 확인되지 않아 상세 화면을 열지 않는다.
- [ ] SDK 연결과 무관하게 기본 사고 대응 안전 안내가 열린다.
- [ ] 앱 재시작 시 정차 상태는 미확인이고 이전 메모리 사고 기록이 복구되지 않는다.
- [ ] 외부 전송·유료 서비스·차량 제어·권한 과다 선언·민감값 로그가 없는지 확인한다.

실패·수행 불가·지원되지 않는 항목은 체크하지 않고 원인을 기록한다. 화면 캡처와 로그는 가상 시험 데이터만 사용하고 개인정보를 제거한 뒤 로컬 검토한다. 실제 환경 검증 완료는 이 표와 증거를 작성한 후에만 보고한다.
