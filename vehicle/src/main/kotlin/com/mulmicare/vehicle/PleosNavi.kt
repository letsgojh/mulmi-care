package com.mulmicare.vehicle

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.annotation.SuppressLint
import android.content.Context
import com.mulmicare.core.place.NaviTurn

/**
 * Pleos NaviHelper 연동. 판단은 core가 하고 여기서는 내비 이벤트를 받아 넘기고 요청만 보낸다.
 * 내비 이벤트는 내비 앱 구현에 따라 다를 수 있다(검증 항목 2) — 받은 이벤트를 모두 [onLog]로 남긴다.
 *
 * 권한: pleos.car.permission.NAVI_ROUTE(정보 조회), pleos.car.permission.NAVI_ROUTE_SEARCH(경로·경유지).
 */
@SuppressLint("MissingPermission")
class PleosNavi(
    context: Context,
    private val listener: Listener,
    private val onLog: (String) -> Unit,
) {
    interface Listener {
        /** 경로 안내 시작. [durationSec] 전체 예상 소요 시간. */
        fun onRouteStarted(durationSec: Int, distanceM: Int) {}

        fun onDestinationInfo(remainTimeSec: Int, remainDistanceM: Int) {}

        fun onRouteEnded() {}

        fun onArrived() {}

        fun onLocation(latitude: Double, longitude: Double, heading: Double) {}

        /** 앞으로의 회전 안내. 거리는 앞 항목부터의 구간 거리. */
        fun onTurns(turns: List<NaviTurn>) {}
    }

    private val navi = NaviHelper(context.applicationContext)
    private var ready = false

    private val events = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            onLog("내비 경로 시작: $routeStartInfo")
            listener.onRouteStarted(routeStartInfo.duration, routeStartInfo.distance)
        }

        override fun onRouteCancelled() {
            onLog("내비 경로 취소")
            listener.onRouteEnded()
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            onLog("내비 도착: $destinationArrivedInfo")
            listener.onArrived()
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            onLog("내비 목적지: ${destinationInfo.name}, 남은 ${destinationInfo.remainTime}초 / ${destinationInfo.remainDistance}m")
            listener.onDestinationInfo(destinationInfo.remainTime, destinationInfo.remainDistance)
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            listener.onLocation(currentLocationInfo.latitude, currentLocationInfo.longitude, currentLocationInfo.heading)
        }

        override fun onTBTInfo(tbtInfo: List<TBTInfo>) {
            val stops = tbtInfo.filter { PleosNaviMapper.kind(it.type) != com.mulmicare.core.place.TurnKind.OTHER }
            onLog("내비 회전 안내 ${tbtInfo.size}개, 휴게소·쉼터 항목: ${stops.joinToString { "${it.type.name.removePrefix("TURN_")} ${it.description}" }}")
            listener.onTurns(tbtInfo.map { PleosNaviMapper.turn(it.type, it.distance, it.description) })
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = onLog("내비 상태: $routeStateInfo")

        override fun onDrivingInfo(drivingInfo: DrivingInfo) = onLog("내비 주행: ${drivingInfo.destination}")

        override fun onNavigationStatus(navigationStatus: NavigationStatus) = onLog("내비 모드: $navigationStatus")

        override fun onError(errorCode: NaviErrorCode) = onLog("⚠ 내비 오류: $errorCode")
    }

    fun start() {
        navi.initialize(
            {
                navi.addListener(events)
                ready = true
                onLog("내비 연결됨")
                navi.getRouteStateInfo()
                navi.getDestinationInfo()
            },
            { e -> onLog("⚠ 내비 연결 실패: ${e.javaClass.simpleName}: ${e.message}") },
        )
    }

    fun stop() {
        if (ready) runCatching { navi.removeListener(events) }
        ready = false
        navi.release()
    }

    /** 디버그용: 지정한 곳으로 경로 안내를 요청한다. */
    fun requestRoute(latitude: Double, longitude: Double, name: String) {
        if (!ready) return onLog("⚠ 내비 연결 전")
        navi.requestRoute(
            RouteInfo(
                latitude = latitude,
                longitude = longitude,
                displayLongitude = null,
                displayLatitude = null,
                poiName = name,
                poiId = "",
                poiSubId = "0",
                address = "",
            ),
        )
    }

    /** F7.3 쉼터를 첫 번째 경유지로 추가한다. */
    fun addWaypoint(latitude: Double, longitude: Double, name: String) {
        if (!ready) return onLog("⚠ 내비 연결 전: 경유지를 추가하지 못함")
        navi.addWaypoint(
            RequestWaypointInfo(
                latitude = latitude,
                longitude = longitude,
                waypointIndex = WaypointIndex.FIRST,
                poiId = "",
                poiName = name,
                poiSubId = "0",
                address = "",
            ),
        )
    }

    /** 회전 안내를 다시 받는다(결과는 onTurns로 온다). */
    fun refreshTurns() {
        if (ready) navi.getTBTInfo()
    }

    /** 현재 상태를 다시 물어본다(결과는 이벤트로 온다). */
    fun refresh() {
        if (!ready) return
        navi.getRouteStateInfo()
        navi.getDestinationInfo()
        navi.getTBTInfo()
        navi.getCurrentLocationInfo()
    }
}
