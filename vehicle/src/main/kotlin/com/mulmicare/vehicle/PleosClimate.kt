package com.mulmicare.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.annotation.SuppressLint
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.os.Handler
import android.os.Looper
import com.mulmicare.core.care.ClimateBaseline
import com.mulmicare.core.care.ClimateTarget
import java.util.concurrent.atomic.AtomicInteger

/**
 * 공조 읽기·제어 (spec F6). 판단은 core(CabinCarePolicy)가 하고, 여기서는 목표값을 차량에 적용만 한다.
 *
 * - 지원 구역은 Capability로 얻고, 목표값은 지원되는 모든 구역에 같게 적용한다.
 * - 적용 뒤 [VERIFY_DELAY_MS]에 다시 읽어 반영됐는지 확인하고, 다르면 한 번 더 요청한다(F6.8).
 * - 공조 전원이 꺼져 있으면 켠 뒤 적용한다(꺼져 있으면 설정이 거부됨).
 * - 창문 등 다른 제어는 하지 않는다(F6.9, S4).
 *
 * 권한: pleos.car.permission.CAR_CLIMATE(조회), pleos.car.permission.CONTROL_CAR_CLIMATE(제어),
 * android.car.permission.CAR_EXTERIOR_ENVIRONMENT(바깥 기온).
 */
