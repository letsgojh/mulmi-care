package com.mulmicare.app

import android.os.Handler
import android.os.Looper
import android.util.Log

/** 개발용 로그. logcat(태그 MulmiCare)에 남기고, 화면이 열려 있으면 화면에도 보여 준다. */
object CareLog {
    private const val TAG = "MulmiCare"
    private const val MAX_LINES = 200

    private val main = Handler(Looper.getMainLooper())
    private val lines = ArrayDeque<String>()
    private var listener: ((List<String>) -> Unit)? = null

    fun add(line: String) {
        Log.i(TAG, line)
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
        notifyListener()
    }

    fun observe(listener: ((List<String>) -> Unit)?) {
        this.listener = listener
        notifyListener()
    }

    private fun notifyListener() {
        val snapshot = synchronized(lines) { lines.toList() }
        main.post { listener?.invoke(snapshot) }
    }
}
