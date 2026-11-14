package com.mulmicare.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.mulmicare.core.signal.Side
import kotlin.math.PI
import kotlin.math.sin

/**
 * 앱이 직접 만든 임시 소리(F4.1). 정식 사운드는 rhythm-audio 단계에서 바꾼다.
 * 모두 미리 만들어 두고(MODE_STATIC) 재생 때 처음으로 되감아 지연을 줄인다.
 */
class CueSounds {
    private val left = track(tone(listOf(880f to 140), pan = -1f))
    private val right = track(tone(listOf(880f to 140), pan = 1f))
    private val starLost = track(tone(listOf(660f to 90, 440f to 160), pan = 0f))
    private val chime = track(tone(listOf(523f to 120, 784f to 200), pan = 0f))
    private var beat = track(beatBar(BASE_FREQ.getValue("바다")), loop = true)

    /** F1.3 음악 테마: 테마마다 비트의 음 높이를 바꾼다(1차는 단순한 형태). */
    fun setTheme(theme: String) {
        val freq = BASE_FREQ[theme] ?: return
        val old = beat
        beat = track(beatBar(freq), loop = true)
        old.release()
    }

    fun side(side: Side) = replay(if (side == Side.LEFT) left else right)

    fun starLost() = replay(starLost)

    fun chime() = replay(chime)

    fun startBeat() = replay(beat)

    fun stopBeat() {
        if (beat.playState == AudioTrack.PLAYSTATE_PLAYING) beat.stop()
    }

    fun release() {
        listOf(left, right, starLost, chime, beat).forEach { it.release() }
    }

    private fun replay(track: AudioTrack) {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
        track.reloadStaticData()
        track.play()
    }

    /** (주파수, 길이 ms) 음들을 이어 붙인 스테레오 PCM. [pan] -1 = 왼쪽만, 1 = 오른쪽만. */
    private fun tone(notes: List<Pair<Float, Int>>, pan: Float, volume: Float = 0.5f): ShortArray {
        val frames = notes.sumOf { it.second * RATE / 1000 }
        val out = ShortArray(frames * 2)
        val leftGain = if (pan > 0) 1f - pan else 1f
        val rightGain = if (pan < 0) 1f + pan else 1f
        var i = 0
        for ((freq, ms) in notes) {
            val n = ms * RATE / 1000
            for (k in 0 until n) {
                val fade = minOf(1f, k / 200f, (n - k) / 200f) // 클릭 잡음 방지
                val s = sin(2 * PI * freq * k / RATE).toFloat() * volume * fade * Short.MAX_VALUE
                out[i++] = (s * leftGain).toInt().toShort()
                out[i++] = (s * rightGain).toInt().toShort()
            }
        }
        return out
    }

    /** 120 BPM 한 마디(2초): 첫 박은 낮고 강하게, 나머지는 높고 약하게. */
    private fun beatBar(base: Float): ShortArray {
        val beatFrames = RATE / 2
        val bar = ShortArray(beatFrames * 4 * 2)
        repeat(4) { b ->
            val click = tone(listOf((if (b == 0) base else base * 2) to 60), pan = 0f, volume = if (b == 0) 0.35f else 0.2f)
            click.copyInto(bar, destinationOffset = b * beatFrames * 2)
        }
        return bar
    }

    private fun track(pcm: ShortArray, loop: Boolean = false): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        if (loop) track.setLoopPoints(0, pcm.size / 2, -1)
        return track
    }

    private companion object {
        const val RATE = 44_100
        val BASE_FREQ = mapOf("바다" to 220f, "숲" to 262f, "우주" to 196f)
    }
}
