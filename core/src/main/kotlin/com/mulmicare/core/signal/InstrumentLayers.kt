package com.mulmicare.core.signal

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect

/**
 * 악기 레이어 (spec F4.4, 1차는 단순한 형태).
 *
 * 최근 [Thresholds.layerWindowMs] 동안 낸 좌우 소리 수로 레이어를 정한다.
 * 소리가 없으면 1(잔잔한 비트), 하나 날 때마다 한 층씩 올라 [Thresholds.maxLayers]에서 멈춘다.
 * 레이어가 바뀔 때만 [Effect.SetLayer]를 낸다. 놀이 중이 아니면 아무것도 내지 않는다.
 */
class InstrumentLayers(private val t: Thresholds = Thresholds()) {

    private var playing = false
    private var layer = 1
    private val cues = ArrayDeque<Long>()

    /** 놀이 시작. 앱은 비트를 레이어 1로 시작한다. */
    fun startPlay() {
        playing = true
        layer = 1
        cues.clear()
    }

    fun stopPlay() {
        playing = false
    }

    fun onCue(atMs: Long) {
        if (playing) cues.addLast(atMs)
    }

    fun onTick(atMs: Long): Effect.SetLayer? {
        if (!playing) return null
        while (cues.isNotEmpty() && cues.first() <= atMs - t.layerWindowMs) cues.removeFirst()
        val next = 1 + minOf(cues.size, t.maxLayers - 1)
        if (next == layer) return null
        layer = next
        return Effect.SetLayer(next)
    }
}
