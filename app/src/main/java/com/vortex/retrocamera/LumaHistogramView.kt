package com.vortex.retrocamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Live 32-bin luminance histogram from CameraX Y-plane, not simulated RGB. */
class LumaHistogramView(context: Context) : View(context) {
    private var bins = IntArray(32)
    private var isAvailable = false
    private val chart = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFAE62FB.toInt() }
    private val baseline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF
        strokeWidth = resources.displayMetrics.density
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFDAD4E3.toInt()
        textSize = 10f * resources.displayMetrics.scaledDensity
        typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
    }

    fun showUnavailable() {
        post { isAvailable = false; invalidate() }
    }

    fun submit(frame: IntArray) {
        if (frame.size != bins.size) return
        post {
            bins = frame.copyOf()
            isAvailable = true
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawText(if (isAvailable) "LUMA" else "HIST —", 10f * d, 17f * d, label)
        val left = 9f * d
        val top = 23f * d
        val bottom = h - 9f * d
        canvas.drawLine(left, bottom, w - left, bottom, baseline)
        if (!isAvailable) return
        val maxCount = bins.maxOrNull()?.coerceAtLeast(1)?.toFloat() ?: 1f
        val step = (w - 2f * left) / bins.size
        for (i in bins.indices) {
            val height = (bottom - top) * (bins[i] / maxCount)
            val x = left + i * step
            canvas.drawRoundRect(x, bottom - height, x + step * .70f, bottom, 1.3f * d, 1.3f * d, chart)
        }
    }
}
