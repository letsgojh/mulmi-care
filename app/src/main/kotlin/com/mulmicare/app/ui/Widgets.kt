package com.mulmicare.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView

/** 차량 화면용 공통 스타일: 큰 글자, 큰 터치 영역(S3: 정차 중에도 짧고 쉽게). */
object Ui {
    val PAPER = Color.parseColor("#F6F2E9")
    val INK = Color.parseColor("#1F2328")
    val MUTED = Color.parseColor("#6B6F76")
    val SEA = Color.parseColor("#2F6FA3")
    val SEA_LIGHT = Color.parseColor("#CFE3F2")
    val STAR = Color.parseColor("#F2B705")
    val ACCENT = Color.parseColor("#E4572E")
    val CARD = Color.WHITE

    fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    fun title(context: Context, text: String, size: Float = 34f) = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(INK)
        typeface = Typeface.DEFAULT_BOLD
    }

    fun body(context: Context, text: String, size: Float = 22f, color: Int = MUTED) = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        setLineSpacing(0f, 1.2f)
    }

    fun rounded(color: Int, radius: Float, stroke: Int? = null, strokeWidth: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (stroke != null) setStroke(strokeWidth, stroke)
    }

    /** 큰 버튼. */
    fun button(context: Context, text: String, primary: Boolean = true, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (primary) Color.WHITE else INK)
        val d = context.dp(1)
        background = rounded(if (primary) SEA else CARD, 24f * d, if (primary) null else Color.parseColor("#C9C9C9"), 2 * d)
        setPadding(context.dp(36), context.dp(22), context.dp(36), context.dp(22))
        setOnClickListener { onClick() }
    }

    /** 하나만 고르는 줄: [라벨] [선택지들]. */
    fun choiceRow(context: Context, label: String, options: List<String>, selected: String, onPick: (String) -> Unit): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(10), 0, context.dp(10))
        }
        row.addView(body(context, label, 24f, INK), LinearLayout.LayoutParams(context.dp(200), WRAP_CONTENT))
        options.forEach { option ->
            val on = option == selected
            row.addView(TextView(context).apply {
                text = option
                textSize = 24f
                gravity = Gravity.CENTER
                typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (on) Color.WHITE else INK)
                val d = context.dp(1)
                background = rounded(if (on) SEA else CARD, 18f * d, if (on) null else Color.parseColor("#D0D0D0"), 2 * d)
                setPadding(context.dp(28), context.dp(16), context.dp(28), context.dp(16))
                setOnClickListener { onPick(option) }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = context.dp(14) })
        }
        return row
    }

    fun column(context: Context, padding: Int = 40) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(padding), context.dp(padding), context.dp(padding), context.dp(padding))
    }

    fun row(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun spacer(context: Context, height: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, context.dp(height))
    }

    /** 별 문자열: ★★☆ */
    fun stars(kept: Int, max: Int = 3) = "★".repeat(kept) + "☆".repeat((max - kept).coerceAtLeast(0))
}
