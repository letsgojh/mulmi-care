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
import com.mulmicare.core.place.NaviTurn
import com.mulmicare.core.place.RestStopAhead
import com.mulmicare.core.session.CareOffer
import com.mulmicare.app.ui.CareUi
import com.mulmicare.core.session.BackseatRating
import com.mulmicare.core.session.CareSession
import com.mulmicare.core.session.NextTripPolicy
import com.mulmicare.core.session.Screen
import com.mulmicare.core.session.TripReport
import com.mulmicare.core.signal.GearPos
import com.mulmicare.core.signal.Side
import com.mulmicare.core.voice.Passenger
import com.mulmicare.core.voice.VoiceLines
import com.mulmicare.core.voice.VoiceSettings
import com.mulmicare.core.signal.VehicleSignal
import com.mulmicare.vehicle.PleosNavi
import com.mulmicare.vehicle.PleosVehicleSource
import com.mulmicare.vehicle.SteeringCalibration

/**
 * 멀미케어 포그라운드 서비스. 두 단계로 동작한다.
 *
 * - 대기: 차량 신호와 내비만 받는다. 30분 이상 경로가 잡히고 P단이면 [CareOffer]가 "케어를 켤까요?"를 묻는다(F1.1).
 * - 케어: "응" 또는 "케어 시작" 버튼으로 시작한다. [CareSession]이 놀이·실내 케어·상태 확인을 한다.
 *   목적지에 도착하면 케어를 끝내고 공조를 되돌린 뒤 대기로 돌아간다.
 *
 * 차량 신호, 1초 Tick, 음성 응답, 내비 이벤트를 모두 [handler] 스레드 하나로 모은다.
 * CareOffer와 CareSession은 스레드 안전하지 않으므로 이 스레드 밖에서 부르지 않는다.
 */
class CareService : Service() {

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var runner: EffectRunner
    private lateinit var climateStore: ClimateStore
    private val main = Handler(Looper.getMainLooper())

    private var source: PleosVehicleSource? = null
    private var navi: PleosNavi? = null
    private var debugReceiver: BroadcastReceiver? = null
    private val offer = CareOffer()

