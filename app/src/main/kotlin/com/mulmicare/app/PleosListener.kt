package com.mulmicare.app

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Locale

/**
 * Pleos SpeechToText SDK(ON_DEVICE)로 짧은 응답을 듣는다 (F3.6: 앱이 물어본 직후와 놀이 중에만).
 *
 * - [listen] continuous=false: 한 번 듣고 끝. 질문에 대한 답.
 * - [listen] continuous=true: 인식이 끝나도 [stop] 전까지 다시 듣는다. 놀이 중 "그만".
 * 마이크가 열리면 [onStarted], 최종 결과가 나오면 [onHeard]를 부른다. 판단은 core가 한다.
 * 모든 상태는 메인 스레드에서만 바꾼다.
 */
class PleosListener(
    context: Context,
    private val onStarted: () -> Unit,
    private val onHeard: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private var ready = false
    private var wanted = false
    private var continuous = false
    /** request()를 보냈고 아직 끝나지 않음. */
    private var requested = false

    private val listener = object : ResultListener {
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit

        // 에뮬레이터에서 확인: request() 뒤 약 2~3초에 onReady(ReadyToStartRecognition)가 온다.
        // onStartedRecognition은 말소리가 감지될 때 온다. 그래서 마이크가 열린 신호로 onReady를 쓴다.
        override fun onReady() {
            main.post {
                if (!requested) return@post
                CareLog.add("🎤 마이크 열림")
                onStarted()
            }
        }

        override fun onStartedRecognition() {
            main.post { CareLog.add("🎤 말소리 감지") }
        }

        override fun onUpdated(stt: String, completed: Boolean) {
            if (!completed || stt.isBlank()) return
            main.post {
                CareLog.add("🎤 들음: $stt")
                // 에뮬레이터에서 확인: 최종 결과가 onEndedRecognition보다 늦게 올 수 있다.
                // 여기서 거르면 답을 잃으므로 항상 넘기고, 지금 답을 받을지는 core가 판단한다(F3.6).
                onHeard(stt)
            }
        }

        override fun onEndedRecognition() {
            main.post { ended() }
        }

        override fun onError(errorType: ErrorType) {
            main.post {
                CareLog.add("⚠ STT 오류: $errorType")
                ended(retryDelayMs = 1_000)
            }
        }
    }

    init {
        // TTS와 같이 리스너는 initialize 성공 뒤에 등록한다
        stt.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                main.post {
                    stt.addListener(listener)
                    stt.setLocale(Locale.KOREA)
                    ready = true
                    CareLog.add("STT 준비됨 (Pleos ON_DEVICE, ko-KR)")
                    if (wanted && !requested) request()
                }
            }

            override fun onFailure(errorType: ErrorType) {
                CareLog.add("⚠ STT 초기화 실패: $errorType")
            }
        })
    }

    fun listen(continuous: Boolean) {
        main.post {
            wanted = true
            this.continuous = continuous
            if (ready && !requested) request()
        }
    }

    fun stop() {
        main.post {
            wanted = false
            if (requested) runCatching { stt.stop() }
        }
    }

    fun release() {
        main.post {
            wanted = false
            if (ready) {
                runCatching { stt.stop() }
                runCatching { stt.removeListener(listener) }
            }
            ready = false
            stt.release()
        }
    }

    private fun ended(retryDelayMs: Long = 300) {
        requested = false
        if (!continuous) {
            wanted = false
            return
        }
        if (wanted) main.postDelayed({ if (wanted && !requested) request() }, retryDelayMs)
    }

    private fun request() {
        requested = true
        runCatching { stt.request() }.onFailure {
            requested = false
            CareLog.add("⚠ STT 시작 실패: ${it.message}")
        }
    }
}
