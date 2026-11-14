package com.mulmicare.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.annotation.SuppressLint
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.mulmicare.core.signal.VehicleSignal

/**
 * 차량 신호를 받아 core의 [VehicleSignal]로 넘긴다.
 *
 * - 조향각·방향지시등: Pleos Vehicle SDK. 호출 전에 Capability를 확인한다(S10).
 * - 속도·기어: Android Car API. Pleos Gear API는 deprecated이고 "Google GEAR_SELECTION을 쓰라"고 안내한다.
 * - 제동 페달은 core 판정에 쓰지 않으므로 등록하지 않는다.
 *
 * 콜백이 어느 스레드에서 오는지 보장하지 않는다. 받는 쪽([start]의 onSignal)에서 한 스레드로 모아 CareSession에 넣는다.
 * 실패·미지원은 예외로 올리지 않고 onIssue로 알린다.
 *
 * 필요 권한: pleos.car.permission.CAR_DRIVE(조향), pleos.car.permission.CAR_LIGHTS(방향지시등),
 * android.car.permission.CAR_SPEED(속도, 런타임 권한), android.car.permission.CAR_POWERTRAIN(기어).
 */
@SuppressLint("MissingPermission")
class PleosVehicleSource(
    context: Context,
    private val steeringCalibration: SteeringCalibration = SteeringCalibration.SDK_FRONT_WHEEL,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val appContext = context.applicationContext

    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var onSignal: ((VehicleSignal) -> Unit)? = null

    /** 공조 읽기·제어. [start] 뒤에 쓸 수 있다. */
    var climate: PleosClimate? = null
        private set
    private var onIssue: ((String) -> Unit)? = null

    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) {
            emit(PleosSignalMapper.steering(clock(), angle, steeringCalibration))
        }

        override fun onFailed(e: Exception) = report("조향각 수신", e)
    }

    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            emit(PleosSignalMapper.turnSignal(clock(), state))
        }

        override fun onFailed(e: Exception) = report("방향지시등 수신", e)
    }

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val now = clock()
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> emit(PleosSignalMapper.speed(now, value.value as? Float))
                VehiclePropertyIds.GEAR_SELECTION -> emit(PleosSignalMapper.gear(now, value.value as? Int))
            }
        }

        override fun onErrorEvent(propertyId: Int, zone: Int) {
            issue("차량 속성 오류: ${VehiclePropertyIds.toString(propertyId)}")
        }
    }

    fun start(onSignal: (VehicleSignal) -> Unit, onIssue: (String) -> Unit) {
        check(vehicle == null) { "이미 시작함" }
        this.onSignal = onSignal
        this.onIssue = onIssue
        startPleos()
        startCarProperties()
    }

    fun stop() {
        vehicle?.let { v ->
            runCatching { v.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener) }
            runCatching { v.getTurnSignal().unregisterTurnSignalState(turnSignalListener) }
            v.release()
        }
        vehicle = null
        climate = null
        propertyManager?.let { pm -> runCatching { pm.unregisterCallback(propertyCallback) } }
        propertyManager = null
        car?.disconnect()
        car = null
        onSignal = null
        onIssue = null
    }

    private fun startPleos() {
        val v = Vehicle(appContext).also {
            it.initialize()
            vehicle = it
        }
        climate = PleosClimate(v, propertyManager = { propertyManager }, onIssue = ::issue)
        val steering = v.getSteeringWheel()
        steering.checkSteeringWheelAngleCapabilities(
            { config, _ ->
                if (config.canRead()) steering.registerSteeringWheelAngle(steeringListener) else issue("조향각 미지원")
            },
            { e -> report("조향각 Capability", e) },
        )
        val turnSignal = v.getTurnSignal()
        turnSignal.checkTurnSignalStateCapabilities(
            { config ->
                if (config.canRead()) turnSignal.registerTurnSignalState(turnSignalListener) else issue("방향지시등 미지원")
            },
            { e -> report("방향지시등 Capability", e) },
        )
    }

    private fun startCarProperties() {
        car = Car.createCar(appContext, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
            if (!ready) {
                propertyManager = null
                return@createCar
            }
            val pm = connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            if (pm == null) {
                issue("CarPropertyManager 없음")
                return@createCar
            }
            propertyManager = pm
            registerProperty(pm, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FAST, "속도")
            registerProperty(pm, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE, "기어")
        }
    }

    private fun registerProperty(pm: CarPropertyManager, propertyId: Int, rate: Float, name: String) {
        try {
            if (!pm.registerCallback(propertyCallback, propertyId, rate)) issue("$name 등록 실패")
        } catch (e: SecurityException) {
            report("$name 권한 없음", e)
        } catch (e: IllegalArgumentException) {
            report("$name 미지원", e)
        }
    }

    private fun CapabilityConfig.canRead() =
        accessType == AccessType.READ || accessType == AccessType.READ_WRITE

    private fun emit(signal: VehicleSignal?) {
        if (signal != null) onSignal?.invoke(signal)
    }

    private fun issue(message: String) {
        Log.w(TAG, message)
        onIssue?.invoke(message)
    }

    private fun report(what: String, e: Exception) {
        // VehicleException은 AAR에서 난독화되어 있어 타입 대신 메시지로 남긴다
        issue("$what 실패: ${e.javaClass.simpleName}: ${e.message}")
    }

    private companion object {
        const val TAG = "MulmiVehicle"
    }
}
