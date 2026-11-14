package com.mulmicare.app

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Locale

/**
 * Pleos TextToSpeech SDK(ON_DEVICE)로 말한다.
 *
 * SDK의 speak()에는 대기열 옵션이 없어서, 말하는 중에 다음 문장이 오면 여기서 줄 세운다.
 * onDone/onStop/onError가 오면 다음 문장을 말하고, 끝 신호가 오지 않으면 [MAX_UTTERANCE_MS] 뒤 넘어간다.
 * 모든 상태는 메인 스레드에서만 바꾼다.
 */
class PleosSpeaker(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val queue = ArrayDeque<String>()
    private var ready = false
    private var speaking = false
    private var utterance = 0

    private val listener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = finished()
        override fun onStop() = finished()
        override fun onError(errorType: ErrorType) {
            CareLog.add("⚠ TTS 오류: $errorType")
            finished()
        }
    }

    init {
        // addEventListener는 initialize 성공 뒤에 불러야 한다 (먼저 부르면 SDK 내부 lateinit 예외로 앱이 죽는다)
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                main.post {
                    tts.addEventListener(listener)
                    tts.setLocale(Locale.KOREA)
                    ready = true
                    CareLog.add("TTS 준비됨 (Pleos ON_DEVICE, ko-KR)")
                    speakNext()
                }
            }

            override fun onFailure(errorType: ErrorType) {
                CareLog.add("⚠ TTS 초기화 실패: $errorType")
            }
        })
    }

    fun speak(text: String) {
        main.post {
            queue.addLast(text)
            speakNext()
        }
    }

    /** 대기열을 다 말하고 조용해지면 [action]을 실행한다. 지금 조용하면 바로 실행한다. */
    fun whenIdle(action: () -> Unit) {
        main.post {
            if (isIdle()) action() else idleActions.addLast(action)
        }
    }

    private val idleActions = ArrayDeque<() -> Unit>()

    // TTS를 쓸 수 없으면(초기화 실패) 기다릴 것이 없으므로 조용한 것으로 본다
    private fun isIdle() = !ready || (!speaking && queue.isEmpty())

    private fun runIdleActions() {
        while (isIdle()) {
            val action = idleActions.removeFirstOrNull() ?: return
            action()
        }
    }

    fun release() {
        main.post {
            idleActions.clear()
            queue.clear()
            if (ready) runCatching { tts.removeEventListener(listener) }
            ready = false
            tts.release()
        }
    }

    private fun finished() {
        main.post {
            speaking = false
            speakNext()
            runIdleActions()
        }
    }

    private fun speakNext() {
        if (!ready || speaking) return
        val text = queue.removeFirstOrNull() ?: return
        speaking = true
        val id = ++utterance
        tts.speak(text, true)
        // 끝 신호가 오지 않아도 대기열이 멈추지 않게 한다
        main.postDelayed({ if (speaking && utterance == id) finished() }, MAX_UTTERANCE_MS)
    }

    private companion object {
        const val MAX_UTTERANCE_MS = 10_000L
    }
}
