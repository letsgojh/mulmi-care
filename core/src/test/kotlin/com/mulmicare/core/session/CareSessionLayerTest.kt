package com.mulmicare.core.session

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect
import com.mulmicare.core.signal.GearPos
import com.mulmicare.core.testing.Say
import com.mulmicare.core.testing.Timed
import com.mulmicare.core.testing.drive
import com.mulmicare.core.testing.trace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** spec F4.4 길이 곧으면 비트가 잔잔해지고, 회전이 이어지면 악기가 하나씩 더해진다. */
class CareSessionLayerTest {

    private val t = Thresholds()
    private val turn = t.steerEventDeg * 2

    /** 굽은 길에 들어서 "응"(21초)으로 놀이를 시작하고, 30초부터 [turns]번 좌우로 돈 뒤 곧은 길을 달린다. */
    private fun play(answer: String, turns: Int): List<Timed> {
        val signals = trace {
            at(0.seconds) { gear(GearPos.D); speed(60f) }
            listOf(-turn, turn, -turn).forEachIndexed { i, deg ->
                at((10 + i * 5).seconds) { steer(deg) }
                at((12 + i * 5).seconds) { steer(0f) }
            }
            repeat(turns) { i ->
                at((30 + i * 3).seconds) { steer(if (i % 2 == 0) -turn else turn) }
                at((31 + i * 3).seconds) { steer(0f) }
            }
            ticks(0.seconds, 180.seconds, every = 1.seconds)
        }
        return CareSession(t).drive(signals, says = listOf(Say(21_000, answer)))
    }

    private fun List<Timed>.layers() = filter { it.effect is Effect.SetLayer }.map { (it.effect as Effect.SetLayer).layer }

    private fun List<Timed>.cueTimes() = filter { it.effect is Effect.PlaySideCue }.map { it.atMs }

    @Test
    fun `F4_4 좌우 소리가 이어질수록 악기가 하나씩 더해지고 최대 레이어에서 멈춘다`() {
        val out = play("응", turns = t.maxLayers) // 소리 4번: 2, 3, 4층, 그다음은 그대로
        val rising = out.filter { it.atMs <= out.cueTimes().last() }.layers()
        assertEquals((2..t.maxLayers).toList(), rising)
    }

    @Test
    fun `F4_4 길이 곧아 좌우 소리가 없으면 다시 잔잔한 비트로 돌아간다`() {
        val out = play("응", turns = 2)
        val lastCue = out.cueTimes().last()
        val stop = out.first { it.effect == Effect.StopBeat }.atMs
        val calm = out.last { it.effect == Effect.SetLayer(1) }.atMs
        assertTrue(calm >= lastCue + t.layerWindowMs, "마지막 소리 뒤 ${t.layerWindowMs}ms가 지나야 잔잔해진다: $calm")
        assertTrue(calm < stop, "구간이 끝나기 전에 잔잔해진다")
    }

    @Test
    fun `F4_4 레이어가 바뀔 때만 SetLayer를 낸다`() {
        val layers = play("응", turns = t.maxLayers + 2).layers()
        assertTrue(layers.isNotEmpty())
        assertTrue(layers.zipWithNext().none { (a, b) -> a == b }, "같은 레이어를 거듭 내지 않는다: $layers")
    }

    @Test
    fun `F4_4 놀이를 하지 않으면 레이어를 바꾸지 않는다`() {
        assertEquals(emptyList(), play("아니", turns = t.maxLayers).layers())
    }
}