@SuppressLint("MissingPermission")
class PleosClimate internal constructor(
    vehicle: Vehicle,
    private val propertyManager: () -> CarPropertyManager?,
    private val onIssue: (String) -> Unit,
) {
    private val hvac = vehicle.getHVAC()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var tempAreas: List<SeatArea> = emptyList()
    @Volatile private var fanAreas: Map<SeatArea, AreaBounds<Int>> = emptyMap()
    @Volatile private var directionAreas: List<SeatArea> = emptyList()
    @Volatile private var recirculationAreas: List<SeatArea> = emptyList()
    @Volatile private var powerAreas: List<SeatArea> = emptyList()

    /** 기준값과 바깥 기온(모르면 null)을 읽는다. 설정 온도를 못 읽으면 baseline이 null. */
    fun readBaseline(onResult: (baseline: ClimateBaseline?, outsideC: Float?) -> Unit) {
        var temp: Float? = null
        var fan: Int? = null
        var fanMax: Int? = null
        var direction: FanDirectionType? = null
        val pending = AtomicInteger(5)
        val done = {
            if (pending.decrementAndGet() == 0) {
                val baseline = temp?.let {
                    ClimateBaseline(it, fan, fanMax, PleosClimateMapper.fromSdk(direction))
                }
                onResult(baseline, readOutsideC())
            }
        }

        hvac.checkHvacTemperatureCapabilities({ _, bounds, _ ->
            tempAreas = bounds.keys.toList()
            val area = tempAreas.firstOrNull() ?: return@checkHvacTemperatureCapabilities done()
            hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, { temp = it; done() }, { failed("온도 읽기", it); done() })
        }, { failed("온도 Capability", it); done() })

        hvac.checkHvacFanSpeedCapabilities({ bounds ->
            fanAreas = bounds
            val (area, range) = bounds.entries.firstOrNull()?.toPair() ?: return@checkHvacFanSpeedCapabilities done()
            fanMax = range.maxValue
            hvac.getHvacFanSpeed(area, { fan = it; done() }, { failed("풍량 읽기", it); done() })
        }, { failed("풍량 Capability", it); done() })

        hvac.checkHvacFanDirectionCapabilities({ _, areas ->
            directionAreas = areas.keys.toList()
            val area = directionAreas.firstOrNull() ?: return@checkHvacFanDirectionCapabilities done()
            hvac.getHvacFanDirection(area, { direction = it; done() }, { failed("풍향 읽기", it); done() })
        }, { failed("풍향 Capability", it); done() })

        hvac.checkHvacRecirculationCapabilities({ areas ->
            recirculationAreas = areas
            done()
        }, { failed("내외기 Capability", it); done() })

        hvac.checkHvacPowerCapabilities({ areas ->
            powerAreas = areas
            done()
        }, { failed("공조 전원 Capability", it); done() })
    }

    /** 목표값을 적용하고 반영을 확인한다. [onVerified]에는 끝내 반영되지 않은 항목 이름이 온다. */
    fun apply(target: ClimateTarget, onVerified: (mismatches: List<String>) -> Unit) = apply(target, attempt = 1, onVerified)

    private fun apply(target: ClimateTarget, attempt: Int, onVerified: (List<String>) -> Unit) {
        // 에뮬레이터에서 확인: 공조 전원이 꺼져 있으면 온도·풍량·풍향 설정이 거부된다(PropertyAsyncError code 2).
        // 실내 케어는 바람이 나와야 하므로 꺼져 있으면 먼저 켜고 잠시 뒤 값을 넣는다.
        val area = powerAreas.firstOrNull()
        if (area == null) {
            applyValues(target, attempt, onVerified)
            return
        }
        hvac.getHvacPowerState(area, { on ->
            if (on == true) {
                applyValues(target, attempt, onVerified)
            } else {
                powerAreas.forEach { hvac.setHvacPower(it, true, onFailed = { e -> failed("공조 전원 켜기", e) }) }
                main.postDelayed({ applyValues(target, attempt, onVerified) }, POWER_ON_DELAY_MS)
            }
        }, { failed("공조 전원 읽기", it); applyValues(target, attempt, onVerified) })
    }

    private fun applyValues(target: ClimateTarget, attempt: Int, onVerified: (List<String>) -> Unit) {
        tempAreas.forEach { area ->
            hvac.setHvacTemperature(area, target.setTempC, TemperatureUnit.CELSIUS, onFailed = { failed("온도 설정", it) })
        }
        target.fanSpeed?.let { fan ->
            fanAreas.forEach { (area, range) ->
                hvac.setHvacFanSpeed(area, fan.coerceIn(range.minValue, range.maxValue), onFailed = { failed("풍량 설정", it) })
            }
        }
        target.fanDirection?.let { direction ->
            directionAreas.forEach { area ->
                hvac.setHvacFanDirection(area, PleosClimateMapper.toSdk(direction), onFailed = { failed("풍향 설정", it) })
            }
        }
        recirculationAreas.forEach { area ->
            hvac.setHvacRecirculation(area, !target.outsideAir, onFailed = { failed("내외기 설정", it) })
        }

        main.postDelayed({
            readBack { read ->
                val mismatches = PleosClimateMapper.mismatches(target, read)
                if (mismatches.isNotEmpty() && attempt == 1) apply(target, attempt = 2, onVerified) else onVerified(mismatches)
            }
        }, VERIFY_DELAY_MS)
    }

    private fun readBack(onResult: (ClimateReadback) -> Unit) {
        var temp: Float? = null
        var fan: Int? = null
        var direction: FanDirectionType? = null
        var recirculation: Boolean? = null
        val pending = AtomicInteger(4)
        val done = { if (pending.decrementAndGet() == 0) onResult(ClimateReadback(temp, fan, direction, recirculation)) }

        tempAreas.firstOrNull()?.let { area ->
            hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, { temp = it; done() }, { done() })
        } ?: done()
        fanAreas.keys.firstOrNull()?.let { area -> hvac.getHvacFanSpeed(area, { fan = it; done() }, { done() }) } ?: done()
        directionAreas.firstOrNull()?.let { area -> hvac.getHvacFanDirection(area, { direction = it; done() }, { done() }) } ?: done()
        recirculationAreas.firstOrNull()?.let { area ->
            hvac.getHvacRecirculationState(area, { recirculation = it; done() }, { done() })
        } ?: done()
    }

    private fun readOutsideC(): Float? = try {
        propertyManager()?.getFloatProperty(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, 0)
    } catch (e: Exception) {
        onIssue("바깥 기온 읽기 실패: ${e.javaClass.simpleName}")
        null
    }

    private fun failed(what: String, e: Exception) = onIssue("$what 실패: ${e.javaClass.simpleName}: ${e.message}")

    private companion object {
        const val VERIFY_DELAY_MS = 1_500L
        const val POWER_ON_DELAY_MS = 500L
    }
}
