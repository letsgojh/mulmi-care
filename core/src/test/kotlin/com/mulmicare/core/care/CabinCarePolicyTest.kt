package com.mulmicare.core.care

import com.mulmicare.core.care.CareLevel.BASE
import com.mulmicare.core.care.CareLevel.BOOST
import com.mulmicare.core.care.FanDirection.FACE
import com.mulmicare.core.care.FanDirection.FLOOR
import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect.ApplyClimate
import kotlin.test.Test
import kotlin.test.assertEquals

/** spec F6 실내 자동 케어, F9.3 다음 이동 강화 시작. */
class CabinCarePolicyTest {

    // 기대값을 숫자로 분명히 쓰려고 임계값을 테스트에서 정한다
    private val t = Thresholds(
        baseDropC = 1f,
        boostExtraDropC = 1.5f,
        boostHoldMs = 5 * 60_000,
        winterOutsideC = 5f,
        winterDropScale = 0.5f,
        winterMinSetC = 20f,
        setTempStepC = 0.5f,
    )
    private val hold = t.boostHoldMs
    private val policy = CabinCarePolicy(t)

    private val baseline = ClimateBaseline(setTempC = 23f, fanSpeed = 3, fanSpeedMax = 8, fanDirection = FLOOR)
    private val summerBase = ApplyClimate(BASE, ClimateTarget(22f, outsideAir = true, fanSpeed = 3, fanDirection = FLOOR))
    private val summerBoost = ApplyClimate(BOOST, ClimateTarget(20.5f, outsideAir = true, fanSpeed = 4, fanDirection = FACE))

    private fun summer() = policy.onBaseline(0, baseline, outsideC = 28f, month = 7)

    // --- F6.2 기본, F6.3 강화 ---

    @Test
    fun `F6_2 케어를 켜면 기본 단계로 외기를 들이고 기준 온도에서 1도 낮춘다`() {
        assertEquals(listOf(summerBase), summer())
    }

    @Test
    fun `F6_3 굽은 길 구간에 들어서면 강화 단계로 더 낮추고 풍량을 올리고 상체로 바람을 보낸다`() {
        summer()
        assertEquals(listOf(summerBoost), policy.onSegmentStarted(10_000))
        assertEquals(BOOST, policy.level)
    }

    @Test
    fun `F6_3 풍량이 이미 최대면 더 올리지 않는다`() {
        policy.onBaseline(0, baseline.copy(fanSpeed = 8), outsideC = 28f, month = 7)
        val boost = policy.onSegmentStarted(10_000).single() as ApplyClimate
        assertEquals(8, boost.target.fanSpeed)
    }

    @Test
    fun `F6_3 풍량을 읽지 못했으면 풍량은 바꾸지 않는다`() {
        policy.onBaseline(0, baseline.copy(fanSpeed = null), outsideC = 28f, month = 7)
        val boost = policy.onSegmentStarted(10_000).single() as ApplyClimate
        assertEquals(null, boost.target.fanSpeed)
    }

    // --- F6.4 강화 단계 진입 ---

    @Test
    fun `F6_4 뒷자리가 힘들어하면 강화 단계로 바꾼다`() {
        summer()
        assertEquals(listOf(summerBoost), policy.onPassengerStruggling(10_000))
    }

    @Test
    fun `F6_4 이미 강화 단계면 다시 요청하지 않는다`() {
        summer()
        policy.onSegmentStarted(10_000)
        assertEquals(emptyList(), policy.onPassengerStruggling(20_000))
    }

    // --- F6.5 기본 단계로 돌아가기: 5분 직전 / 정확히 5분 ---

    @Test
    fun `F6_5 구간이 끝나고 5분이 되기 직전까지는 강화 단계를 유지한다`() {
        summer()
        policy.onSegmentStarted(0)
        policy.onSegmentEnded(60_000)
        assertEquals(emptyList(), policy.onTick(60_000 + hold - 1))
    }

    @Test
    fun `F6_5 구간이 끝나고 5분이 지나면 기본 단계로 돌아간다`() {
        summer()
        policy.onSegmentStarted(0)
        policy.onSegmentEnded(60_000)
        assertEquals(listOf(summerBase), policy.onTick(60_000 + hold))
        assertEquals(emptyList(), policy.onTick(60_000 + hold + 1_000))
    }

