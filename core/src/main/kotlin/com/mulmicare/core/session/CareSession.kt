package com.mulmicare.core.session

import com.mulmicare.core.care.CabinCarePolicy
import com.mulmicare.core.care.ClimateBaseline
import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect
import com.mulmicare.core.place.RestStop
import com.mulmicare.core.signal.CurveSegmentDetector
import com.mulmicare.core.signal.GearPos
import com.mulmicare.core.signal.SegmentEvent
import com.mulmicare.core.signal.SideCueGenerator
import com.mulmicare.core.signal.StarJudge
import com.mulmicare.core.signal.VehicleSignal
import com.mulmicare.core.voice.VoiceFlow
import com.mulmicare.core.voice.VoiceSettings

/**
 * 케어가 켜진 이동 한 번. 구간 판단·음성 흐름·좌우 소리·별 판정·실내 케어를 묶는다.
 *
 * 차량 신호 하나마다: 음성 흐름의 시간 경과 → 좌우 소리 → 별 판정 → 구간 시작·종료 순으로 처리한다.
 * 음성 흐름이 [Effect.StartBeat]를 내면 판을 시작하고 [Effect.StopBeat]를 내면 끝낸다.
 * 그래서 한 판은 "응"부터 구간 종료(또는 "그만")까지다(F5.1).
 */
class CareSession(
    t: Thresholds = Thresholds(),
    settings: VoiceSettings = VoiceSettings(),
    /** F9.3 지난 이동에서 자주 힘들어했으면 true. */
    startBoost: Boolean = false,
    /** F5.8 지난 이동들까지 모은 별. */
    private val previousStars: Int = 0,
) {
    private val rounds = mutableListOf<RoundRecord>()
    private var roundStartedAtMs: Long? = null
    private var struggles = 0
    private var gear: GearPos? = null

    /** 지금까지의 이동 기록 (F9.1). */
    val report: TripReport get() = TripReport(rounds.toList(), struggles)

    private val detector = CurveSegmentDetector(t)
    private val voice = VoiceFlow(t, settings)
    private val cues = SideCueGenerator(t)
    private val stars = StarJudge(t)
    private val cabin = CabinCarePolicy(t, startBoost)

    fun onSignal(signal: VehicleSignal): List<Effect> {
        val effects = buildList {
            addAll(voice.onTick(signal.atMs))
            cues.onSignal(signal)?.let(::add)
            stars.onSignal(signal)?.let(::add)
            when (val segment = detector.onSignal(signal)) {
                is SegmentEvent.Started -> {
                    addAll(voice.onSegmentStarted(segment.atMs))
                    addAll(cabin.onSegmentStarted(segment.atMs)) // F6.1 놀이 여부와 관계없이
                }
                is SegmentEvent.Ended -> {
                    addAll(voice.onSegmentEnded(segment.atMs, starsKept = stars.stars))
                    addAll(cabin.onSegmentEnded(segment.atMs))
                }
                null -> Unit
            }
            addAll(cabin.onTick(signal.atMs))
            if (signal is VehicleSignal.Gear) addAll(onGear(signal.pos))
        }
        return follow(signal.atMs, effects)
    }

    fun onSpeech(atMs: Long, text: String): List<Effect> = follow(atMs, voice.onSpeech(atMs, text))

    /** F6 케어를 켤 때 읽은 공조 상태. [outsideC]를 모르면 null, [month]는 1~12. */
    fun onClimateBaseline(atMs: Long, baseline: ClimateBaseline, outsideC: Float?, month: Int): List<Effect> =
        cabin.onBaseline(atMs, baseline, outsideC, month)

    /**
     * 케어를 끝낸다(정지·도착). 공조를 케어 전 상태로 되돌린다.
     * 리포트 화면은 정차(P단) 중일 때만 띄운다(S1). 주행 중이면 [reportScreen]을 앱이 보관했다가 정차 뒤에 보여 준다.
     */
    fun end(atMs: Long): List<Effect> {
        val restore = cabin.onCareEnded(atMs)
        return if (gear == GearPos.P) restore + Effect.ShowScreen(reportScreen()) else restore
    }

    /** F9.1 이번 이동의 리포트. */
    fun reportScreen(): Screen.Report {
        val total = previousStars + report.starsKept
        return Screen.Report(report, total, Rewards.unlocked(total))
    }

    /** F8.2 지금까지의 항해 지도. 띄우는 것은 정차 중일 때만(S1). */
    fun voyageMap(): Screen.VoyageMap {
        val total = previousStars + report.starsKept
        return Screen.VoyageMap(rounds.toList(), total, Rewards.unlocked(total))
    }

    /** F8 화면은 P단일 때만 띄우고, P단을 벗어나면 내린다(S1). */
    private fun onGear(pos: GearPos): List<Effect> {
        val previous = gear
        if (pos == previous) return emptyList()
        gear = pos
        return when {
            pos == GearPos.P -> listOf(Effect.ShowScreen(voyageMap()))
            previous == GearPos.P -> listOf(Effect.HideScreen)
            else -> emptyList()
        }
    }

    /** 앱이 실제로 마이크를 연 순간 (TTS가 질문을 다 말한 뒤). */
    fun onListeningStarted(atMs: Long) = voice.onListeningStarted(atMs)

    fun onRestStopFound(atMs: Long, stop: RestStop?): List<Effect> = follow(atMs, voice.onRestStopFound(atMs, stop))

    fun onStruggleButton(atMs: Long): List<Effect> = follow(atMs, voice.onStruggleButton(atMs))

    /**
     * 음성 흐름이 낸 Effect를 따라 다른 부품을 맞춘다.
     * - 비트를 켜고 끄면 좌우 소리와 별 판정 판을 시작·종료한다.
     * - 뒷자리가 힘들어하면 실내 케어를 강화 단계로 바꾼다(F6.4).
     */
    private fun follow(atMs: Long, effects: List<Effect>): List<Effect> {
        val extra = mutableListOf<Effect>()
        for (effect in effects) {
            when (effect) {
                Effect.StartBeat -> {
                    cues.startPlay()
                    stars.startRound()
                    roundStartedAtMs = atMs
                }
                Effect.StopBeat -> {
                    cues.stopPlay()
                    // F5.1 한 판 기록 ("그만"으로 끝낸 판도 그때까지 지킨 별로)
                    rounds += RoundRecord(stars.endRound(), roundStartedAtMs ?: atMs, atMs)
                    roundStartedAtMs = null
                }
                Effect.PassengerStruggling -> {
                    struggles++
                    extra += cabin.onPassengerStruggling(atMs)
                }
                else -> Unit
            }
        }
        return effects + extra
    }
}
