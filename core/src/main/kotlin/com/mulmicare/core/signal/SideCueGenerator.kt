package com.mulmicare.core.signal

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect
import kotlin.math.abs

/**
 * 좌우 소리 신호 (spec F4.2, F4.3, F2.5).
 *
 * - F4.3 방향지시등이 켜지는 순간 그 방향 소리 (차가 움직이기 전 예고).
 * - F4.2 핸들이 기준 각도 이상 한쪽으로 들어서는 순간 그 방향 소리 (움직임과 동시).
 *   가운데([Thresholds.steerCueResetDeg] 미만)로 돌아와야 같은 쪽 소리를 다시 낸다.
 * - 방향지시등이 켜져 있거나 꺼진 직후의 같은 쪽 조향은 같은 동작으로 보고 다시 울리지 않는다.
 * - F2.5 처음 [Thresholds.voiceCueCount]번은 말과 함께. 놀이를 다시 시작해도 이어서 센다.
 *
 * 놀이 중이 아닐 때도 핸들·방향지시등 상태는 계속 따라가서,
 * 놀이를 시작하는 순간 이미 돌고 있던 회전에는 소리를 내지 않는다.
 */
class SideCueGenerator(private val t: Thresholds = Thresholds()) {

    private var playing = false
    private var voiceCuesLeft = t.voiceCueCount

    /** 핸들이 들어서 있는 쪽. 가운데면 null. */
    private var steerSide: Side? = null
    private var turnState = TurnState.OFF
    private var lastTurnOff: TurnOff? = null

    private data class TurnOff(val side: Side, val atMs: Long)

    fun startPlay() {
        playing = true
    }

    fun stopPlay() {
        playing = false
    }

    fun onSignal(signal: VehicleSignal): Effect.PlaySideCue? {
        val side = when (signal) {
            is VehicleSignal.SteeringAngle -> steeringEnteredSide(signal)
            is VehicleSignal.TurnSignal -> turnSignalTurnedOnSide(signal)
            else -> null
        }
        if (side == null || !playing) return null
        return cue(side)
    }

    private fun steeringEnteredSide(signal: VehicleSignal.SteeringAngle): Side? {
        val deg = signal.deg
        val side = when {
            deg <= -t.steerCueDeg -> Side.LEFT
            deg >= t.steerCueDeg -> Side.RIGHT
            abs(deg) < t.steerCueResetDeg -> null
            else -> return null // 가운데와 기준 사이: 이전 상태 유지 (떨림 방지)
        }
        val previous = steerSide
        steerSide = side
        if (side == null || side == previous) return null
        return if (isSameManeuverAsTurnSignal(side, signal.atMs)) null else side
    }

    private fun turnSignalTurnedOnSide(signal: VehicleSignal.TurnSignal): Side? {
        val prev = turnState
        turnState = signal.state
        if (prev == signal.state) return null
        prev.side()?.let { lastTurnOff = TurnOff(it, signal.atMs) }
        return signal.state.side()
    }

    private fun isSameManeuverAsTurnSignal(side: Side, nowMs: Long): Boolean {
        if (turnState.side() == side) return true
        val off = lastTurnOff ?: return false
        return off.side == side && nowMs - off.atMs < t.turnSignalSuppressMs
    }

    private fun cue(side: Side): Effect.PlaySideCue {
        val withVoice = voiceCuesLeft > 0
        if (withVoice) voiceCuesLeft--
        return Effect.PlaySideCue(side, withVoice)
    }

    private fun TurnState.side(): Side? = when (this) {
        TurnState.LEFT -> Side.LEFT
        TurnState.RIGHT -> Side.RIGHT
        TurnState.OFF, TurnState.HAZARD -> null
    }
}
