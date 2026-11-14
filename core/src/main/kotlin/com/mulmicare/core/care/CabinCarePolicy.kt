package com.mulmicare.core.care

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 실내 자동 케어 (spec F6, F9.3).
 *
 * - 목표값은 늘 케어를 켤 때 읽은 [ClimateBaseline]에서 계산한다(현재값에서 반복해 빼지 않는다).
 * - 기본: 외기 유입, 기준 온도 − [Thresholds.baseDropC].
 * - 강화: 기본에서 [Thresholds.boostExtraDropC] 더 낮추고, 풍량 +1(최대 제한), 상체 방향.
 * - 강화 진입: 굽은 길 구간 시작, "힘들어해요". 구간이 끝나거나 힘들어해요 뒤 [Thresholds.boostHoldMs]가
 *   지나면 기본으로. 구간 안에서는 돌아가지 않는다.
 * - 겨울: 낮추는 폭에 [Thresholds.winterDropScale]을 곱하고 [Thresholds.winterMinSetC] 아래로 내리지 않는다.
 *   바깥 기온을 모르면 12~2월을 겨울로 본다.
 * - 어떤 경우에도 기준 온도보다 올리지 않고, 0.5℃ 단위로 맞출 때는 따뜻한 쪽으로 올린다.
 */
class CabinCarePolicy(
    private val t: Thresholds = Thresholds(),
    startBoost: Boolean = false,
) {
    var level: CareLevel = if (startBoost) CareLevel.BOOST else CareLevel.BASE
        private set

    private var baseline: ClimateBaseline? = null
    private var winter = false
    private var inSegment = false
    private var revertAtMs: Long? = null
    private var ended = false

    /** 케어를 켤 때 읽은 공조 상태. [outsideC]를 모르면 null, [month]는 1~12. */
    fun onBaseline(atMs: Long, baseline: ClimateBaseline, outsideC: Float?, month: Int): List<Effect> {
        this.baseline = baseline
        winter = if (outsideC != null) outsideC < t.winterOutsideC else month in WINTER_MONTHS
        return applyCurrent()
    }

    fun onSegmentStarted(atMs: Long): List<Effect> {
        inSegment = true
        revertAtMs = null
        return boost()
    }

    fun onSegmentEnded(atMs: Long): List<Effect> {
        inSegment = false
        if (level == CareLevel.BOOST) revertAtMs = atMs + t.boostHoldMs
        return emptyList()
    }

    fun onPassengerStruggling(atMs: Long): List<Effect> {
        if (!inSegment) revertAtMs = atMs + t.boostHoldMs
        return boost()
    }

    /** 케어가 끝남(정지·도착). 공조를 기준값으로 되돌린다. 이후에는 아무것도 바꾸지 않는다. */
    fun onCareEnded(atMs: Long): List<Effect> {
        if (ended) return emptyList()
        ended = true
        revertAtMs = null
        val b = baseline ?: return emptyList()
        return listOf(Effect.RestoreClimate(b))
    }

    fun onTick(atMs: Long): List<Effect> {
        val at = revertAtMs ?: return emptyList()
        if (ended || inSegment || atMs < at) return emptyList()
        revertAtMs = null
        level = CareLevel.BASE
        return applyCurrent()
    }

    private fun boost(): List<Effect> {
        if (ended || level == CareLevel.BOOST) return emptyList()
        level = CareLevel.BOOST
        return applyCurrent()
    }

    private fun applyCurrent(): List<Effect> {
        val b = baseline ?: return emptyList()
        return listOf(Effect.ApplyClimate(level, target(b)))
    }

    private fun target(b: ClimateBaseline): ClimateTarget {
        val boosted = level == CareLevel.BOOST
        val drop = (t.baseDropC + if (boosted) t.boostExtraDropC else 0f) * (if (winter) t.winterDropScale else 1f)
        var temp = b.setTempC - drop
        if (winter) temp = max(temp, t.winterMinSetC)
        temp = min(ceilToStep(temp), b.setTempC)

        val fan = if (boosted) b.fanSpeed?.let { fan -> b.fanSpeedMax?.let { min(fan + 1, it) } ?: (fan + 1) } else b.fanSpeed
        val direction = if (boosted) FanDirection.FACE else b.fanDirection
        return ClimateTarget(setTempC = temp, outsideAir = true, fanSpeed = fan, fanDirection = direction)
    }

    private fun ceilToStep(temp: Float): Float {
        val step = t.setTempStepC
        return ceil(temp / step - FLOAT_SLACK) * step
    }

    private companion object {
        val WINTER_MONTHS = setOf(12, 1, 2)
        const val FLOAT_SLACK = 1e-4f
    }
}
