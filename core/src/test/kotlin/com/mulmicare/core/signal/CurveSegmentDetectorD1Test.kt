package com.mulmicare.core.signal

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.signal.SegmentEvent.Started
import com.mulmicare.core.testing.TraceBuilder
import com.mulmicare.core.testing.trace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** spec D1: 최근 1분 동안 조향 변화 + 감속 횟수가 기준 이상이면 굽은 길 구간 시작. */
class CurveSegmentDetectorD1Test {

    private val t = Thresholds()
    private val detector = CurveSegmentDetector(t)

    private fun run(signals: List<VehicleSignal>): List<SegmentEvent> =
        signals.mapNotNull(detector::onSignal)

    /** 핸들을 [deg]까지 돌렸다가 2초 뒤 가운데로 돌아오는 한 번의 조향. */
    private fun TraceBuilder.steerPulse(at: Duration, deg: Float) {
        at(at) { steer(deg) }
        at(at + 2.seconds) { steer(0f) }
    }

    /** 1초 동안 [dropKph]만큼 속도가 줄었다가 유지되는 한 번의 감속. */
    private fun TraceBuilder.decel(at: Duration, fromKph: Float = 60f, dropKph: Float) {
        at(at) { speed(fromKph) }
        at(at + 1.seconds) { speed(fromKph - dropKph) }
        at(at + 2.seconds) { speed(fromKph - dropKph) }
    }

    private val curveDeg get() = t.steerEventDeg * 2

    // --- 횟수 경계: 기준 -1 / 기준 / 기준 +1 ---

    @Test
    fun `D1 1분 안의 조향 이벤트가 기준보다 1회 적으면 구간이 시작되지 않는다`() {
        val signals = trace {
            repeat(t.curveEventsToStart - 1) { i -> steerPulse((i * 10).seconds, curveDeg) }
        }
        assertEquals(emptyList(), run(signals))
    }

    @Test
    fun `D1 1분 안의 조향 이벤트가 기준 횟수에 이르는 순간 구간이 시작된다`() {
        val signals = trace {
            repeat(t.curveEventsToStart) { i -> steerPulse((i * 10).seconds, curveDeg) }
        }
        val lastEventMs = ((t.curveEventsToStart - 1) * 10).seconds.inWholeMilliseconds
        assertEquals(listOf(Started(lastEventMs)), run(signals))
    }

    @Test
    fun `D1 기준을 넘는 이벤트가 이어져도 시작은 한 번만 알린다`() {
        val signals = trace {
            repeat(t.curveEventsToStart + 1) { i -> steerPulse((i * 10).seconds, curveDeg) }
        }
        assertEquals(1, run(signals).count { it is Started })
    }

    // --- 창 경계: 첫 이벤트로부터 1분 미만 / 정확히 1분 ---

    @Test
    fun `D1 첫 이벤트로부터 1분이 되기 직전의 이벤트까지는 같은 창으로 센다`() {
        val lastAt = t.curveWindowMs.milliseconds - 1.milliseconds
        val signals = trace {
            repeat(t.curveEventsToStart - 1) { i -> steerPulse((i * 10).seconds, curveDeg) }
            at(lastAt) { steer(curveDeg) }
        }
        assertEquals(listOf(Started(lastAt.inWholeMilliseconds)), run(signals))
    }

    @Test
    fun `D1 첫 이벤트로부터 정확히 1분이 지나면 첫 이벤트는 창 밖이다`() {
        val signals = trace {
            repeat(t.curveEventsToStart - 1) { i -> steerPulse((i * 10).seconds, curveDeg) }
            at(t.curveWindowMs.milliseconds) { steer(curveDeg) }
        }
        assertEquals(emptyList(), run(signals))
    }

    // --- 조향 이벤트 정의 ---

    @Test
    fun `D1 기준 각도 아래의 조향은 이벤트가 아니다 - 고속도로 차로 변경`() {
        val laneChangeDeg = t.steerEventDeg * 0.5f
        val signals = trace {
            repeat(t.curveEventsToStart * 3) { i ->
                steerPulse((i * 5).seconds, if (i % 2 == 0) laneChangeDeg else -laneChangeDeg)
            }
        }
        assertEquals(emptyList(), run(signals))
    }

