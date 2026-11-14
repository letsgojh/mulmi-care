package com.mulmicare.vehicle

import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import com.mulmicare.core.care.ClimateTarget
import com.mulmicare.core.care.FanDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PleosClimateMapperTest {

    @Test
    fun `core 풍향을 SDK 풍향으로 바꾼다`() {
        assertEquals(FanDirectionType.FACE, PleosClimateMapper.toSdk(FanDirection.FACE))
        assertEquals(FanDirectionType.FLOOR, PleosClimateMapper.toSdk(FanDirection.FLOOR))
        assertEquals(FanDirectionType.FACE_AND_FLOOR, PleosClimateMapper.toSdk(FanDirection.FACE_AND_FLOOR))
        assertEquals(FanDirectionType.DEFROST, PleosClimateMapper.toSdk(FanDirection.DEFROST))
    }

    @Test
    fun `SDK 풍향 중 core가 다루는 것만 바꾸고 나머지 조합은 null`() {
        assertEquals(FanDirection.FACE, PleosClimateMapper.fromSdk(FanDirectionType.FACE))
        assertEquals(FanDirection.FLOOR, PleosClimateMapper.fromSdk(FanDirectionType.FLOOR))
        assertNull(PleosClimateMapper.fromSdk(FanDirectionType.FACE_AND_DEFROST))
        assertNull(PleosClimateMapper.fromSdk(FanDirectionType.UNKNOWN))
        assertNull(PleosClimateMapper.fromSdk(null))
    }

    private val target = ClimateTarget(setTempC = 20.5f, outsideAir = true, fanSpeed = 4, fanDirection = FanDirection.FACE)

    @Test
    fun `F6_8 요청한 값이 모두 반영됐으면 다른 항목이 없다`() {
        val read = ClimateReadback(20.5f, 4, FanDirectionType.FACE, recirculation = false)
        assertEquals(emptyList<String>(), PleosClimateMapper.mismatches(target, read))
    }

    @Test
    fun `F6_8 반영되지 않은 항목을 알려 준다`() {
        val read = ClimateReadback(22f, 3, FanDirectionType.FLOOR, recirculation = true)
        assertEquals(listOf("온도", "풍량", "풍향", "외기"), PleosClimateMapper.mismatches(target, read))
    }

    @Test
    fun `F6_8 읽지 못한 항목과 요청하지 않은 항목은 비교하지 않는다`() {
        val partial = target.copy(fanSpeed = null, fanDirection = null)
        val read = ClimateReadback(setTempC = null, fanSpeed = 1, fanDirection = FanDirectionType.FLOOR, recirculation = null)
        assertEquals(emptyList<String>(), PleosClimateMapper.mismatches(partial, read))
    }

    @Test
    fun `F6_8 온도는 0점1도 안쪽 차이는 같다고 본다`() {
        val read = ClimateReadback(20.55f, 4, FanDirectionType.FACE, recirculation = false)
        assertEquals(emptyList<String>(), PleosClimateMapper.mismatches(target, read))
    }
}
