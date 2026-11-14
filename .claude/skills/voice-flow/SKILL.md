---
name: voice-flow
description: 음성 대화 흐름 상태 머신(core/voice). 케어 제안(F1.1), 리듬 놀이 제안·수락·거절·15분 쿨다운(F3), "그만"(F3.5), 판 종료 멘트(F5.7), 뒷자리 상태 확인과 쉼터 제안(F7), 음성 인식 창(F3.6), 응답 단어 매칭. VoiceFlow나 CareSession의 대화 부분을 만들거나 고칠 때, 멘트·응답 단어를 바꿀 때 사용. tdd 스킬과 함께 쓴다.
---

# 음성 흐름 (core/voice)

## 상태
```
IDLE
 ├─ SegmentStarted & 아이 탑승 & 제안받기 & 쿨다운 아님 & 놀이 중 아님 ─→ PROPOSING
PROPOSING   Chime, Speak("리듬 놀이 할까요?"), Listen(proposalListenMs, [YES, NO])
 ├─ YES ─→ PLAYING   (StartBeat, 판 시작)
 └─ NO | 응답 없음(타임아웃) ─→ IDLE, 쿨다운 = now + 15분 (F3.4)
PLAYING     Listen(놀이 동안 계속, [STOP])
 ├─ STOP ─→ 판 즉시 종료 (StopBeat) ─→ IDLE   (F3.5)
 └─ SegmentEnded ─→ Speak("종이배 도착! 별 N개를 지켰어요"), StopBeat ─→ CHECKING
IDLE에서 SegmentEnded (놀이 안 함) ─→ CHECKING
CHECKING    Speak("뒷자리는 괜찮아요?"), Listen(checkListenMs, [OK, STRUGGLING])
 ├─ OK | 응답 없음 ─→ IDLE
 └─ STRUGGLING ─→ 케어 BOOST + 가장 가까운 쉼터 찾기 ─→ SUGGEST_REST
SUGGEST_REST Speak("{거리}km 앞에 쉼터가 있어요. 들를까요?"), Listen(..., [YES, NO])
 ├─ YES ─→ AddWaypoint(쉼터) ─→ IDLE   (F7.3)
 └─ NO | 응답 없음 ─→ IDLE
```
- 정차 중 "힘들어해요" 버튼(F7.2)은 어느 상태에서든 STRUGGLING과 같은 처리를 한다.
- 탑승자가 성인이면(F1.5) PROPOSING과 PLAYING을 건너뛰고 CHECKING부터 쓴다.
- 케어 제안(F1.1)은 이동당 한 번이며 정차(P단) 중 화면 카드로 보여 준다. 음성 상태 머신과는 별도의 출발 전 흐름이다.

## 음성 인식 창 (F3.6)
- `Listen`은 위 표의 시점에만 낸다. 그 밖의 시간에는 마이크를 열지 않는다.
- 놀이 중에는 STOP만 인식 대상으로 둔다.

## 응답 매칭
STT 결과를 공백·문장부호를 없앤 뒤 아래 목록과 비교한다. 목록은 `VoicePhrases`에 모으고 테스트로 고정한다.
| 의도 | 예 |
|---|---|
| YES | 응, 네, 그래, 좋아, 할래 |
| NO | 아니, 아니요, 싫어, 안 할래 |
| STOP | 그만, 멈춰, 끝 |
| OK | 괜찮아요, 괜찮아, 좋아요 |
| STRUGGLING | 힘들어해요, 힘들어, 안 좋아, 속 안 좋아 |
인식하지 못한 말은 응답 없음으로 본다. 다시 묻지 않는다.

## 우선순위·충돌
- 내비 안내 음성이 나오는 중이면 `Speak`를 미룬다(S6). 미루는 처리는 app의 `EffectRunner`가 하고, core는 `NaviSpeaking(true/false)` 신호를 받아 PROPOSING의 응답 대기 시간을 그만큼 늘린다.
- PROPOSING 중에 구간이 끝나면 제안을 취소한다. 응답 없음과 같게 보고 쿨다운을 건다.
- 운전자의 주행을 평가하는 멘트는 만들지 않는다(S5). 별이 떨어질 때도 말하지 않는다.

## 필수 테스트
- F3.4: 거절 후 14분 59초에는 제안하지 않고, 15분 뒤 다음 구간에서는 제안한다. 무응답도 같다.
- F3.1, S2: YES 없이 `StartBeat`가 나오는 경로가 없다.
- F3.6: IDLE 상태에서는 `Listen`이 나오지 않는다.
- F3.8: "묻지 않기"면 구간이 시작돼도 PROPOSING으로 가지 않는다.

## Thresholds (voice)
`proposalListenMs = TBD`, `checkListenMs = TBD`, `proposalCooldownMs = 900_000`
