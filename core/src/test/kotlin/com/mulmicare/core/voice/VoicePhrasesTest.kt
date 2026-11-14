package com.mulmicare.core.voice

import com.mulmicare.core.voice.VoiceIntent.NO
import com.mulmicare.core.voice.VoiceIntent.OK
import com.mulmicare.core.voice.VoiceIntent.STOP
import com.mulmicare.core.voice.VoiceIntent.STRUGGLING
import com.mulmicare.core.voice.VoiceIntent.YES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoicePhrasesTest {

    private val yesNo = setOf(YES, NO)
    private val check = setOf(OK, STRUGGLING)

    @Test
    fun `F3_3 응과 아니의 여러 표현을 알아듣는다`() {
        listOf("응", "네", "그래", "좋아", "할래").forEach { assertEquals(YES, VoicePhrases.match(it, yesNo), it) }
        listOf("아니", "아니요", "싫어", "안 할래").forEach { assertEquals(NO, VoicePhrases.match(it, yesNo), it) }
    }

    @Test
    fun `F3_5 그만의 여러 표현을 알아듣는다`() {
        listOf("그만", "그만해", "멈춰", "끝").forEach { assertEquals(STOP, VoicePhrases.match(it, setOf(STOP)), it) }
    }

    @Test
    fun `F7_2 괜찮다와 힘들다의 여러 표현을 알아듣는다`() {
        listOf("괜찮아요", "괜찮아").forEach { assertEquals(OK, VoicePhrases.match(it, check), it) }
        listOf("힘들어해요", "힘들어", "안 좋아", "속 안 좋아").forEach {
            assertEquals(STRUGGLING, VoicePhrases.match(it, check), it)
        }
    }

    @Test
    fun `앞뒤 공백과 문장부호는 무시한다`() {
        assertEquals(YES, VoicePhrases.match("  응! ", yesNo))
        assertEquals(STOP, VoicePhrases.match("그만.", setOf(STOP)))
    }

    @Test
    fun `같은 말이라도 그 질문에서 기대하는 의도 안에서만 읽는다`() {
        assertEquals(YES, VoicePhrases.match("좋아요", yesNo))
        assertEquals(OK, VoicePhrases.match("좋아요", check))
        assertNull(VoicePhrases.match("그만", yesNo))
    }

    @Test
    fun `목록에 없는 말이나 다른 낱말 속의 응은 알아듣지 못한 것으로 본다`() {
        assertNull(VoicePhrases.match("몰라", yesNo))
        assertNull(VoicePhrases.match("응원해", yesNo))
        assertNull(VoicePhrases.match("", yesNo))
    }
}
