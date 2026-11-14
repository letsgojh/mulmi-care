package com.mulmicare.core.care

import kotlin.test.Test
import kotlin.test.assertEquals

class BaselineResolverTest {

    private val original = ClimateBaseline(setTempC = 22f, fanSpeed = 3, fanSpeedMax = 8, fanDirection = FanDirection.FLOOR, recirculation = true)
    private val saved = SavedClimate(original, lastAppliedTempC = 19.5f)

    @Test
    fun `저장된 기준값이 없으면 지금 읽은 값을 쓴다`() {
        val read = original.copy(setTempC = 19.5f)
        assertEquals(read, BaselineResolver.resolve(read, saved = null))
    }

    @Test
    fun `지금 온도가 앱이 마지막으로 맞춘 값이면 되돌리지 못하고 끝난 것이라 저장해 둔 기준값을 쓴다`() {
        val read = ClimateBaseline(setTempC = 19.5f, fanSpeed = 4, fanSpeedMax = 8, fanDirection = FanDirection.FACE, recirculation = false)
        assertEquals(original, BaselineResolver.resolve(read, saved))
    }

    @Test
    fun `0점1도 안쪽 차이는 같은 값으로 본다`() {
        val read = original.copy(setTempC = 19.55f)
        assertEquals(original, BaselineResolver.resolve(read, saved))
    }

    @Test
    fun `운전자가 그 사이 온도를 바꿨으면 지금 읽은 값을 기준으로 한다`() {
        val read = original.copy(setTempC = 24f)
        assertEquals(read, BaselineResolver.resolve(read, saved))
    }
}
