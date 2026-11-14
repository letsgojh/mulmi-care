package com.mulmicare.core.signal

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect.StarLost
import com.mulmicare.core.testing.TraceBuilder
import com.mulmicare.core.testing.trace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** spec F5: 별 3개로 시작, 급조향·급감속마다 하나씩 떨어지고, 결과는 3·2·1개 중 하나. */
class StarJudgeTest {

    private val t = Thresholds()
    private val steerDt = 100.milliseconds
    private val decelDt = 1.seconds

    private var angle = 0f
    private var speed = 100f

    /** 핸들을 [degPerSec] 속도로 [steerDt] 동안 돌린 뒤 잠시 멈춘다. 감지 시각을 돌려준다. */
    private fun TraceBuilder.steerJerk(at: Duration, degPerSec: Float = t.harshSteerDegPerSec + 50f): Duration {
        val delta = degPerSec * steerDt.inWholeMilliseconds / 1000f
        val start = angle
        val end = if (start > 0f) start - delta else start + delta
        at(at) { steer(start) }
        at(at + steerDt) { steer(end) }
        at(at + steerDt + 500.milliseconds) { steer(end) }
        angle = end
        return at + steerDt
    }

    /** [decelDt] 동안 [kphPerSec]로 감속한 뒤 속도를 유지한다. 감지 시각을 돌려준다. */
    private fun TraceBuilder.decelJerk(at: Duration, kphPerSec: Float = t.harshDecelKphPerSec + 2f): Duration {
        val start = speed
        val end = start - kphPerSec * decelDt.inWholeMilliseconds / 1000f
        at(at) { speed(start) }
        at(at + decelDt) { speed(end) }
        at(at + decelDt + 500.milliseconds) { speed(end) }
        speed = end
        return at + decelDt
    }

    private class Result(val lost: List<Long>, val stars: Int)

    private fun round(signals: List<VehicleSignal>): Result {
        val judge = StarJudge(t).apply { startRound() }
        val lost = signals.mapNotNull { s -> judge.onSignal(s)?.let { s.atMs } }
        return Result(lost, judge.endRound())
    }

    // --- F5.2 시작 ---

    @Test
    fun `F5_2 급한 동작이 없으면 별 3개를 지킨다`() {
        val calm = t.harshSteerDegPerSec / 4 // 1초마다 좌우로 바꿔도 기준의 절반 속도
        val signals = trace {
            for (s in 0..30) at(s.seconds) { steer(if (s % 2 == 0) -calm else calm); speed(60f - s * 0.5f) }
        }
        val result = round(signals)
        assertEquals(emptyList(), result.lost)
        assertEquals(3, result.stars)
    }

    // --- F5.3 급조향: 기준 아래 / 같음 ---

    @Test
    fun `F5_3 회전 속도가 급조향 기준보다 조금 느리면 별이 떨어지지 않는다`() {
        val signals = trace { steerJerk(0.seconds, degPerSec = t.harshSteerDegPerSec - 1f) }
        assertEquals(3, round(signals).stars)
    }

    @Test
    fun `F5_3 회전 속도가 급조향 기준과 같으면 별이 하나 떨어진다`() {
        var detectedAt = Duration.ZERO
        val signals = trace { detectedAt = steerJerk(0.seconds, degPerSec = t.harshSteerDegPerSec) }
        val result = round(signals)
        assertEquals(listOf(detectedAt.inWholeMilliseconds), result.lost)
        assertEquals(2, result.stars)
    }

    // --- F5.3 급감속: 기준 아래 / 같음 ---

    @Test
    fun `F5_3 감속률이 급감속 기준보다 조금 약하면 별이 떨어지지 않는다`() {
        val signals = trace { decelJerk(0.seconds, kphPerSec = t.harshDecelKphPerSec - 0.5f) }
        assertEquals(3, round(signals).stars)
    }

    @Test
    fun `F5_3 감속률이 급감속 기준과 같으면 별이 하나 떨어진다`() {
        var detectedAt = Duration.ZERO
        val signals = trace { detectedAt = decelJerk(0.seconds, kphPerSec = t.harshDecelKphPerSec) }
        val result = round(signals)
        assertEquals(listOf(detectedAt.inWholeMilliseconds), result.lost)
        assertEquals(2, result.stars)
    }

    // --- F5.3 한 번의 동작은 한 번만 ---

    @Test
    fun `F5_3 급조향이 여러 샘플에 걸쳐 이어져도 별은 하나만 떨어진다`() {
        val fast = t.harshSteerDegPerSec + 100f
        val signals = trace {
            for (i in 0..8) atMs(i * 100L) { steer(i * fast / 10f) }
        }
        assertEquals(2, round(signals).stars)
    }

    @Test
    fun `F5_3 급감속이 여러 샘플에 걸쳐 이어져도 별은 하나만 떨어진다`() {
        val hard = t.harshDecelKphPerSec + 2f
        val signals = trace {
            for (s in 0..5) at(s.seconds) { speed(100f - s * hard) }
        }
        assertEquals(2, round(signals).stars)
    }

