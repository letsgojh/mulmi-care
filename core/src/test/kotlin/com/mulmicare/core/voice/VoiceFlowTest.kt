package com.mulmicare.core.voice

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect
import com.mulmicare.core.effect.Effect.AddWaypoint
import com.mulmicare.core.effect.Effect.Chime
import com.mulmicare.core.effect.Effect.FindRestStop
import com.mulmicare.core.effect.Effect.Listen
import com.mulmicare.core.effect.Effect.PassengerStruggling
import com.mulmicare.core.effect.Effect.Speak
import com.mulmicare.core.effect.Effect.StartBeat
import com.mulmicare.core.effect.Effect.StopBeat
import com.mulmicare.core.effect.Effect.StopListening
import com.mulmicare.core.place.RestStop
import com.mulmicare.core.voice.VoiceIntent.NO
import com.mulmicare.core.voice.VoiceIntent.OK
import com.mulmicare.core.voice.VoiceIntent.STOP
import com.mulmicare.core.voice.VoiceIntent.STRUGGLING
import com.mulmicare.core.voice.VoiceIntent.YES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec F3 놀이 제안, F5.7 판 종료 멘트, F7 뒷자리 상태 확인과 쉬어 가기. */
class VoiceFlowTest {

    private val t = Thresholds()
    private val flow = VoiceFlow(t, VoiceSettings())
    private val min = 60_000L

    private val askPlay = listOf(Chime, Speak(VoiceLines.PROPOSE_PLAY), Listen(setOf(YES, NO), t.proposalListenMs))
    private val askCheck = listOf(Speak(VoiceLines.CHECK_BACKSEAT), Listen(setOf(OK, STRUGGLING), t.checkListenMs))
    private val startPlay = listOf(StartBeat, Listen(setOf(STOP), null))
    private val restStop = RestStop("한빛 졸음쉼터", 8.0, latitude = 36.5, longitude = 127.5)
    private val onRouteStop = RestStop("한빛휴게소", 8.0)

    /** 놀이 없이 구간을 끝내고 "괜찮아요"로 답한다. */
    private fun VoiceFlow.finishSegmentCalmly(atMs: Long) {
        onSegmentEnded(atMs, starsKept = 3)
        onSpeech(atMs + 1_000, "괜찮아요")
    }

    // --- F3.1, F3.2 제안 ---

    @Test
    fun `F3_1 구간에 들어서면 알림음과 함께 묻기만 하고 놀이를 시작하지 않는다`() {
        assertEquals(askPlay, flow.onSegmentStarted(0))
        assertFalse(flow.isPlaying)
    }

    // --- F3.3 응답 ---

    @Test
    fun `F3_3 응이라고 답하면 놀이를 시작하고 그만을 듣는다`() {
        flow.onSegmentStarted(0)
        assertEquals(startPlay, flow.onSpeech(2_000, "응"))
        assertTrue(flow.isPlaying)
    }

    @Test
    fun `F3_3 아니라고 답하면 시작하지 않는다`() {
        flow.onSegmentStarted(0)
        assertEquals(emptyList(), flow.onSpeech(2_000, "아니"))
        assertFalse(flow.isPlaying)
    }

    @Test
    fun `F3_3 답을 기다리는 시간이 끝나면 듣기를 닫고 늦게 온 응은 받지 않는다`() {
        flow.onSegmentStarted(0)
        assertEquals(emptyList(), flow.onTick(t.proposalListenMs - 1))
        assertEquals(listOf(StopListening), flow.onTick(t.proposalListenMs))
        assertEquals(emptyList(), flow.onSpeech(t.proposalListenMs + 1, "응"))
        assertFalse(flow.isPlaying)
    }

