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
import android.os.Looper
import android.os.SystemClock
import com.mulmicare.core.care.BaselineResolver
import com.mulmicare.core.care.ClimateBaseline
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
    private lateinit var climateStore: ClimateStore
    private var climateBaseline: ClimateBaseline? = null
    private val main = Handler(Looper.getMainLooper())
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
        climateStore = ClimateStore(this)
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
        // 공조 기준값은 차량 서비스 연결을 잠시 기다린 뒤 읽는다 (F6)
        handler.postDelayed({ readClimateBaseline() }, CLIMATE_READ_DELAY_MS)
        registerDebugReceiver()
        CareLog.add("케어 시작")
    }

    private fun readClimateBaseline() {
        val climate = source?.climate ?: return
        runner.climate = climate
        runner.onClimateRestored = { climateStore.clear() }
        climate.readBaseline { read, outsideC ->
            handler.post {
                val s = session ?: return@post
                if (read == null) {
                    CareLog.add("⚠ 공조 기준값을 읽지 못해 실내 케어를 하지 않음")
                    return@post
                }
                // 지난 케어가 공조를 되돌리지 못하고 끝났으면 저장해 둔 기준값을 쓴다 (온도가 계속 내려가는 것 방지)
                val baseline = BaselineResolver.resolve(read, climateStore.load())
                if (baseline != read) CareLog.add("공조 기준값: 되돌려지지 않은 ${read.setTempC}℃ 대신 저장해 둔 기준값 사용")
                climateBaseline = baseline
                val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
                CareLog.add("공조 기준값: $baseline, 바깥 ${outsideC ?: "모름"}℃, ${month}월")
                dispatch(s.onClimateBaseline(now(), baseline, outsideC, month))
            }
        }
    }

    private fun stopCare() {
        handler.removeCallbacks(tick)
        // 공조를 케어 전 상태로 되돌리고, 되돌리기 확인이 끝날 때까지 차량 연결을 잠시 유지한다
        session?.let { dispatch(it.end(now())) }
        val closing = source
        main.postDelayed({
            runner.climate = null
            closing?.stop()
        }, CLOSE_DELAY_MS)
        source = null
        session = null
        climateBaseline = null
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
        // 앱이 맞춘 온도를 기록해 둔다. 되돌리지 못하고 끝나도 다음 케어가 원래 기준값을 찾을 수 있다
        effects.filterIsInstance<Effect.ApplyClimate>().lastOrNull()?.let { apply ->
            climateBaseline?.let { climateStore.save(it, apply.target.setTempC) }
        }
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
                        ACTION_DEBUG_STOP -> {
                            CareLog.add("(adb) 케어 정지")
                            stopSelf()
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
            addAction(ACTION_DEBUG_STOP)
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
        const val ACTION_DEBUG_STOP = "com.mulmicare.DEBUG_STOP"

        private const val CHANNEL_ID = "care"
        private const val NOTIFICATION_ID = 1
        private const val TICK_MS = 1_000L
        private const val CLIMATE_READ_DELAY_MS = 2_000L
        private const val CLOSE_DELAY_MS = 3_000L
    }
}
