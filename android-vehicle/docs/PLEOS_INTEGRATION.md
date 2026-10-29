# Pleos 연동 경계와 후속 검증

확인일: 2026-10-30 (Asia/Seoul)

## Phase 1의 실행 범위

기존 `ai.pleos.playground:Vehicle:2.3.2` 의존성과 SDK 설정을 유지한다. 앱의 개발 화면은 `MockVehicleProvider`를 사용한다. `PleosVehicleProvider`는 교체 가능한 SDK 세션 경계와 실패·종료 처리를 제공하며, 기본 세션은 **미연동/사용 불가**를 반환한다. 실제 차량값, 실제 충돌 신호, Vehicle Service 연결 성공을 주장하지 않는다.

이 문서는 후속 실제 어댑터 구현을 위한 조사 기록이다. 문서에서 API 이름을 확인한 것과 해당 PC에서 Pleos 런타임을 검증한 것은 구분한다. 이번 작업에서는 Pleos Emulator를 실행하지 않았다.

## 공식 자료에서 확인한 내용

- [Vehicle SDK Introduction](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro): SDK 2.3.2, Vehicle Service 2.3.21 기준. 차량 통신은 IPC를 사용하며 capability/get 결과가 비동기로 전달된다.
- [Obtaining a domain object](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object): `Vehicle(context)`, `initialize()`, `getDoor()`와 `release()`의 생명주기 예시를 확인했다. 이것만으로 속도·기어·충돌 신호의 읽기 API가 검증되지는 않는다.
- [Vehicle SDK Sample App Guide](https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk-sample-app): SDK 2.3.2, Vehicle Service 2.3.22 기준이며 전용 Pleos Connect AVD가 필요하다. 초기화 완료 표시는 서비스 연결이나 개별 기능 지원의 증거가 아니다. 필요한 권한만 선언하고, 등록 때의 리스너와 영역으로 구독을 해제해야 한다.
- [공식 Vehicle Sample 저장소](https://github.com/Pleos-Playground/pleos-sample-vehicle-app): Door Position이 typed API 참조 구현이다. 기능 탐색용 reflection 코드는 제품용 차량 데이터 어댑터에 복사하지 않는다.

소개 문서와 Sample의 Vehicle Service 기준 버전이 다르므로, 별도 검증 PC의 실제 SDK·서비스·AVD 버전을 함께 기록해야 한다. Sample의 Gradle·SDK 버전으로 CheckMate의 정상 동작 중인 빌드 설정을 교체하지 않는다.

## 실제 어댑터 구현 순서

1. SDK 2.3.2의 공개 API와 필요한 읽기 기능을 공식 문서·Sample 소스에서 대조한다. 속도 0만으로 정차·주차를 확정하거나 충돌 감지 지원을 가정하지 않는다.
2. 필요한 읽기 권한만 선언한다. 차량 제어 API와 `CONTROL_*`, `PRIVATE_*` 권한은 이 앱의 Phase 1 범위에 넣지 않는다.
3. SDK 초기화, 서비스 연결, 기능 지원, 값 수신을 별개 상태로 전달한다. 권한 오류·미지원·값 없음·연결 실패도 구분한다.
4. SDK 콜백에서 immutable `VehicleState`로 변환한다. 종료 이후 콜백을 무시하고, 진행 중 요청·구독의 정리 순서를 확인한다.
5. 화면 생명주기가 끝날 때 구독을 해제하고 세션을 종료한다. 연결 실패가 앱 프로세스 종료로 이어지지 않는지 확인한다.
6. 전용 AVD에서 연결·지원 여부·실제 값·재연결·중복 해제·종료 후 콜백을 검증한다. JVM 테스트 및 APK 빌드 결과와 따로 보고한다.

## 보안·비용 경계

Phase 1은 네트워크 서비스, 인증, 유료 API, 자동 신고, 보험사 전송을 사용하지 않는다. 사건·영상·위치 정보를 로그나 오류 메시지에 넣지 않는다. 실제 SDK 오류의 원문도 개인정보가 포함될 수 있으므로 그대로 노출하지 않는다.

이번 구성에서는 사용하지 않는 `CAR_DRIVE`·`CAR_LIGHTS` 권한을 제거하고, 인터넷 권한을 추가하지 않는다. `allowBackup=false`와 클라우드·기기 이전 제외 규칙으로 앱 데이터 백업을 차단하며, `usesCleartextTraffic=false`를 설정한다. 런처 Activity의 `exported=true`는 앱 실행에 필요한 기본 진입점이며, 외부 Intent의 이벤트·파일·URI를 입력 데이터로 처리하지 않는다.

SDK 의존성을 유지하므로 병합 Manifest에는 SDK의 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 권한과 AndroidX의 앱 전용 동적 수신기 signature 권한이 포함될 수 있다. 차량 읽기 권한을 제거했다는 것은 패키지 권한 전체가 0개라는 의미가 아니다. 최종 빌드의 병합 Manifest에서 인터넷·차량 제어·비공개 권한이 추가되지 않았는지 별도로 확인한다.

후속 저장 기능을 도입할 때 앱 전용 저장소, 보관 기간, 삭제 정책과 백업 제외 정책을 함께 검토해야 한다. 외부 배포·유료 서비스·외부 데이터 전송은 별도 동의 후 진행한다.

## GitHub 확인 기록

2026-10-30에 연결된 GitHub 도구로 `repo:WhiteBearCode02/CheckMate is:pr is:open`을 조회했으며 결과는 0건이었다. 확인 시점의 상태이며 원격 저장소를 변경하지 않았다. Push, PR 생성, Merge는 실행하지 않는다.
