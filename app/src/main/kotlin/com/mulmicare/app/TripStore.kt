package com.mulmicare.app

import android.content.Context
import com.mulmicare.core.session.BackseatRating
import com.mulmicare.core.session.TripReport

/**
 * 이동 기록을 기기 안에만 저장한다(S8). 아이 이름·나이 같은 개인정보는 받지 않는다(S7).
 * 지난 이동의 "힘들어해요" 횟수와 평가는 다음 이동의 실내 케어에 쓴다(F9.3).
 */
class TripStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("trips", Context.MODE_PRIVATE)

    /** F5.8 지금까지 모은 별. */
    var totalStars: Int
        get() = prefs.getInt(KEY_TOTAL_STARS, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_STARS, value).apply()

    /** F2.1 연습을 이미 봤는지. */
    var practiceDone: Boolean
        get() = prefs.getBoolean(KEY_PRACTICE_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_PRACTICE_DONE, value).apply()

    /** F8.3 고른 꾸미기. */
    var decoration: String?
        get() = prefs.getString(KEY_DECORATION, null)
        set(value) = prefs.edit().putString(KEY_DECORATION, value).apply()

    val lastStruggles: Int? get() = if (prefs.contains(KEY_LAST_STRUGGLES)) prefs.getInt(KEY_LAST_STRUGGLES, 0) else null
    val lastRating: BackseatRating?
        get() = prefs.getString(KEY_LAST_RATING, null)?.let { runCatching { BackseatRating.valueOf(it) }.getOrNull() }

    fun saveTrip(report: TripReport) {
        prefs.edit()
            .putInt(KEY_TOTAL_STARS, totalStars + report.starsKept)
            .putInt(KEY_LAST_STRUGGLES, report.struggles)
            .remove(KEY_LAST_RATING)
            .apply()
    }

    fun saveRating(rating: BackseatRating) = prefs.edit().putString(KEY_LAST_RATING, rating.name).apply()

    /** F9 사용자 안내: 기록 지우기. */
    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_TOTAL_STARS = "total_stars"
        const val KEY_PRACTICE_DONE = "practice_done"
        const val KEY_DECORATION = "decoration"
        const val KEY_LAST_STRUGGLES = "last_struggles"
        const val KEY_LAST_RATING = "last_rating"
    }
}
