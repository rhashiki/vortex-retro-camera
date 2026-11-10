package com.vortex.retrocamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import kotlin.math.min

/**
 * Original six-blade Vortex aperture mark painted as native vectors.
 * Inspired by an optical iris, not a borrowed brand/logo asset.
 */
class VortexApertureMark(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF180723.toInt() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = min(width, height) * .47f
        if (r <= 0f) return
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        for (blade in 0 until 6) {
            canvas.save()
            canvas.rotate(blade * 60f)
            val path = Path().apply {
                moveTo(-r * .17f, r * .06f)
                cubicTo(-r * .56f, -r * .30f, -r * .44f, -r * .83f, -r * .10f, -r)
                cubicTo(r * .28f, -r * 1.02f, r * .59f, -r * .73f, r * .46f, -r * .44f)
                cubicTo(r * .36f, -r * .22f, r * .07f, -r * .10f, -r * .17f, r * .06f)
                close()
            }
            paint.shader = LinearGradient(
                -r * .35f, -r, r * .50f, r * .08f,
                intArrayOf(0xFFE3A1FF.toInt(), 0xFF9F35F7.toInt(), 0xFF4E078C.toInt()),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawPath(path, paint)
            canvas.restore()
        }
        paint.shader = null
        canvas.drawCircle(0f, 0f, r * .21f, center)
        canvas.restore()
    }
}
