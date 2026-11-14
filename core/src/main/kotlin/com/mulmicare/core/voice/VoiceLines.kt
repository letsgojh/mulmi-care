package com.mulmicare.core.voice

import kotlin.math.max
import kotlin.math.roundToInt

/** 앱이 하는 말. 운전자의 주행을 평가하는 말은 넣지 않는다(S5). */
object VoiceLines {
    const val PROPOSE_PLAY = "리듬 놀이 할까요?"
    const val CHECK_BACKSEAT = "뒷자리는 괜찮아요?"

    private val nativeCount = mapOf(1 to "한", 2 to "두", 3 to "세")

    /** F5.7 */
    fun roundFinished(stars: Int): String =
        "종이배 도착! 별 ${nativeCount[stars] ?: stars.toString()} 개를 지켰어요"

    /** F7.2 거리는 km 단위 정수로 반올림하고, 1km 미만도 1km로 말한다. */
    fun suggestRest(distanceKm: Double): String =
        "${max(1, distanceKm.roundToInt())}km 앞에 쉼터가 있어요. 들를까요?"
}
