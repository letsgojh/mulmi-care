package com.mulmicare.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import com.mulmicare.core.session.RoundRecord
import kotlin.math.PI
import kotlin.math.sin

/** 꾸미기 → 돛·깃발 색 (F8.3). */
object Decoration {
    fun sail(decoration: String?) = when (decoration) {
        "빨간 돛" -> Color.parseColor("#E4572E")
        "무지개 돛" -> Color.parseColor("#9B5DE5")
        "고래 돛" -> Color.parseColor("#2F6FA3")
        else -> Color.WHITE
    }

    fun flag(decoration: String?) = when (decoration) {
        "파란 깃발" -> Color.parseColor("#2F6FA3")
        "별 깃발" -> Color.parseColor("#F2B705")
        "등대 깃발" -> Color.parseColor("#E4572E")
        else -> null
    }
}

/** 종이배 그리기. */
internal fun drawBoat(canvas: Canvas, cx: Float, cy: Float, size: Float, decoration: String?, paint: Paint) {
    val hull = Path().apply {
        moveTo(cx - size * 0.5f, cy)
        lineTo(cx + size * 0.5f, cy)
        lineTo(cx + size * 0.3f, cy + size * 0.28f)
        lineTo(cx - size * 0.3f, cy + size * 0.28f)
        close()
    }
    val sail = Path().apply {
        moveTo(cx, cy - size * 0.75f)
        lineTo(cx, cy - size * 0.05f)
        lineTo(cx + size * 0.38f, cy - size * 0.05f)
        close()
    }
    paint.style = Paint.Style.FILL
    paint.color = Color.WHITE
    canvas.drawPath(hull, paint)
    paint.color = Decoration.sail(decoration)
    canvas.drawPath(sail, paint)
    Decoration.flag(decoration)?.let { color ->
        paint.color = color
        canvas.drawRect(cx - size * 0.22f, cy - size * 0.75f, cx, cy - size * 0.6f, paint)
    }
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = size * 0.04f
    paint.color = Ui.INK
    canvas.drawPath(hull, paint)
    canvas.drawPath(sail, paint)
    canvas.drawLine(cx, cy - size * 0.78f, cx, cy, paint)
}

/** F2 연습: 소리에 맞춰 기우는 종이배. */
class BoatView(context: Context) : View(context) {
    var decoration: String? = null
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun tilt(deg: Float) = animate().rotation(deg).setDuration(450).start()

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height) * 0.7f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.03f
        paint.color = Ui.SEA_LIGHT
        repeat(3) { i ->
            val y = height * 0.78f + i * size * 0.08f
            canvas.drawLine(width * 0.15f, y, width * 0.85f, y, paint)
        }
        drawBoat(canvas, width / 2f, height * 0.62f, size, decoration, paint)
    }
}

/** F8.2 항해 지도: 오늘의 길을 강처럼 그리고, 판마다 종이배와 지킨 별, 다음 굽은 길은 빈칸. */
class VoyageMapView(context: Context) : View(context) {
    var rounds: List<RoundRecord> = emptyList()
        set(value) { field = value; invalidate() }
    var decoration: String? = null
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private fun riverY(x: Float) = height * 0.55f + sin(x / width * 3 * PI).toFloat() * height * 0.16f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val river = Path().apply {
            moveTo(0f, riverY(0f))
            var x = 0f
            while (x <= w) { lineTo(x, riverY(x)); x += 8f }
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = height * 0.16f
        paint.color = Ui.SEA_LIGHT
        canvas.drawPath(river, paint)
        paint.strokeWidth = height * 0.012f
        paint.color = Ui.SEA
        canvas.drawPath(river, paint)

        text.textSize = height * 0.055f
        text.color = Ui.MUTED
        canvas.drawText("출발", w * 0.04f, riverY(w * 0.04f) - height * 0.14f, text)
        canvas.drawText("목적지", w * 0.95f, riverY(w * 0.95f) - height * 0.14f, text)

        val slots = rounds.size + 1
        val boatSize = height * 0.22f
        for (i in 0 until slots) {
            val x = w * (i + 1) / (slots + 1)
            val y = riverY(x)
            if (i < rounds.size) {
                drawBoat(canvas, x, y - boatSize * 0.05f, boatSize, decoration, paint)
                text.textSize = height * 0.07f
                text.color = Ui.STAR
                text.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText(Ui.stars(rounds[i].stars), x, y - boatSize * 0.95f, text)
                text.textSize = height * 0.05f
                text.color = Ui.INK
                text.typeface = Typeface.DEFAULT
                canvas.drawText("${i + 1}판", x, y + boatSize * 0.65f, text)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = height * 0.008f
                paint.color = Ui.MUTED
                paint.pathEffect = DashPathEffect(floatArrayOf(18f, 14f), 0f)
                canvas.drawCircle(x, y, boatSize * 0.42f, paint)
                paint.pathEffect = null
                text.textSize = height * 0.05f
                text.color = Ui.MUTED
                canvas.drawText("다음 굽은 길", x, y + boatSize * 0.75f, text)
            }
        }
    }
}

/** F9.1 판마다 흔들린 정도를 물결 높이로. 잃은 별이 많을수록 물결이 높다. */
class WaveView(context: Context) : View(context) {
    var rounds: List<RoundRecord> = emptyList()
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val mid = height * 0.42f
        text.textSize = height * 0.1f
        if (rounds.isEmpty()) {
            text.color = Ui.MUTED
            canvas.drawText("오늘은 리듬 놀이를 하지 않았어요", w / 2, mid, text)
            return
        }
        val seg = w / rounds.size
        val path = Path()
        rounds.forEachIndexed { i, r ->
            val amp = height * (0.05f + 0.1f * r.starsLost)
            var x = 0f
            while (x <= seg) {
                val px = i * seg + x
                val py = mid - sin(x / seg * 4 * PI).toFloat() * amp
                if (i == 0 && x == 0f) path.moveTo(px, py) else path.lineTo(px, py)
                x += 6f
            }
            text.color = Ui.STAR
            text.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(Ui.stars(r.stars), i * seg + seg / 2, height * 0.82f, text)
            text.color = Ui.INK
            text.typeface = Typeface.DEFAULT
            canvas.drawText("${i + 1}판", i * seg + seg / 2, height * 0.97f, text)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = height * 0.03f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Ui.SEA
        canvas.drawPath(path, paint)
    }
}
