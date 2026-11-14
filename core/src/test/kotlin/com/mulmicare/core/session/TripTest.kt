package com.mulmicare.core.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripTest {

    @Test
    fun `F5_8 별이 5개 모일 때마다 꾸미기가 하나씩 열린다`() {
        assertEquals(emptyList(), Rewards.unlocked(4))
        assertEquals(listOf(Rewards.ITEMS[0]), Rewards.unlocked(5))
        assertEquals(Rewards.ITEMS.take(2), Rewards.unlocked(14))
        assertEquals(Rewards.ITEMS.take(3), Rewards.unlocked(15))
    }

    @Test
    fun `F5_8 준비된 꾸미기보다 많이 모아도 넘치지 않는다`() {
        assertEquals(Rewards.ITEMS, Rewards.unlocked(1_000))
    }

    @Test
    fun `F9_1 리포트의 지킨 별은 판마다 지킨 별의 합이다`() {
        val report = TripReport(listOf(RoundRecord(3, 0, 1), RoundRecord(2, 2, 3)), struggles = 0)
        assertEquals(5, report.starsKept)
        assertEquals(listOf(0, 1), report.rounds.map { it.starsLost })
    }

    @Test
    fun `F9_3 도착 후 힘들었다고 남겼으면 다음 이동은 강화 단계로 시작한다`() {
        assertTrue(NextTripPolicy.startBoost(TripReport(emptyList(), 0), BackseatRating.STRUGGLED))
    }

    @Test
    fun `F9_3 이동 중 힘들어해요가 기준 횟수 이상이면 강화로 시작한다`() {
        assertFalse(NextTripPolicy.startBoost(TripReport(emptyList(), struggles = 1), BackseatRating.FINE))
        assertTrue(NextTripPolicy.startBoost(TripReport(emptyList(), struggles = 2), BackseatRating.FINE))
    }

    @Test
    fun `F9_3 지난 기록이 없으면 기본 단계로 시작한다`() {
        assertFalse(NextTripPolicy.startBoost(null, null))
    }

    // --- F9.3 기록이 놀이 제안 빈도에 반영된다 ---

    private val t = com.mulmicare.core.config.Thresholds()

    @Test
    fun `F9_3 지난 이동에서 힘들어했으면 거절 뒤 다시 묻는 간격을 줄인다`() {
        val cooldown = NextTripPolicy.proposalCooldownMs(t, startBoost = true)
        assertEquals(t.proposalCooldownAfterStruggleMs, cooldown)
        assertTrue(cooldown < t.proposalCooldownMs)
    }

    @Test
    fun `F9_3 지난 이동이 괜찮았으면 거절 뒤 15분 동안 묻지 않는 규칙 그대로`() {
        assertEquals(t.proposalCooldownMs, NextTripPolicy.proposalCooldownMs(t, startBoost = false))
    }
}
