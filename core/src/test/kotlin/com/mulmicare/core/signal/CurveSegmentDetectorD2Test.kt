package com.mulmicare.core.signal

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.signal.SegmentEvent.Ended
import com.mulmicare.core.signal.SegmentEvent.Started
import com.mulmicare.core.testing.TraceBuilder
import com.mulmicare.core.testing.trace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * spec D2: 이벤트 수가 기준 아래인 상태가 1분 동안 이어지면 구간 종료.
 * "기준 아래가 된 시점" = 가장 오래된 이벤트가 창에서 빠져 횟수가 기준 밑으로 내려간 순간.
 */
class CurveSegmentDetectorD2Test {

    private val t = Thresholds()
    private val detector = CurveSegmentDetector(t)
    private val curveDeg = t.steerEventDeg * 2
    private var nextSign = 1f

    private fun run(signals: List<VehicleSignal>): List<SegmentEvent> =
        signals.mapNotNull(detector::onSignal)

    /** 조향 이벤트 1회. 방향을 번갈아 바꿔 매번 새 이벤트가 되게 한다. */
    private fun TraceBuilder.steerEvent(at: Duration) {
        val deg = nextSign * curveDeg
        nextSign = -nextSign
        at(at) { steer(deg) }
    }

    /** [from]초부터 1초 간격으로 기준 횟수만큼 이벤트를 넣는다. 마지막 이벤트 시각을 돌려준다. */
    private fun TraceBuilder.enterCurve(from: Duration): Duration {
        repeat(t.curveEventsToStart) { i -> steerEvent(from + i.seconds) }
        return from + (t.curveEventsToStart - 1).seconds
    }

    private val window get() = t.curveWindowMs.milliseconds
    private val quiet get() = t.curveQuietToEndMs.milliseconds

    /** [firstEventAt]의 이벤트가 창에서 빠진 뒤 조용한 시간이 다 차는 시각. */
    private fun endAt(firstEventAt: Duration) = firstEventAt + window + quiet

    private val Duration.ms get() = inWholeMilliseconds

    // --- 시간 경계: 1분 직전 / 정확히 1분 ---

    @Test
    fun `D2 기준 아래로 내려간 지 1분이 되기 직전에는 구간이 끝나지 않는다`() {
        val signals = trace {
            val startAt = enterCurve(0.seconds)
            ticks(startAt, endAt(0.seconds) - 1.milliseconds, every = 1.seconds)
            atMs(endAt(0.seconds).ms - 1) { tick() }
        }
        assertEquals(listOf(Started((t.curveEventsToStart - 1).seconds.ms)), run(signals))
    }

    @Test
    fun `D2 기준 아래로 1분 유지되면 그 순간 구간이 끝난다`() {
        val signals = trace {
            enterCurve(0.seconds)
            atMs(endAt(0.seconds).ms) { tick() }
        }
        assertEquals(
            listOf(Started((t.curveEventsToStart - 1).seconds.ms), Ended(endAt(0.seconds).ms)),
            run(signals),
        )
    }

    @Test
    fun `D2 종료는 한 번만 알린다`() {
        val signals = trace {
            enterCurve(0.seconds)
            ticks(endAt(0.seconds), endAt(0.seconds) + 2.seconds * 60, every = 1.seconds)
        }
        assertEquals(1, run(signals).count { it is Ended })
    }

    // --- 조용한 동안의 재진입 ---

    @Test
    fun `D2 조용한 동안 다시 기준 이상이 되면 종료 타이머가 처음부터 다시 시작된다`() {
        val reentryFrom = 90.seconds
        val signals = trace {
            enterCurve(0.seconds)
            enterCurve(reentryFrom)
            ticks(0.seconds, endAt(reentryFrom), every = 1.seconds)
        }
        val events = run(signals)
        assertEquals(
            listOf(Started((t.curveEventsToStart - 1).seconds.ms), Ended(endAt(reentryFrom).ms)),
            events,
        )
    }

    @Test
    fun `D2 조용한 동안 기준에 못 미치는 이벤트가 있어도 종료 타이머는 이어진다`() {
        val signals = trace {
            enterCurve(0.seconds)
            steerEvent(90.seconds) // 창 안에 1회뿐이라 기준 아래 그대로
            ticks(0.seconds, endAt(0.seconds), every = 1.seconds)
        }
        assertEquals(Ended(endAt(0.seconds).ms), run(signals).last())
    }

    // --- 종료 뒤 ---

    @Test
    fun `D2 구간이 끝난 뒤 다시 기준에 이르면 새 구간이 시작된다`() {
        val nextFrom = endAt(0.seconds) + 10.seconds
        val signals = trace {
            enterCurve(0.seconds)
            atMs(endAt(0.seconds).ms) { tick() }
            enterCurve(nextFrom)
        }
        assertEquals(
            listOf(
                Started((t.curveEventsToStart - 1).seconds.ms),
                Ended(endAt(0.seconds).ms),
                Started((nextFrom + (t.curveEventsToStart - 1).seconds).ms),
            ),
            run(signals),
        )
    }
}
