package com.mulmicare.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import com.mulmicare.core.effect.Effect
import com.mulmicare.core.session.CareSession
import com.mulmicare.core.signal.VehicleSignal
import com.mulmicare.vehicle.PleosVehicleSource
import com.mulmicare.vehicle.SteeringCalibration

/**
 * 케어가 켜져 있는 동안 도는 포그라운드 서비스.
 * 차량 신호, 1초 Tick, 음성 응답을 모두 [handler] 스레드 하나로 모아 [CareSession]에 넣는다.
 * CareSession은 스레드 안전하지 않으므로 이 스레드 밖에서 부르지 않는다.
 */
class CareService : Service() {

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var runner: EffectRunner

    private var session: CareSession? = null
    private var source: PleosVehicleSource? = null
    private var debugReceiver: BroadcastReceiver? = null
    private var signalCount = 0L

    private val tick = object : Runnable {
        override fun run() {
            feed(VehicleSignal.Tick(now()))
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("care").apply { start() }
        handler = Handler(thread.looper)
        runner = EffectRunner(
            this,
            onListeningStarted = { handler.post { session?.onListeningStarted(now()) } },
            onHeard = { text -> handler.post { session?.let { dispatch(it.onSpeech(now(), text)) } } },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                handler.post { startCare() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.post { stopCare() }
        thread.quitSafely()
        runner.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCare() {
        if (session != null) return
        session = CareSession()
        // Pleos 에뮬레이터는 조향을 핸들 회전각·반대 부호로 준다 (pleos-sdk/references/vehicle.md)
        val onEmulator = Build.FINGERPRINT.contains("crp_avd")
        val calibration = if (onEmulator) SteeringCalibration.PLEOS_EMULATOR else SteeringCalibration.SDK_FRONT_WHEEL
        CareLog.add("조향 보정: ${if (onEmulator) "에뮬레이터" else "실차(SDK 문서)"} $calibration")
        source = PleosVehicleSource(this, calibration).also {
            it.start(
                onSignal = { signal -> handler.post { feed(signal) } },
                onIssue = { message -> CareLog.add("⚠ $message") },
            )
        }
        handler.postDelayed(tick, TICK_MS)
        registerDebugReceiver()
        CareLog.add("케어 시작")
    }

    private fun stopCare() {
        handler.removeCallbacks(tick)
        source?.stop()
        source = null
        session = null
        debugReceiver?.let { unregisterReceiver(it) }
        debugReceiver = null
        CareLog.add("케어 끝")
    }

    private fun feed(signal: VehicleSignal) {
        val s = session ?: return
        logSignal(signal)
        dispatch(s.onSignal(signal))
    }

    private var lastLoggedSteerDeg: Float? = null
    private var lastLoggedKph: Float? = null

    /** 신호가 들어오는지 확인용. 조향·속도는 자주 오므로 의미 있게 바뀔 때만 남긴다. */
    private fun logSignal(signal: VehicleSignal) {
        if (signal is VehicleSignal.Tick) return
        signalCount++
        val log = when (signal) {
            is VehicleSignal.SteeringAngle -> changedBy(lastLoggedSteerDeg, signal.deg, 0.5f).also {
                if (it) lastLoggedSteerDeg = signal.deg
            }
            is VehicleSignal.Speed -> changedBy(lastLoggedKph, signal.kph, 5f).also {
                if (it) lastLoggedKph = signal.kph
            }
            else -> true
        }
        if (log) CareLog.add("← #$signalCount $signal")
    }

    private fun changedBy(last: Float?, now: Float, step: Float) = last == null || kotlin.math.abs(now - last) >= step

    private fun dispatch(effects: List<Effect>) {
        runner.run(effects)
        if (Effect.FindRestStop in effects) {
            // 쉼터 데이터(F7.4)가 아직 없어 "없음"으로 돌려준다
            handler.post { session?.let { dispatch(it.onRestStopFound(now(), null)) } }
        }
    }

    /** 디버그 빌드에서만: adb로 음성 응답과 "힘들어해요" 버튼을 흉내 낸다. */
    private fun registerDebugReceiver() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handler.post {
                    val s = session ?: return@post
                    when (intent.action) {
                        ACTION_DEBUG_SAY -> {
                            val text = intent.getStringExtra("text").orEmpty()
                            CareLog.add("(adb) 말함: $text")
                            dispatch(s.onSpeech(now(), text))
                        }
                        ACTION_DEBUG_SPEAK -> runner.run(listOf(Effect.Speak(intent.getStringExtra("text").orEmpty())))
                        ACTION_DEBUG_STRUGGLE -> {
                            CareLog.add("(adb) 힘들어해요 버튼")
                            dispatch(s.onStruggleButton(now()))
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_DEBUG_SAY)
            addAction(ACTION_DEBUG_STRUGGLE)
            addAction(ACTION_DEBUG_SPEAK)
        }
        registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        debugReceiver = receiver
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "멀미케어", NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("멀미케어")
            .setContentText("뒷자리 케어 중")
            .setOngoing(true)
            .build()
    }

    private fun now() = SystemClock.elapsedRealtime()

    companion object {
        const val ACTION_START = "com.mulmicare.app.START"
        const val ACTION_STOP = "com.mulmicare.app.STOP"
        const val ACTION_DEBUG_SAY = "com.mulmicare.DEBUG_SAY"
        const val ACTION_DEBUG_STRUGGLE = "com.mulmicare.DEBUG_STRUGGLE"
        const val ACTION_DEBUG_SPEAK = "com.mulmicare.DEBUG_SPEAK"

        private const val CHANNEL_ID = "care"
        private const val NOTIFICATION_ID = 1
        private const val TICK_MS = 1_000L
    }
}
