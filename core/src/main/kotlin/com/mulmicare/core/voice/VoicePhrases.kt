package com.mulmicare.core.voice

/** 앱이 알아듣는 짧은 응답의 의도. */
enum class VoiceIntent { YES, NO, STOP, OK, STRUGGLING }

/**
 * 음성 인식 결과를 의도로 바꾼다.
 *
 * 공백과 문장부호를 지운 뒤 목록과 **정확히 같을 때만** 인정한다.
 * "응원해"처럼 다른 낱말 속에 든 말을 잘못 알아듣지 않게 하기 위해서다.
 * 같은 말이 여러 의도에 있을 수 있으므로("좋아요": 놀이 수락 / 괜찮음), 질문에서 기대하는 의도 안에서만 찾는다.
 */
object VoicePhrases {

    private val phrases: Map<VoiceIntent, Set<String>> = mapOf(
        VoiceIntent.YES to setOf("응", "어", "네", "예", "그래", "좋아", "좋아요", "할래", "할래요", "해", "할게"),
        VoiceIntent.NO to setOf("아니", "아니요", "아니야", "싫어", "싫어요", "안할래", "안해", "됐어"),
        VoiceIntent.STOP to setOf("그만", "그만해", "그만할래", "그만하자", "멈춰", "끝", "끝내"),
        VoiceIntent.OK to setOf("괜찮아요", "괜찮아", "괜찮대", "괜찮대요", "좋아", "좋아요"),
        VoiceIntent.STRUGGLING to setOf(
            "힘들어해요", "힘들어", "힘들어요", "안좋아", "안좋아요", "속안좋아", "속안좋아요", "멀미나", "멀미해요",
        ),
    )

    /** [text]를 [expect] 안의 의도 중 하나로 읽는다. 알아듣지 못하면 null. */
    fun match(text: String, expect: Set<VoiceIntent>): VoiceIntent? {
        val normalized = text.filter { it.isLetterOrDigit() }
        if (normalized.isEmpty()) return null
        return expect.firstOrNull { normalized in phrases.getValue(it) }
    }
}
