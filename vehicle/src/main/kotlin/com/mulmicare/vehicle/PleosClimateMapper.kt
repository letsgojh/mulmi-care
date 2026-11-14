package com.mulmicare.vehicle

import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import com.mulmicare.core.care.ClimateTarget
import com.mulmicare.core.care.FanDirection
import kotlin.math.abs

/** 다시 읽은 공조 상태. 읽지 못한 항목은 null. */
data class ClimateReadback(
    val setTempC: Float?,
    val fanSpeed: Int?,
    val fanDirection: FanDirectionType?,
    val recirculation: Boolean?,
)

/** core 공조 타입과 Pleos HVAC 값 사이 변환, 반영 확인(F6.8). 판단 로직은 넣지 않는다. */
object PleosClimateMapper {

    fun toSdk(direction: FanDirection): FanDirectionType = when (direction) {
        FanDirection.FACE -> FanDirectionType.FACE
        FanDirection.FLOOR -> FanDirectionType.FLOOR
        FanDirection.FACE_AND_FLOOR -> FanDirectionType.FACE_AND_FLOOR
        FanDirection.DEFROST -> FanDirectionType.DEFROST
    }

    /** core가 다루지 않는 조합(앞유리+얼굴 등)은 null: 기본 단계에서 그 항목을 바꾸지 않는다. */
    fun fromSdk(direction: FanDirectionType?): FanDirection? = when (direction) {
        FanDirectionType.FACE -> FanDirection.FACE
        FanDirectionType.FLOOR -> FanDirection.FLOOR
        FanDirectionType.FACE_AND_FLOOR -> FanDirection.FACE_AND_FLOOR
        FanDirectionType.DEFROST -> FanDirection.DEFROST
        else -> null
    }

    /** 요청한 값과 다시 읽은 값이 다른 항목 이름들. 읽지 못한 항목은 비교하지 않는다. */
    fun mismatches(target: ClimateTarget, read: ClimateReadback): List<String> = buildList {
        read.setTempC?.let { if (abs(it - target.setTempC) > TEMP_TOLERANCE_C) add("온도") }
        if (target.fanSpeed != null && read.fanSpeed != null && read.fanSpeed != target.fanSpeed) add("풍량")
        val wantedDirection = target.fanDirection
        if (wantedDirection != null && read.fanDirection != null && read.fanDirection != toSdk(wantedDirection)) {
            add("풍향")
        }
        // 내기 순환(recirculation)이 꺼져 있어야 외기 유입
        if (read.recirculation != null && read.recirculation == target.outsideAir) add("외기")
    }

    private const val TEMP_TOLERANCE_C = 0.1f
}
