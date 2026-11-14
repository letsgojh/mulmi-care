# NaviHelper SDK 3.0.5 (문서 + AAR javap + 에뮬레이터, 2026-11-15)

구현: `vehicle/.../PleosNavi.kt`, 변환: `PleosNaviMapper.kt`. 소스 jar 비공개. 데이터 클래스는 `ai.pleos.playground:navi-data:3.0.4`.

- 의존성: `ai.pleos.playground:NaviHelper:3.0.5`. 권한: `pleos.car.permission.NAVI_ROUTE`(get*Info), `NAVI_ROUTE_SEARCH`(경로·경유지), `NAVI_CUSTOM_MAP`(requestNavigationScreen, 안 씀)
```kotlin
val navi = NaviHelper(context.applicationContext)
navi.initialize({ navi.addListener(listener) /* 여기서 등록 */ }, { e -> })
navi.requestRoute(RouteInfo(latitude = .., longitude = .., displayLongitude = null, displayLatitude = null, poiName = .., poiId = "", poiSubId = "0", address = ""))
navi.addWaypoint(RequestWaypointInfo(latitude = .., longitude = .., waypointIndex = WaypointIndex.FIRST, poiId = "", poiName = .., poiSubId = "0", address = ""))
navi.getTBTInfo(); navi.getDestinationInfo(); navi.getCurrentLocationInfo(); navi.getRouteStateInfo()  // 결과는 리스너로
navi.removeListener(listener); navi.release()
```
- `NaviHelperEventListener`(모두 default 메서드): `onRouteStarted(RouteStartInfo)`, `onRouteCancelled()`, `onDestinationArrived(DestinationArrivedInfo)`, `onDestinationInfo(DestinationInfo)`, `onTBTInfo(List<TBTInfo>)`, `onCurrentLocationInfo(CurrentLocationInfo)`, `onRouteStateInfo`, `onDrivingInfo`, `onNavigationStatus`, `onError(NaviErrorCode)` 등. **`onRouteEnded`는 deprecated**이고 에뮬레이터에서 경로 시작과 동시에 와서 쓰지 않는다.
- 데이터:
  - `RouteStartInfo(duration 초, distance m, tollFare, name, ...)`
  - `DestinationInfo(latitude, longitude, name, address, remainDistance m, remainTime 초, ...)`
  - `TBTInfo(type: TurnType, distance: Double m, description: String)` — **distance는 앞 항목부터의 구간 거리**(합 = 경로 거리)
  - `CurrentLocationInfo(latitude, longitude, heading, address, routeId, currentRouteIndex)`
  - `TurnType`: TURN_LEFT/RIGHT/KEEP_L/R/SHARP_L/R/UTURN, IC·고가·지하차도·로터리 진입/진출, TURN_TOLL, **TURN_RESTAREA(졸음쉼터)**, **TURN_ENTER_SERVICEAREA(휴게소, 하이패스센터 등 섞임)**, TURN_ARRIVE 등
- **경로 모양(좌표 목록·곡률·도로 종류)은 주지 않는다** → 굽은 길 사전 예측 불가(2단계 과제)
- 에뮬레이터: 경로 요청 약 10초 뒤 `onRouteStarted`. 부산역 경로 회전 안내 54개 중 휴게소·쉼터 23개.
