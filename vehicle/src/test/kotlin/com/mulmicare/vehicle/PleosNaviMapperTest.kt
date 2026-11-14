package com.mulmicare.vehicle

import ai.pleos.playground.navi.constants.TurnType
import com.mulmicare.core.place.NaviTurn
import com.mulmicare.core.place.TurnKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PleosNaviMapperTest {

    @Test
    fun `쉼터와 휴게소 진입 안내를 구분한다`() {
        assertEquals(TurnKind.REST_AREA, PleosNaviMapper.kind(TurnType.TURN_RESTAREA))
        assertEquals(TurnKind.SERVICE_AREA, PleosNaviMapper.kind(TurnType.TURN_ENTER_SERVICEAREA))
    }

    @Test
    fun `그 밖의 회전 안내와 알 수 없는 값은 기타`() {
        assertEquals(TurnKind.OTHER, PleosNaviMapper.kind(TurnType.TURN_LEFT))
        assertEquals(TurnKind.OTHER, PleosNaviMapper.kind(TurnType.TURN_TOLL))
        assertEquals(TurnKind.OTHER, PleosNaviMapper.kind(null))
    }

    @Test
    fun `회전 안내 한 항목을 거리와 설명과 함께 바꾼다`() {
        assertEquals(
            NaviTurn(TurnKind.SERVICE_AREA, 2_600.0, "한빛휴게소"),
            PleosNaviMapper.turn(TurnType.TURN_ENTER_SERVICEAREA, 2_600.0, "한빛휴게소"),
        )
        assertEquals(NaviTurn(TurnKind.OTHER, 80.0, ""), PleosNaviMapper.turn(TurnType.TURN_LEFT, 80.0, null))
    }
}
