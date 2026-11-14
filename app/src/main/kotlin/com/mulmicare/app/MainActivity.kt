package com.mulmicare.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.TextView
import com.mulmicare.app.ui.BoatView
import com.mulmicare.app.ui.CareUi
import com.mulmicare.app.ui.Pages
import com.mulmicare.app.ui.SetupChoice
import com.mulmicare.app.ui.Ui
import com.mulmicare.app.ui.UiActions
import com.mulmicare.core.session.BackseatRating
import com.mulmicare.core.signal.Side

/**
 * 정차 중 화면(F1.3 설정, F2 연습, F8 항해 지도, F9 리포트). 어떤 화면을 띄울지는 [CareUi] 상태를 따른다.
 * 주행 중이 되면(CareUi.State.driving) 뒤로 물러나 내비가 보이게 한다(S1).
 */
class MainActivity : Activity(), UiActions {

    private lateinit var root: FrameLayout
    private val logView by lazy { TextView(this) }
    private val main = Handler(Looper.getMainLooper())
    private var rendered: Pair<CareUi.Page, Any?>? = null
    private var rated = false

    // F2 연습
    private val boat by lazy { BoatView(this) }
    private val caption by lazy { TextView(this) }
    private var practiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(Ui.PAPER) }
        setContentView(root)
        // 앱을 열면 대기 단계로 시작한다: 내비 경로가 잡히면 출발 전에 케어를 제안한다 (F1.1)
        // 디버그 빌드: --ez autostart true 면 바로 케어를 시작한다 (시나리오 스크립트용)
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        start(if (debuggable && intent.getBooleanExtra("autostart", false)) CareService.ACTION_START_CARE else CareService.ACTION_START)
    }

    override fun onStart() {
        super.onStart()
        rendered = null
        CareUi.visible = true
        CareUi.observe { render(it) }
        CareLog.observe { lines -> logView.text = lines.takeLast(120).joinToString("\n") }
    }

    override fun onStop() {
        CareUi.visible = false
        CareUi.observe(null)
        CareLog.observe(null)
        super.onStop()
    }

    private fun render(state: CareUi.State) {
        if (state.driving) {
            stopPractice()
            moveTaskToBack(true) // 주행 중에는 화면을 쓰지 않는다 (S1)
            return
        }
        val key = state.page to listOf(state.caring, state.setup, state.decoration, rated)
        if (key == rendered) return
        rendered = key
        if (state.page !is CareUi.Page.Practice) stopPractice()
        if (state.page !is CareUi.Page.Report) rated = false
        val view = when (val page = state.page) {
            CareUi.Page.Home -> Pages.home(this, state, this)
            CareUi.Page.Setup -> Pages.setup(this, state, this)
            CareUi.Page.Practice -> Pages.practice(this, state, detach(boat), detach(caption), this).also { startPractice() }
            is CareUi.Page.Voyage -> Pages.voyage(this, state, page.screen, this)
            is CareUi.Page.Report -> Pages.report(this, state, page.screen, rated, this)
            CareUi.Page.DevLog -> Pages.devLog(this, detach(logView), this)
        }
        root.removeAllViews()
        root.addView(view)
    }

    private fun <T : android.view.View> detach(view: T): T {
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        return view
    }

    // --- F2 30초 연습: 말 → 소리 → 종이배가 같은 쪽으로 기움 ---

    private fun startPractice() {
        if (practiceRunning) return
        practiceRunning = true
        val steps = listOf<Pair<Long, () -> Unit>>(
            0L to { say("놀이 방법을 알려 줄게. 이 소리가 나면 왼쪽으로 몸을 기울여 봐", "이 소리가 나면\n왼쪽으로 몸을 기울여 봐"); boat.tilt(0f) },
            6_000L to { cue(Side.LEFT); boat.tilt(-18f) },
            9_000L to { say("이번엔 오른쪽!", "이번엔 오른쪽!"); boat.tilt(0f) },
            11_000L to { cue(Side.RIGHT); boat.tilt(18f) },
            14_000L to {
                say("굽은 길에서 음악이 나오면 시작이야. 앞을 보면서 하면 더 잘할 수 있어", "굽은 길에서 음악이 나오면 시작!\n앞을 보면서 하면 더 잘할 수 있어")
                boat.tilt(0f)
            },
            24_000L to { finishPractice() },
        )
        steps.forEach { (delay, step) -> main.postDelayed({ if (practiceRunning) step() }, delay) }
    }

    private fun stopPractice() {
        practiceRunning = false
        main.removeCallbacksAndMessages(null)
    }

    private fun say(speech: String, text: String) {
        caption.text = text
        service(CareService.ACTION_UI_SPEAK) { putExtra("text", speech) }
    }

    private fun cue(side: Side) = service(CareService.ACTION_UI_CUE) { putExtra("side", side.name) }

    private fun finishPractice() {
        stopPractice()
        service(CareService.ACTION_PRACTICE_DONE)
    }

    // --- UiActions: 서비스로 보낸다 ---

    override fun openSetup() = CareUi.show(CareUi.Page.Setup)
    override fun pickSetup(choice: SetupChoice) = CareUi.update { it.copy(setup = choice) }
    override fun startCare() = start(CareService.ACTION_START_CARE)
    override fun declineOffer() = CareUi.show(CareUi.Page.Home)
    override fun skipPractice() = finishPractice()
    override fun openVoyage() = service(CareService.ACTION_SHOW_VOYAGE)
    override fun pickDecoration(name: String) = service(CareService.ACTION_DECORATE) { putExtra("name", name) }
    override fun rate(rating: BackseatRating) {
        rated = true
        service(CareService.ACTION_RATE) { putExtra("rating", rating.name) }
        render(CareUi.state)
    }
    override fun clearRecords() = service(CareService.ACTION_CLEAR_RECORDS)
    override fun goHome() = CareUi.show(CareUi.Page.Home)
    override fun openDevLog() = CareUi.show(CareUi.Page.DevLog)

    private fun service(action: String, extras: Intent.() -> Unit = {}) {
        startService(Intent(this, CareService::class.java).setAction(action).apply(extras))
    }

    // --- 권한 ---

    private var pendingAction = CareService.ACTION_START

    private fun start(action: String) {
        val missing = RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            pendingAction = action
            requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
            return
        }
        startForegroundService(Intent(this, CareService::class.java).setAction(action))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != REQUEST_PERMISSIONS) return
        permissions.zip(grantResults.toTypedArray()).forEach { (p, r) ->
            CareLog.add("권한 $p: ${if (r == PackageManager.PERMISSION_GRANTED) "허용" else "거부"}")
        }
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) start(pendingAction)
    }

    private companion object {
        const val REQUEST_PERMISSIONS = 1
        val RUNTIME_PERMISSIONS = listOf("android.car.permission.CAR_SPEED")
    }
}
