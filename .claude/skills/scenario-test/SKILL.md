---
name: scenario-test
description: 기획서 4장 사용 시나리오(출발 전, 고속도로, 굽은 국도, 운전 집중, 아이가 힘들어함, 휴게소·도착)를 core 인수 테스트와 Pleos Connect Emulator 확인 절차로 만든다. 기능 묶음을 끝냈을 때, "시나리오대로 되는지 확인", 에뮬레이터에서 시연·검증할 때, 시연 영상 전 점검할 때 사용.
---

# 시나리오 테스트

단위 테스트(`tdd`)가 규칙 하나를 본다면, 시나리오 테스트는 기획서의 이용 흐름 전체가 이어지는지 본다.

## 1. core 인수 테스트
위치: `core/src/test/kotlin/com/mulmicare/core/scenario/ScenarioTest.kt`
`CareSession` 하나에 `trace { }` 신호와 사용자 응답을 순서대로 넣고, 나온 Effect를 순서까지 검사한다.

| ID | 시나리오 (기획서 4장) | 꼭 확인할 Effect |
|---|---|---|
| SC1 | 출발 전: ETA 2시간, P단 → 케어 제안 → 아이·제안받기·종이배·테마 선택 → 케어 시작 → 첫 사용 연습 | `ShowScreen(CareOffer)`, 연습에서 좌·우 cue 1회씩, `ApplyClimate(BASE)` |
| SC2 | 고속도로: 직선, 차로 변경 몇 번 | 말하지 않음(`Speak` 없음), BASE 유지, `ShowScreen` 없음 |
| SC3 | 굽은 국도: 구간 시작 → 제안 → "응" → 왼쪽 조향 → 방향지시등 오른쪽 → 급감속 1회 → 3분 뒤 종료 | `Chime`, `Speak(제안)`, `StartBeat`, `ApplyClimate(BOOST)`, `PlaySideCue(LEFT)`, `PlaySideCue(RIGHT)`가 조향보다 먼저, `StarLost` 1회, `Speak("…별 두 개…")`, `StopBeat` |
| SC4 | 운전 집중: 다음 구간에서 "아니" 또는 무응답 | `StartBeat` 없음, 실내 케어는 계속, 15분 안의 다음 구간에서 제안 없음 |
| SC5 | 아이가 힘들어함: 구간 종료 → "힘들어해요" → 쉼터 제안 → "응" | BOOST, `Speak("…km 앞에 쉼터…")`, `AddWaypoint` |
| SC6 | 휴게소·도착: P단 → 항해 지도(종이배 2척, 별) → 별 5개로 깃발 해금 → 도착 리포트 | `ShowScreen(VoyageMap)`, 해금 이벤트, `ShowScreen(TripReport(rounds=3, stars=7))` |

- 테스트 이름에 시나리오 ID와 관련 spec ID를 같이 쓴다: `` `SC3 굽은 국도 - F3_3 F4_2 F4_3 F5_3`() ``
- 시나리오 테스트가 실패하면, 먼저 원인이 되는 규칙의 단위 테스트를 추가해 재현한 다음 고친다.

## 2. 에뮬레이터 확인
차량 값은 Virtual Console HTTP API로 바꾼다(방법: `pleos-sdk/references/verification.md`). 시나리오는 `scripts/emulator/sc<번호>-*.sh`로 만들어 실제 시간으로 재생한다. 예: `scripts/emulator/sc3-country-road.sh`.
```bash
adb shell am start -n com.mulmicare.app/.MainActivity --ez autostart true   # 디버그 빌드: 케어 바로 시작
bash scripts/emulator/sc3-country-road.sh
adb logcat -s MulmiCare                                                     # 결과
adb shell am broadcast -a com.mulmicare.DEBUG_SAY --es text "응"            # 음성 응답 흉내 (STT 전)
```
Windows Git Bash에서는 `export MSYS_NO_PATHCONV=1`을 먼저 한다(`/sdcard` 같은 경로가 바뀌는 것 방지).

결과는 [docs/emulator-checklist.md](../../../docs/emulator-checklist.md)에 시나리오별로 기록한다:
```
## SC3 굽은 국도 — 2026-11-15
- [x] 조향 -30° → 왼쪽 소리 (지연 약 __ms)
- [ ] 방향지시등 RIGHT → 오른쪽 소리  ← 실패: 이벤트 수신 안 됨. 검증 항목 1 참고
```
- 에뮬레이터에서 확인할 수 없는 것(실제 스피커 좌우 구분, 실제 멀미 효과, 실차 조향 노이즈)은 "확인 불가"로 따로 적는다. 확인하지 못한 것을 됐다고 쓰지 않는다.
- 시연 영상은 이 체크리스트에서 통과한 시나리오만 찍는다.
