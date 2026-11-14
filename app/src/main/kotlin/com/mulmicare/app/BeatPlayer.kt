package com.mulmicare.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.max
import kotlin.math.min

/**
 * F4.4 레이어 비트. 테마의 레이어 4개(모노, 같은 길이의 루프)를 직접 섞어 스트리밍으로 재생한다.
 * [setLayer]는 다음 마디 경계에서 반영하고, 소리가 튀지 않게 짧게 페이드한다.
 */
class BeatPlayer(private var stems: List<ShortArray>) {

    @Volatile private var running = false
    @Volatile private var pendingLayer = 1
    private var thread: Thread? = null

    /** 테마를 바꾼다. 재생 중이면 다음 시작부터 반영된다. */
    fun setStems(newStems: List<ShortArray>) {
        stems = newStems
    }

    fun start() {
        if (running) return
        running = true
        pendingLayer = 1
        val mix = stems
        thread = Thread({ loop(mix) }, "mulmi-beat").apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(300)
        thread = null
    }

    /** 1 = 잔잔한 비트 … stems.size = 모든 악기. */
    fun setLayer(layer: Int) {
        pendingLayer = layer.coerceIn(1, stems.size)
    }

    private fun loop(mix: List<ShortArray>) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT), BLOCK * 4))
            .build()
        val length = mix.minOf { it.size }
        val gains = FloatArray(mix.size) { if (it == 0) 1f else 0f }
        val targets = gains.copyOf()
        val out = ShortArray(BLOCK * 2)
        var pos = 0
        track.play()
        try {
            while (running) {
                for (f in 0 until BLOCK) {
                    if (pos % BAR_FRAMES == 0) {
                        val layer = pendingLayer
                        for (i in targets.indices) targets[i] = if (i < layer) 1f else 0f
                    }
                    var sample = 0f
                    for (i in mix.indices) {
                        val g = gains[i]
                        val tg = targets[i]
                        gains[i] = if (g < tg) min(tg, g + FADE_STEP) else max(tg, g - FADE_STEP)
                        if (gains[i] > 0f) sample += mix[i][pos] * gains[i]
                    }
                    val s = sample.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
                    out[f * 2] = s
                    out[f * 2 + 1] = s
                    pos = (pos + 1) % length
                }
                track.write(out, 0, out.size)
            }
        } finally {
            track.pause()
            track.flush()
            track.release()
        }
    }

    private companion object {
        const val RATE = 44_100
        const val BLOCK = 2_048

        /** 120 BPM 4/4 한 마디 = 2초. 소리 에셋(scripts/audio/make_sounds.py)과 같아야 한다. */
        const val BAR_FRAMES = RATE * 2

        /** 약 50ms 동안 켜고 끈다. */
        const val FADE_STEP = 1f / (RATE * 0.05f)
    }
}