    @Test
    fun `F6_5 5분 안에 새 구간이 시작되면 돌아가지 않고 그 구간이 끝난 때부터 다시 센다`() {
        summer()
        policy.onSegmentStarted(0)
        policy.onSegmentEnded(60_000)
        assertEquals(emptyList(), policy.onSegmentStarted(120_000))
        assertEquals(emptyList(), policy.onTick(60_000 + hold))
        policy.onSegmentEnded(300_000)
        assertEquals(emptyList(), policy.onTick(300_000 + hold - 1))
        assertEquals(listOf(summerBase), policy.onTick(300_000 + hold))
    }

    @Test
    fun `F6_5 힘들어해요로 들어간 강화 단계도 5분 뒤 기본 단계로 돌아간다`() {
        summer()
        policy.onPassengerStruggling(0)
        assertEquals(emptyList(), policy.onTick(hold - 1))
        assertEquals(listOf(summerBase), policy.onTick(hold))
    }

    @Test
    fun `F6_5 굽은 길 구간 안에서는 힘들어해요 뒤 5분이 지나도 강화 단계를 유지한다`() {
        summer()
        policy.onSegmentStarted(0)
        policy.onPassengerStruggling(10_000)
        assertEquals(emptyList(), policy.onTick(10_000 + hold))
    }

    // --- F6.6 겨울 보정 ---

    @Test
    fun `F6_6 겨울에는 낮추는 폭을 줄이고 외기와 상체 바람은 그대로 쓴다`() {
        assertEquals(
            listOf(ApplyClimate(BASE, ClimateTarget(22.5f, outsideAir = true, fanSpeed = 3, fanDirection = FLOOR))),
            policy.onBaseline(0, baseline, outsideC = 0f, month = 1),
        )
        // 23 - (1 + 1.5) * 0.5 = 21.75 → 0.5 단위로 따뜻한 쪽으로 올려 22.0
        assertEquals(
            listOf(ApplyClimate(BOOST, ClimateTarget(22f, outsideAir = true, fanSpeed = 4, fanDirection = FACE))),
            policy.onSegmentStarted(10_000),
        )
    }

    @Test
    fun `F6_6 겨울에는 하한 아래로 내리지 않는다`() {
        policy.onBaseline(0, baseline.copy(setTempC = 21f), outsideC = 0f, month = 1)
        // 21 - 1.25 = 19.75 → 하한 20
        val boost = policy.onSegmentStarted(10_000).single() as ApplyClimate
        assertEquals(20f, boost.target.setTempC)
    }

    @Test
    fun `F6_6 기준 온도가 이미 하한보다 낮으면 올리지도 않는다`() {
        val base = policy.onBaseline(0, baseline.copy(setTempC = 19f), outsideC = 0f, month = 1).single() as ApplyClimate
        assertEquals(19f, base.target.setTempC)
    }

    // --- F6.7 계절 판단: 기온 경계 / 기온 없으면 날짜 ---

    @Test
    fun `F6_7 바깥 기온이 기준보다 조금이라도 낮으면 겨울로 본다`() {
        val base = policy.onBaseline(0, baseline, outsideC = t.winterOutsideC - 0.1f, month = 7).single() as ApplyClimate
        assertEquals(22.5f, base.target.setTempC)
    }

    @Test
    fun `F6_7 바깥 기온이 기준과 같으면 겨울이 아니다`() {
        val base = policy.onBaseline(0, baseline, outsideC = t.winterOutsideC, month = 1).single() as ApplyClimate
        assertEquals(22f, base.target.setTempC)
    }

    @Test
    fun `F6_7 바깥 기온을 모르면 12월부터 2월까지를 겨울로 본다`() {
        fun baseFor(month: Int) =
            (CabinCarePolicy(t).onBaseline(0, baseline, outsideC = null, month = month).single() as ApplyClimate).target.setTempC
        assertEquals(22.5f, baseFor(12))
        assertEquals(22.5f, baseFor(1))
        assertEquals(22.5f, baseFor(2))
        assertEquals(22f, baseFor(3))
        assertEquals(22f, baseFor(11))
    }

    // --- F9.3, 기준값 ---

    @Test
    fun `F9_3 지난 이동에서 자주 힘들어했으면 처음부터 강화 단계로 시작한다`() {
        val boosted = CabinCarePolicy(t, startBoost = true)
        assertEquals(listOf(summerBoost), boosted.onBaseline(0, baseline, outsideC = 28f, month = 7))
    }

    @Test
    fun `F6 기준값을 읽기 전의 단계 변화는 기억했다가 기준값이 오면 적용한다`() {
        assertEquals(emptyList(), policy.onSegmentStarted(0))
        assertEquals(emptyList(), policy.onTick(1_000))
        assertEquals(listOf(summerBoost), summer())
    }
}
