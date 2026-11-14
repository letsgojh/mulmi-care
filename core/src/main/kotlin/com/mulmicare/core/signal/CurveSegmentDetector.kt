package com.mulmicare.core.signal

import com.mulmicare.core.config.Thresholds

sealed interface SegmentEvent {
    val atMs: Long

    data class Started(override val atMs: Long) : SegmentEvent

    data class Ended(override val atMs: Long) : SegmentEvent
}

/**
 * 굽은 길 구간 판단 (spec D1, D2).
 *
 * 조향 이벤트: 핸들 각도가 기준 이상으로 한쪽(왼쪽/오른쪽)에 새로 들어선 순간.
 * 감속 이벤트: 감속률이 기준 이상으로 커지기 시작한 순간.
 * 최근 [Thresholds.curveWindowMs] 안의 이벤트 수가 [Thresholds.curveEventsToStart] 이상이면 구간 시작.
 */
class CurveSegmentDetector(private val t: Thresholds = Thresholds()) {

    private enum class SteerZone { CENTER, LEFT, RIGHT }

    private var steerZone = SteerZone.CENTER
    private val decelOnset = OnsetDetector(t.minSpeedDtMs) { kphPerSec ->
        kphPerSec <= -t.decelEventKphPerSec
    }
    private val eventTimes = ArrayDeque<Long>()
    private var inSegment = false

    /** 구간 안에서 이벤트 수가 기준 아래로 내려간 시각. 기준 이상이면 null. */
    private var belowThresholdSinceMs: Long? = null

    fun onSignal(signal: VehicleSignal): SegmentEvent? {
        val now = signal.atMs
        if (isCurveEvent(signal)) eventTimes.addLast(now)
        val droppedBelowAt = expireEventsOutsideWindow(now)
        val atOrAboveThreshold = eventTimes.size >= t.curveEventsToStart

        if (!inSegment) {
            if (!atOrAboveThreshold) return null
            inSegment = true
            belowThresholdSinceMs = null
            return SegmentEvent.Started(now)
        }

        if (atOrAboveThreshold) {
            belowThresholdSinceMs = null
            return null
        }
        val since = belowThresholdSinceMs ?: droppedBelowAt ?: now
        belowThresholdSinceMs = since
        if (now - since < t.curveQuietToEndMs) return null

        inSegment = false
        belowThresholdSinceMs = null
        return SegmentEvent.Ended(now)
    }

    private fun isCurveEvent(signal: VehicleSignal): Boolean = when (signal) {
        is VehicleSignal.SteeringAngle -> steeringEnteredZone(signal.deg)
        is VehicleSignal.Speed -> decelOnset.onSample(signal.atMs, signal.kph)
        else -> false
    }

    private fun steeringEnteredZone(deg: Float): Boolean {
        val zone = when {
            deg <= -t.steerEventDeg -> SteerZone.LEFT
            deg >= t.steerEventDeg -> SteerZone.RIGHT
            else -> SteerZone.CENTER
        }
        val entered = zone != SteerZone.CENTER && zone != steerZone
        steerZone = zone
        return entered
    }

    /**
     * 창을 벗어난 이벤트를 지운다.
     * 이 과정에서 횟수가 기준 아래로 내려갔다면 그 정확한 시각(빠진 이벤트 + 창 길이)을 돌려준다.
     * 신호가 띄엄띄엄 와도 종료 시각이 신호 간격에 끌려가지 않게 하기 위해서다.
     */
    private fun expireEventsOutsideWindow(now: Long): Long? {
        var droppedBelowAt: Long? = null
        while (eventTimes.isNotEmpty() && now - eventTimes.first() >= t.curveWindowMs) {
            val wasAtOrAbove = eventTimes.size >= t.curveEventsToStart
            val expired = eventTimes.removeFirst()
            if (wasAtOrAbove && eventTimes.size < t.curveEventsToStart) {
                droppedBelowAt = expired + t.curveWindowMs
            }
        }
        return droppedBelowAt
    }
}
