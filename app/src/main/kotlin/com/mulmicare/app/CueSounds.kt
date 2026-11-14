package com.mulmicare.app

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.mulmicare.core.signal.Side
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 앱이 직접 만든 소리(F4.1). 에셋은 scripts/audio/make_sounds.py로 합성한다(assets/sounds/SOURCES.md).
 * - 비트: 테마별 120 BPM 4마디 루프(드럼·베이스·코드·멜로디)
 * - 좌우 소리: 왼쪽은 낮은 나무 소리(내려가는 음), 오른쪽은 높은 종소리(올라가는 음).
 *   스테레오가 아닌 스피커에서도 음색과 높이로 구분된다.
 * 모두 미리 올려 두고(MODE_STATIC) 재생 때 처음으로 되감아 지연을 줄인다.
 */
class CueSounds(private val assets: AssetManager) {
    private val left = track(load("cue_left.wav"))
    private val right = track(load("cue_right.wav"))
    private val starLost = track(load("star_lost.wav"))
    private val chime = track(load("chime.wav"))
    private var beat = track(load(BEAT_FILE.getValue("바다")), loop = true)

    /** F1.3 음악 테마: 테마마다 다른 비트(악기·조성)를 쓴다. */
    fun setTheme(theme: String) {
        val file = BEAT_FILE[theme] ?: return
        val old = beat
        beat = track(load(file), loop = true)
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

    /** 16비트 스테레오 44.1kHz WAV에서 PCM만 읽는다. */
    private fun load(name: String): ShortArray {
        val bytes = assets.open("sounds/$name").use { it.readBytes() }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = buf.getInt(pos + 4)
            if (id == "data") {
                val out = ShortArray(size / 2)
                buf.position(pos + 8)
                buf.asShortBuffer().get(out)
                return out
            }
            pos += 8 + size + (size and 1)
        }
        error("WAV data 없음: $name")
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
        val BEAT_FILE = mapOf("바다" to "beat_sea.wav", "숲" to "beat_forest.wav", "우주" to "beat_space.wav")
    }
}
