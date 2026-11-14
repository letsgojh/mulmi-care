package com.mulmicare.core.session

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect
import com.mulmicare.core.place.RestStop
import com.mulmicare.core.signal.CurveSegmentDetector
import com.mulmicare.core.signal.SegmentEvent
import com.mulmicare.core.signal.SideCueGenerator
import com.mulmicare.core.signal.StarJudge
import com.mulmicare.core.signal.VehicleSignal
import com.mulmicare.core.voice.VoiceFlow
import com.mulmicare.core.voice.VoiceSettings

/**
 * 케어가 켜진 이동 한 번. 구간 판단·음성 흐름·좌우 소리·별 판정을 묶는다.
 *
 * 차량 신호 하나마다: 음성 흐름의 시간 경과 → 좌우 소리 → 별 판정 → 구간 시작·종료 순으로 처리한다.
 * 음성 흐름이 [Effect.StartBeat]를 내면 판을 시작하고 [Effect.StopBeat]를 내면 끝낸다.
 * 그래서 한 판은 "응"부터 구간 종료(또는 "그만")까지다(F5.1).
 */
class CareSession(
    t: Thresholds = Thresholds(),
    settings: VoiceSettings = VoiceSettings(),
) {
    private val detector = CurveSegmentDetector(t)
    private val voice = VoiceFlow(t, settings)
    private val cues = SideCueGenerator(t)
    private val stars = StarJudge(t)

    fun onSignal(signal: VehicleSignal): List<Effect> {
        val effects = buildList {
            addAll(voice.onTick(signal.atMs))
            cues.onSignal(signal)?.let(::add)
            stars.onSignal(signal)?.let(::add)
            when (val segment = detector.onSignal(signal)) {
                is SegmentEvent.Started -> addAll(voice.onSegmentStarted(segment.atMs))
                is SegmentEvent.Ended -> addAll(voice.onSegmentEnded(segment.atMs, starsKept = stars.stars))
                null -> Unit
            }
        }
        return followPlay(effects)
    }

    fun onSpeech(atMs: Long, text: String): List<Effect> = followPlay(voice.onSpeech(atMs, text))

    /** 앱이 실제로 마이크를 연 순간 (TTS가 질문을 다 말한 뒤). */
    fun onListeningStarted(atMs: Long) = voice.onListeningStarted(atMs)

    fun onRestStopFound(atMs: Long, stop: RestStop?): List<Effect> = followPlay(voice.onRestStopFound(atMs, stop))

    fun onStruggleButton(atMs: Long): List<Effect> = followPlay(voice.onStruggleButton(atMs))

    /** 음성 흐름이 비트를 켜고 끄는 것에 맞춰 좌우 소리와 별 판정 판을 시작·종료한다. */
    private fun followPlay(effects: List<Effect>): List<Effect> {
        for (effect in effects) {
            when (effect) {
                Effect.StartBeat -> {
                    cues.startPlay()
                    stars.startRound()
                }
                Effect.StopBeat -> {
                    cues.stopPlay()
                    stars.endRound()
                }
                else -> Unit
            }
        }
        return effects
    }
}
