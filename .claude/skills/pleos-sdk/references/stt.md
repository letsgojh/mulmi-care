# SpeechToText SDK 3.3.1 (문서 + AAR javap + 에뮬레이터, 2026-11-14)

구현: [app/.../PleosListener.kt](../../../../app/src/main/kotlin/com/mulmicare/app/PleosListener.kt). 소스 jar 비공개.

- 의존성: `ai.pleos.playground:SpeechToText:3.3.1`. 권한: `pleos.car.permission.STT_SERVICE`. AAR이 `INTERNET`을 합쳐 넣는다. `RECORD_AUDIO`는 요구하지 않음(음성 서비스가 녹음)
- 모드: `Mode.ON_DEVICE` / `HYBRID`(기본, 앱 등록 필요) / `SERVER`
```kotlin
val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
stt.initialize(object : OnInitializeListener {          // ai.pleos.playground.stt.listener
    override fun onSuccess() { stt.addListener(listener); stt.setLocale(Locale.KOREA) }
    override fun onFailure(errorType: ErrorType) {}       // ai.pleos.playground.stt.constant
})
stt.request()   // 인자 없음. 한 번 듣는다
stt.stop()
stt.removeListener(listener); stt.release()
// 그 밖: sendAudio(ByteArray), completeAudioSend(), registerApp(...)
```
- `ResultListener`: `onReady()`, `onStartedRecognition()`, `onUpdated(stt: String, completed: Boolean)`, `onUpdatedEpdData(on: Long, off: Long)`, `onUpdatedRms(Double)`, `onEndedRecognition()`, `onError(ErrorType)`
- **에뮬레이터에서 확인한 순서**: `request()` → 약 0.5~2.7초 뒤 `onReady`(로그 `ReadyToStartRecognition`) → 말소리가 감지되면 `onStartedRecognition`. 그래서 "마이크가 열림" 신호로 `onReady`를 쓴다. 이 시각을 core `onListeningStarted`로 넘겨 답을 기다리는 시간을 그때부터 센다.
- TTS와 겹치지 않게: `Listen`은 `PleosSpeaker.whenIdle`로 TTS가 다 말한 뒤에 `request()`. 에뮬레이터 로그에서 TTS 끝(UpdateTtsDone) 직후 STT request가 나가는 것 확인.
- 놀이 중 "그만"은 연속 듣기: `onEndedRecognition`/`onError` 뒤 다시 `request()`.
- **에뮬레이터에서 사람 목소리로 확인(2026-11-15)**: ON_DEVICE 한국어 인식 동작. "응", "그만"을 정확히 인식(말한 뒤 약 0.4초에 결과). 마이크: Extended Controls → Microphone → **Enable Host Microphone Access**.
- **최종 결과(`onUpdated(completed=true)`)가 `onEndedRecognition`보다 늦게 올 수 있다.** 앱에서 "듣는 중"일 때만 결과를 넘기면 답을 잃는다(실제로 "응"을 잃었음). 결과는 항상 core로 넘기고, 받을지는 core가 판단한다.
- 마이크는 주변 소리도 듣는다(PC에서 재생 중인 소리를 문장으로 인식함). 목록에 없는 말은 core가 "답 없음"으로 처리한다.
