package com.vortex.retrocamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.min

/**
 * Lightweight responsive camera HUD. Not a camera sensor emulator:
 * renders composition grid, focus frame and a gentle vignette.
 * Touch input passes through to CameraX PreviewView.
 */
class VortexCompositionOverlay(context: Context) : View(context) {
    private fun dp(px: Float) = px * resources.displayMetrics.density
    var halfFrame: Boolean = false
        set(value) { field = value; invalidate() }
    var warmTint: Boolean = true
        set(value) { field = value; invalidate() }

    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59EEE6FF
        strokeWidth = dp(.65f)
        style = Paint.Style.STROKE
    }
    private val reticle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xEFFFFFFF.toInt()
        strokeWidth = dp(1.7f)
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 1f || h < 1f) return
        val shadow = Paint().apply {
            shader = RadialGradient(
                w / 2f, h * .49f, maxOf(w, h) * .74f,
                intArrayOf(Color.TRANSPARENT, 0x13000000, 0x69000000),
                floatArrayOf(0f, .6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, shadow)
        // Horizon and thirds; enough opacity for bright sunlight.
        for (i in 1..2) {
            canvas.drawLine(w * i / 3f, 0f, w * i / 3f, h, grid)
            canvas.drawLine(0f, h * i / 3f, w, h * i / 3f, grid)
        }

        val edgeW = if (halfFrame) min(w * .80f, h * .73f * .75f) else min(w * .40f, dp(136f))
        val edgeH = if (halfFrame) edgeW / .75f else min(h * .18f, dp(124f))
        val x1 = (w - edgeW) / 2
        val y1 = (h - edgeH) / 2
        val x2 = x1 + edgeW
        val y2 = y1 + edgeH
        val segment = min(edgeW, edgeH) * .23f

        val corners = Path().apply {
            moveTo(x1 + segment, y1); lineTo(x1 + dp(10f), y1)
            quadTo(x1, y1, x1, y1 + dp(10f)); lineTo(x1, y1 + segment)
            moveTo(x2 - segment, y1); lineTo(x2 - dp(10f), y1)
            quadTo(x2, y1, x2, y1 + dp(10f)); lineTo(x2, y1 + segment)
            moveTo(x1, y2 - segment); lineTo(x1, y2 - dp(10f))
            quadTo(x1, y2, x1 + dp(10f), y2); lineTo(x1 + segment, y2)
            moveTo(x2 - segment, y2); lineTo(x2 - dp(10f), y2)
            quadTo(x2, y2, x2, y2 - dp(10f)); lineTo(x2, y2 - segment)
        }
        canvas.drawPath(corners, reticle)
        canvas.drawLine(w / 2f - dp(8f), h / 2f, w / 2f + dp(8f), h / 2f, cross)
        canvas.drawLine(w / 2f, h / 2f - dp(8f), w / 2f, h / 2f + dp(8f), cross)
    }
}
