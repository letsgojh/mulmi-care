---
name: cabin-care-policy
description: 실내 자동 케어(F6)의 기본·강화 단계 공조 목표값, 계절 보정, 겨울 하한값, 5분 복귀, 반영 확인(F6.8), 다음 이동 강화 시작(F9.3)을 계산하는 core/care 로직. CabinCarePolicy, ClimateTarget, Season을 만들거나 고칠 때, 공조 관련 동작을 바꿀 때 사용. tdd 스킬과 함께 쓴다.
---

# 실내 자동 케어 (core/care)

## 단계
| 단계 | 진입 | 목표 |
|---|---|---|
| OFF | 케어 꺼짐 | 아무것도 바꾸지 않음 |
| BASE | 케어 시작, 또는 BOOST 해제 | 외기 유입, 설정 온도 = 기준 − `baseDropC`(1℃) |
| BOOST | 굽은 길 구간 시작, "힘들어해요" (F6.4) | 외기 유입, 설정 온도 = 기준 − `baseDropC` − `boostExtraDropC`(1~2℃), 풍량 = 기준 + 1(최대값 제한), 풍향 = 상체 |

- **기준값**: 케어를 켠 순간의 공조 상태(설정 온도, 풍량, 풍향, 내외기)를 저장해 두고, 목표는 늘 이 기준에서 계산한다. 현재값에서 반복해 빼면 온도가 계속 내려가기 때문이다.
- **BOOST → BASE (F6.5)**: 마지막 구간이 끝나고 `boostHoldAfterSegmentMs`(5분) 뒤. 그 사이 새 구간이 시작되면 타이머를 취소한다. "힘들어해요"로 들어간 BOOST도 같은 규칙을 쓸지는 열린 질문이다.

## 계절 보정 (F6.6, F6.7)
- 바깥 기온을 읽을 수 있으면 `outsideC < winterOutsideC`일 때 겨울.
- 읽을 수 없으면 날짜로 판단한다(12~2월 = 겨울). `Clock`에서 날짜를 얻는다.
- 겨울에는 낮추는 폭에 `winterDropScale`을 곱하고, 결과가 `winterMinSetC`보다 낮아지지 않게 한다. 외기 유입과 상체 바람은 그대로 둔다.
- 공조가 지원하는 온도 단위(예: 0.5℃)로 반올림한다. 단위는 pleos-sdk 문서에서 확인한다.

## 반영 확인 (F6.8)
- `ApplyClimate(target)`을 낸 뒤 `climateVerifyTimeoutMs` 안에 들어온 `ClimateState`를 목표와 비교한다.
- 다르면 1회 다시 요청하고, 그래도 다르면 해당 항목을 이번 이동 동안 `Unsupported`로 표시하고 더 시도하지 않는다. 운전자에게 알리지 않는다(S5 취지).
- Capability가 없는 항목(예: 풍향 미지원)은 처음부터 빼고 나머지만 적용한다.

## 다음 이동 반영 (F9.3)
- 이전 이동 기록에서 "힘들어해요" 횟수가 `struggleCountForBoostStart` 이상이거나 도착 후 평가가 "힘들었음"이면, 다음 이동은 BOOST로 시작한다.

## 하지 않는 것
- 창문 제어(F6.9, S4)
- 케어가 꺼져 있을 때의 공조 변경

## Thresholds (care)
`baseDropC = 1.0`, `boostExtraDropC = TBD(1~2)`, `boostHoldAfterSegmentMs = 300_000`, `winterOutsideC = TBD`, `winterDropScale = TBD`, `winterMinSetC = TBD`, `climateVerifyTimeoutMs = TBD`, `struggleCountForBoostStart = TBD`

## 열린 질문 (구현 전에 사용자에게 확인)
- 케어 중 운전자가 직접 공조를 바꾸면, 그 항목은 자동 조절을 멈출지.
- ~~케어를 끄거나 도착하면 공조를 기준값으로 되돌릴지~~ → **되돌린다**(사용자 결정, 2026-11-15). `onCareEnded` → `RestoreClimate`. 앱이 강제 종료돼 못 되돌린 경우는 `BaselineResolver`가 저장해 둔 기준값으로 복구