    @Test
    fun `F5_3 급조향과 급감속이 한꺼번에 일어나면 별은 하나만 떨어진다`() {
        val signals = trace {
            steerJerk(1.seconds)
            decelJerk(0.5.seconds)
        }
        assertEquals(2, round(signals).stars)
    }

    // --- F5.3 재판정 금지 시간 경계 ---

    @Test
    fun `F5_3 별이 떨어진 뒤 재판정 금지 시간 안의 급조향은 같은 동작으로 본다`() {
        val refractory = t.harshRefractoryMs.milliseconds
        val signals = trace {
            val first = steerJerk(0.seconds)
            steerJerk(first + refractory - 1.milliseconds - steerDt)
        }
        assertEquals(2, round(signals).stars)
    }

    @Test
    fun `F5_3 재판정 금지 시간이 지난 뒤의 급조향은 별을 하나 더 떨어뜨린다`() {
        val refractory = t.harshRefractoryMs.milliseconds
        val signals = trace {
            val first = steerJerk(0.seconds)
            steerJerk(first + refractory - steerDt)
        }
        assertEquals(1, round(signals).stars)
    }

    // --- F5.4 최소 1개 ---

    @Test
    fun `F5_4 별은 1개 아래로 내려가지 않고 떨어질 별이 없으면 효과음도 없다`() {
        val signals = trace {
            steerJerk(0.seconds)
            steerJerk(5.seconds)
            steerJerk(10.seconds)
            decelJerk(15.seconds)
        }
        val result = round(signals)
        assertEquals(2, result.lost.size)
        assertEquals(1, result.stars)
    }

    // --- 보조 신호와 노이즈 ---

    @Test
    fun `F5_3 제동 페달만 밟고 속도가 크게 줄지 않으면 별이 떨어지지 않는다`() {
        val signals = trace {
            at(0.seconds) { brake(true); speed(60f) }
            at(1.seconds) { speed(58f) }
            at(2.seconds) { brake(false); speed(57f) }
        }
        assertEquals(3, round(signals).stars)
    }

    @Test
    fun `F5_3 조향 샘플 간격이 너무 짧으면 그 간격으로 회전 속도를 계산하지 않는다`() {
        val shortGap = (t.minSteerDtMs - 1).milliseconds
        val signals = trace {
            at(0.seconds) { steer(10f) }
            // 짧은 간격의 값 튐: 이 간격으로 계산하면 급조향처럼 보인다
            at(shortGap) { steer(10f + t.harshSteerDegPerSec * 0.1f) }
            at(1.seconds) { steer(10f + t.harshSteerDegPerSec * 0.1f) }
        }
        assertEquals(3, round(signals).stars)
    }

    @Test
    fun `F5_3 속도가 계단식으로 갱신돼 순간 변화율만 큰 완만한 감속은 급감속이 아니다`() {
        // 리허설: 평균 5.4km/h/s의 완만한 감속(커브 앞)에서 별이 떨어졌다. 0.5초마다 2.7km/h씩 계단으로 들어오는 경우
        val signals = trace {
            var kph = 60f
            for (i in 0..40) {
                val ms = i * 100L
                if (i > 0 && i % 5 == 0) kph -= 2.7f
                atMs(ms) { speed(kph) }
            }
        }
        assertEquals(3, round(signals).stars)
    }

    @Test
    fun `F5_3 1초 넘게 이어지는 급감속은 계단식으로 들어와도 급감속이다`() {
        val step = t.harshDecelKphPerSec + 2f // 초당 이만큼
        val signals = trace {
            var kph = 80f
            for (i in 0..30) {
                val ms = i * 100L
                if (i > 0 && i % 5 == 0) kph -= step / 2 // 0.5초마다 계단
                atMs(ms) { speed(kph) }
            }
        }
        assertEquals(2, round(signals).stars)
    }

    // --- 판 경계 ---

    @Test
    fun `F5 판이 아닐 때의 급한 동작은 세지 않는다`() {
        val judge = StarJudge(t)
        val before = trace { steerJerk(0.seconds); decelJerk(5.seconds) }
        assertEquals(emptyList(), before.mapNotNull(judge::onSignal))
    }

    @Test
    fun `F5_2 새 판은 다시 별 3개로 시작한다`() {
        val judge = StarJudge(t)
        judge.startRound()
        trace { steerJerk(0.seconds) }.forEach { judge.onSignal(it) }
        assertEquals(2, judge.endRound())

        judge.startRound()
        assertEquals(3, judge.stars)
        assertEquals(3, judge.endRound())
    }

    @Test
    fun `F5_5 별이 떨어질 때 내는 것은 효과음 하나뿐이다`() {
        val judge = StarJudge(t).apply { startRound() }
        val effects = trace { steerJerk(0.seconds) }.mapNotNull(judge::onSignal)
        assertEquals(listOf(StarLost), effects)
    }
}
