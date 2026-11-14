package com.mulmicare.core.session

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect
import com.mulmicare.core.signal.GearPos
import com.mulmicare.core.signal.VehicleSignal
import com.mulmicare.core.voice.VoiceIntent.NO
import com.mulmicare.core.voice.VoiceIntent.YES
import com.mulmicare.core.voice.VoiceLines
import com.mulmicare.core.voice.VoicePhrases

/**
 * 출발 전 케어 제안 (spec F1.1, F1.2, F1.4). 케어를 켜기 전 단계라 CareSession과 따로 둔다.
 *
 * - 내비 경로 예상 소요가 [Thresholds.careOfferMinRouteMs] 이상이고 P단이면 "케어를 켤까요?"를 경로당 한 번 묻는다.
 * - 짧은 이동은 묻지 않는다(케어 기본 꺼짐). 주행 중에 경로를 잡았으면 P단이 되면 묻는다.
 * - "응"이면 [Effect.StartCare]. 답이 없거나 "아니"면 아무것도 하지 않는다.
 */
class CareOffer(private val t: Thresholds = Thresholds()) {

    private var gear: GearPos? = null
    private var longRoutePending = false
    private var listenDeadlineMs: Long? = null

    /** 내비 경로 안내가 시작됨. [durationSec]는 전체 예상 소요 시간. */
    fun onRouteStarted(atMs: Long, durationSec: Int): List<Effect> {
        listenDeadlineMs = null
        longRoutePending = durationSec * 1000L >= t.careOfferMinRouteMs
        return tryOffer(atMs)
    }

    /** 기어(P단 판단)와 Tick(답 기다리는 시간)을 받는다. */
    fun onSignal(signal: VehicleSignal): List<Effect> {
        val deadline = listenDeadlineMs
        if (deadline != null && signal.atMs >= deadline) {
            listenDeadlineMs = null
            return listOf(Effect.StopListening)
        }
        if (signal is VehicleSignal.Gear) {
            gear = signal.pos
            return tryOffer(signal.atMs)
        }
        return emptyList()
    }

    fun onSpeech(atMs: Long, text: String): List<Effect> {
        val deadline = listenDeadlineMs ?: return emptyList()
        listenDeadlineMs = null
        if (atMs >= deadline) return emptyList()
        return if (VoicePhrases.match(text, setOf(YES, NO)) == YES) listOf(Effect.StartCare) else emptyList()
    }

    /** 앱이 질문을 다 말하고 마이크를 연 순간. */
    fun onListeningStarted(atMs: Long) {
        if (listenDeadlineMs != null) listenDeadlineMs = atMs + t.careOfferListenMs
    }

    private fun tryOffer(atMs: Long): List<Effect> {
        if (!longRoutePending || gear != GearPos.P) return emptyList()
        longRoutePending = false // 경로당 한 번
        listenDeadlineMs = atMs + t.careOfferListenMs
        return listOf(Effect.Speak(VoiceLines.OFFER_CARE), Effect.Listen(setOf(YES, NO), t.careOfferListenMs))
    }
}
