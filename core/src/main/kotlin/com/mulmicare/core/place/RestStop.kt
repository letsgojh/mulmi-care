package com.mulmicare.core.place

/**
 * F7.2 쉬어 갈 곳(휴게소·졸음쉼터). [distanceKm]는 현재 위치에서 경로상 남은 거리.
 * 좌표는 알 때만 있다. 내비 회전 안내로 찾은 쉼터는 좌표가 없고, 원래 경로 위에 있다.
 */
data class RestStop(
    val name: String,
    val distanceKm: Double,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /** 내비 경유지로 추가할 수 있는지(좌표가 있는지). */
    val canBeWaypoint: Boolean get() = latitude != null && longitude != null
}
