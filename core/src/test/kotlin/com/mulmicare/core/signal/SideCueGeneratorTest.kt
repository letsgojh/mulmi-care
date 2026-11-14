package com.mulmicare.core.signal

import com.mulmicare.core.config.Thresholds
import com.mulmicare.core.effect.Effect.PlaySideCue
import com.mulmicare.core.signal.Side.LEFT
import com.mulmicare.core.signal.Side.RIGHT
import com.mulmicare.core.testing.trace
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * spec F4.2 조향 시작 순간의 좌우 소리, F4.3 방향지시등 점등 시 예고 소리, F2.5 처음 몇 번은 말과 함께.
 * 말로 알리는 횟수의 영향을 받지 않도록 대부분의 테스트는 [quiet] 설정(voiceCueCount = 0)을 쓴다.
 */
class SideCueGeneratorTest {

    private val t = Thresholds()
    private val quiet = t.copy(voiceCueCount = 0)
    private val curveDeg = t.steerCueDeg * 2

    /** 놀이 중인 생성기에 신호를 넣고 (시각, 소리) 목록을 돌려준다. */
    private fun play(signals: List<VehicleSignal>, thresholds: Thresholds = quiet): List<Pair<Long, PlaySideCue>> {
        val gen = SideCueGenerator(thresholds).apply { startPlay() }
        return signals.mapNotNull { s -> gen.onSignal(s)?.let { s.atMs to it } }
    }

    private fun sides(cues: List<Pair<Long, PlaySideCue>>) = cues.map { it.second.side }

    private fun cue(side: Side) = PlaySideCue(side, withVoice = false)

    // --- F4.2 조향 시작: 기준 아래 / 같음 / 유지 ---

    @Test
    fun `F4_2 조향각이 기준보다 작으면 소리를 내지 않는다`() {
        val signals = trace { at(0.seconds) { steer(-t.steerCueDeg * 0.9f) } }
        assertEquals(emptyList(), play(signals))
    }

    @Test
    fun `F4_2 왼쪽 조향각이 기준에 이르는 순간 왼쪽 소리를 낸다`() {
        val signals = trace {
            at(0.seconds) { steer(-t.steerCueDeg / 2) }
            at(1.seconds) { steer(-t.steerCueDeg) }
        }
        assertEquals(listOf(1_000L to cue(LEFT)), play(signals))
    }

    @Test
    fun `F4_2 오른쪽 조향각이 기준에 이르는 순간 오른쪽 소리를 낸다`() {
        val signals = trace { at(1.seconds) { steer(t.steerCueDeg) } }
        assertEquals(listOf(1_000L to cue(RIGHT)), play(signals))
    }

    @Test
    fun `F4_2 기준 위에서 회전이 이어지는 동안 다시 내지 않는다`() {
        val signals = trace {
            for (s in 0..10) at(s.seconds) { steer(-curveDeg - s) }
        }
        assertEquals(listOf(LEFT), sides(play(signals)))
    }

    // --- F4.2 떨림 방지: 복귀 기준 위 / 같음 / 아래 ---

    @Test
    fun `F4_2 복귀 기준보다 덜 돌아왔다가 다시 돌면 같은 쪽 소리를 다시 내지 않는다`() {
        val signals = trace {
            at(0.seconds) { steer(-curveDeg) }
            at(1.seconds) { steer(-(t.steerCueResetDeg + t.steerCueDeg) / 2) }
            at(2.seconds) { steer(-curveDeg) }
        }
        assertEquals(listOf(LEFT), sides(play(signals)))
    }

    @Test
    fun `F4_2 복귀 기준과 같은 각도까지만 돌아오면 복귀로 보지 않는다`() {
        val signals = trace {
            at(0.seconds) { steer(-curveDeg) }
            at(1.seconds) { steer(-t.steerCueResetDeg) }
            at(2.seconds) { steer(-curveDeg) }
        }
        assertEquals(listOf(LEFT), sides(play(signals)))
    }

    @Test
    fun `F4_2 복귀 기준 아래로 돌아오면 다음 회전에서 다시 소리를 낸다`() {
        val signals = trace {
            at(0.seconds) { steer(-curveDeg) }
            at(1.seconds) { steer(-t.steerCueResetDeg * 0.9f) }
            at(2.seconds) { steer(-curveDeg) }
        }
        assertEquals(listOf(LEFT, LEFT), sides(play(signals)))
    }

    @Test
    fun `F4_2 왼쪽에서 가운데를 거치지 않고 오른쪽으로 넘어가면 오른쪽 소리를 낸다`() {
        val signals = trace {
            at(0.seconds) { steer(-curveDeg) }
            at(1.seconds) { steer(curveDeg) }
        }
        assertEquals(listOf(LEFT, RIGHT), sides(play(signals)))
    }

    // --- F4.3 방향지시등 ---

    @Test
    fun `F4_3 방향지시등을 켜는 순간 그 방향 소리를 낸다`() {
        val signals = trace {
            at(1.seconds) { turnSignal(TurnState.LEFT) }
            at(2.seconds) { turnSignal(TurnState.OFF) }
            at(3.seconds) { turnSignal(TurnState.RIGHT) }
        }
        assertEquals(listOf(1_000L to cue(LEFT), 3_000L to cue(RIGHT)), play(signals))
    }

    @Test
    fun `F4_3 방향지시등이 켜진 상태가 반복해서 와도 한 번만 낸다`() {
        val signals = trace {
            for (s in 0..5) at(s.seconds) { turnSignal(TurnState.LEFT) }
        }
        assertEquals(listOf(LEFT), sides(play(signals)))
    }