    @Test
    fun `D1 기준 각도와 같은 조향은 이벤트로 센다`() {
        val signals = trace {
            repeat(t.curveEventsToStart) { i -> steerPulse((i * 10).seconds, t.steerEventDeg) }
        }
        assertEquals(1, run(signals).size)
    }

    @Test
    fun `D1 기준 각도를 넘은 채 유지되는 긴 회전은 한 번만 센다`() {
        val signals = trace {
            // 20초 동안 계속 왼쪽으로 돌고 있음 (샘플 여러 개)
            for (s in 0..20 step 2) at(s.seconds) { steer(-curveDeg) }
            at(22.seconds) { steer(0f) }
            // 이벤트 합계가 기준 -1이 되도록 나머지를 채운다
            repeat(t.curveEventsToStart - 2) { i -> steerPulse((30 + i * 5).seconds, curveDeg) }
        }
        assertEquals(emptyList(), run(signals))
    }

    @Test
    fun `D1 왼쪽에서 오른쪽으로 가운데를 거치지 않고 넘어가도 각각 센다`() {
        val signals = trace {
            repeat(t.curveEventsToStart) { i ->
                at((i * 3).seconds) { steer(if (i % 2 == 0) -curveDeg else curveDeg) }
            }
        }
        assertEquals(1, run(signals).size)
    }

    // --- 감속 이벤트 정의: 기준보다 약함 / 같음, 지속, 짧은 샘플 간격 ---

    @Test
    fun `D1 감속 이벤트도 조향 이벤트와 함께 센다`() {
        val signals = trace {
            repeat(t.curveEventsToStart - 1) { i -> steerPulse((i * 10).seconds, curveDeg) }
            decel(40.seconds, dropKph = t.decelEventKphPerSec + 3f)
        }
        assertEquals(1, run(signals).size)
    }

    @Test
    fun `D1 감속률이 기준보다 약하면 이벤트가 아니다`() {
        val signals = trace {
            repeat(t.curveEventsToStart - 1) { i -> steerPulse((i * 10).seconds, curveDeg) }
            decel(40.seconds, dropKph = t.decelEventKphPerSec - 0.5f)
        }
        assertEquals(emptyList(), run(signals))
    }

    @Test
    fun `D1 감속률이 기준과 같으면 이벤트로 센다`() {
        val signals = trace {
            repeat(t.curveEventsToStart - 1) { i -> steerPulse((i * 10).seconds, curveDeg) }
            decel(40.seconds, dropKph = t.decelEventKphPerSec)
        }
        assertEquals(1, run(signals).size)
    }

    @Test
    fun `D1 감속이 여러 샘플 동안 이어져도 한 번만 센다`() {
        val signals = trace {
            // 5초 동안 매초 기준 이상으로 감속
            for (s in 0..5) at(s.seconds) { speed(80f - s * (t.decelEventKphPerSec + 1f)) }
            repeat(t.curveEventsToStart - 2) { i -> steerPulse((10 + i * 5).seconds, curveDeg) }
        }
        assertEquals(emptyList(), run(signals))
    }

    @Test
    fun `D1 속도 샘플 간격이 너무 짧으면 그 간격으로 감속률을 계산하지 않는다`() {
        val shortGap = (t.minSpeedDtMs - 1).milliseconds
        val signals = trace {
            repeat(t.curveEventsToStart - 1) { i -> steerPulse((i * 10).seconds, curveDeg) }
            at(40.seconds) { speed(60f) }
            // 짧은 간격 안에서는 급감속처럼 보이지만 실제 변화는 작다 (센서 노이즈)
            at(40.seconds + shortGap) { speed(60f - t.decelEventKphPerSec * 0.5f) }
            at(41.seconds) { speed(60f - t.decelEventKphPerSec * 0.5f) }
        }
        assertEquals(emptyList(), run(signals))
    }
}
