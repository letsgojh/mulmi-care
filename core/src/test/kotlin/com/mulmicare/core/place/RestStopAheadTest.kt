package com.mulmicare.core.place

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.place.TurnKind.OTHER
import com.mulmicare.core.place.TurnKind.REST_AREA
import com.mulmicare.core.place.TurnKind.SERVICE_AREA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** spec F7.2 쉬어 갈 곳 찾기 (내비 회전 안내 기반). */
class RestStopAheadTest {

    private val t = Thresholds()

    @Test
    fun `F7_2 구간 거리를 누적해 현재 위치부터의 거리로 알려 준다`() {
        val turns = listOf(NaviTurn(OTHER, 1_000.0), NaviTurn(OTHER, 2_000.0), NaviTurn(SERVICE_AREA, 3_000.0, "한빛휴게소"))
        assertEquals(RestStop("한빛휴게소", 6.0), RestStopAhead.find(turns, t))
    }

    @Test
    fun `F7_2 휴게소와 쉼터 중 앞쪽의 첫 번째를 고른다`() {
        val turns = listOf(NaviTurn(OTHER, 1_000.0), NaviTurn(REST_AREA, 2_000.0, "가람졸음쉼터"), NaviTurn(SERVICE_AREA, 3_000.0, "한빛휴게소"))
        assertEquals(RestStop("가람졸음쉼터", 3.0), RestStopAhead.find(turns, t))
    }

    @Test
    fun `F7_2 너무 가까워 들어갈 수 없는 쉼터는 건너뛰고 다음 것을 고른다`() {
        val tooClose = t.restStopMinAheadKm * 1000 - 1
        val turns = listOf(NaviTurn(REST_AREA, tooClose, "가까운쉼터"), NaviTurn(SERVICE_AREA, 5_000.0, "한빛휴게소"))
        assertEquals("한빛휴게소", RestStopAhead.find(turns, t)?.name)
    }

    @Test
    fun `F7_2 최소 거리와 같은 쉼터는 고른다`() {
        val turns = listOf(NaviTurn(REST_AREA, t.restStopMinAheadKm * 1000, "가람졸음쉼터"))
        assertEquals("가람졸음쉼터", RestStopAhead.find(turns, t)?.name)
    }

    @Test
    fun `F7_2 최대 거리보다 멀면 없는 것으로 본다`() {
        val turns = listOf(NaviTurn(OTHER, t.restStopMaxAheadKm * 1000), NaviTurn(SERVICE_AREA, 1.0, "먼휴게소"))
        assertNull(RestStopAhead.find(turns, t))
    }

    @Test
    fun `F7_2 최대 거리와 같으면 고른다`() {
        val turns = listOf(NaviTurn(SERVICE_AREA, t.restStopMaxAheadKm * 1000, "먼휴게소"))
        assertEquals("먼휴게소", RestStopAhead.find(turns, t)?.name)
    }

    @Test
    fun `F7_2 경로에 쉼터가 없으면 null`() {
        assertNull(RestStopAhead.find(listOf(NaviTurn(OTHER, 1_000.0)), t))
        assertNull(RestStopAhead.find(emptyList(), t))
    }

    @Test
    fun `F7_2 이름이 없는 쉼터 항목은 쉼터라고 부른다`() {
        assertEquals("쉼터", RestStopAhead.find(listOf(NaviTurn(REST_AREA, 2_000.0, "  ")), t)?.name)
    }

    @Test
    fun `F7_2 하이패스센터처럼 휴게소가 아닌 서비스 시설은 건너뛴다`() {
        // 에뮬레이터 내비 실제 값: 서비스 시설 진입 안내에 하이패스센터가 섞여 있었다
        val turns = listOf(NaviTurn(SERVICE_AREA, 5_500.0, "하이패스센터"), NaviTurn(REST_AREA, 3_000.0, "용인졸음쉼터"))
        assertEquals(RestStop("용인졸음쉼터", 8.5), RestStopAhead.find(turns, t))
    }

    @Test
    fun `F7_2 이름이 없는 서비스 시설은 무엇인지 알 수 없어 건너뛴다`() {
        assertNull(RestStopAhead.find(listOf(NaviTurn(SERVICE_AREA, 2_000.0)), t))
    }

    @Test
    fun `F7_3 내비 회전 안내로 찾은 쉼터는 좌표가 없어 경유지로 추가할 수 없다`() {
        val stop = RestStopAhead.find(listOf(NaviTurn(SERVICE_AREA, 2_000.0, "용인휴게소")), t)!!
        assertEquals(false, stop.canBeWaypoint)
    }
}