    @Test
    fun `F3_3 마이크가 열린 때부터 답을 기다리는 시간을 센다`() {
        flow.onSegmentStarted(0)
        val micOpenAt = 3_000L // 질문을 말하는 데 3초 걸림
        flow.onListeningStarted(micOpenAt)
        assertEquals(emptyList(), flow.onTick(t.proposalListenMs))
        assertEquals(emptyList(), flow.onTick(micOpenAt + t.proposalListenMs - 1))
        assertEquals(listOf(StopListening), flow.onTick(micOpenAt + t.proposalListenMs))
    }

    @Test
    fun `F3_3 마이크가 열린 때부터 센 시간 안의 응은 받는다`() {
        flow.onSegmentStarted(0)
        flow.onListeningStarted(3_000)
        assertEquals(startPlay, flow.onSpeech(t.proposalListenMs + 1_000, "응"))
    }

    @Test
    fun `F3_6 답을 기다리는 중이 아닐 때의 듣기 시작 신호는 무시한다`() {
        flow.onListeningStarted(0)
        assertEquals(emptyList(), flow.onTick(10 * min))
        flow.onSegmentStarted(0)
        flow.onSpeech(2_000, "응")
        flow.onListeningStarted(3_000) // 놀이 중 "그만" 듣기는 시간 제한이 없다
        assertEquals(emptyList(), flow.onTick(10 * min))
        assertTrue(flow.isPlaying)
    }

    @Test
    fun `F3_3 알아듣지 못한 말은 답이 없는 것으로 보고 시작하지 않는다`() {
        flow.onSegmentStarted(0)
        assertEquals(emptyList(), flow.onSpeech(2_000, "몰라"))
        assertFalse(flow.isPlaying)
        flow.finishSegmentCalmly(3 * min)
        assertEquals(emptyList(), flow.onSegmentStarted(2_000 + t.proposalCooldownMs - 1))
    }

    // --- F3.4 15분 쿨다운: 직전 / 정확히 ---

    @Test
    fun `F3_4 거절한 뒤 15분이 되기 직전에 시작된 구간에서는 다시 묻지 않는다`() {
        flow.onSegmentStarted(0)
        flow.onSpeech(2_000, "아니")
        flow.finishSegmentCalmly(3 * min)
        assertEquals(emptyList(), flow.onSegmentStarted(2_000 + t.proposalCooldownMs - 1))
    }

    @Test
    fun `F3_4 거절하고 15분이 지나 시작된 구간에서는 다시 묻는다`() {
        flow.onSegmentStarted(0)
        flow.onSpeech(2_000, "아니")
        flow.finishSegmentCalmly(3 * min)
        assertEquals(askPlay, flow.onSegmentStarted(2_000 + t.proposalCooldownMs))
    }

    @Test
    fun `F9_3 줄어든 간격을 받으면 그 시간 직전까지만 묻지 않고 그 뒤에는 다시 묻는다`() {
        val short = VoiceFlow(t, VoiceSettings(), proposalCooldownMs = t.proposalCooldownAfterStruggleMs)
        short.onSegmentStarted(0)
        short.onSpeech(2_000, "아니")
        short.finishSegmentCalmly(3 * min)
        assertEquals(emptyList(), short.onSegmentStarted(2_000 + t.proposalCooldownAfterStruggleMs - 1))
        short.finishSegmentCalmly(9 * min)
        assertEquals(askPlay, short.onSegmentStarted(2_000 + t.proposalCooldownAfterStruggleMs))
    }

    @Test
    fun `F3_4 답이 없었을 때도 그때부터 15분 동안 다시 묻지 않는다`() {
        flow.onSegmentStarted(0)
        flow.onTick(t.proposalListenMs)
        flow.finishSegmentCalmly(3 * min)
        assertEquals(emptyList(), flow.onSegmentStarted(t.proposalListenMs + t.proposalCooldownMs - 1))
        flow.finishSegmentCalmly(20 * min)
        assertEquals(askPlay, flow.onSegmentStarted(t.proposalListenMs + t.proposalCooldownMs + 10 * min))
    }

    // --- F3.5 그만 ---

