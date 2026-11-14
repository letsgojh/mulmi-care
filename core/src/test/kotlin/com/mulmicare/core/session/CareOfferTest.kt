package com.mulmicare.core.session

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect.Listen
import com.mulmicare.core.effect.Effect.Speak
import com.mulmicare.core.effect.Effect.StartCare
import com.mulmicare.core.effect.Effect.StopListening
import com.mulmicare.core.signal.GearPos
import com.mulmicare.core.signal.VehicleSignal
import com.mulmicare.core.voice.VoiceIntent.NO
import com.mulmicare.core.voice.VoiceIntent.YES
import com.mulmicare.core.voice.VoiceLines
import kotlin.test.Test
import kotlin.test.assertEquals

/** spec F1.1 출발 전 케어 제안, F1.2 짧은 이동은 끔, F1.4 "응"이면 케어 시작. */
class CareOfferTest {

    private val t = Thresholds()
    private val offer = CareOffer(t)
    private val ask = listOf(Speak(VoiceLines.OFFER_CARE), Listen(setOf(YES, NO), t.careOfferListenMs))
    private val longRouteSec = (t.careOfferMinRouteMs / 1000).toInt() + 30 * 60

    private fun gear(atMs: Long, pos: GearPos) = offer.onSignal(VehicleSignal.Gear(atMs, pos))
    private fun tick(atMs: Long) = offer.onSignal(VehicleSignal.Tick(atMs))

    // --- F1.1 묻는 조건: 경로 시간 경계, P단 ---

    @Test
    fun `F1_1 P단에서 30분 이상 걸리는 경로가 시작되면 케어를 켤지 한 번 묻는다`() {
        gear(0, GearPos.P)
        assertEquals(ask, offer.onRouteStarted(1_000, longRouteSec))
    }

    @Test
    fun `F1_1 예상 소요가 정확히 30분이면 묻는다`() {
        gear(0, GearPos.P)
        assertEquals(ask, offer.onRouteStarted(1_000, (t.careOfferMinRouteMs / 1000).toInt()))
    }

    @Test
    fun `F1_2 예상 소요가 30분보다 1초라도 짧으면 묻지 않는다`() {
        gear(0, GearPos.P)
        assertEquals(emptyList(), offer.onRouteStarted(1_000, (t.careOfferMinRouteMs / 1000).toInt() - 1))
    }

    @Test
    fun `F1_1 주행 중에 경로를 잡았으면 그때는 묻지 않고 P단이 되면 묻는다`() {
        gear(0, GearPos.D)
        assertEquals(emptyList(), offer.onRouteStarted(1_000, longRouteSec))
        assertEquals(emptyList(), gear(5_000, GearPos.N))
        assertEquals(ask, gear(9_000, GearPos.P))
    }

    @Test
    fun `F1_1 기어를 아직 모르면 묻지 않는다`() {
        assertEquals(emptyList(), offer.onRouteStarted(1_000, longRouteSec))
    }

    @Test
    fun `F1_1 한 경로에서는 한 번만 묻는다`() {
        gear(0, GearPos.P)
        offer.onRouteStarted(1_000, longRouteSec)
        offer.onSpeech(3_000, "아니")
        gear(10_000, GearPos.D)
        assertEquals(emptyList(), gear(20_000, GearPos.P))
    }

    @Test
    fun `F1_1 새 경로가 시작되면 다시 묻는다`() {
        gear(0, GearPos.P)
        offer.onRouteStarted(1_000, longRouteSec)
        offer.onSpeech(3_000, "아니")
        assertEquals(ask, offer.onRouteStarted(60_000, longRouteSec))
    }

    // --- F1.4 답 ---

    @Test
    fun `F1_4 응이라고 답하면 케어를 시작한다`() {
        gear(0, GearPos.P)
        offer.onRouteStarted(1_000, longRouteSec)
        assertEquals(listOf(StartCare), offer.onSpeech(3_000, "응"))
    }

    @Test
    fun `F1_4 아니라고 답하면 케어를 시작하지 않는다`() {
        gear(0, GearPos.P)
        offer.onRouteStarted(1_000, longRouteSec)
        assertEquals(emptyList(), offer.onSpeech(3_000, "아니"))
    }

    @Test
    fun `F1_4 답을 기다리는 시간이 지나면 듣기를 닫고 늦은 응은 받지 않는다`() {
        gear(0, GearPos.P)
        offer.onRouteStarted(1_000, longRouteSec)
        assertEquals(emptyList(), tick(1_000 + t.careOfferListenMs - 1))
        assertEquals(listOf(StopListening), tick(1_000 + t.careOfferListenMs))
        assertEquals(emptyList(), offer.onSpeech(1_000 + t.careOfferListenMs + 1, "응"))
    }

    @Test
    fun `F1_4 마이크가 열린 때부터 답을 기다리는 시간을 센다`() {
        gear(0, GearPos.P)
        offer.onRouteStarted(1_000, longRouteSec)
        offer.onListeningStarted(4_000)
        assertEquals(emptyList(), tick(1_000 + t.careOfferListenMs))
        assertEquals(listOf(StartCare), offer.onSpeech(4_000 + t.careOfferListenMs - 1, "응"))
    }

    @Test
    fun `F3_6 묻지 않은 상태에서는 응이라고 해도 케어를 시작하지 않는다`() {
        assertEquals(emptyList(), offer.onSpeech(0, "응"))
    }
}
