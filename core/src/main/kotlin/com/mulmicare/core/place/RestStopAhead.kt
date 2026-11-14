package com.mulmicare.core.place

import com.mulmicare.core.config.Thresholds

enum class TurnKind { REST_AREA, SERVICE_AREA, OTHER }

/** 내비 회전 안내 한 항목. [distanceM]는 앞 항목(첫 항목은 현재 위치)부터의 구간 거리. */
data class NaviTurn(val kind: TurnKind, val distanceM: Double, val description: String = "")

/**
 * 내비 회전 안내 목록에서 경로 앞쪽의 쉬어 갈 곳을 찾는다 (spec F7.2).
 * 구간 거리를 누적해 현재 위치부터의 거리를 구하고, 너무 가까운 곳과 휴게소·쉼터가 아닌 시설은 건너뛴다.
 */
object RestStopAhead {
    fun find(turns: List<NaviTurn>, t: Thresholds = Thresholds()): RestStop? {
        var aheadM = 0.0
        for (turn in turns) {
            aheadM += turn.distanceM
            val aheadKm = aheadM / 1000
            if (aheadKm > t.restStopMaxAheadKm) return null
            if (turn.kind == TurnKind.OTHER || aheadKm < t.restStopMinAheadKm) continue
            val name = restStopName(turn) ?: continue
            return RestStop(name, aheadKm)
        }
        return null
    }

    /**
     * 쉬어 갈 수 있는 곳의 이름. 아니면 null.
     * 에뮬레이터 내비에서 확인: 쉼터 항목은 "OO졸음쉼터", 서비스 시설 항목은 대부분 "OO휴게소"지만
     * "하이패스센터" 같은 다른 시설도 섞여 있다. 그래서 이름에 휴게소·쉼터가 있는 곳만 고른다.
     */
    private fun restStopName(turn: NaviTurn): String? {
        val name = turn.description.trim()
        return when {
            "휴게소" in name || "쉼터" in name -> name
            name.isEmpty() && turn.kind == TurnKind.REST_AREA -> "쉼터"
            else -> null
        }
    }
}
