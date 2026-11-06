package com.vortex.retrocamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.min

/**
 * Viewfinder overlay that resembles an early-2000s compact film camera.
 * Safe to draw over CameraX PreviewView: no blocking touch events.
 */
class RetroViewfinderOverlay(context: Context) : View(context) {
    var halfFrame: Boolean = false
        set(value) { field = value; invalidate() }
    var warmTint: Boolean = true
        set(value) { field = value; invalidate() }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 253, 237, 205)
        strokeWidth = dp(1.2f)
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 232, 188)
        typeface = android.graphics.Typeface.MONOSPACE
        textSize = dp(9f)
        letterSpacingCompat()
    }
    private fun Paint.letterSpacingCompat() = Unit
    private fun dp(value: Float) = value * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val tintPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, w, h,
                if (warmTint) 0x16E1A96D else 0x1292C7CB,
                0x00111111,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, tintPaint)
        val vignette = Paint().apply {
            shader = RadialGradient(
                w / 2f, h / 2f, min(w, h) * 0.76f,
                intArrayOf(0x00000000, 0x0C000000, 0x75000000),
                floatArrayOf(0f, 0.60f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.save()
        canvas.scale(1f, maxOf(1f, h / w), w / 2f, h / 2f)
        canvas.drawRect(0f, 0f, w, h, vignette)
        canvas.restore()

        val frameW = if (halfFrame) min(w * 0.86f, h * 0.80f * 0.75f) else w * 0.87f
        val frameH = if (halfFrame) frameW / 0.75f else h * 0.82f
        val left = (w - frameW) / 2f
        val top = (h - frameH) / 2f
        val corner = dp(19f)
        val right = left + frameW
        val bottom = top + frameH
        // Faint corner brackets keep the viewfinder legible without a full frame.
        listOf(
            floatArrayOf(left, top + corner, left, top, left + corner, top),
            floatArrayOf(right - corner, top, right, top, right, top + corner),
            floatArrayOf(left, bottom - corner, left, bottom, left + corner, bottom),
            floatArrayOf(right - corner, bottom, right, bottom, right, bottom - corner)
        ).forEach { pts ->
            canvas.drawLines(floatArrayOf(pts[0], pts[1], pts[2], pts[3], pts[2], pts[3], pts[4], pts[5]), line)
        }
        val centerX = w / 2f
        val centerY = h / 2f
        val tick = dp(9f)
        line.alpha = 100
        canvas.drawLine(centerX - tick, centerY, centerX + tick, centerY, line)
        canvas.drawLine(centerX, centerY - tick, centerX, centerY + tick, line)
        line.alpha = 160
        canvas.drawText(if (halfFrame) "HALF FRAME  /  3:4" else "35MM  /  VIEWFINDER", left + dp(3f), bottom - dp(12f), labelPaint)
    }
}
