package com.mulmicare.app

import android.content.Context
import com.mulmicare.core.effect.Effect
import com.mulmicare.core.signal.Side

/**
 * core가 낸 Effect를 실제 동작으로 바꾼다. 판단은 하지 않는다.
 *
 * 지금은 껍데기 단계라 말하기는 Pleos TTS([PleosSpeaker]), 소리는 [CueSounds], 나머지는 로그로만 처리한다.
 * - Listen: Pleos STT([PleosListener]). 디버그 빌드에서는 adb 브로드캐스트로도 응답을 넣을 수 있다(CareService 참고).
 * - FindRestStop: 쉼터 데이터(F7.4) 전. CareService가 "없음"으로 돌려준다.
 * - PassengerStruggling: 실내 케어(F6) 전.
 */
class EffectRunner(
    context: Context,
    onListeningStarted: () -> Unit,
    onHeard: (String) -> Unit,
) {

    private val sounds = CueSounds()
    private val speaker = PleosSpeaker(context)
    private val listener = PleosListener(context, onListeningStarted, onHeard)

    /** StopListening이 오면 늘어나서, 아직 마이크를 열지 않은 듣기 요청을 취소한다. */
    @Volatile
    private var listenGeneration = 0

    fun run(effects: List<Effect>) = effects.forEach(::run)

    private fun run(effect: Effect) {
        when (effect) {
            is Effect.PlaySideCue -> {
                sounds.side(effect.side)
                if (effect.withVoice) speak(if (effect.side == Side.LEFT) "왼쪽!" else "오른쪽!")
                CareLog.add("🔊 ${effect.side}${if (effect.withVoice) " (말과 함께)" else ""}")
            }
            Effect.StarLost -> {
                sounds.starLost()
                CareLog.add("⭐ 별 하나 떨어짐")
            }
            Effect.Chime -> sounds.chime()
            is Effect.Speak -> {
                speak(effect.text)
                CareLog.add("🗣 ${effect.text}")
            }
            is Effect.Listen -> {
                CareLog.add("🎤 듣기 ${effect.expect} ${effect.timeoutMs?.let { "${it / 1000}초" } ?: "계속"}")
                // 질문을 다 말한 뒤에 마이크를 연다 (앱 목소리를 듣지 않게)
                val generation = listenGeneration
                speaker.whenIdle {
                    if (generation == listenGeneration) listener.listen(continuous = effect.timeoutMs == null)
                }
            }
            Effect.StopListening -> {
                listenGeneration++
                listener.stop()
                CareLog.add("🎤 듣기 끝")
            }
            Effect.StartBeat -> {
                sounds.startBeat()
                CareLog.add("🎵 비트 시작")
            }
            Effect.StopBeat -> {
                sounds.stopBeat()
                CareLog.add("🎵 비트 끝")
            }
            Effect.PassengerStruggling -> CareLog.add("😣 뒷자리 힘들어함 → 실내 케어 강화 (F6 미구현)")
            Effect.FindRestStop -> CareLog.add("🔎 쉼터 찾기")
            is Effect.AddWaypoint -> CareLog.add("📍 경유지 추가: ${effect.stop.name} (내비 연동 미구현)")
        }
    }

    private fun speak(text: String) = speaker.speak(text)

    fun release() {
        sounds.release()
        speaker.release()
        listener.release()
    }
}
