package com.mulmicare.core.session

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect
import com.mulmicare.core.effect.Effect.HideScreen
import com.mulmicare.core.effect.Effect.ShowScreen
import com.mulmicare.core.signal.GearPos
import com.mulmicare.core.signal.VehicleSignal
import com.mulmicare.core.testing.Say
import com.mulmicare.core.testing.drive
import com.mulmicare.core.testing.trace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** spec F8 정차 중에만 보이는 화면, S1 주행 중 화면 없음, F9.1 리포트. */
class CareSessionScreenTest {

    private val t = Thresholds()
    private val turn = t.steerEventDeg * 2

    private fun gear(session: CareSession, atMs: Long, pos: GearPos) = session.onSignal(VehicleSignal.Gear(atMs, pos))
    private fun List<Effect>.screens() = filter { it is ShowScreen || it == HideScreen }

    /** 굽은 길 한 판: "응"으로 시작하고, 30초에 급감속 1번, 구간이 끝날 때까지 달린다. */
    private fun playOneRound(session: CareSession, hardBrake: Boolean): List<Effect> {
        val signals = trace {
            at(0.seconds) { gear(GearPos.D); speed(60f) }
            listOf(-turn, turn, -turn).forEachIndexed { i, deg ->
                at((10 + i * 5).seconds) { steer(deg) }
                at((12 + i * 5).seconds) { steer(0f) }
            }
            if (hardBrake) {
                at(29.seconds) { speed(60f) }
                at(30.seconds) { speed(50f) }
                at(31.seconds) { speed(50f) }
            }
            ticks(0.seconds, 200.seconds, every = 1.seconds)
        }
        return session.drive(signals, says = listOf(Say(21_000, "응"), Say(140_000, "괜찮아요"))).map { it.effect }
    }

    @Test
    fun `S1 F8_1 주행 중에는 놀이와 상태 확인을 하는 동안에도 화면을 띄우지 않는다`() {
        val session = CareSession(t)
        val effects = playOneRound(session, hardBrake = true)
        assertTrue(effects.none { it is ShowScreen }, "$effects")
    }

    @Test
    fun `F8_2 정차하면 마친 판과 별이 그려진 항해 지도를 띄운다`() {
        val session = CareSession(t, previousStars = 4)
        playOneRound(session, hardBrake = true)
        val effects = gear(session, 300_000, GearPos.P)
        val map = effects.filterIsInstance<ShowScreen>().single().screen as Screen.VoyageMap
        assertEquals(listOf(2), map.rounds.map { it.stars })
        assertEquals(6, map.totalStars)
        assertEquals(listOf(Rewards.ITEMS[0]), map.unlocked)
    }

    @Test
    fun `F8_1 다시 출발하면 화면을 내린다`() {
        val session = CareSession(t)
        gear(session, 0, GearPos.P)
        assertEquals(listOf<Effect>(HideScreen), gear(session, 1_000, GearPos.D).screens())
    }

    @Test
    fun `F8 같은 기어가 반복해서 와도 화면을 다시 띄우지 않는다`() {
        val session = CareSession(t)
        gear(session, 0, GearPos.P)
        assertEquals(emptyList(), gear(session, 1_000, GearPos.P).screens())
    }

    @Test
    fun `F8_1 중립이나 후진은 정차가 아니라서 화면을 띄우지 않는다`() {
        val session = CareSession(t)
        assertEquals(emptyList(), gear(session, 0, GearPos.N).screens().filterIsInstance<ShowScreen>())
        assertEquals(emptyList(), gear(session, 1_000, GearPos.R).screens().filterIsInstance<ShowScreen>())
    }

    @Test
    fun `F9_1 이동 기록에 판마다 지킨 별과 힘들어해요 횟수가 쌓인다`() {
        val session = CareSession(t)
        playOneRound(session, hardBrake = true)
        session.onStruggleButton(250_000)
        assertEquals(listOf(2), session.report.rounds.map { it.stars })
        assertEquals(1, session.report.struggles)
    }

    @Test
    fun `F9_1 케어를 끝내면 공조를 되돌리고 리포트 화면을 띄운다`() {
        val session = CareSession(t, previousStars = 9)
        playOneRound(session, hardBrake = false)
        gear(session, 299_000, GearPos.P)
        val screens = session.end(300_000).filterIsInstance<ShowScreen>()
        val report = screens.single().screen as Screen.Report
        assertEquals(listOf(3), report.report.rounds.map { it.stars })
        assertEquals(12, report.totalStars)
        assertEquals(Rewards.ITEMS.take(2), report.unlocked)
    }

    @Test
    fun `S1 F9_1 주행 중에 케어가 끝나면 리포트 화면을 띄우지 않고 리포트만 남긴다`() {
        val session = CareSession(t, previousStars = 9)
        playOneRound(session, hardBrake = false) // 아직 D단
        val effects = session.end(300_000)
        assertTrue(effects.none { it is ShowScreen }, "주행 중에는 화면을 띄우면 안 된다: $effects")
        assertEquals(12, session.reportScreen().totalStars)
    }

    @Test
    fun `F3_5 그만으로 끝낸 판도 그때까지 지킨 별로 기록한다`() {
        val session = CareSession(t)
        val signals = trace {
            at(0.seconds) { gear(GearPos.D); speed(60f) }
            listOf(-turn, turn, -turn).forEachIndexed { i, deg -> at((10 + i * 5).seconds) { steer(deg) } }
            ticks(0.seconds, 40.seconds, every = 1.seconds)
        }
        session.drive(signals, says = listOf(Say(21_000, "응"), Say(30_000, "그만")))
        assertEquals(listOf(3), session.report.rounds.map { it.stars })
    }
}
