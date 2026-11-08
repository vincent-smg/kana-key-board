package com.example.kanaflick

import android.graphics.*
import android.graphics.drawable.Drawable

/** Solid colour with an optional centre-cropped image on top. */
class CropBg(private val bmp: Bitmap?, private val color: Int, alphaPct: Int) : Drawable() {
    private val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { alpha = alphaPct * 255 / 100 }
    override fun draw(c: Canvas) {
        c.drawColor(color)
        val b = bmp ?: return
        val r = bounds
        val s = maxOf(r.width().toFloat() / b.width, r.height().toFloat() / b.height)
        val sw = r.width() / s; val sh = r.height() / s
        val l = (b.width - sw) / 2; val t = (b.height - sh) / 2
        c.drawBitmap(b, Rect(l.toInt(), t.toInt(), (l + sw).toInt(), (t + sh).toInt()), r, p)
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
