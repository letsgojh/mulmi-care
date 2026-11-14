package com.mulmicare.core.session

/** 한 판(굽은 길 구간 하나)의 기록. [starsLost]는 흔들린 정도(F9.1 물결 기록). */
data class RoundRecord(val stars: Int, val startedAtMs: Long, val endedAtMs: Long) {
    val starsLost: Int get() = 3 - stars
}

/** F9.1 도착 후 리포트. */
data class TripReport(val rounds: List<RoundRecord>, val struggles: Int) {
    val starsKept: Int get() = rounds.sumOf { it.stars }
}

/** F9.2 도착 후 뒷자리가 어땠는지 한 번 터치로 남긴 평가. */
enum class BackseatRating { FINE, STRUGGLED }

/** 화면. 정차 중에만 띄운다(S1, F8.1). */
sealed interface Screen {
    /** F8.2 항해 지도: 이번 이동에서 마친 판들, 지금까지 모은 별, 열린 꾸미기. */
    data class VoyageMap(val rounds: List<RoundRecord>, val totalStars: Int, val unlocked: List<String>) : Screen

    /** F9.1 도착 후 리포트. */
    data class Report(val report: TripReport, val totalStars: Int, val unlocked: List<String>) : Screen
}

/** F5.8 가족이 지킨 별이 쌓이면 종이배 꾸미기가 열린다. */
object Rewards {
    const val STARS_PER_REWARD = 5
    val ITEMS = listOf("빨간 돛", "파란 깃발", "별 깃발", "무지개 돛", "등대 깃발", "고래 돛")

    fun unlocked(totalStars: Int): List<String> = ITEMS.take((totalStars / STARS_PER_REWARD).coerceAtMost(ITEMS.size))
}

/** F9.3 지난 이동 기록을 다음 이동의 실내 케어와 놀이 제안 빈도에 반영한다. */
object NextTripPolicy {
    /** 처음부터 강화 단계로 시작할지. */
    fun startBoost(lastReport: TripReport?, rating: BackseatRating?, struggleCountForBoost: Int = 2): Boolean =
        rating == BackseatRating.STRUGGLED || (lastReport?.struggles ?: 0) >= struggleCountForBoost

    /** 거절·무응답 뒤 다시 묻지 않는 시간. */
    fun proposalCooldownMs(t: com.mulmicare.core.config.Thresholds, startBoost: Boolean): Long =
        if (startBoost) t.proposalCooldownAfterStruggleMs else t.proposalCooldownMs
}
