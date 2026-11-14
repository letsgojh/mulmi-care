package com.mulmicare.core.voice

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect
import com.mulmicare.core.place.RestStop
import com.mulmicare.core.voice.VoiceIntent.NO
import com.mulmicare.core.voice.VoiceIntent.OK
import com.mulmicare.core.voice.VoiceIntent.STOP
import com.mulmicare.core.voice.VoiceIntent.STRUGGLING
import com.mulmicare.core.voice.VoiceIntent.YES

enum class Passenger { CHILD, ADULT }

/** F1.3에서 고른 값 중 음성 흐름에 필요한 것. */
data class VoiceSettings(
    val passenger: Passenger = Passenger.CHILD,
    /** F3.8 "제안받기"면 true, "묻지 않기"면 false. */
    val proposePlay: Boolean = true,
)

/**
 * 음성 대화 흐름 (spec F3, F5.7, F7).
 *
 * ```
 * IDLE ─구간 시작─▶ PROPOSING ─응─▶ PLAYING ─구간 끝─▶ (별 멘트) CHECKING
 *                    └아니·무응답─▶ IDLE (15분 쿨다운)     └그만─▶ IDLE
 * IDLE ─구간 끝─▶ CHECKING ─힘들어해요─▶ FINDING_REST ─쉼터 있음─▶ SUGGESTING_REST ─응─▶ 경유지 추가
 * ```
 * 놀이는 PROPOSING에서 "응"을 들었을 때만 시작한다(F3.1, S2).
 * 듣기는 질문한 직후와 놀이 중에만 연다(F3.6).
 */
