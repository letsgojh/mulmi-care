package com.mulmicare.app

import android.content.Context
import com.mulmicare.core.care.ClimateBaseline
import com.mulmicare.core.care.FanDirection
import com.mulmicare.core.care.SavedClimate

/**
 * 공조 기준값과 앱이 마지막으로 맞춘 온도를 기기 안에만 저장한다(S8: 외부로 보내지 않음).
 * 다음 케어 때 BaselineResolver가 "되돌리지 못하고 끝난 경우"를 알아보는 데 쓴다.
 */
class ClimateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("climate", Context.MODE_PRIVATE)

    fun load(): SavedClimate? {
        if (!prefs.contains(KEY_TEMP)) return null
        val baseline = ClimateBaseline(
            setTempC = prefs.getFloat(KEY_TEMP, 0f),
            fanSpeed = prefs.getInt(KEY_FAN, -1).takeIf { it >= 0 },
            fanSpeedMax = prefs.getInt(KEY_FAN_MAX, -1).takeIf { it >= 0 },
            fanDirection = prefs.getString(KEY_DIRECTION, null)?.let { runCatching { FanDirection.valueOf(it) }.getOrNull() },
            recirculation = if (prefs.contains(KEY_RECIRC)) prefs.getBoolean(KEY_RECIRC, false) else null,
        )
        return SavedClimate(baseline, prefs.getFloat(KEY_LAST_APPLIED, baseline.setTempC))
    }

    fun save(baseline: ClimateBaseline, lastAppliedTempC: Float) {
        prefs.edit().apply {
            putFloat(KEY_TEMP, baseline.setTempC)
            putInt(KEY_FAN, baseline.fanSpeed ?: -1)
            putInt(KEY_FAN_MAX, baseline.fanSpeedMax ?: -1)
            if (baseline.fanDirection != null) putString(KEY_DIRECTION, baseline.fanDirection!!.name) else remove(KEY_DIRECTION)
            if (baseline.recirculation != null) putBoolean(KEY_RECIRC, baseline.recirculation!!) else remove(KEY_RECIRC)
            putFloat(KEY_LAST_APPLIED, lastAppliedTempC)
        }.apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_TEMP = "baseline_temp"
        const val KEY_FAN = "baseline_fan"
        const val KEY_FAN_MAX = "baseline_fan_max"
        const val KEY_DIRECTION = "baseline_direction"
        const val KEY_RECIRC = "baseline_recirc"
        const val KEY_LAST_APPLIED = "last_applied_temp"
    }
}
