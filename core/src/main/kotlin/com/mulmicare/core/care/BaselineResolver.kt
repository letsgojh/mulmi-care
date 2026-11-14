package com.mulmicare.core.care

import kotlin.math.abs

/** 앱이 저장해 둔 공조 기준값과, 앱이 마지막으로 맞춘 설정 온도. */
data class SavedClimate(val baseline: ClimateBaseline, val lastAppliedTempC: Float)

/**
 * 케어를 켤 때 쓸 기준값을 고른다.
 *
 * 지난 케어가 공조를 되돌리지 못하고 끝났다면(앱 강제 종료 등) 지금 읽은 온도는 앱이 낮춰 둔 값이다.
 * 그걸 기준으로 삼으면 이동할 때마다 온도가 계속 내려간다. 그래서 지금 온도가 앱이 마지막으로 맞춘 값과
 * 같으면 저장해 둔 기준값을 쓰고, 다르면 운전자가 직접 바꾼 것으로 보고 지금 읽은 값을 쓴다.
 */
object BaselineResolver {
    fun resolve(read: ClimateBaseline, saved: SavedClimate?): ClimateBaseline {
        if (saved == null) return read
        return if (abs(read.setTempC - saved.lastAppliedTempC) <= SAME_TEMP_C) saved.baseline else read
    }

    private const val SAME_TEMP_C = 0.1f
}
