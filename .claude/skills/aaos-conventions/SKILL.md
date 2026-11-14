---
name: aaos-conventions
description: 멀미케어 프로젝트 구조(core/vehicle/app 모듈), Gradle 설정, 포트·Effect 아키텍처, AAOS 앱 규칙(백그라운드 서비스, 주행 중 UX 제약, 생명주기). 프로젝트를 처음 만들 때, 모듈·패키지·클래스를 어디에 둘지 정할 때, 빌드 설정을 바꿀 때, 서비스·Activity를 만들 때 사용.
---

# 프로젝트 구조와 규칙

## 모듈
```
settings.gradle.kts      include(":core", ":vehicle", ":app")
core/                    kotlin("jvm") + java-test-fixtures. Android 의존 금지
  src/main/kotlin/com/mulmicare/core/
    signal/    VehicleSignal, CurveSegmentDetector, SideCueGenerator, StarJudge
    care/      CabinCarePolicy, ClimateTarget, Season
    voice/     VoiceFlow (제안·응답·쿨다운 상태 머신)
    session/   CareSession (전체 조정), TripRecord, TripReport
    effect/    Effect (sealed)
    port/      Clock, ClimatePort, NaviPort, SpeechPort, AudioPort, RecordStore
    config/    Thresholds
  src/test/kotlin/...
  src/testFixtures/kotlin/...  FakeClock, trace { }, RecordingEffects, Fake*Port
vehicle/                 com.android.library. Pleos SDK 어댑터 (pleos-sdk 스킬)
app/                     com.android.application. AAOS
  CareService            포그라운드 서비스. 신호 → CareSession → Effect 실행
  EffectRunner           Effect를 실제 TTS/STT/오디오/공조/내비 호출로 바꿈
  ui/                    정차 중 화면(설정, 연습, 항해 지도, 리포트)
  audio/                 rhythm-audio 스킬
```

## 아키텍처: 함수형 코어, 명령형 셸
- core는 `fun onSignal(signal: VehicleSignal): List<Effect>`, `fun onUserInput(input: UserInput): List<Effect>` 형태다. I/O를 하지 않는다.
- `Effect` 예: `Speak(text)`, `Listen(window, expect)`, `StartBeat`, `StopBeat`, `PlaySideCue(side, withVoice)`, `SetLayer(n)`, `StarLost`, `ApplyClimate(target)`, `AddWaypoint(place)`, `ShowScreen(screen)`, `HideScreen`, `SaveRecord(..)`.
- 비동기 결과(공조 반영 확인, STT 결과)는 다시 입력으로 core에 넣는다: `onUserInput(SpeechResult(..))`, `onSignal(ClimateState(..))`.
- 시간은 `Clock` 포트로만 얻는다. 타이머가 필요하면 `Tick` 신호를 주기적으로 넣는다 (예: 1초마다).

## 빌드
```bash
./gradlew :core:test           # 빠름. TDD 루프는 이것만
./gradlew :app:assembleDebug   # APK
./gradlew ktlintCheck          # 설정한 경우
```
- PATH에 `java`가 없다. 먼저 `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. 버전 정보는 `pleos-sdk/references/setup.md`.
- Gradle 9.4.1 / AGP 9.2.0 / Kotlin 2.4.0 / JVM 17 / SDK 34. AGP 9는 Kotlin을 내장 지원하므로 Android 모듈에 `kotlin-android` 플러그인을 넣지 않는다.
- core 테스트: JUnit 5 + `kotlin("test")`. 다른 테스트 라이브러리를 추가하려면 먼저 이유를 말한다.

## AAOS 규칙
- **주행 중 화면 표시 금지(S1)**: UI는 기어가 P(또는 정차)일 때만 띄운다. 이 판단은 core가 `ShowScreen`/`HideScreen`으로 내리고, app은 따르기만 한다.
- 차량 신호 수신과 음성 출력은 앱이 화면에 없어도 돌아야 하므로 `CareService`(포그라운드 서비스)에서 처리한다. 가능 여부는 pleos-sdk 검증 항목 3.
- 서비스 종료 시 SDK 리스너 해제, 오디오 포커스 반납, 공조를 케어 전 상태로 되돌릴지 여부는 spec에 없으므로 사용자에게 확인한다.
- 정차 중 화면도 큰 터치 영역, 짧은 문구로 만든다. 운전자에게 주행 중 조작을 요구하지 않는다(S3).