    @Test
    fun `F3_5 놀이 중 그만이라고 하면 바로 끝내고 구간이 끝나도 별 멘트는 하지 않는다`() {
        flow.onSegmentStarted(0)
        flow.onSpeech(2_000, "응")
        assertEquals(listOf(StopListening, StopBeat), flow.onSpeech(30_000, "그만"))
        assertFalse(flow.isPlaying)
        assertEquals(askCheck, flow.onSegmentEnded(3 * min, starsKept = 2))
    }

    // --- F3.6 듣는 때 ---

    @Test
    fun `F3_6 묻지 않은 상태에서는 말을 들어도 아무것도 하지 않는다`() {
        assertEquals(emptyList(), flow.onSpeech(0, "응"))
        assertEquals(emptyList(), flow.onTick(10_000))
        assertFalse(flow.isPlaying)
    }

    @Test
    fun `F3_6 듣기는 질문하거나 놀이를 시작한 바로 뒤에만 연다`() {
        val effects = buildList {
            addAll(flow.onSegmentStarted(0))
            addAll(flow.onSpeech(2_000, "응"))
            addAll(flow.onSegmentEnded(3 * min, starsKept = 2))
            addAll(flow.onSpeech(3 * min + 2_000, "힘들어해요"))
            addAll(flow.onRestStopFound(3 * min + 3_000, restStop))
            addAll(flow.onSpeech(3 * min + 5_000, "응"))
        }
        effects.forEachIndexed { i, e ->
            if (e is Listen) {
                val before = effects[i - 1]
                assertTrue(before is Speak || before == StartBeat, "Listen 앞: $before")
            }
        }
    }

    // --- F3.8, F1.5 제안하지 않는 경우 ---

    @Test
    fun `F3_8 묻지 않기를 골랐으면 구간에 들어서도 묻지 않는다`() {
        val noAsk = VoiceFlow(t, VoiceSettings(proposePlay = false))
        assertEquals(emptyList(), noAsk.onSegmentStarted(0))
        assertEquals(askCheck, noAsk.onSegmentEnded(3 * min, starsKept = 3))
    }

    @Test
    fun `F1_5 뒷자리가 성인이면 놀이를 제안하지 않고 상태 확인만 한다`() {
        val adult = VoiceFlow(t, VoiceSettings(passenger = Passenger.ADULT))
        assertEquals(emptyList(), adult.onSegmentStarted(0))
        assertEquals(askCheck, adult.onSegmentEnded(3 * min, starsKept = 3))
    }

    // --- F5.7 판 종료 ---

    @Test
    fun `F5_7 놀이 중 구간이 끝나면 지킨 별을 말하고 음악을 끈 뒤 뒷자리 상태를 묻는다`() {
        flow.onSegmentStarted(0)
        flow.onSpeech(2_000, "응")
        val expected = listOf(StopListening, Speak(VoiceLines.roundFinished(2)), StopBeat) + askCheck
        assertEquals(expected, flow.onSegmentEnded(3 * min, starsKept = 2))
        assertFalse(flow.isPlaying)
    }

    @Test
    fun `F5_7 별 개수는 우리말 수로 말한다`() {
        assertEquals("종이배 도착! 별 세 개를 지켰어요", VoiceLines.roundFinished(3))
        assertEquals("종이배 도착! 별 두 개를 지켰어요", VoiceLines.roundFinished(2))
        assertEquals("종이배 도착! 별 한 개를 지켰어요", VoiceLines.roundFinished(1))
    }

    // --- F7.1 상태 확인 ---

    @Test
    fun `F7_1 놀이를 하지 않았어도 구간이 끝나면 뒷자리 상태를 묻는다`() {
        flow.onSegmentStarted(0)
        flow.onSpeech(2_000, "아니")
        assertEquals(askCheck, flow.onSegmentEnded(3 * min, starsKept = 3))
    }

