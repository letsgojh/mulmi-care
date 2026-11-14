# TextToSpeech SDK 3.3.1 (문서 + AAR javap로 확인, 2026-11-14)

구현: [app/.../PleosSpeaker.kt](../../../../app/src/main/kotlin/com/mulmicare/app/PleosSpeaker.kt). 소스 jar는 공개되어 있지 않다.

- 의존성: `ai.pleos.playground:TextToSpeech:3.3.1` (저장소는 Vehicle과 같음). 전이: `ai.pleos.playground.security:universal-access:0.6.0`
- 권한: `pleos.car.permission.TTS_SERVICE`. **AAR 매니페스트가 `android.permission.INTERNET`을 합쳐 넣는다** (S8 점검 대상)
- 모드: `Mode.ON_DEVICE`(네트워크·앱 등록 불필요) / `HYBRID`(기본, 앱 등록 필요) / `SERVER`
```kotlin
val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
tts.initialize(object : OnInitializeListener {
    override fun onSuccess() { tts.addEventListener(listener); tts.setLocale(Locale.KOREA) }
    override fun onFailure(errorType: ErrorType) {}
})
tts.speak(text, true)   // speak(String, isFinal: Boolean). 대기열 옵션 없음
tts.stop(); tts.removeEventListener(listener); tts.release()
```
- **`addEventListener`는 `initialize` 성공 뒤에 불러야 한다.** 먼저 부르면 SDK 내부 `lateinit property tts` 예외로 앱이 죽는다(에뮬레이터에서 확인).
- `EventListener`: `onReady()`, `onStart()`, `onUpdatedRms(Double)`, `onDone()`, `onStop()`, `onError(ErrorType)`
- `ErrorType`: AUDIO_ERROR, MIC_ERROR, SERVER_INTERNAL_ERROR, NETWORK_ERROR, INVALID_REQUEST_ERROR, TIMEOUT, UNAUTHENTICATED, PERMISSION_DENIED, ON_DEVICE_ENGINE_ERROR, UNKNOWN, UNAVAILABLE_LANGUAGE, NOT_SUPPORTED_API
- 그 밖의 공개 메서드: `setAudioAttributes(AudioAttributes)`(S6 내비 우선 처리에 쓸 수 있음), `setSpeakerType(String)`, `setAudioZoneId(Int)`, `registerApp(...)`
- 에뮬레이터: ON_DEVICE 한국어(`ko_KR`, 화자 `seoyoon`)로 합성·재생 확인. 짧은 문장 하나에 약 2.5초.
- 연속 발화: 앱에서 대기열로 처리(onDone 뒤 다음 문장). 두 문장이 끊기지 않고 순서대로 나오는 것 확인.