class VoiceFlow(
    private val t: Thresholds = Thresholds(),
    private val settings: VoiceSettings = VoiceSettings(),
) {
    private enum class State { IDLE, PROPOSING, PLAYING, CHECKING, FINDING_REST, SUGGESTING_REST }

    private var state = State.IDLE
    private var listenDeadlineMs: Long? = null
    private var currentListenMs = 0L
    private var proposalCooldownUntilMs: Long? = null
    private var pendingRestStop: RestStop? = null

    val isPlaying: Boolean get() = state == State.PLAYING

    fun onSegmentStarted(atMs: Long): List<Effect> {
        if (state != State.IDLE || !mayProposePlay(atMs)) return emptyList()
        return listOf(Effect.Chime) +
            ask(State.PROPOSING, atMs, VoiceLines.PROPOSE_PLAY, setOf(YES, NO), t.proposalListenMs)
    }

    /** [starsKept]는 놀이 중이었을 때만 쓴다(F5.7). */
    fun onSegmentEnded(atMs: Long, starsKept: Int): List<Effect> {
        val closing = when (state) {
            State.IDLE -> emptyList()
            State.PROPOSING -> {
                startProposalCooldown(atMs)
                listOf(Effect.StopListening)
            }
            State.PLAYING -> listOf(
                Effect.StopListening,
                Effect.Speak(VoiceLines.roundFinished(starsKept)),
                Effect.StopBeat,
            )
            // 이미 뒷자리 상태나 쉼터 이야기를 하는 중이면 다시 묻지 않는다
            State.CHECKING, State.FINDING_REST, State.SUGGESTING_REST -> return emptyList()
        }
        return closing + ask(State.CHECKING, atMs, VoiceLines.CHECK_BACKSEAT, setOf(OK, STRUGGLING), t.checkListenMs)
    }

    fun onSpeech(atMs: Long, text: String): List<Effect> {
        val expired = onTick(atMs)
        if (expired.isNotEmpty()) return expired

        return when (state) {
            State.PROPOSING -> {
                listenDeadlineMs = null
                if (VoicePhrases.match(text, setOf(YES, NO)) == YES) {
                    state = State.PLAYING
                    listOf(Effect.StartBeat, Effect.Listen(setOf(STOP), timeoutMs = null))
                } else {
                    // 거절과 알아듣지 못한 말은 같게 본다
                    state = State.IDLE
                    startProposalCooldown(atMs)
                    emptyList()
                }
            }
            State.PLAYING -> {
                if (VoicePhrases.match(text, setOf(STOP)) != STOP) return emptyList()
                state = State.IDLE
                listOf(Effect.StopListening, Effect.StopBeat)
            }
            State.CHECKING -> {
                listenDeadlineMs = null
                if (VoicePhrases.match(text, setOf(OK, STRUGGLING)) == STRUGGLING) {
                    passengerStruggling()
                } else {
                    state = State.IDLE
                    emptyList()
                }
            }
            State.SUGGESTING_REST -> {
                listenDeadlineMs = null
                val stop = pendingRestStop
                pendingRestStop = null
                state = State.IDLE
                if (stop != null && VoicePhrases.match(text, setOf(YES, NO)) == YES) {
                    listOf(Effect.AddWaypoint(stop))
                } else {
                    emptyList()
                }
            }
            State.IDLE, State.FINDING_REST -> emptyList()
        }
    }

    /** 앱이 질문을 다 말하고 실제로 마이크를 연 순간. 답을 기다리는 시간을 이때부터 센다. */
    fun onListeningStarted(atMs: Long) {
        // 시간 제한이 있는 질문에 답을 기다리는 중일 때만. 놀이 중 "그만" 듣기는 제한이 없다
        if (listenDeadlineMs == null) return
        listenDeadlineMs = atMs + currentListenMs
    }

    /** 답을 기다리는 시간이 지났으면 듣기를 닫는다. */
    fun onTick(atMs: Long): List<Effect> {
        val deadline = listenDeadlineMs ?: return emptyList()
        if (atMs < deadline) return emptyList()

        listenDeadlineMs = null
        if (state == State.PROPOSING) startProposalCooldown(deadline)
        pendingRestStop = null
        state = State.IDLE
        return listOf(Effect.StopListening)
    }

    fun onRestStopFound(atMs: Long, stop: RestStop?): List<Effect> {
        if (state != State.FINDING_REST) return emptyList()
        if (stop == null) {
            state = State.IDLE
            return emptyList()
        }
        pendingRestStop = stop
        return ask(State.SUGGESTING_REST, atMs, VoiceLines.suggestRest(stop.distanceKm), setOf(YES, NO), t.restListenMs)
    }

    /** F7.2 정차 중 "힘들어해요" 버튼. 진행 중인 대화나 놀이는 접고 쉼터 찾기로 넘어간다. */
    fun onStruggleButton(atMs: Long): List<Effect> {
        val closing = when (state) {
            State.IDLE -> emptyList()
            State.PROPOSING -> {
                startProposalCooldown(atMs)
                listOf(Effect.StopListening)
            }
            State.PLAYING -> listOf(Effect.StopListening, Effect.StopBeat)
            State.CHECKING, State.SUGGESTING_REST -> listOf(Effect.StopListening)
            State.FINDING_REST -> return emptyList()
        }
        listenDeadlineMs = null
        pendingRestStop = null
        return closing + passengerStruggling()
    }

    private fun mayProposePlay(atMs: Long): Boolean {
        if (settings.passenger != Passenger.CHILD || !settings.proposePlay) return false
        val until = proposalCooldownUntilMs ?: return true
        return atMs >= until
    }

    private fun startProposalCooldown(fromMs: Long) {
        proposalCooldownUntilMs = fromMs + t.proposalCooldownMs
    }

    private fun passengerStruggling(): List<Effect> {
        state = State.FINDING_REST
        return listOf(Effect.PassengerStruggling, Effect.FindRestStop)
    }

    private fun ask(next: State, atMs: Long, line: String, expect: Set<VoiceIntent>, listenMs: Long): List<Effect> {
        state = next
        currentListenMs = listenMs
        // 앱이 마이크를 연 순간을 알려 주면(onListeningStarted) 그때부터 다시 센다
        listenDeadlineMs = atMs + listenMs
        return listOf(Effect.Speak(line), Effect.Listen(expect, listenMs))
    }
}
