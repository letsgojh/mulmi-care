package com.mulmicare.core.session

import com.mulmicare.core.care.CareLevel
import com.mulmicare.core.care.ClimateBaseline
import com.mulmicare.core.care.FanDirection
import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect.ApplyClimate
import com.mulmicare.core.effect.Effect
import com.mulmicare.core.effect.Effect.FindRestStop
import com.mulmicare.core.effect.Effect.Listen
import com.mulmicare.core.effect.Effect.PassengerStruggling
import com.mulmicare.core.effect.Effect.PlaySideCue
import com.mulmicare.core.effect.Effect.Speak
import com.mulmicare.core.effect.Effect.StarLost
import com.mulmicare.core.effect.Effect.StartBeat
import com.mulmicare.core.effect.Effect.StopListening
import com.mulmicare.core.place.RestStop
import com.mulmicare.core.signal.TurnState
import com.mulmicare.core.testing.Say
import com.mulmicare.core.testing.Timed
import com.mulmicare.core.testing.TraceBuilder
import com.mulmicare.core.testing.drive
import com.mulmicare.core.testing.trace
import com.mulmicare.core.voice.VoiceIntent.NO
import com.mulmicare.core.voice.VoiceIntent.YES
import com.mulmicare.core.voice.VoiceLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CareSessionTest {

    private val t = Thresholds()

    /** 굽은 길에서 핸들을 돌린 앞바퀴 각도. 조향 이벤트 기준의 두 배. */
    private val turn = t.steerEventDeg * 2
    private val session = CareSession(t)

    /** [fromSec]초부터 5초 간격으로 좌·우·좌로 핸들을 돌려 굽은 길 구간에 들어선다. 구간 시작 시각(초)을 돌려준다. */
    private fun TraceBuilder.enterCurve(fromSec: Int): Int {
        listOf(-turn, turn, -turn).forEachIndexed { i, deg ->
            at((fromSec + i * 5).seconds) { steer(deg) }
            at((fromSec + i * 5 + 2).seconds) { steer(0f) }
        }
        return fromSec + 10
    }

    /** [atSec]초에 1초 동안 시속 10km 급감속. */
    private fun TraceBuilder.hardBrake(atSec: Int, fromKph: Float = 60f) {
        at((atSec - 1).seconds) { speed(fromKph) }
        at(atSec.seconds) { speed(fromKph - 10f) }
        at((atSec + 1).seconds) { speed(fromKph - 10f) }
    }

    private fun List<Timed>.effects() = map { it.effect }

    @Test
    fun `S2 F3_1 놀이를 수락하지 않으면 굽은 길에서 급한 동작이 있어도 좌우 소리와 별 효과음이 없다`() {
        val signals = trace {
            at(0.seconds) { speed(60f) }
            enterCurve(10)
            at(30.seconds) { turnSignal(TurnState.LEFT) }
            hardBrake(40)
            ticks(0.seconds, 200.seconds, every = 1.seconds)
        }
        val effects = session.drive(signals).effects()
        assertTrue(effects.none { it is PlaySideCue || it == StarLost || it == StartBeat }, "$effects")
    }

    @Test
    fun `F3_3 답을 기다리는 시간은 차량 신호의 시각으로 흘러간다`() {
        val signals = trace {
            enterCurve(10)
            ticks(0.seconds, 40.seconds, every = 1.seconds)
        }
        val startMs = 20_000L
        assertTrue(Timed(startMs + t.proposalListenMs, StopListening) in session.drive(signals))
    }

    @Test
    fun `F3_3 듣기 시작 신호가 세션을 거쳐 답을 기다리는 시간을 미룬다`() {
        val signals = trace {
            enterCurve(10) // 20초에 구간 시작, 질문
            ticks(0.seconds, 40.seconds, every = 1.seconds)
        }
        val micOpenAt = 23_000L
        val (before, after) = signals.partition { it.atMs < micOpenAt }
        session.drive(before)
        session.onListeningStarted(micOpenAt)
        val timed = session.drive(after)
        assertEquals(listOf(Timed(micOpenAt + t.proposalListenMs, StopListening)), timed.filter { it.effect == StopListening })
    }

    @Test
    fun `F3_5 그만이라고 하면 그 뒤로는 좌우 소리와 별 효과음이 나지 않는다`() {
        val signals = trace {
            at(0.seconds) { speed(60f) }
            enterCurve(10)
            at(25.seconds) { steer(turn) }
            at(27.seconds) { steer(0f) }
            at(35.seconds) { steer(-turn) }
            hardBrake(40)
        }
        val timed = session.drive(signals, says = listOf(Say(21_000, "응"), Say(30_000, "그만")))
        val after = timed.filter { it.atMs > 30_000 }.effects()
        assertTrue(timed.any { it.atMs == 25_000L && it.effect is PlaySideCue }, "놀이 중 소리: $timed")
        assertTrue(after.none { it is PlaySideCue || it == StarLost }, "$after")
    }

    @Test
    fun `F5_1 판은 구간이 끝날 때 끝나고 다음 구간의 새 판은 별 3개로 시작한다`() {
        val signals = trace {
            at(0.seconds) { speed(60f) }
            enterCurve(10) // 20초에 구간 시작
            hardBrake(30)
            enterCurve(200) // 210초에 다음 구간 시작
            ticks(0.seconds, 400.seconds, every = 1.seconds)
        }
        val says = listOf(
            Say(21_000, "응"),
            Say(140_000, "괜찮아요"),
            Say(211_000, "응"),
            Say(330_000, "괜찮아요"),
        )
        val spoken = session.drive(signals, says).effects().filterIsInstance<Speak>().map { it.text }
        val rounds = spoken.filter { it.startsWith("종이배") }
        assertEquals(listOf(VoiceLines.roundFinished(2), VoiceLines.roundFinished(3)), rounds)
    }

    private val baseline = ClimateBaseline(setTempC = 23f, fanSpeed = 3, fanSpeedMax = 8, fanDirection = FanDirection.FLOOR)

    private fun List<Timed>.climate() = filter { it.effect is ApplyClimate }.map { it.atMs to (it.effect as ApplyClimate).level }

    @Test
    fun `F6_1 놀이를 하지 않아도 굽은 길 구간에 들어서면 강화 단계로 바꾸고 끝나고 5분 뒤 기본으로 돌아간다`() {
        val start = session.onClimateBaseline(0, baseline, outsideC = 28f, month = 7)
        assertEquals(listOf(CareLevel.BASE), start.filterIsInstance<ApplyClimate>().map { it.level })
        val signals = trace {
            enterCurve(10) // 20초 구간 시작, 묻기만 하고 답 없음
            ticks(0.seconds, 600.seconds, every = 1.seconds)
        }
        val climate = session.drive(signals).climate()
        // 조향 이벤트 10·15·20초 → 70초에 기준 아래 → 130초 구간 종료 → 5분 뒤 430초에 기본
        assertEquals(listOf(20_000L to CareLevel.BOOST, 430_000L to CareLevel.BASE), climate)
    }

    @Test
    fun `F6_4 힘들어해요 버튼을 누르면 실내 케어를 강화 단계로 바꾼다`() {
        session.onClimateBaseline(0, baseline, outsideC = 28f, month = 7)
        val effects = session.onStruggleButton(1_000)
        assertTrue(effects.any { it is ApplyClimate && it.level == CareLevel.BOOST }, "$effects")
    }

    @Test
    fun `케어를 끝내면 공조를 케어 전 기준값으로 되돌린다`() {
        session.onClimateBaseline(0, baseline, outsideC = 28f, month = 7)
        // 도착 리포트 화면(F9.1)도 함께 나오므로 공조 되돌리기만 골라 본다
        assertEquals(listOf(Effect.RestoreClimate(baseline)), session.end(60_000).filterIsInstance<Effect.RestoreClimate>())
    }

    @Test
    fun `F9_3 지난 이동에서 힘들어했으면 처음부터 강화 단계로 시작한다`() {
        val boosted = CareSession(t, startBoost = true)
        val start = boosted.onClimateBaseline(0, baseline, outsideC = 28f, month = 7)
        assertEquals(listOf(CareLevel.BOOST), start.filterIsInstance<ApplyClimate>().map { it.level })
    }

    @Test
    fun `F7_2 힘들어해요 요청과 쉼터 검색 결과가 세션을 거쳐 이어진다`() {
        val stop = RestStop("한빛 졸음쉼터", 8.0)
        assertEquals(listOf<Effect>(PassengerStruggling, FindRestStop), session.onStruggleButton(0)) // 기준값 전이라 공조 없음
        assertEquals(
            listOf(Speak(VoiceLines.suggestRest(8.0)), Listen(setOf(YES, NO), t.restListenMs)),
            session.onRestStopFound(1_000, stop),
        )
    }
}
