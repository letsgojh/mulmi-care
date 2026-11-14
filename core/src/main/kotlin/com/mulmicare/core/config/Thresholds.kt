package com.mulmicare.core.config

/**
 * 판단 기준값 모음. 테스트는 숫자를 직접 쓰지 않고 이 필드를 참조한다.
 * "임시값"은 기획서에 수치가 없어 정한 값이며 시험 주행으로 보정한다.
 *
 * 조향 각도는 모두 **앞바퀴 조향각**(°)이다. Pleos SteeringWheelAngleListener가 핸들 회전각이 아니라
 * 앞바퀴 각도를 주기 때문이다. 참고: 조향비 약 14:1, 반경 100m 커브 ≈ 1.7°, 고속도로 차로 변경 ≈ 0.3~0.5°.
 */
data class Thresholds(
    /** D1: 구간 판단 창. 기획서 "최근 1분". */
    val curveWindowMs: Long = 60_000,
    /** D1: 창 안의 조향·감속 이벤트가 이 횟수 이상이면 구간 시작. 기획서 "예: 3회 이상". */
    val curveEventsToStart: Int = 3,
    /** D2: 기준 아래로 이 시간 유지되면 구간 종료. 기획서 "1분". */
    val curveQuietToEndMs: Long = 60_000,
    /** D1 조향 이벤트 기준 앞바퀴 각도(°). 임시값: 차로 변경(0.3~0.5°)과 굽은 길(1.5° 이상)을 가른다. */
    val steerEventDeg: Float = 1.0f,
    /** D1 감속 이벤트 기준(km/h per s). 임시값. */
    val decelEventKphPerSec: Float = 5f,
    /** 속도 샘플 간격이 이보다 짧으면 감속률을 계산하지 않는다(노이즈 방지). */
    val minSpeedDtMs: Long = 200,

    /** F4.2 좌우 소리를 낼 앞바퀴 각도(°). 임시값. */
    val steerCueDeg: Float = 1.0f,
    /** F4.2 이 앞바퀴 각도 아래로 돌아와야 다음 소리를 낼 수 있다(떨림 방지). 임시값. */
    val steerCueResetDeg: Float = 0.3f,
    /** F4.3 방향지시등을 끈 뒤 이 시간 안의 같은 쪽 조향은 같은 동작으로 보고 소리를 내지 않는다. 임시값. */
    val turnSignalSuppressMs: Long = 5_000,
    /** F2.5 처음 이 횟수만큼은 소리와 함께 "왼쪽!"/"오른쪽!"을 말로 알린다. 기획서 "처음 몇 번". */
    val voiceCueCount: Int = 3,

    /** F5.3 급조향: 앞바퀴 조향 속도(°/s)가 이 값 이상. 임시값 (핸들 기준 약 210°/s). */
    val harshSteerDegPerSec: Float = 15f,
    /** F5.3 급감속(km/h per s). 구간 판단용 감속([decelEventKphPerSec])보다 강하다. 임시값. */
    val harshDecelKphPerSec: Float = 8f,
    /** F5.3 별이 떨어진 뒤 이 시간 안의 급한 동작은 같은 동작으로 본다(종류 무관). 임시값. */
    val harshRefractoryMs: Long = 2_000,
    /** 조향 샘플 간격이 이보다 짧으면 회전 속도를 계산하지 않는다(노이즈 방지). */
    val minSteerDtMs: Long = 50,

    /** F3.3 놀이 제안 뒤 답을 기다리는 시간. 임시값. */
    val proposalListenMs: Long = 8_000,
    /** F3.4 거절하거나 답이 없으면 이 시간 동안 다시 묻지 않는다. 기획서 "15분". */
    val proposalCooldownMs: Long = 15 * 60_000,
    /** F7.1 뒷자리 상태 질문 뒤 답을 기다리는 시간. 임시값. */
    val checkListenMs: Long = 8_000,
    /** F7.2 쉼터 제안 뒤 답을 기다리는 시간. 임시값. */
    val restListenMs: Long = 8_000,

    /** F6.2 기본 단계: 기준 설정 온도에서 낮추는 폭(℃). 기획서 "1도". */
    val baseDropC: Float = 1.0f,
    /** F6.3 강화 단계: 기본 단계에서 더 낮추는 폭(℃). 기획서 "1~2도". 임시값. */
    val boostExtraDropC: Float = 1.5f,
    /** F6.5 구간이 끝나거나 "힘들어해요" 뒤 이 시간이 지나면 기본 단계로 돌아간다. 기획서 "5분". */
    val boostHoldMs: Long = 5 * 60_000,
    /** F6.6 바깥 기온이 이보다 낮으면 겨울로 본다(℃). 임시값. */
    val winterOutsideC: Float = 5f,
    /** F6.6 겨울에는 낮추는 폭에 이 값을 곱한다. 임시값. */
    val winterDropScale: Float = 0.5f,
    /** F6.6 겨울에는 설정 온도를 이 값 아래로 내리지 않는다(℃). 임시값. */
    val winterMinSetC: Float = 20f,
    /** 공조 설정 온도 단위(℃). Virtual Console 기준 0.5. */
    val setTempStepC: Float = 0.5f,
)
