package com.mulmicare.core.signal

/**
 * core가 받는 차량 신호. Pleos SDK 값은 vehicle/ 어댑터에서 이 규약으로 바꿔 넣는다.
 * 모든 시간은 같은 기준의 밀리초.
 */
sealed interface VehicleSignal {
    val atMs: Long

    /** 앞바퀴 조향각(°). 음수 = 왼쪽, 양수 = 오른쪽. Pleos SteeringWheelAngleListener와 같은 규약. */
    data class SteeringAngle(override val atMs: Long, val deg: Float) : VehicleSignal

    data class TurnSignal(override val atMs: Long, val state: TurnState) : VehicleSignal

    data class Speed(override val atMs: Long, val kph: Float) : VehicleSignal

    data class Brake(override val atMs: Long, val pressed: Boolean) : VehicleSignal

    data class Gear(override val atMs: Long, val pos: GearPos) : VehicleSignal

    /** 시간 경과만 알리는 신호. 타이머 판단(D2 등)을 위해 주기적으로 넣는다. */
    data class Tick(override val atMs: Long) : VehicleSignal
}

enum class TurnState { OFF, LEFT, RIGHT, HAZARD }

enum class Side { LEFT, RIGHT }

enum class GearPos { P, R, N, D }
