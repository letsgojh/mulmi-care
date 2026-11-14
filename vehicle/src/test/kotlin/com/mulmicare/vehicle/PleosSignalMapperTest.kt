package com.mulmicare.vehicle

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import com.mulmicare.core.signal.GearPos
import com.mulmicare.core.signal.TurnState
import com.mulmicare.core.signal.VehicleSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PleosSignalMapperTest {

    // --- 조향: SteeringWheelAngleListener는 앞바퀴 각도(°), 왼쪽 음수 → core 규약과 같다 ---

    @Test
    fun `조향각은 앞바퀴 각도와 부호를 그대로 쓴다`() {
        assertEquals(VehicleSignal.SteeringAngle(5, -1.5f), PleosSignalMapper.steering(5, -1.5f))
        assertEquals(VehicleSignal.SteeringAngle(5, 2.0f), PleosSignalMapper.steering(5, 2.0f))
    }

    @Test
    fun `실차 보정은 SDK 값을 그대로 쓴다`() {
        assertEquals(-1.5f, PleosSignalMapper.steering(5, -1.5f, SteeringCalibration.SDK_FRONT_WHEEL)!!.deg, 0.0001f)
    }

    @Test
    fun `에뮬레이터 보정은 핸들 각도를 앞바퀴 각도로 줄이고 방향을 뒤집는다`() {
        // Virtual Console에서 오른쪽으로 핸들 140° → 에뮬레이터는 -140을 준다 → 오른쪽(양수) 앞바퀴 10°
        val ratio = SteeringCalibration.PLEOS_EMULATOR.steeringRatio
        val signal = PleosSignalMapper.steering(5, -140f, SteeringCalibration.PLEOS_EMULATOR)!!
        assertEquals(140f / ratio, signal.deg, 0.0001f)
    }

    @Test
    fun `조향각 값이 없거나 숫자가 아니면 신호를 만들지 않는다`() {
        assertNull(PleosSignalMapper.steering(5, null))
        assertNull(PleosSignalMapper.steering(5, Float.NaN))
    }

    // --- 방향지시등 ---

    @Test
    fun `방향지시등 값을 core 상태로 바꾼다`() {
        assertEquals(VehicleSignal.TurnSignal(7, TurnState.OFF), PleosSignalMapper.turnSignal(7, TurnSignalValue.OFF))
        assertEquals(VehicleSignal.TurnSignal(7, TurnState.LEFT), PleosSignalMapper.turnSignal(7, TurnSignalValue.LEFT))
        assertEquals(VehicleSignal.TurnSignal(7, TurnState.RIGHT), PleosSignalMapper.turnSignal(7, TurnSignalValue.RIGHT))
        assertNull(PleosSignalMapper.turnSignal(7, null))
    }

    // --- 속도: PERF_VEHICLE_SPEED는 m/s, 후진이면 음수 ---

    @Test
    fun `속도는 m per s를 km per h로 바꾼다`() {
        assertEquals(36f, PleosSignalMapper.speed(1, 10f)!!.kph, 0.001f)
    }

    @Test
    fun `후진 속도는 크기만 쓴다`() {
        assertEquals(7.2f, PleosSignalMapper.speed(1, -2f)!!.kph, 0.001f)
    }

    @Test
    fun `속도 값이 없으면 신호를 만들지 않는다`() {
        assertNull(PleosSignalMapper.speed(1, null))
        assertNull(PleosSignalMapper.speed(1, Float.NaN))
    }

    // --- 기어: GEAR_SELECTION 값 (android.car.VehicleGear) ---

    @Test
    fun `기어 선택 값을 P R N D로 바꾼다`() {
        assertEquals(GearPos.P, PleosSignalMapper.gear(3, 0x0004)!!.pos)
        assertEquals(GearPos.R, PleosSignalMapper.gear(3, 0x0002)!!.pos)
        assertEquals(GearPos.N, PleosSignalMapper.gear(3, 0x0001)!!.pos)
        assertEquals(GearPos.D, PleosSignalMapper.gear(3, 0x0008)!!.pos)
    }

    @Test
    fun `수동 변속 단수는 주행으로 본다`() {
        assertEquals(GearPos.D, PleosSignalMapper.gear(3, 0x0010)!!.pos) // 1단
        assertEquals(GearPos.D, PleosSignalMapper.gear(3, 0x0100)!!.pos) // 5단
    }

    @Test
    fun `알 수 없는 기어 값이면 신호를 만들지 않는다`() {
        assertNull(PleosSignalMapper.gear(3, 0))
        assertNull(PleosSignalMapper.gear(3, null))
    }
}
