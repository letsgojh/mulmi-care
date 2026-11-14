package com.mulmicare.core.care

enum class FanDirection { FACE, FLOOR, FACE_AND_FLOOR, DEFROST }

/** 케어를 켤 때 읽은 공조 상태. 목표값은 늘 이 기준에서 계산한다. 읽지 못한 항목은 null. */
data class ClimateBaseline(
    val setTempC: Float,
    val fanSpeed: Int? = null,
    val fanSpeedMax: Int? = null,
    val fanDirection: FanDirection? = null,
    /** 내기 순환이 켜져 있었는지. 케어가 끝나면 이 상태로 되돌린다. */
    val recirculation: Boolean? = null,
)

/**
 * 공조 목표값. null인 항목은 바꾸지 않는다.
 * 창문 등 주행 중 위험할 수 있는 제어 항목은 두지 않는다(F6.9, S4).
 */
data class ClimateTarget(
    val setTempC: Float,
    val outsideAir: Boolean,
    val fanSpeed: Int? = null,
    val fanDirection: FanDirection? = null,
)

enum class CareLevel { BASE, BOOST }
