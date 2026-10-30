# README 최신화·공식 로고 복구 보고서

기준일: 2026-10-31 (Asia/Seoul). 범위는 루트 README, 기존 공식 브랜드 이미지의 Git 반영 준비, 누락된 링크 대상 문서의 포함과 검증이다. Android 소스·리소스·Room 구조·SDK·안전 정책·빌드 설정 및 과거 Phase 보고서를 변경하지 않았다.

## A. 원인 분석

### 원격 README와 로고가 최신화되지 않은 이유

GitHub의 main·vehicle-app 브랜치·compare·PR·완전한 recursive tree·파일 API와 로컬 Git을 확인했다.

| 작업 시작 시 항목 | 확인 결과 |
| --- | --- |
| 로컬/원격 `vehicle-app` | `98930d4d6ee42ffa96558179d1fefb65dd401bdb` |
| 원격 `main` | `f041a5bcffbe519ab8ad7bc7c01c1468cce0d0b5` |
| 관계 | vehicle-app ahead 0 / behind 4. 두 tip의 파일 tree는 동일 |
| tree SHA | `e8251ce87ddf6f85aded85fbb6cf844e2f86b4ba` |
| Phase 3A [PR #4](https://github.com/WhiteBearCode02/CheckMate/pull/4) | 2026-10-31 02:51:42 KST 병합 |
| 원격 README | 양쪽 모두 blob `20a96d926421692d94831c6045fe4ae739a35662`, Phase 1 중심, 로고 이미지 참조 없음 |
| 로컬 README | 이미 Phase 3A와 로고를 포함하지만 수정 내용이 미커밋 상태였음 |
| 공식 원본 PNG | 로컬에는 존재, 양쪽 원격에는 없음(404), 로컬 Git 미추적 |
| 루트 architecture·contracts 6개 | 로컬 미추적, 양쪽 원격에는 없음 |

원격의 문제는 잘못된 대소문자나 깨진 PNG가 아니라 **원본 이미지와 최신 README가 커밋에 포함되지 않은 상태**였다. Android 아이콘 리소스는 원격에 있지만 README가 사용할 루트 원본은 별개 파일이다. URL을 절대 주소로 바꾸거나 캐시를 새로 고치는 것만으로 누락된 파일이 생성되지는 않는다.

기존 로컬 README가 전부 Phase 1 상태였던 것은 아니다. Phase 3A 내용이 로컬에 있었고 원격에 반영되지 않았다. 이번에는 그 내용을 최신 기획에 맞춰 전체 재구성했다. 커밋에서 누락된 구체적인 작업 방식·원인을 추측하지 않는다.

### 경로·이미지·브랜치 검사

- 원본: 저장소 루트 `docs/assets/checkmate-icon.png`, 1254×1254 RGB PNG.
- 원본 SHA-256: `A1CDB44A1F049AE9309D7F478D39AC26EFBB9FB40DC48CB301602A97D071E38B`.
- `.gitignore`는 이 PNG를 제외하지 않는다. README 상대 경로와 디렉터리·파일 대소문자는 일치한다.
- Android 변환본은 `android-vehicle/app/src/main/res/drawable-nodpi/checkmate_launcher_foreground.png` 등 기존 리소스다. 재생성·대체·이미지 편집을 하지 않았다.
- 요청한 `git fetch origin`을 실행해 오래된 origin/main·origin/mobile-app 참조를 갱신했다. 작업 파일·로컬 브랜치·다른 담당자의 브랜치를 수정한 것은 아니다.
- 4개 커밋 차이는 내용 충돌을 뜻하지 않는다. 현재 두 원격 tip은 같은 tree다. 기존 미커밋 파일을 보존하고 자동 merge 금지 범위를 지켜 로컬 병합은 수행하지 않았다.

원격 근거: [main README](https://github.com/WhiteBearCode02/CheckMate/blob/main/README.md) · [vehicle-app README](https://github.com/WhiteBearCode02/CheckMate/blob/vehicle-app/README.md) · [브랜치 비교](https://github.com/WhiteBearCode02/CheckMate/compare/main...vehicle-app). 링크의 화면은 이후 사용자 반영에 따라 바뀔 수 있으며 위 SHA는 작업 시점 기록이다.

## B. 수정 결과와 파일 목록

### README

공식 아이콘 160px·이름·슬로건을 상단에 표시하고 다음 14개 섹션으로 정리했다.

프로젝트 소개 → 핵심 가치 → 주요 기능 → 앱별 역할 → 시스템 아키텍처 → 사고 대응 시나리오 → 기술 스택 → 개발 현황 → 저장소 구조 → 설치 및 실행 → 테스트 및 검증 → 팀과 협업 → 향후 계획 → 제한사항 및 참고자료.

사고 후 대응을 중심으로 설명하고 녹화는 선택 기능으로 분리했다. 차량·모바일·공통 DB 역할, 차량 Room과 향후 모바일 로컬 저장, Fake와 실제 클라우드, 코드 구현과 기기 검증을 구분했다. 보험사 연락 모델만으로 등록·통화가 구현된 것처럼 표시하지 않았고, 빌트인캠 지원은 미확인으로 명시했다. AI 과실비율은 참고 추정이며 실제 모델·성능이 없음을 유지했다.

기존 프로젝트명·슬로건·공식 디자인·3인 역할·협업 브랜치·Phase 이력 링크·AI 데이터 이용조건과 평가 방향·라이선스 미선언 설명·공식 참고자료는 보존하거나 중복을 통합했다. 과거 Phase 1·2·3/APP_ICON 보고서의 당시 내용을 현재 사실처럼 고쳐 쓰지 않았다.

### 이번에 내용이 변경·생성된 파일

| 파일 | 변경 |
| --- | --- |
| `README.md` | 전체 구조·설명·다이어그램·목차·검증 출처 최신화, 원본 PNG의 상대 경로를 160px로 표시 |
| `android-vehicle/docs/README_REFRESH_REPORT.md` | 이번 원인·검증 범위·변경 대상·안전한 Git 반영 절차 기록(신규) |

### 함께 Git에 포함할 기존 미추적 파일

아래 파일은 이번에 새로 만들거나 수정한 파일이 아니라, 로컬에 존재했지만 이전 커밋에서 누락된 파일이다. README와 공개된 Phase 3 문서의 이미지·계약 링크가 깨지지 않도록 같은 커밋/PR 대상에 포함한다.

```text
docs/assets/checkmate-icon.png
docs/architecture/integration-overview.md
docs/architecture/database-design.md
docs/architecture/sync-policy.md
shared/contracts/README.md
shared/contracts/accident-event.example.json
shared/contracts/accident-event.schema.json
```

따라서 준비 대상은 수정 README, 신규 갱신 보고서, 기존 원본 PNG와 계약·설계 문서 6개를 합한 **9개 파일**이다. 앱 코드나 다른 작업 파일을 포괄적으로 staging하지 않는다.

## C. 실제 검증

| 검사 | 결과 |
| --- | --- |
| 공식 PNG | 실제 존재, 1254×1254, Git 제외 규칙 없음. 원본 바이트·SHA-256 보존 |
| 이미지 Git 준비 | index에 추가, `git ls-files --error-unmatch` 성공. 저장 예정 blob이 원본과 동일 |
| README 경로 | `docs/assets/checkmate-icon.png`, HTML img 160×160. 실제 경로·대소문자 일치 |
| Markdown | H2 14개, Markdown 링크 57개·HTML 이미지 1개, 로컬 참조 45개(파일 19종)의 존재·대소문자·앵커 확인 |
| Mermaid | 2개 모두 새 SVG로 로컬 렌더링 성공. 목표 시나리오는 점선·세로 방향으로 구분 |
| 로컬 Chrome 1280px·390px | 원본 naturalWidth 1254, 표시 폭 160, 깨진 이미지·미해결 목차·페이지 오류·가로 넘침 모두 0. 상단과 모바일 도표를 직접 확인 |
| 앱·과거 문서 보존 | Android 소스·리소스·빌드 설정·과거 보고서·원본 이미지 등 95개 파일의 SHA-256 동일 |
| 기존 기능 검증 근거 | 기존 JVM XML의 150개·실패/오류/건너뜀 0 확인. 새 Gradle 실행 아님 |
| Git | 대상 9개 파일만 staging 준비, 원본 PNG blob 일치 및 `git diff --check`·`git diff --cached --check` 검사 |
| 원격 GitHub main | 수정 미반영. PR 병합 후 실제 이미지·README 화면 확인 필요 |

로컬 검증 자료는 Git에서 제외되는 `android-vehicle/build/readme-refresh/`의 `validation.json`, `preview-checks.json`, `readme-top-1280.png`, `readme-top-390.png`, 도표별 캡처에 보관했다. 이번 검증의 README SHA-256은 `981C16647200C25ACF0F0A4FDE9FA0AEDAF47F8C18AD112897F73E9380329133`이다.

Phase 3A 보고서와 기존 XML 산출물의 14 suite·150 tests·실패/오류/건너뜀 0, Lint 경고 18개를 확인했다. **이번 작업에서 Gradle 테스트·전체 빌드를 새로 실행하지 않았다.** Android 코드·리소스·빌드 설정에 변경이 없으며 기존 150개 결과를 새로운 실행 성공으로 표시하지 않았다.

로컬 브라우저 미리보기는 GitHub의 라이브 화면 검증이 아니다. 현재 원격 main의 로고 표시 문제는 **미반영 상태**다. 이 작업에서 commit·push·PR 생성·merge를 하지 않았으므로 원격 수정 완료라고 보고하지 않는다.

문서·브랜드 경로에는 민감정보·자격증명·개인 영상·새 네트워크 기능을 추가하지 않았다. 무료 로컬 문서 검증 도구만 사용하며 유료 서비스·배포·클라우드·데이터 전송을 활성화하지 않았다.

## D. GitHub 반영 절차

아래 명령은 사용자가 검토 후 실행할 절차다. 이 작업에서 commit·push·merge·PR 생성 명령은 실행하지 않았다. 원격 상태가 바뀔 수 있으므로 작업 시점의 4 behind 숫자만 믿고 진행하지 않는다.

### 정확한 git add 대상과 커밋

PowerShell에서 저장소 **루트**를 기준으로 실행한다. `git add .` 대신 대상 9개만 명시한다.

```powershell
cd D:\CheckMate
git status --short
git add -- README.md android-vehicle/docs/README_REFRESH_REPORT.md docs/assets/checkmate-icon.png docs/architecture/integration-overview.md docs/architecture/database-design.md docs/architecture/sync-policy.md shared/contracts/README.md shared/contracts/accident-event.example.json shared/contracts/accident-event.schema.json
git diff --cached --name-status
git diff --cached --check
git ls-files --error-unmatch docs/assets/checkmate-icon.png
git commit -m "docs: refresh CheckMate README and restore official app icon"
```

작업 종료 시 위 9개 파일의 staging·PNG blob 일치를 검사한다. **staging은 커밋·원격 반영이 아니다.** IDE에서 `android-vehicle/` 아래 파일만 선택해 커밋하면 루트 README·원본 이미지·공통 문서가 다시 누락될 수 있으므로 커밋 목록을 확인한다.

### 최신 main 확인과 수동 동기화

위 커밋으로 기존 작업을 보존한 후, 사용자가 필요할 때 다음을 실행한다.

```powershell
git fetch origin
git log --oneline --left-right HEAD...origin/main
git diff --name-status HEAD...origin/main
git merge --no-edit origin/main
```

작업 시점에는 main과 기존 vehicle-app 파일 tree가 같았으므로 내용 충돌 징후는 없었다. 이후 원격 변경이나 충돌이 생기면 자동 해결·초기화·강제 push하지 말고 중단해 내용을 확인한다. 과거 커밋을 삭제하거나 다른 담당자의 브랜치를 수정하지 않는다.

### Push와 PR

```powershell
git status
git push origin vehicle-app
```

GitHub의 [PR 비교 화면](https://github.com/WhiteBearCode02/CheckMate/compare/main...vehicle-app?expand=1)에서 **base: main / compare: vehicle-app**을 선택하고 Files changed에서 원본 PNG·README·계약 문서·갱신 보고서가 모두 포함됐는지 확인한다. 제목·설명을 입력하고 검토 후 Create pull request를 누른다. 절차 참고: [GitHub 공식 PR 생성 안내](https://docs.github.com/en/pull-requests/how-tos/create-pull-requests/creating-a-pull-request).

추천 PR 제목:

```text
docs: update CheckMate branding and project documentation
```

PR 설명 초안:

```markdown
원격 README가 Phase 1 설명에 머물고 공식 로고 원본이 누락된 문제를 수정합니다.
사고 후 대응 중심의 차량·스마트폰 역할, Phase 1·2·3A 구현과 Phase 3B 계획,
Room·공통 JSON 계약·Fake 동기화 및 실제 연동의 경계를 정리합니다.

README·원본 PNG와 누락된 루트 계약/설계 문서를 함께 포함합니다.
Android 코드·아이콘 리소스·Room 구조·안전 정책·과거 보고서는 변경하지 않습니다.

검증: README 갱신 보고서의 이미지·링크·앵커·Mermaid·로컬 렌더링·Git blob 검사 참조.
150개 JVM 테스트·빌드·Lint 결과는 기존 Phase 3A 검증 이력이며 이번에 재실행하지 않았습니다.
main 라이브 표시 확인은 PR 병합 후 수행합니다.
```

### PR 병합 후 main 표시 확인

1. 승인·리뷰 후 사용자가 PR을 main에 병합한다.
2. 저장소 메인 화면에서 브랜치가 `main`인지 확인하고 README 상단의 원본 아이콘과 최신 설명을 확인한다.
3. main 파일 목록의 `docs/assets/checkmate-icon.png`가 존재하고 이미지 파일 페이지에서 정상 표시되는지 확인한다.
4. PC·모바일 화면에서 160px 로고, 목차 앵커, 두 Mermaid와 공통 계약·설계 링크를 확인한다.
5. 필요하면 아래 명령으로 **README와 PNG가 함께 main에 포함됐는지** 추가 확인한다.

```powershell
git fetch origin
git ls-tree -r --name-only origin/main -- README.md docs/assets/checkmate-icon.png docs/architecture shared/contracts
git show origin/main:README.md
```

로컬 표시·staging·push 성공만으로 GitHub 기본 화면 수정 완료를 판단하지 않는다. 실제 원격 main에 README와 원본 이미지가 함께 반영되고 화면 표시까지 확인되어야 한다.

## E. 남은 작업

- 사용자 검토·커밋·필요한 수동 브랜치 동기화·push·PR·main 병합 및 실제 GitHub PC/모바일 화면 확인.
- 실제 Room 재오픈·Pleos·Compose 기기 검증. Door만으로 정차를 판정하지 않는 제한 유지.
- 운영 인증·Firebase 등 클라우드 선택·사용자별 DB 규칙·실제 모바일 양방향 통신·장애 복구.
- 모바일 현장 증거·보험사 전화 연결·선택적 녹화, 실제 AI·영상 분석·보고서 기능.
- 빌트인캠 접근 지원 여부는 미확인. 실제 지원·약관·비용·권한을 확인하기 전 구현 완료로 표시하지 않음.

위 기능은 문서로 구현된 것처럼 바꾸지 않았다. 원본 PNG와 현재 Android 디자인은 그대로 유지했다.

커밋 내용 정리: 사고 후 대응 중심 README 전체 갱신, 공식 원본 로고와 누락된 공통 문서의 동일 커밋 포함, 실제 구현·계획·기존 검증 이력·원격 반영 절차 구분.