    private var session: CareSession? = null
    private lateinit var tripStore: TripStore
    private var lastGear: GearPos? = null
    /** 주행 중에 케어가 끝나 아직 못 보여 준 리포트(F9.1). 정차하면 띄운다. */
    private var pendingReport: Screen.Report? = null
    private var latestTurns: List<NaviTurn> = emptyList()
    private var climateBaseline: ClimateBaseline? = null
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
        tripStore = TripStore(this)
        CareUi.update { it.copy(decoration = tripStore.decoration) }
        runner = EffectRunner(
            this,
            onListeningStarted = {
                handler.post {
                    val s = session
                    if (s != null) s.onListeningStarted(now()) else offer.onListeningStarted(now())
                }
            },
            onHeard = { text -> handler.post { hear(text) } },
        )
        runner.onClimateRestored = { climateStore.clear() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            in UI_ACTIONS -> {
                handler.post { handleUi(intent!!) }
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        handler.post {
            startStandby()
            if (intent?.action == ACTION_START_CARE) beginCare()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.post { shutdown() }
        thread.quitSafely()
        runner.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- 대기 ---

    private fun startStandby() {
        if (source != null) return
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
        navi = PleosNavi(this, naviListener, onLog = { CareLog.add(it) }).also { it.start() }
        handler.postDelayed(tick, TICK_MS)
        registerDebugReceiver()
        CareLog.add("대기 시작: 내비 경로를 기다림")
    }

    private val naviListener = object : PleosNavi.Listener {
        override fun onRouteStarted(durationSec: Int, distanceM: Int) {
            handler.post {
                if (session != null) return@post
                CareLog.add("경로 예상 ${durationSec / 60}분 → 출발 전 케어 제안 판단 (F1.1)")
                dispatch(offer.onRouteStarted(now(), durationSec))
            }
        }

        override fun onTurns(turns: List<NaviTurn>) {
            handler.post { latestTurns = turns }
        }

        override fun onArrived() {
            handler.post {
                if (session == null) return@post
                CareLog.add("목적지 도착 → 케어 끝")
                endCare()
            }
        }
    }

    private fun shutdown() {
        handler.removeCallbacks(tick)
        endCare()
        navi?.stop()
        navi = null
        // 공조 되돌리기 확인이 끝날 때까지 차량 연결을 잠시 유지한다
        val closing = source
        main.postDelayed({
            runner.climate = null
            closing?.stop()
        }, CLOSE_DELAY_MS)
        source = null
        debugReceiver?.let { unregisterReceiver(it) }
        debugReceiver = null
        CareLog.add("서비스 끝")
    }

    // --- 케어 ---

    private fun beginCare() {
        if (session != null) return
        val choice = CareUi.state.setup
        // F9.3 지난 이동에서 힘들어했으면 처음부터 강화 단계로
        val last = tripStore.lastStruggles?.let { TripReport(emptyList(), it) }
        val boost = NextTripPolicy.startBoost(last, tripStore.lastRating)
        session = CareSession(
            settings = VoiceSettings(choice.passenger, choice.proposePlay),
            startBoost = boost,
            previousStars = tripStore.totalStars,
        )
        runner.setTheme(choice.theme)
        CareUi.update { it.copy(caring = true) }
        // F2.1 처음 쓸 때만, 정차 중에, 아이가 탈 때 30초 연습
        if (!tripStore.practiceDone && choice.passenger == Passenger.CHILD && lastGear == GearPos.P) {
            CareUi.show(CareUi.Page.Practice)
            CareLog.add("🖥 연습 화면 (F2)")
            bringToFront()
        } else {
            CareUi.show(CareUi.Page.Home)
        }
        // 공조 기준값은 차량 서비스 연결을 잠시 기다린 뒤 읽는다 (F6)
        handler.postDelayed({ readClimateBaseline() }, CLIMATE_READ_DELAY_MS)
        CareLog.add("케어 시작: $choice${if (boost) ", 지난 이동 기록으로 강화 단계부터" else ""}")
    }

    private fun endCare() {
        val s = session ?: return
        val report = s.report
        val effects = s.end(now()) // 공조를 케어 전 상태로 되돌리고, 정차 중이면 리포트 화면(F9)
        dispatch(effects)
        // 주행 중에 끝났으면(도착 직후 등) 리포트는 정차한 뒤에 보여 준다 (S1)
        if (effects.none { it is Effect.ShowScreen }) {
            pendingReport = s.reportScreen()
            CareLog.add("리포트는 정차한 뒤에 보여 줌 (S1)")
        }
        tripStore.saveTrip(report)
        CareUi.update { it.copy(caring = false) }
        session = null
        climateBaseline = null
        CareLog.add("케어 끝")
    }

    private fun readClimateBaseline() {
        val climate = source?.climate ?: return
        runner.climate = climate
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

    // --- 입력 ---

    private fun feed(signal: VehicleSignal) {
        logSignal(signal)
        if (signal is VehicleSignal.Gear && signal.pos != lastGear) {
            lastGear = signal.pos
            // 주행 중에는 멀미케어 화면을 쓰지 않는다 (S1). 케어 전 대기 중에도 마찬가지
            CareUi.update { it.copy(driving = signal.pos != GearPos.P) }
            if (signal.pos != GearPos.P && CareUi.visible) leaveScreen()
            if (signal.pos == GearPos.P) pendingReport?.let { pendingReport = null; showScreen(it) }
        }
        dispatch(offer.onSignal(signal))
        session?.let { dispatch(it.onSignal(signal)) }
    }

    private fun hear(text: String) {
        val s = session
        dispatch(if (s != null) s.onSpeech(now(), text) else offer.onSpeech(now(), text))
    }

    private fun dispatch(effects: List<Effect>) {
        if (effects.isEmpty()) return
        // 앱이 맞춘 온도를 기록해 둔다. 되돌리지 못하고 끝나도 다음 케어가 원래 기준값을 찾을 수 있다
        effects.filterIsInstance<Effect.ApplyClimate>().lastOrNull()?.let { apply ->
            climateBaseline?.let { climateStore.save(it, apply.target.setTempC) }
        }
        runner.run(effects)
        effects.forEach { effect ->
            when (effect) {
                is Effect.ShowScreen -> showScreen(effect.screen)
                Effect.HideScreen -> CareUi.update { it.copy(driving = true, page = CareUi.Page.Home) }
                else -> Unit
            }
        }
        // F1.1 출발 전 케어 제안은 설정 카드와 함께 (정차 중에만 묻는다)
        if (effects.any { it is Effect.Speak && it.text == VoiceLines.OFFER_CARE }) {
            CareUi.show(CareUi.Page.Setup)
            bringToFront()
        }
        if (Effect.StartCare in effects) beginCare()
        if (Effect.FindRestStop in effects) findRestStop()
        effects.filterIsInstance<Effect.AddWaypoint>().forEach { add ->
            val stop = add.stop
            if (stop.latitude != null && stop.longitude != null) navi?.addWaypoint(stop.latitude!!, stop.longitude!!, stop.name)
        }
    }

    private fun showScreen(screen: Screen) {
        val page = when (screen) {
            is Screen.VoyageMap -> CareUi.Page.Voyage(screen)
            is Screen.Report -> CareUi.Page.Report(screen)
        }
        if (lastGear != GearPos.P) {
            // 기어를 모르거나 주행 중이면 띄우지 않는다 (S1)
            CareLog.add("⚠ 주행 중이라 화면을 띄우지 않음: ${screen::class.simpleName}")
            return
        }
        CareUi.update { it.copy(page = page, driving = false) }
        bringToFront()
    }

    /** 정차 중 화면을 앞으로 띄운다. 차량 OS가 막으면(주행 중 등) 그냥 둔다. */
    private fun bringToFront() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }.onFailure { CareLog.add("⚠ 화면을 앞으로 띄우지 못함: ${it.message}") }
    }

    /**
     * 주행이 시작됐는데 멀미케어 화면이 앞에 있으면 차량 홈(내비)으로 돌린다 (S1).
     * 차량 OS는 주행 중 일반 앱을 차단 화면으로 덮기만 하므로, 내비가 보이도록 직접 비켜 준다.
     */
    private fun leaveScreen() {
        val navi = packageManager.getLaunchIntentForPackage(PLEOS_NAVI_PACKAGE)
        val target = navi ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        runCatching { startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onSuccess { CareLog.add("🖥 주행 시작: ${if (navi != null) "내비" else "홈"} 화면으로 비켜 줌 (S1)") }
            .onFailure { CareLog.add("⚠ 화면을 비키지 못함: ${it.message}") }
    }

    /** 화면 버튼 동작. */
    private fun handleUi(intent: Intent) {
        when (intent.action) {
            ACTION_UI_SPEAK -> runner.run(listOf(Effect.Speak(intent.getStringExtra("text").orEmpty())))
            ACTION_UI_CUE -> {
                val side = runCatching { Side.valueOf(intent.getStringExtra("side").orEmpty()) }.getOrNull() ?: return
                runner.run(listOf(Effect.PlaySideCue(side, withVoice = false)))
            }
            ACTION_PRACTICE_DONE -> {
                tripStore.practiceDone = true
                CareLog.add("연습 끝")
                CareUi.show(CareUi.Page.Home)
            }
            ACTION_SHOW_VOYAGE -> {
                val s = session ?: return
                if (lastGear == GearPos.P) showScreen(s.voyageMap()) else CareLog.add("항해 지도는 정차 중에만 (S1)")
            }
            ACTION_DECORATE -> {
                val name = intent.getStringExtra("name") ?: return
                tripStore.decoration = name
                CareUi.update { it.copy(decoration = name) }
            }
            ACTION_RATE -> {
                val rating = runCatching { BackseatRating.valueOf(intent.getStringExtra("rating").orEmpty()) }.getOrNull() ?: return
                tripStore.saveRating(rating)
                CareLog.add("뒷자리 평가: $rating (다음 이동에 반영, F9.3)")
            }
            ACTION_CLEAR_RECORDS -> {
                tripStore.clear()
                CareUi.update { it.copy(decoration = null) }
                CareLog.add("이동 기록을 지움")
            }
        }
    }

    /** F7.2 내비 회전 안내를 새로 받아 경로 앞쪽의 휴게소·쉼터를 찾는다. */
    private fun findRestStop() {
        navi?.refreshTurns()
        handler.postDelayed({
            val s = session ?: return@postDelayed
            val stop = RestStopAhead.find(latestTurns)
            CareLog.add(if (stop == null) "🔎 앞쪽 30km 안에 쉼터 없음" else "🔎 쉼터: ${stop.name}, ${"%.1f".format(stop.distanceKm)}km 앞")
            dispatch(s.onRestStopFound(now(), stop))
        }, REST_STOP_WAIT_MS)
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

    /** 디버그 빌드에서만: adb로 음성 응답, 버튼, 내비 경로 요청을 흉내 낸다. */
    private fun registerDebugReceiver() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handler.post {
                    when (intent.action) {
                        ACTION_DEBUG_SAY -> {
                            val text = intent.getStringExtra("text").orEmpty()
                            CareLog.add("(adb) 말함: $text")
                            hear(text)
                        }
                        ACTION_DEBUG_ROUTE -> {
                            val lat = intent.getStringExtra("lat")?.toDoubleOrNull() ?: 37.48544722
                            val lng = intent.getStringExtra("lng")?.toDoubleOrNull() ?: 127.03636666
                            val name = intent.getStringExtra("name") ?: "42dot"
                            CareLog.add("(adb) 경로 요청: $name ($lat, $lng)")
                            navi?.requestRoute(lat, lng, name)
                        }
                        ACTION_DEBUG_NAVI_INFO -> {
                            CareLog.add("(adb) 내비 정보 요청")
                            navi?.refresh()
                        }
                        ACTION_DEBUG_START_CARE -> {
                            CareLog.add("(adb) 케어 시작")
                            beginCare()
                        }
                        ACTION_DEBUG_END_CARE -> {
                            CareLog.add("(adb) 케어 끝 (도착 흉내)")
                            endCare()
                        }
                        ACTION_DEBUG_STOP -> {
                            CareLog.add("(adb) 서비스 정지")
                            stopSelf()
                        }
                        ACTION_DEBUG_SPEAK -> runner.run(listOf(Effect.Speak(intent.getStringExtra("text").orEmpty())))
                        ACTION_DEBUG_STRUGGLE -> {
                            CareLog.add("(adb) 힘들어해요 버튼")
                            session?.let { dispatch(it.onStruggleButton(now())) }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            listOf(
                ACTION_DEBUG_SAY, ACTION_DEBUG_STRUGGLE, ACTION_DEBUG_SPEAK, ACTION_DEBUG_STOP, ACTION_DEBUG_ROUTE,
                ACTION_DEBUG_NAVI_INFO, ACTION_DEBUG_START_CARE, ACTION_DEBUG_END_CARE,
            ).forEach(::addAction)
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
            .setContentText("뒷자리 케어 대기 중")
            .setOngoing(true)
            .build()
    }

    private fun now() = SystemClock.elapsedRealtime()

    companion object {
        /** 대기 단계로 시작한다(내비 경로를 기다렸다가 제안). */
        const val ACTION_START = "com.mulmicare.app.START"

        /** Pleos Connect 기본 내비 앱. 주행이 시작되면 이 화면이 보이게 한다. */
        private const val PLEOS_NAVI_PACKAGE = "ai.umos.maps.android.navigation.app"

        // 화면 버튼 동작
        const val ACTION_UI_SPEAK = "com.mulmicare.app.UI_SPEAK"
        const val ACTION_UI_CUE = "com.mulmicare.app.UI_CUE"
        const val ACTION_PRACTICE_DONE = "com.mulmicare.app.PRACTICE_DONE"
        const val ACTION_SHOW_VOYAGE = "com.mulmicare.app.SHOW_VOYAGE"
        const val ACTION_DECORATE = "com.mulmicare.app.DECORATE"
        const val ACTION_RATE = "com.mulmicare.app.RATE"
        const val ACTION_CLEAR_RECORDS = "com.mulmicare.app.CLEAR_RECORDS"
        private val UI_ACTIONS = setOf(
            ACTION_UI_SPEAK, ACTION_UI_CUE, ACTION_PRACTICE_DONE, ACTION_SHOW_VOYAGE, ACTION_DECORATE, ACTION_RATE, ACTION_CLEAR_RECORDS,
        )

        /** 바로 케어를 시작한다("케어 시작" 버튼). */
        const val ACTION_START_CARE = "com.mulmicare.app.START_CARE"
        const val ACTION_STOP = "com.mulmicare.app.STOP"

        const val ACTION_DEBUG_SAY = "com.mulmicare.DEBUG_SAY"
        const val ACTION_DEBUG_STRUGGLE = "com.mulmicare.DEBUG_STRUGGLE"
        const val ACTION_DEBUG_SPEAK = "com.mulmicare.DEBUG_SPEAK"
        const val ACTION_DEBUG_STOP = "com.mulmicare.DEBUG_STOP"
        const val ACTION_DEBUG_ROUTE = "com.mulmicare.DEBUG_ROUTE"
        const val ACTION_DEBUG_NAVI_INFO = "com.mulmicare.DEBUG_NAVI_INFO"
        const val ACTION_DEBUG_START_CARE = "com.mulmicare.DEBUG_START_CARE"
        const val ACTION_DEBUG_END_CARE = "com.mulmicare.DEBUG_END_CARE"

        private const val CHANNEL_ID = "care"
        private const val NOTIFICATION_ID = 1
        private const val TICK_MS = 1_000L
        private const val CLIMATE_READ_DELAY_MS = 2_000L
        private const val CLOSE_DELAY_MS = 3_000L
        private const val REST_STOP_WAIT_MS = 1_500L
    }
}