    @Test
    fun `F4_3 왼쪽에서 오른쪽으로 바로 바꾸면 오른쪽 소리를 낸다`() {
        val signals = trace {
            at(0.seconds) { turnSignal(TurnState.LEFT) }
            at(1.seconds) { turnSignal(TurnState.RIGHT) }
        }
        assertEquals(listOf(LEFT, RIGHT), sides(play(signals)))
    }

    @Test
    fun `F4_3 비상등은 무시한다`() {
        val signals = trace {
            at(0.seconds) { turnSignal(TurnState.HAZARD) }
            at(5.seconds) { turnSignal(TurnState.OFF) }
        }
        assertEquals(emptyList(), play(signals))
    }

    // --- F4.3 방향지시등 뒤 같은 동작의 조향은 다시 울리지 않음 ---

    @Test
    fun `F4_3 방향지시등 소리는 차가 돌기 전에 나고 이어지는 같은 쪽 조향은 다시 울리지 않는다`() {
        val signals = trace {
            at(0.seconds) { turnSignal(TurnState.RIGHT) }
            at(3.seconds) { steer(curveDeg) }
            at(6.seconds) { steer(0f) }
        }
        assertEquals(listOf(0L to cue(RIGHT)), play(signals))
    }

    @Test
    fun `F4_3 방향지시등이 켜져 있어도 반대쪽 조향은 소리를 낸다`() {
        val signals = trace {
            at(0.seconds) { turnSignal(TurnState.RIGHT) }
            at(3.seconds) { steer(-curveDeg) }
        }
        assertEquals(listOf(RIGHT, LEFT), sides(play(signals)))
    }

    @Test
    fun `F4_3 방향지시등을 끈 뒤 억제 시간 안의 같은 쪽 조향은 소리를 내지 않는다`() {
        val offAt = 2.seconds
        val signals = trace {
            at(0.seconds) { turnSignal(TurnState.LEFT) }
            at(offAt) { turnSignal(TurnState.OFF) }
            at(offAt + t.turnSignalSuppressMs.milliseconds - 1.milliseconds) { steer(-curveDeg) }
        }
        assertEquals(listOf(LEFT), sides(play(signals)))
    }

    @Test
    fun `F4_3 억제 시간이 지난 뒤의 같은 쪽 조향은 소리를 낸다`() {
        val offAt = 2.seconds
        val signals = trace {
            at(0.seconds) { turnSignal(TurnState.LEFT) }
            at(offAt) { turnSignal(TurnState.OFF) }
            at(offAt + t.turnSignalSuppressMs.milliseconds) { steer(-curveDeg) }
        }
        assertEquals(listOf(LEFT, LEFT), sides(play(signals)))
    }

    // --- 놀이 중에만 ---

    @Test
    fun `F4 놀이 중이 아니면 소리를 내지 않는다`() {
        val gen = SideCueGenerator(quiet)
        val signals = trace {
            at(0.seconds) { steer(-curveDeg) }
            at(1.seconds) { turnSignal(TurnState.RIGHT) }
        }
        assertEquals(emptyList(), signals.mapNotNull(gen::onSignal))
    }

    @Test
    fun `F4 놀이를 시작할 때 이미 돌고 있던 회전에는 소리를 내지 않는다`() {
        val gen = SideCueGenerator(quiet)
        gen.onSignal(VehicleSignal.SteeringAngle(0, -curveDeg))
        gen.startPlay()
        val during = trace {
            at(1.seconds) { steer(-curveDeg * 1.5f) }
            at(2.seconds) { steer(0f) }
            at(3.seconds) { steer(-curveDeg) }
        }
        assertEquals(listOf(PlaySideCue(LEFT, false)), during.mapNotNull(gen::onSignal))
    }

    @Test
    fun `F4 놀이가 끝나면 소리를 내지 않는다`() {
        val gen = SideCueGenerator(quiet).apply { startPlay() }
        gen.onSignal(VehicleSignal.SteeringAngle(0, -curveDeg))
        gen.stopPlay()
        val after = trace {
            at(1.seconds) { steer(0f) }
            at(2.seconds) { steer(curveDeg) }
            at(3.seconds) { turnSignal(TurnState.LEFT) }
        }
        assertEquals(emptyList(), after.mapNotNull(gen::onSignal))
    }

    // --- F2.5 처음 몇 번은 말과 함께 ---

    @Test
    fun `F2_5 처음 정해진 횟수의 소리는 말과 함께 내고 그 뒤로는 소리만 낸다`() {
        val signals = trace {
            repeat(t.voiceCueCount + 2) { i -> at((i * 2).seconds) { steer(if (i % 2 == 0) -curveDeg else curveDeg) } }
        }
        val voices = play(signals, thresholds = t).map { it.second.withVoice }
        assertEquals(List(t.voiceCueCount) { true } + listOf(false, false), voices)
    }

    @Test
    fun `F2_5 말로 알리는 횟수는 놀이를 다시 시작해도 이어서 센다`() {
        val gen = SideCueGenerator(t)
        var ms = 0L
        fun nextCue(): PlaySideCue? {
            ms += 2_000
            val deg = if ((ms / 2_000) % 2 == 0L) -curveDeg else curveDeg
            return gen.onSignal(VehicleSignal.SteeringAngle(ms, deg))
        }
        gen.startPlay()
        val first = List(t.voiceCueCount - 1) { nextCue() }
        gen.stopPlay()
        gen.startPlay()
        val second = List(2) { nextCue() }
        assertEquals(
            List(t.voiceCueCount - 1) { true } + listOf(true, false),
            (first + second).map { it?.withVoice },
        )
    }
}
