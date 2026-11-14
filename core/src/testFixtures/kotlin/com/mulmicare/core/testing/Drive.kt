package com.mulmicare.core.testing

import com.mulmicare.core.effect.Effect
import com.mulmicare.core.session.CareSession
import com.mulmicare.core.signal.VehicleSignal

/** 음성 응답 한 번. */
data class Say(val atMs: Long, val text: String)

/** 시각과 함께 기록한 Effect. */
data class Timed(val atMs: Long, val effect: Effect)

/**
 * 차량 신호와 음성 응답을 시간 순으로 세션에 넣고, 나온 Effect를 시각과 함께 돌려준다.
 * 같은 시각이면 차량 신호를 먼저 넣는다.
 */
fun CareSession.drive(signals: List<VehicleSignal>, says: List<Say> = emptyList()): List<Timed> {
    val steps: List<Pair<Long, () -> List<Effect>>> =
        signals.map { s -> s.atMs to { onSignal(s) } } +
            says.map { say -> say.atMs to { onSpeech(say.atMs, say.text) } }
    return steps.sortedBy { it.first }.flatMap { (atMs, step) -> step().map { Timed(atMs, it) } }
}
