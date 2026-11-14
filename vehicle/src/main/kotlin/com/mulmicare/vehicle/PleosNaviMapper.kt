package com.mulmicare.vehicle

import ai.pleos.playground.navi.constants.TurnType
import com.mulmicare.core.place.NaviTurn
import com.mulmicare.core.place.TurnKind

/** Pleos NaviHelper 값을 core 타입으로 바꾼다. 판단 로직은 넣지 않는다. */
object PleosNaviMapper {

    fun kind(type: TurnType?): TurnKind = when (type) {
        TurnType.TURN_RESTAREA -> TurnKind.REST_AREA
        TurnType.TURN_ENTER_SERVICEAREA -> TurnKind.SERVICE_AREA
        else -> TurnKind.OTHER
    }

    /** [distanceM]는 앞 항목부터의 구간 거리(에뮬레이터에서 구간 거리 합 = 경로 전체 거리 확인). */
    fun turn(type: TurnType?, distanceM: Double, description: String?): NaviTurn =
        NaviTurn(kind(type), distanceM, description.orEmpty())
}
