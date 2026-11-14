package com.mulmicare.core.signal

/**
 * 샘플 사이의 초당 변화량이 조건을 만족하기 시작하는 순간(상승 에지)을 찾는다.
 *
 * 간격이 [minDtMs]보다 짧은 샘플은 계산에 쓰지 않고, 이전 샘플을 유지한 채
 * 다음 샘플에서 더 긴 간격으로 계산한다(센서 노이즈 방지).
 *
 * @param isActive 초당 변화량(현재 − 이전, 단위/s)을 받아 "기준을 넘었는가"를 돌려준다.
 */
internal class OnsetDetector(
    private val minDtMs: Long,
    private val isActive: (perSec: Float) -> Boolean,
) {
    private var prevAtMs: Long? = null
    private var prevValue = 0f
    private var active = false

    /** 조건이 새로 만족되기 시작한 샘플이면 true. */
    fun onSample(atMs: Long, value: Float): Boolean {
        val prevAt = prevAtMs
        if (prevAt == null) {
            prevAtMs = atMs
            prevValue = value
            return false
        }
        val dtMs = atMs - prevAt
        if (dtMs < minDtMs) return false

        val perSec = (value - prevValue) * 1000f / dtMs
        prevAtMs = atMs
        prevValue = value

        val nowActive = isActive(perSec)
        val started = nowActive && !active
        active = nowActive
        return started
    }
}
