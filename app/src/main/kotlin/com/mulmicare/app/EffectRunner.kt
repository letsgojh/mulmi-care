package com.mulmicare.app

import android.content.Context
import com.mulmicare.core.effect.Effect
import com.mulmicare.core.signal.Side
import com.mulmicare.vehicle.PleosClimate

/**
 * core가 낸 Effect를 실제 동작으로 바꾼다. 판단은 하지 않는다.
 *
 * 지금은 껍데기 단계라 말하기는 Pleos TTS([PleosSpeaker]), 소리는 [CueSounds], 나머지는 로그로만 처리한다.
 * - Listen: Pleos STT([PleosListener]). 디버그 빌드에서는 adb 브로드캐스트로도 응답을 넣을 수 있다(CareService 참고).
 * - FindRestStop·AddWaypoint: CareService가 내비 회전 안내로 찾고 내비에 추가한다(F7.2, F7.3).
 * - ApplyClimate: [PleosClimate]로 적용하고 다시 읽어 확인(F6.8).
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

    /** 공조 제어. 차량 연결 뒤 CareService가 넣는다. */
    @Volatile
    var climate: PleosClimate? = null

    /** 공조 되돌리기가 확인되면 부른다(저장해 둔 기준값을 지우는 데 쓴다). */
    @Volatile
    var onClimateRestored: (() -> Unit)? = null

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
            Effect.PassengerStruggling -> CareLog.add("😣 뒷자리 힘들어함")
            is Effect.ApplyClimate -> {
                val t = effect.target
                CareLog.add("🌬 공조 ${effect.level}: ${t.setTempC}℃, 풍량 ${t.fanSpeed ?: "유지"}, 풍향 ${t.fanDirection ?: "유지"}, ${if (t.outsideAir) "외기" else "내기"}")
                val c = climate
                if (c == null) {
                    CareLog.add("⚠ 공조 제어 불가: 차량 연결 전")
                } else {
                    c.apply(t) { mismatches ->
                        CareLog.add(if (mismatches.isEmpty()) "🌬 공조 반영 확인" else "⚠ 공조 반영 안 됨: $mismatches")
                    }
                }
            }
            is Effect.RestoreClimate -> {
                val b = effect.baseline
                CareLog.add("🌬 공조 되돌리기: ${b.setTempC}℃, 풍량 ${b.fanSpeed ?: "유지"}, 풍향 ${b.fanDirection ?: "유지"}, 내기순환 ${b.recirculation ?: "유지"}")
                val c = climate
                if (c == null) {
                    CareLog.add("⚠ 공조 되돌리기 불가: 차량 연결 없음")
                } else {
                    c.restore(b) { mismatches ->
                        if (mismatches.isEmpty()) {
                            CareLog.add("🌬 공조 되돌리기 확인")
                            onClimateRestored?.invoke()
                        } else {
                            CareLog.add("⚠ 공조 되돌리기 안 됨: $mismatches")
                        }
                    }
                }
            }
            Effect.StartCare -> CareLog.add("✅ 케어를 켜기로 함") // 실제 시작은 CareService가 한다
            Effect.FindRestStop -> CareLog.add("🔎 쉼터 찾기")
            is Effect.AddWaypoint -> CareLog.add("📍 경유지 추가: ${effect.stop.name}") // 실제 추가는 CareService가 내비로 한다
        }
    }

    private fun speak(text: String) = speaker.speak(text)

    fun release() {
        sounds.release()
        speaker.release()
        listener.release()
    }
}
