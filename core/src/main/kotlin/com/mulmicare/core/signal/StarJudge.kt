package com.mulmicare.core.signal

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect
import kotlin.math.abs

/**
 * 별 판정 (spec F5.2~F5.5).
 *
 * - 판마다 별 3개로 시작한다.
 * - 급조향(핸들 회전 속도) 또는 급감속(1초 이상 구간의 속도 변화율)이 시작되는 순간 별 하나가 떨어진다.
 * - 별이 떨어진 뒤 [Thresholds.harshRefractoryMs] 안의 급한 동작은 종류와 관계없이 같은 동작으로 본다.
 * - 1개 아래로는 내려가지 않고, 떨어질 별이 없으면 효과음도 내지 않는다.
 * - 제동 페달은 판정에 쓰지 않는다. 감속은 속도 변화로만 판단한다.
 *
 * 판이 아닐 때도 신호 상태는 따라가서, 판이 시작될 때 이미 진행 중이던 동작은 세지 않는다.
 */
class StarJudge(private val t: Thresholds = Thresholds()) {

    var stars: Int = START_STARS
        private set

    private var inRound = false
    private var lastHarshAtMs: Long? = null

    private val harshSteerOnset = OnsetDetector(t.minSteerDtMs) { degPerSec ->
        abs(degPerSec) >= t.harshSteerDegPerSec
    }
    // 급감속은 1초 이상 떨어진 샘플로 잰다: 계단식 갱신·센서 잡음의 순간 변화율에 속지 않게
    private val harshDecelOnset = OnsetDetector(t.harshDecelWindowMs) { kphPerSec ->
        kphPerSec <= -t.harshDecelKphPerSec
    }

    fun startRound() {
        inRound = true
        stars = START_STARS
        lastHarshAtMs = null
    }

    fun endRound(): Int {
        inRound = false
        return stars
    }

    fun onSignal(signal: VehicleSignal): Effect.StarLost? {
        val harshStarted = when (signal) {
            is VehicleSignal.SteeringAngle -> harshSteerOnset.onSample(signal.atMs, signal.deg)
            is VehicleSignal.Speed -> harshDecelOnset.onSample(signal.atMs, signal.kph)
            else -> false
        }
        if (!harshStarted || !inRound) return null

        val now = signal.atMs
        val last = lastHarshAtMs
        if (last != null && now - last < t.harshRefractoryMs) return null
        lastHarshAtMs = now

        if (stars <= MIN_STARS) return null
        stars--
        return Effect.StarLost
    }

    companion object {
        /** F5.2 */
        const val START_STARS = 3

        /** F5.4 */
        const val MIN_STARS = 1
    }
}
