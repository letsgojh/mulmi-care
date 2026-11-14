package com.mulmicare.vehicle

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import com.mulmicare.core.signal.GearPos
import com.mulmicare.core.signal.TurnState
import com.mulmicare.core.signal.VehicleSignal
import kotlin.math.abs

/**
 * 조향 신호 보정.
 * - SDK 문서: SteeringWheelAngleListener는 앞바퀴 조향각(°), 왼쪽 음수.
 * - Pleos 에뮬레이터(v2.1.0)에서 확인: Virtual Console의 핸들 회전각이 그대로 들어오고, 오른쪽이 음수다.
 */
data class SteeringCalibration(val invert: Boolean, val steeringRatio: Float) {
    companion object {
        val SDK_FRONT_WHEEL = SteeringCalibration(invert = false, steeringRatio = 1f)
        val PLEOS_EMULATOR = SteeringCalibration(invert = true, steeringRatio = 14f)
    }
}

/** Pleos Vehicle SDK·Android Car API 값을 core의 [VehicleSignal]로 바꾼다. 판단 로직은 넣지 않는다. */
object PleosSignalMapper {

    /** core 규약: 앞바퀴 조향각(°), 왼쪽 음수. */
    fun steering(
        atMs: Long,
        raw: Float?,
        calibration: SteeringCalibration = SteeringCalibration.SDK_FRONT_WHEEL,
    ): VehicleSignal.SteeringAngle? {
        if (raw == null || raw.isNaN()) return null
        val sign = if (calibration.invert) -1f else 1f
        return VehicleSignal.SteeringAngle(atMs, sign * raw / calibration.steeringRatio)
    }

    fun turnSignal(atMs: Long, value: TurnSignalValue?): VehicleSignal.TurnSignal? {
        val state = when (value) {
            TurnSignalValue.OFF -> TurnState.OFF
            TurnSignalValue.LEFT -> TurnState.LEFT
            TurnSignalValue.RIGHT -> TurnState.RIGHT
            null -> return null
        }
        return VehicleSignal.TurnSignal(atMs, state)
    }

    /** PERF_VEHICLE_SPEED: m/s, 후진이면 음수. 감속 판단에는 크기만 필요하다. */
    fun speed(atMs: Long, metersPerSecond: Float?): VehicleSignal.Speed? {
        if (metersPerSecond == null || metersPerSecond.isNaN()) return null
        return VehicleSignal.Speed(atMs, abs(metersPerSecond) * MPS_TO_KPH)
    }

    /** GEAR_SELECTION: android.car.VehicleGear 값. 수동 변속 단수(1단 이상)는 주행으로 본다. */
    fun gear(atMs: Long, carGear: Int?): VehicleSignal.Gear? {
        val pos = when {
            carGear == null -> return null
            carGear == GEAR_PARK -> GearPos.P
            carGear == GEAR_REVERSE -> GearPos.R
            carGear == GEAR_NEUTRAL -> GearPos.N
            carGear == GEAR_DRIVE || carGear >= GEAR_FIRST -> GearPos.D
            else -> return null
        }
        return VehicleSignal.Gear(atMs, pos)
    }

    private const val MPS_TO_KPH = 3.6f

    // android.car.VehicleGear 상수. 순수 JVM 테스트에서 android.car 없이 쓰려고 값을 옮겨 둔다.
    private const val GEAR_NEUTRAL = 0x0001
    private const val GEAR_REVERSE = 0x0002
    private const val GEAR_PARK = 0x0004
    private const val GEAR_DRIVE = 0x0008
    private const val GEAR_FIRST = 0x0010
}
