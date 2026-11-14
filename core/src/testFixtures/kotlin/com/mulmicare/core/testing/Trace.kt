package com.mulmicare.core.testing

import com.mulmicare.core.signal.GearPos
import com.mulmicare.core.signal.TurnState
import com.mulmicare.core.signal.VehicleSignal
import kotlin.time.Duration

/**
 * 테스트용 신호 시퀀스 빌더.
 *
 * ```
 * val signals = trace {
 *     at(0.seconds)  { gear(GearPos.D); speed(50f) }
 *     at(10.seconds) { steer(-30f) }
 * }
 * ```
 * 결과는 시간 순으로 정렬된다.
 */
fun trace(block: TraceBuilder.() -> Unit): List<VehicleSignal> =
    TraceBuilder().apply(block).build()

class TraceBuilder {
    private val signals = mutableListOf<VehicleSignal>()

    fun at(time: Duration, block: At.() -> Unit) {
        At(time.inWholeMilliseconds).block()
    }

    fun atMs(ms: Long, block: At.() -> Unit) {
        At(ms).block()
    }

    /** [from]부터 [to]까지(포함) [every] 간격으로 Tick을 넣는다. */
    fun ticks(from: Duration, to: Duration, every: Duration) {
        var ms = from.inWholeMilliseconds
        while (ms <= to.inWholeMilliseconds) {
            signals += VehicleSignal.Tick(ms)
            ms += every.inWholeMilliseconds
        }
    }

    fun build(): List<VehicleSignal> = signals.sortedBy { it.atMs }

    inner class At(private val atMs: Long) {
        fun steer(deg: Float) { signals += VehicleSignal.SteeringAngle(atMs, deg) }
        fun speed(kph: Float) { signals += VehicleSignal.Speed(atMs, kph) }
        fun turnSignal(state: TurnState) { signals += VehicleSignal.TurnSignal(atMs, state) }
        fun brake(pressed: Boolean) { signals += VehicleSignal.Brake(atMs, pressed) }
        fun gear(pos: GearPos) { signals += VehicleSignal.Gear(atMs, pos) }
        fun tick() { signals += VehicleSignal.Tick(atMs) }
    }
}
