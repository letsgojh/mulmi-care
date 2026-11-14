# 멀미케어 (mulmi-care)

뒷자리 아이의 멀미 부담을 덜어 주는 차량용 동승자 케어 앱. Pleos Connect(Android Automotive OS) + Kotlin.

- 요구사항: [docs/spec.md](docs/spec.md) (ID: F1~F9, D, S). 원문: [docs/plan-original.md](docs/plan-original.md)
- 루트의 기획서 `.docx`에는 개인정보가 있다. 커밋·업로드·인용 금지.

## 모듈 경계
```
core/     순수 Kotlin(JVM). Android·Pleos 의존 금지. 판단 로직 전부 여기.
vehicle/  Pleos SDK 어댑터. core/port 인터페이스를 구현만 한다. 판단 로직 금지.
app/      AAOS 앱. 서비스, 오디오 재생, 정차 중 화면.
```
- 시간은 `Clock` 포트로 주입한다. `System.currentTimeMillis()`, `Thread.sleep`, 코루틴 `delay`로 로직을 짜지 않는다.
- 임계값은 `Thresholds`에 모은다. 매직 넘버 금지.

## 작업 방식: TDD
- `core/`의 동작 변경은 **실패하는 테스트를 먼저** 쓴다. 절차는 `tdd` 스킬.
- 테스트 이름에 spec ID를 넣는다: `` `F3_4 거절하면 15분 동안 다시 묻지 않는다`() ``
- 기능을 끝냈다고 말하기 전에 `./gradlew :core:test`를 돌려 통과를 확인한다.

## 반드시 지킬 것 (spec S1~S10)
주행 중 화면 표시 없음 · 놀이 자동 시작 없음 · 창문 제어 없음 · 운전자 지적 없음 · 내비 음성 우선 · 아이 개인정보 미수집 · 위치 외부 전송 없음 · 의료 효과 주장 없음 · 승인된 Pleos API와 최소 권한만.
UI·오디오·SDK 코드를 바꾸면 `safety-guard` 스킬로 점검한다.

## 스킬 안내
| 상황 | 스킬 |
|---|---|
| 기능 범위·수치 확인 | `mulmi-spec` |
| core 로직 구현 | `tdd` + 해당 도메인 스킬 |
| 굽은 길 구간, 좌우 신호, 별 판정 | `drive-signal-engine` |
| 공조 단계 | `cabin-care-policy` |
| 음성 제안·응답 흐름 | `voice-flow` |
| 비트, 효과음, 오디오 포커스 | `rhythm-audio` |
| Pleos SDK 호출, 권한, Capability | `pleos-sdk` |
| 프로젝트 구조, 빌드, AAOS 규칙 | `aaos-conventions` |
| 기획서 시나리오 인수 테스트, 에뮬레이터 확인 | `scenario-test` |
| 안전 원칙 점검 | `safety-guard` |
| APK, README, 제출물 | `submission-pack` |
