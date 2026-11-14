---
name: drive-signal-engine
description: 차량 신호(조향각, 방향지시등, 속도, 제동, 기어)로 굽은 길 구간 시작·종료(D1, D2), 좌우 소리 신호(F4.2, F4.3), 악기 레이어(F4.4), 별 판정(F5)을 계산하는 core/signal 로직. CurveSegmentDetector, SideCueGenerator, StarJudge를 만들거나 고칠 때, 임계값을 정하거나 보정할 때 사용. tdd 스킬과 함께 쓴다.
---

# 주행 신호 엔진 (core/signal)

입력은 `VehicleSignal`, 출력은 `Effect`다. Android와 Pleos 타입을 쓰지 않는다.

## 입력 신호 규약
```kotlin
sealed interface VehicleSignal { val atMs: Long }
data class SteeringAngle(override val atMs: Long, val deg: Float) // 음수 = 왼쪽, 양수 = 오른쪽
data class TurnSignal(override val atMs: Long, val state: TurnState) // OFF, LEFT, RIGHT, HAZARD
data class Speed(override val atMs: Long, val kph: Float)
data class Brake(override val atMs: Long, val pressed: Boolean)
data class Gear(override val atMs: Long, val pos: GearPos) // P, R, N, D
data class Tick(override val atMs: Long)
```
SDK의 부호와 단위가 다르면 `vehicle/` 어댑터에서 맞춘다. core는 위 규약만 믿는다.

## 1. 굽은 길 구간 (D1, D2) — `CurveSegmentDetector`
- **조향 이벤트**: |조향각|이 `steerEventDeg` 아래에 있다가 위로 넘어가는 순간(상승 에지) 1회.
- **감속 이벤트**: 속도 변화율이 `-decelEventKphPerSec` 아래로 내려가는 순간 1회. 변화율은 직전 샘플과의 차이를 시간 간격으로 나눠 구하고, 간격이 `minSpeedDtMs`보다 짧으면 계산하지 않는다.
- **시작(D1)**: 최근 60초 창 안의 이벤트 수가 `curveEventsToStart`(기본 3) 이상.
- **종료(D2)**: 이벤트 수가 기준 아래인 상태가 **연속 60초** 유지. 중간에 다시 기준 이상이 되면 종료 타이머를 초기화한다.
- 출력: `SegmentStarted`, `SegmentEnded` (내부 이벤트로 `CareSession`에 전달)
- 필수 테스트: 2/3/4회 경계, 59초/60초/61초 경계, 종료 직전 재진입, 고속도로 차로 변경(작은 조향각) 반복으로는 시작하지 않음(F3.7).

## 2. 좌우 소리 신호 (F4.2, F4.3) — `SideCueGenerator`
놀이 중일 때만 동작한다.
- **방향지시등(F4.3)**: OFF → LEFT/RIGHT로 바뀌는 순간 그 방향 `PlaySideCue`. HAZARD는 무시한다.
- **조향 시작(F4.2)**: |조향각|이 `steerCueDeg`를 넘는 상승 에지에서 부호 방향으로 `PlaySideCue`.
  - 히스테리시스: |조향각|이 `steerCueResetDeg` 아래로 돌아와야 다음 신호를 낼 수 있다(떨림 방지).
  - 방향지시등 신호를 낸 뒤 `turnSignalSuppressMs` 안의 같은 방향 조향 시작은 같은 동작으로 보고 다시 울리지 않는다.
- **말로 알리기(F2.5)**: 놀이 전체에서 처음 `voiceCueCount`번은 `PlaySideCue(side, withVoice = true)`.

## 3. 악기 레이어 (F4.4) — 1차는 단순하게
- 최근 `layerWindowMs` 동안의 좌우 신호 수를 레이어 1~`maxLayers`로 바꾼다. 신호가 없으면 1(잔잔한 비트).
- 레이어가 바뀔 때만 `SetLayer(n)`을 낸다.

## 4. 별 판정 (F5) — `StarJudge`
- 판 시작 시 3개.
- **급조향**: 조향각 변화 속도(|Δdeg/Δt|)가 `harshSteerDegPerSec` 이상.
- **급감속**: 속도 변화율이 `-harshDecelKphPerSec` 이하. 제동 페달은 감속 시점을 확인하는 보조 신호로만 쓰고, 단독으로 별을 깎지 않는다.
- 한 번의 급한 동작이 여러 샘플에 걸쳐도 1회로 센다: 상승 에지 + `harshRefractoryMs` 동안 재판정 금지.
- 별이 떨어지면 `StarLost`(효과음만). 1개 아래로는 내려가지 않는다(F5.4).
- 판 종료 시 `RoundFinished(stars)`.

## Thresholds
> **현재 값은 `core/.../config/Thresholds.kt`가 기준이다.** 아래는 처음 설계할 때의 초안이다.
> 조향 관련 값은 모두 **앞바퀴 조향각** 기준이다(Pleos SDK가 핸들 회전각이 아닌 앞바퀴 각도를 준다).
> 테스트에서 각도를 쓸 때는 숫자를 직접 쓰지 말고 `t.steerEventDeg * 2`처럼 임계값에서 계산한다.
```kotlin
data class Thresholds(
    val curveWindowMs: Long = 60_000,          // D1
    val curveEventsToStart: Int = 3,           // D1 (기획서 "예: 3회")
    val curveQuietToEndMs: Long = 60_000,      // D2
    val steerEventDeg: Float = TODO(),         // 시험 보정 필요
    val decelEventKphPerSec: Float = TODO(),
    val minSpeedDtMs: Long = 200,
    val steerCueDeg: Float = TODO(),           // F4.2
    val steerCueResetDeg: Float = TODO(),
    val turnSignalSuppressMs: Long = TODO(),
    val voiceCueCount: Int = 3,                // F2.5 (기획서 "처음 몇 번")
    val layerWindowMs: Long = 30_000,
    val maxLayers: Int = 4,
    val harshSteerDegPerSec: Float = TODO(),   // F5.3
    val harshDecelKphPerSec: Float = TODO(),   // F5.3
    val harshRefractoryMs: Long = 2_000,
)
```
`TODO()` 자리는 기획서에 값이 없는 항목이다. 처음 구현할 때 사용자와 기본값을 정하고, 정한 값과 근거를 주석으로 남긴다. 테스트는 숫자를 직접 쓰지 말고 이 필드를 참조한다.

## 열린 질문 (구현 전에 사용자에게 확인)
- 구간이 5분을 넘으면 판을 나눌지(F5.1 "2~5분").
- 정차(P단, 속도 0)가 길어지면 구간을 바로 끝낼지.
