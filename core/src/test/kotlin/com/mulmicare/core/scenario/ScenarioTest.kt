package com.mulmicare.core.scenario

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect.Chime
import com.mulmicare.core.effect.Effect.Listen
import com.mulmicare.core.effect.Effect.PlaySideCue
import com.mulmicare.core.effect.Effect.Speak
import com.mulmicare.core.effect.Effect.StarLost
import com.mulmicare.core.effect.Effect.StartBeat
import com.mulmicare.core.effect.Effect.StopBeat
import com.mulmicare.core.effect.Effect.StopListening
import com.mulmicare.core.session.CareSession
import com.mulmicare.core.signal.GearPos
import com.mulmicare.core.signal.Side.LEFT
import com.mulmicare.core.signal.Side.RIGHT
import com.mulmicare.core.signal.TurnState
import com.mulmicare.core.testing.Say
import com.mulmicare.core.testing.Timed
import com.mulmicare.core.testing.drive
import com.mulmicare.core.testing.trace
import com.mulmicare.core.voice.VoiceIntent.NO
import com.mulmicare.core.voice.VoiceIntent.OK
import com.mulmicare.core.voice.VoiceIntent.STOP
import com.mulmicare.core.voice.VoiceIntent.STRUGGLING
import com.mulmicare.core.voice.VoiceIntent.YES
import com.mulmicare.core.voice.VoiceLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** 기획서 4장 사용 시나리오 인수 테스트 (scenario-test 스킬). */
class ScenarioTest {

    private val t = Thresholds()

    /** 굽은 길에서 핸들을 돌린 앞바퀴 각도. 조향 이벤트 기준의 두 배. */
    private val turn = t.steerEventDeg * 2

    /**
     * 굽은 국도 주행. 조향 이벤트 10·15·20초(20초에 구간 시작), 25초 오른쪽, 30초 왼쪽 방향지시등,
     * 32초 왼쪽 조향(방향지시등과 같은 동작), 41초 급감속, 45초 오른쪽. 이후 직선.
     * 구간 이벤트: 10,15,20,25,32,41,45 → 32초 이벤트가 92초에 창에서 빠져 기준 아래 → 152초에 구간 종료.
     */
    private val countryRoad = trace {
        at(0.seconds) { gear(GearPos.D); speed(60f); steer(0f) }
        at(10.seconds) { steer(-turn) }
        at(12.seconds) { steer(0f) }
        at(15.seconds) { steer(turn) }
        at(17.seconds) { steer(0f) }
        at(20.seconds) { steer(-turn) }
        at(22.seconds) { steer(0f) }
        at(25.seconds) { steer(turn) }
        at(27.seconds) { steer(0f) }
        at(30.seconds) { turnSignal(TurnState.LEFT) }
        at(32.seconds) { steer(-turn) }
        at(34.seconds) { turnSignal(TurnState.OFF); steer(0f) }
        at(40.seconds) { speed(60f) }
        at(41.seconds) { speed(50f) }
        at(42.seconds) { speed(50f) }
        at(45.seconds) { steer(turn) }
        at(47.seconds) { steer(0f) }
        ticks(48.seconds, 200.seconds, every = 1.seconds)
    }

    @Test
    fun `SC3 굽은 국도 - F3_2 F3_3 F4_2 F4_3 F2_5 F5_3 F5_7 F7_1`() {
        val timed = CareSession(t).drive(
            countryRoad,
            says = listOf(Say(21_500, "응"), Say(155_000, "괜찮아요")),
        )
        val expected = listOf(
            // 구간 진입: 알림음과 함께 한 번 묻는다
            Timed(20_000, Chime),
            Timed(20_000, Speak(VoiceLines.PROPOSE_PLAY)),
            Timed(20_000, Listen(setOf(YES, NO), t.proposalListenMs)),
            // "응" → 비트 시작
            Timed(21_500, StartBeat),
            Timed(21_500, Listen(setOf(STOP), null)),
            // 핸들이 오른쪽으로 들어서는 순간 오른쪽 (처음 3번은 말과 함께)
            Timed(25_000, PlaySideCue(RIGHT, withVoice = true)),
            // 방향지시등: 차가 돌기 전에 왼쪽. 32초의 왼쪽 조향은 같은 동작이라 다시 울리지 않는다
            Timed(30_000, PlaySideCue(LEFT, withVoice = true)),
            // 급감속 → 별 하나
            Timed(41_000, StarLost),
            Timed(45_000, PlaySideCue(RIGHT, withVoice = true)),
            // 구간 끝: 별 멘트, 음악 끔, 뒷자리 확인
            Timed(152_000, StopListening),
            Timed(152_000, Speak(VoiceLines.roundFinished(2))),
            Timed(152_000, StopBeat),
            Timed(152_000, Speak(VoiceLines.CHECK_BACKSEAT)),
            Timed(152_000, Listen(setOf(OK, STRUGGLING), t.checkListenMs)),
        )
        assertEquals(expected, timed)
    }

    @Test
    fun `SC4 운전에 집중하고 싶을 때 - F3_3 F3_4`() {
        val session = CareSession(t)
        val timed = session.drive(countryRoad, says = listOf(Say(21_500, "아니"), Say(155_000, "괜찮아요")))
        val expected = listOf(
            Timed(20_000, Chime),
            Timed(20_000, Speak(VoiceLines.PROPOSE_PLAY)),
            Timed(20_000, Listen(setOf(YES, NO), t.proposalListenMs)),
            // 놀이 없이 구간이 끝나면 뒷자리 확인만
            Timed(152_000, Speak(VoiceLines.CHECK_BACKSEAT)),
            Timed(152_000, Listen(setOf(OK, STRUGGLING), t.checkListenMs)),
        )
        assertEquals(expected, timed)

        // 15분 안에 다시 굽은 길에 들어서도 묻지 않는다
        val nextCurve = trace {
            for ((i, sec) in listOf(300, 305, 310).withIndex()) {
                at(sec.seconds) { steer(if (i % 2 == 0) -turn else turn) }
            }
        }
        assertEquals(emptyList(), session.drive(nextCurve))
    }
}
