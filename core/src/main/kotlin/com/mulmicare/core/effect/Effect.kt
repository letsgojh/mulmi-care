package com.mulmicare.core.effect

import com.mulmicare.core.care.CareLevel
import com.mulmicare.core.care.ClimateBaseline
import com.mulmicare.core.care.ClimateTarget
import com.mulmicare.core.place.RestStop
import com.mulmicare.core.session.Screen
import com.mulmicare.core.signal.Side
import com.mulmicare.core.voice.VoiceIntent

/** core가 app에 요청하는 동작. app의 EffectRunner가 실제 소리·음성·공조·화면으로 바꾼다. */
sealed interface Effect {

    /** F4.2, F4.3 좌우 방향 소리. [withVoice]면 "왼쪽!"/"오른쪽!"을 함께 말한다(F2.5). */
    data class PlaySideCue(val side: Side, val withVoice: Boolean) : Effect

    /** F5.3, F5.5 별 하나가 떨어짐. 짧은 효과음만 낸다. 말하지 않고(S5) 주행 중 화면에 표시하지 않는다(S1). */
    data object StarLost : Effect

    /** F3.2 놀이 제안 전 짧은 알림음. */
    data object Chime : Effect

    /** TTS로 말한다. 내비 안내 음성이 나오는 중이면 app이 미뤄서 말한다(S6). */
    data class Speak(val text: String) : Effect

    /**
     * F3.6 음성 인식을 연다. 앱이 질문한 직후와 놀이 중에만 낸다.
     * [timeoutMs]가 null이면 [StopListening]까지 계속 듣는다(놀이 중 "그만").
     */
    data class Listen(val expect: Set<VoiceIntent>, val timeoutMs: Long?) : Effect

    data object StopListening : Effect

    /** F4.1 리듬 놀이 비트 시작. 이것이 나오는 경로는 "응" 응답 뒤뿐이다(S2). */
    data object StartBeat : Effect

    data object StopBeat : Effect

    /** F7.2 뒷자리가 힘들어함. 세션이 받아 실내 케어를 강화 단계로 바꾼다(F6.4). */
    data object PassengerStruggling : Effect

    /** F7.2 가까운 쉼터를 찾아 달라는 요청. 결과는 VoiceFlow.onRestStopFound로 돌려준다. */
    data object FindRestStop : Effect

    /** F7.3 쉼터를 내비 경유지로 추가. */
    data class AddWaypoint(val stop: RestStop) : Effect

    /** F6 공조를 목표값으로 맞춘다. app은 요청 뒤 상태를 다시 읽어 반영됐는지 확인한다(F6.8). */
    data class ApplyClimate(val level: CareLevel, val target: ClimateTarget) : Effect

    /** F8 정차 중 화면을 띄운다. core는 P단일 때만 이것을 낸다(S1). */
    data class ShowScreen(val screen: Screen) : Effect

    /** F8.1 출발하면 화면을 내린다. */
    data object HideScreen : Effect

    /** F1.4 출발 전 제안에 "응"이라고 답함. app이 케어(CareSession)를 시작한다. */
    data object StartCare : Effect

    /** 케어가 끝나면 공조를 케어 전 상태로 되돌린다. null인 항목은 건드리지 않는다. */
    data class RestoreClimate(val baseline: ClimateBaseline) : Effect
}
