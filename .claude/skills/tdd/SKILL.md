---
name: tdd
description: 멀미케어 core 로직을 테스트 먼저(Red→Green→Refactor) 구현하는 절차. core/ 아래 Kotlin 코드를 새로 쓰거나 동작을 바꿀 때, 버그를 고칠 때, "구현해줘/기능 추가/고쳐줘" 요청이 판단 로직(구간 판단, 좌우 신호, 별, 공조 단계, 음성 흐름, 리포트)에 닿을 때 사용.
---

# TDD 절차 (멀미케어)

## 사이클
1. **spec ID 고르기**: [docs/spec.md](../../../docs/spec.md)에서 이번에 구현할 규칙 ID 하나(예: `F3.4`). 한 사이클에 규칙 하나.
2. **Red**: 그 규칙을 검증하는 테스트를 쓴다.
   - 이름: `` `F3_4 거절하면 15분 동안 다시 묻지 않는다`() ``
   - 실행해서 **기대한 이유로** 실패하는지 확인한다. 컴파일 에러는 Red가 아니다. 먼저 빈 구현을 만들어 컴파일되게 한 다음, 단언(assertion)에서 실패하는지 본다.
3. **Green**: 테스트를 통과시키는 가장 작은 구현을 쓴다. 다음 규칙을 미리 구현하지 않는다.
4. **Refactor**: 테스트가 초록인 상태에서 중복과 이름을 정리한다.
5. **전체 확인**: `./gradlew :core:test`를 돌린다. 통과 출력을 본 뒤에야 "완료"라고 말한다.

```bash
./gradlew :core:test --tests "*CurveSegmentDetectorTest*"   # 한 클래스
./gradlew :core:test                                        # 전체
```
Windows PowerShell에서는 `.\gradlew.bat`을 쓴다.

## 테스트 규칙
- **경계값 3점**: 임계값이 있는 규칙은 기준 바로 아래 / 같음 / 바로 위를 모두 테스트한다. 예: D1은 1분 안에 2회, 3회, 4회.
- **시간은 FakeClock으로 넘긴다**: 15분 쿨다운이나 5분 복귀를 실제로 기다리지 않는다. `clock.advance(15.minutes)`처럼 쓴다.
- **임계값은 주입한다**: 테스트는 `Thresholds.default()`의 숫자를 그대로 적지 않고 `t.curveEventsToStart`처럼 참조한다. 시험 주행으로 값이 바뀌어도 테스트가 깨지지 않게 하기 위해서다.
- **목(mock) 프레임워크 대신 손으로 만든 Fake**를 쓴다: `FakeClock`, `RecordingEffects`, 신호 시퀀스용 `trace { }` 빌더. 위치는 `core/src/testFixtures/`.
- **출력은 Effect 목록으로 단언한다**: core는 `onSignal(signal): List<Effect>` 형태로 동작하므로 "무엇을 하라고 했는가"를 검사한다. 내부 상태 필드를 직접 들여다보지 않는다.
- **금지 동작도 테스트한다**: 안전 원칙(S1~S10)은 "하지 않는다"를 단언하는 테스트로 만든다. 예: 주행 중에는 `Effect.ShowScreen`이 나오지 않음, 응답 없이 `Effect.StartBeat`이 나오지 않음.

## trace 빌더 예시
```kotlin
val signals = trace {
    at(0.seconds)  { gear(P) }
    at(10.seconds) { gear(D); speed(50f) }
    at(20.seconds) { steer(deg = -30f) }   // 왼쪽
    at(35.seconds) { steer(deg = 25f) }    // 오른쪽
    at(50.seconds) { speed(30f) }          // 감속
}
signals.forEach { clock.set(it.atMs); effects += engine.onSignal(it) }
```
기획서 시나리오 전체를 재현하는 인수 테스트는 `scenario-test` 스킬을 따른다.

## 바꾸면 안 되는 것
- 테스트를 통과시키려고 테스트를 고치지 않는다. 고쳐야 한다면 spec이 바뀐 경우뿐이고, 그때는 spec.md를 먼저 고친다.
- `vehicle/`과 `app/`은 core 테스트 대상이 아니다. 어댑터 안에 들어가는 순수 변환 함수(SDK 값 → `VehicleSignal`)만 단위 테스트하고, 실제 SDK 동작은 에뮬레이터에서 확인한다(`scenario-test`).
