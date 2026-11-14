package com.mulmicare.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 개발용 화면: 권한 요청, 케어 시작·정지, 로그.
 * 정식 화면(F1 출발 전 설정, F8 항해 지도, F9 리포트)은 정차 중에만 보이도록 따로 만든다(S1).
 */
class MainActivity : Activity() {

    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logView = TextView(this).apply { textSize = 14f }
        scroll = ScrollView(this).apply { addView(logView) }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(context).apply { text = "케어 시작"; textSize = 22f; setOnClickListener { start(CareService.ACTION_START_CARE) } })
            addView(Button(context).apply { text = "케어 정지"; textSize = 22f; setOnClickListener { stopCare() } })
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                addView(buttons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            },
        )
        // 앱을 열면 대기 단계로 시작한다: 내비 경로가 잡히면 출발 전에 케어를 제안한다 (F1.1)
        // 디버그 빌드: --ez autostart true 면 바로 케어를 시작한다 (시나리오 스크립트용)
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        start(if (debuggable && intent.getBooleanExtra("autostart", false)) CareService.ACTION_START_CARE else CareService.ACTION_START)
    }

    override fun onStart() {
        super.onStart()
        CareLog.observe { lines ->
            logView.text = lines.joinToString("\n")
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onStop() {
        CareLog.observe(null)
        super.onStop()
    }

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

    private fun stopCare() {
        startService(Intent(this, CareService::class.java).setAction(CareService.ACTION_STOP))
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
