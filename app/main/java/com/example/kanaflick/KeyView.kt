package com.example.kanaflick

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

/** One key. chars = [tap, left, up, right, down]; romaji (subs[0]) is drawn under the main kana. */
class KeyView(c: Context, private val prefs: Prefs, private val th: Th) : View(c) {
    var label = ""
    var chars: List<String> = emptyList()
    var subs: List<String> = emptyList()
    var kind = 0            // 0 normal, 1 function, 2 accent
    var small = false
    var repeat = false
    var pick: (Int) -> Unit = {}

    private var down = false
    private var sel = 0
    private var sx = 0f
    private var sy = 0f
    private val sub get() = subs.firstOrNull() ?: ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val handler = Handler(Looper.getMainLooper())
    private val rep = object : Runnable { override fun run() { pick(0); handler.postDelayed(this, 55) } }

    private fun sp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v.toFloat(), resources.displayMetrics)
    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun buzz() {
        if (prefs.haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.color = when (kind) { 2 -> th.acc; 1 -> th.alt; else -> th.key }
        c.drawRoundRect(0f, 0f, w, h, dp(10f), dp(10f), paint)
        if (down) { paint.color = Color.argb(45, 128, 128, 128); c.drawRoundRect(0f, 0f, w, h, dp(10f), dp(10f), paint) }
        if (down && chars.size > 1) { drawFlick(c, w, h); return }
        tp.color = th.text
        val big = sp(prefs.kanaSp) * (if (small) 0.8f else 1f)
        tp.textSize = big
        if (sub.isNotEmpty() && prefs.showRomaji && !small) {
            val rs = sp(prefs.romajiSp); val gap = dp(1f)
            val y0 = (h - (big + gap + rs)) / 2f
            c.drawText(label, w / 2, y0 + big * 0.82f, tp)
            tp.textSize = rs; tp.color = th.sub
            c.drawText(sub, w / 2, y0 + big + gap + rs * 0.82f, tp)
        } else c.drawText(label, w / 2, h / 2 + big * 0.35f, tp)
    }

    private fun drawFlick(c: Canvas, w: Float, h: Float) {
        val big = sp(prefs.kanaSp)
        val pts = listOf(0.5f to 0.5f, 0.18f to 0.5f, 0.5f to 0.2f, 0.82f to 0.5f, 0.5f to 0.8f)
        for (i in chars.indices) {
            val (fx, fy) = pts[i]
            val s = if (i == 0) big else big * 0.8f
            if (i == sel) {
                paint.color = th.acc
                c.drawRoundRect(fx * w - s * 0.7f, fy * h - s * 0.7f, fx * w + s * 0.7f, fy * h + s * 0.7f, dp(8f), dp(8f), paint)
            }
            tp.color = th.text
            tp.textSize = s
            c.drawText(chars[i], fx * w, fy * h + s * 0.35f, tp)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = true; sel = 0; sx = e.x; sy = e.y
                parent?.requestDisallowInterceptTouchEvent(true)
                buzz(); invalidate()
                if (repeat) { pick(0); handler.postDelayed(rep, 400) }
            }
            MotionEvent.ACTION_MOVE -> if (chars.size > 1 && down) {
                val dx = e.x - sx; val dy = e.y - sy
                var s = 0
                if (hypot(dx, dy) > dp(16f)) s = if (abs(dx) > abs(dy)) (if (dx < 0) 1 else 3) else (if (dy < 0) 2 else 4)
                if (s != sel) { sel = s; if (s != 0) buzz(); invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(rep)
                val was = down; val s = sel
                down = false; invalidate()
                if (was && !repeat) pick(s)
            }
            MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(rep); down = false; invalidate() }
        }
        return true
    }
}
