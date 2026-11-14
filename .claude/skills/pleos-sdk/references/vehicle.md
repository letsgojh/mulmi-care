# Vehicle SDK 2.3.2 (소스 jar에서 확인, 2026-11-14)

소스 위치: `~/.gradle/caches/modules-2/files-2.1/ai.pleos.playground/Vehicle/2.3.2/*/Vehicle-2.3.2-sources.jar`
구현: [vehicle/.../PleosVehicleSource.kt](../../../../vehicle/src/main/kotlin/com/mulmicare/vehicle/PleosVehicleSource.kt), 변환: `PleosSignalMapper.kt`

## 진입점
```kotlin
val vehicle = Vehicle(context.applicationContext)
vehicle.initialize()      // 서비스 바인드
...
vehicle.release()
```
도메인: `getSteeringWheel()`, `getTurnSignal()`, `getBrake()`, `getHVAC()`, `getGear()`(deprecated), `getCarInfo()` 등.

## 공통
- 리스너는 모두 `VehicleListener`를 상속하며 `fun onFailed(e: Exception)`를 구현해야 한다.
- Capability: `check...Capabilities(onComplete: (CapabilityConfig[, AreaBounds]) -> Unit, onFailed: (Exception) -> Unit)`
  - `CapabilityConfig(accessType: AccessType)`, `AccessType { NONE, READ, WRITE, READ_WRITE }`
- `VehicleException`은 AAR에서 난독화되어 있다. 타입으로 분기하지 말고 메시지로 기록한다.

## 조향 — `SteeringWheel` (권한 `pleos.car.permission.CAR_DRIVE`)
- `checkSteeringWheelAngleCapabilities(onComplete: (CapabilityConfig, AreaBounds<Float>) -> Unit, onFailed)`
- `registerSteeringWheelAngle(listener)` / `unregisterSteeringWheelAngle(listener)`
- `SteeringWheelAngleListener.onSteeringWheelAngleUpdated(angle: Float?)`
- **값은 핸들 회전각이 아니라 앞바퀴 조향각(°)이다. 왼쪽이 음수.** 그래서 core 임계값도 앞바퀴 기준이다.

## 방향지시등 — `TurnSignal` (권한 `pleos.car.permission.CAR_LIGHTS`)
- `checkTurnSignalStateCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed)`
- `registerTurnSignalState(listener)` / `unregisterTurnSignalState(listener)`
- `TurnSignalStateListener.onTurnSignalStateUpdated(state: TurnSignalValue?)`
- `TurnSignalValue { OFF, RIGHT, LEFT }` (패키지 `ai.pleos.playground.vehicle.constant.area`). 비상등 값은 없다.

## 제동 — `Brake` (권한 `CAR_DRIVE`) — 현재 사용 안 함
- `registerBrakePedalStatus(listener)`, `BrakePedalStatusListener.onBrakePedalStatusUpdated(status: Boolean?)`

## 기어·속도 — Android Car API
- Pleos `Gear` API는 `@Deprecated("use the Google GEAR_SELECTION property")`, `@hide`.
- 속도는 Vehicle SDK에 없다.
- `useLibrary("android.car")` (Gradle), `<uses-library android:name="android.car" android:required="false"/>` (매니페스트)
```kotlin
Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { car, ready -> ... }
val pm = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
pm.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FAST) // m/s, 후진 음수
pm.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE) // VehicleGear 값
```
- `VehicleGear`: NEUTRAL=0x1, REVERSE=0x2, PARK=0x4, DRIVE=0x8, FIRST=0x10 이상(수동 단수)
- 권한: `android.car.permission.CAR_SPEED`(dangerous → 런타임 요청 필요), `android.car.permission.CAR_POWERTRAIN`(기어)

## HVAC — `vehicle.getHVAC()` (조회 `CAR_CLIMATE`, 제어 `CONTROL_CAR_CLIMATE`)
구현: `PleosClimate.kt`, 변환·반영 비교: `PleosClimateMapper.kt`
- 모두 `SeatArea` 단위. 지원 구역은 Capability로 얻는다:
  - `checkHvacTemperatureCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Float>>, HvacTemperatureConfigArray?) -> Unit, onFailed)`
  - `checkHvacFanSpeedCapabilities(onComplete: (Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed)` — `AreaBounds(minValue, maxValue)`
  - `checkHvacFanDirectionCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, List<FanDirectionType>>) -> Unit, onFailed)`
  - `checkHvacRecirculationCapabilities(onComplete: (List<SeatArea>) -> Unit, onFailed)`, `checkHvacPowerCapabilities(...)` 같은 형태
- 조회: `getHvacTemperature(area, TemperatureUnit.CELSIUS, onComplete: (Float?) -> Unit, onFailed)`, `getHvacFanSpeed(area, (Int?) -> Unit, onFailed)`, `getHvacFanDirection(area, (FanDirectionType?) -> Unit, onFailed)`, `getHvacRecirculationState(area, (Boolean?) -> Unit, onFailed)`, `getHvacPowerState(...)`
- 제어: `setHvacTemperature(area, value, TemperatureUnit.CELSIUS, onComplete, onFailed)`, `setHvacFanSpeed(area, Int, ...)`, `setHvacFanDirection(area, FanDirectionType, ...)`, `setHvacRecirculation(area, Boolean, ...)` (false = 외기), `setHvacPower(area, Boolean, ...)`
- `FanDirectionType`(constant.control): UNKNOWN, FACE, FLOOR, FACE_AND_FLOOR, DEFROST, FACE_AND_DEFROST, DEFROST_AND_FLOOR, FACE_AND_FLOOR_AND_DEFROST
- 바깥 기온: Vehicle SDK에 없음 → Car API `VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE` (`CAR_EXTERIOR_ENVIRONMENT`)
- **에뮬레이터: 공조 전원이 꺼져 있으면 설정이 `PropertyAsyncError code 2`로 거부된다.** 앱의 `setHvacPower(true)`는 반영되지 않았고, Virtual Console로 켜면 동작했다.