    @Test
    fun `F7_1 괜찮다고 하거나 답이 없으면 더 묻지 않는다`() {
        flow.onSegmentEnded(0, starsKept = 3)
        assertEquals(emptyList(), flow.onSpeech(2_000, "괜찮아요"))

        val other = VoiceFlow(t, VoiceSettings())
        other.onSegmentEnded(0, starsKept = 3)
        assertEquals(listOf(StopListening), other.onTick(t.checkListenMs))
    }

    // --- F7.2 힘들어함 → 쉼터 ---

    @Test
    fun `F7_2 힘들어한다고 하면 강화 케어를 요청하고 쉼터를 찾는다`() {
        flow.onSegmentEnded(0, starsKept = 3)
        assertEquals(listOf(PassengerStruggling, FindRestStop), flow.onSpeech(2_000, "힘들어해요"))
    }

    @Test
    fun `F7_2 쉼터를 찾으면 거리와 함께 들를지 묻는다`() {
        flow.onSegmentEnded(0, starsKept = 3)
        flow.onSpeech(2_000, "힘들어해요")
        assertEquals(
            listOf(Speak("8km 앞에 쉼터가 있어요. 들를까요?"), Listen(setOf(YES, NO), t.restListenMs)),
            flow.onRestStopFound(3_000, restStop),
        )
    }

    @Test
    fun `F7_2 근처에 쉼터가 없으면 더 묻지 않는다`() {
        flow.onSegmentEnded(0, starsKept = 3)
        flow.onSpeech(2_000, "힘들어해요")
        assertEquals(emptyList(), flow.onRestStopFound(3_000, null))
    }

    @Test
    fun `F7_2 정차 중 힘들어해요 버튼도 같은 처리를 한다`() {
        assertEquals(listOf(PassengerStruggling, FindRestStop), flow.onStruggleButton(0))
    }

    // --- F7.3 경유지 ---

    @Test
    fun `F7_3 쉼터에 들르자고 하면 경유지로 추가한다`() {
        flow.onStruggleButton(0)
        flow.onRestStopFound(1_000, restStop)
        assertEquals(listOf(AddWaypoint(restStop)), flow.onSpeech(3_000, "응"))
    }

    @Test
    fun `F7_3 좌표가 없는 경로 위 쉼터는 경유지 대신 들어가는 방법을 안내한다`() {
        flow.onStruggleButton(0)
        flow.onRestStopFound(1_000, onRouteStop)
        assertEquals(listOf(Speak(VoiceLines.REST_ON_ROUTE)), flow.onSpeech(3_000, "응"))
    }

    @Test
    fun `F7_3 쉼터 제안을 거절하거나 답이 없으면 추가하지 않는다`() {
        flow.onStruggleButton(0)
        flow.onRestStopFound(1_000, restStop)
        assertEquals(emptyList(), flow.onSpeech(3_000, "아니"))

        val other = VoiceFlow(t, VoiceSettings())
        other.onStruggleButton(0)
        other.onRestStopFound(1_000, restStop)
        assertEquals(listOf(StopListening), other.onTick(1_000 + t.restListenMs))
    }

    // --- 겹칠 때 ---

    @Test
    fun `F3 묻는 중에 구간이 끝나면 제안을 거두고 15분 동안 다시 묻지 않는다`() {
        flow.onSegmentStarted(0)
        assertEquals(listOf<Effect>(StopListening) + askCheck, flow.onSegmentEnded(5_000, starsKept = 3))
        flow.onSpeech(6_000, "괜찮아요")
        assertEquals(emptyList(), flow.onSegmentStarted(5_000 + t.proposalCooldownMs - 1))
    }

    @Test
    fun `F3 쉼터 안내를 진행하는 중에 구간이 시작되면 놀이를 제안하지 않는다`() {
        flow.onStruggleButton(0)
        assertEquals(emptyList(), flow.onSegmentStarted(1_000))
    }
}
