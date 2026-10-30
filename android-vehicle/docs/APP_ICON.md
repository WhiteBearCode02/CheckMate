# CheckMate 공식 아이콘 리소스

## 원본과 변환본

- 원본: [`docs/assets/checkmate-icon.png`](../../docs/assets/checkmate-icon.png), 저장소 루트 기준. 사용자 제공 공식 아이콘이며 수정하지 않는다.
- 원본 크기: 1254×1254, 불투명 RGB PNG.
- 원본 SHA-256: `A1CDB44A1F049AE9309D7F478D39AC26EFBB9FB40DC48CB301602A97D071E38B`.
- Android 전경 변환본: `app/src/main/res/drawable-nodpi/checkmate_launcher_foreground.png`.
- Android adaptive 정의: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`.
- 배경·전경 drawable: `drawable/ic_launcher_background.xml`, `drawable/ic_launcher_foreground.xml`.
- PNG fallback: `mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi`의 `ic_launcher.png`, `ic_launcher_round.png`.

## 배치와 마스크

원본의 로봇·차량·사고 기록·연락 요소를 다시 그리거나 삭제하지 않는다. Android용 전경은 108dp 레이어에 64dp 크기의 원본을 중앙 배치한 형태다. 432×432 PNG에서 원본 그림은 256×256, 가장자리 여백은 각 88px다. 흰색 배경 레이어와 합성하며 비율은 유지한다.

[Android adaptive icon 기준](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)의 66dp 안전 영역 안에 주요 그림을 배치했다. 런처가 사용하는 중앙 72dp viewport를 기준으로 원형·둥근 사각형·사각형 마스크 및 48px 크기를 로컬에서 미리 확인한다. 모든 제조사 런처의 동작이나 실제 Pleos 화면 검증을 대신하지 않는다.

기존 녹색 Android 기본 아이콘과 density별 WebP는 새 PNG·adaptive 구조로 교체했다. Manifest의 `@mipmap/ic_launcher`, `@mipmap/ic_launcher_round` 참조는 유지한다. 원본을 통째로 단색 레이어로 사용하면 불투명한 사각형이 될 수 있으므로 전용 `monochrome`은 제공하지 않는다. 별도의 단색 브랜드 재설계는 이번 범위에 포함하지 않았다.

Fallback 크기는 mdpi 48px, hdpi 72px, xhdpi 96px, xxhdpi 144px, xxxhdpi 192px다. `minSdk=28`의 런처는 API 26 이상 adaptive 정의를 사용할 수 있다.

## 재생성

Windows의 System.Drawing만 사용해 로컬에서 결정적으로 크기·여백·마스크를 변환한다. 외부 이미지 생성 API, 네트워크 업로드, 추가 과금 서비스를 사용하지 않는다.

```powershell
cd D:\CheckMate\android-vehicle
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\generate-launcher-icons.ps1
.\gradlew.bat assembleDebug
```

`ExecutionPolicy Bypass`는 이 명령의 PowerShell 프로세스에만 적용하며 시스템 정책을 변경하지 않는다. 스크립트는 원본 해시를 전후 비교하고, 기존 기본 아이콘의 알려진 파일만 정리한다. 미리보기는 Git에서 제외되는 `build/branding-review/launcher-mask-preview.png`에 생성된다.

## README 브랜드 의미

루트 README 상단에서 원본을 상대 경로 `docs/assets/checkmate-icon.png`로 표시한다. 아이콘은 사고 순간에 사용자를 돕는 협력자·보조자, 즉 스마트 사고 대응 파트너를 뜻한다. 사고 대응 안내·보험사 연락 보조·AI 분석·참고 과실비율이라는 서비스 방향을 설명하되 실제 구현 상태를 완료로 바꾸지 않는다.

## 검증

- `assembleDebug --offline --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process --max-workers=2`: 성공, 33초. 로컬 Gradle 캐시를 사용했다.
- Debug APK에서 adaptive XML 2개, foreground PNG 1개(432×432), density별 PNG 10개의 포함과 크기를 확인했다. 기존 기본 WebP는 포함되지 않는다.
- 패키징된 Manifest의 `@mipmap/ic_launcher`, `@mipmap/ic_launcher_round` 참조를 확인했다. 권한·네트워크 설정·앱 실행 코드는 변경하지 않았다.
- 원본 SHA-256이 변하지 않았고, 원형·둥근 사각형·사각형 및 48px 마스크 미리보기에서 주요 그림이 잘리지 않는 것을 확인했다.
- README의 기존 본문 유지, 상대 이미지 경로, 로컬 브라우저 1280px·390px 화면의 이미지 로딩과 가로 넘침 없음을 확인했다. Markdown 링크·앵커 검사와 `git diff --check`도 통과했다.
- 실제 기기·Pleos 런처 및 라이브 GitHub 화면은 검증하지 않았다. 로컬 미리보기는 해당 환경의 검증을 대신하지 않는다.
- 원본 `docs/assets/checkmate-icon.png`는 작업 시작 시 Git 미추적 파일이었다. README와 함께 저장소에 포함해야 GitHub에서 이미지가 표시된다.
