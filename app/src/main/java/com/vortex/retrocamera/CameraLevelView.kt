package com.vortex.retrocamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.min

/** Genuine horizon indicator based on accelerometer gravity, not a fabricated 0°. */
class CameraLevelView(context: Context) : View(context), SensorEventListener {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var angle = 0f
    private var hasReading = false
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xD7EEE5F6.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(1.7f)
        strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        textSize = dp(10f)
        color = Color.WHITE
    }
    private fun dp(size: Float) = size * resources.displayMetrics.density

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        accelerometer?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onDetachedFromWindow() {
        sensors.unregisterListener(this)
        super.onDetachedFromWindow()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        if (abs(x) + abs(y) < .2f) return
        // In portrait, a level phone with its camera aimed at the horizon
        // reports gravity along the Y-axis. Orientation is smoothed.
        var degrees = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
        if (degrees > 90f) degrees -= 180f
        if (degrees < -90f) degrees += 180f
        angle += (degrees - angle) * .15f
        hasReading = true
        postInvalidateOnAnimation()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * .46f
        canvas.drawCircle(cx, cy, radius, ring)
        line.color = if (hasReading && abs(angle) < 1.5f) 0xFF72F063.toInt()
                     else 0xFFC0B2C9.toInt()
        canvas.drawLine(cx - radius * .63f, cy, cx + radius * .63f, cy, line)
        line.color = 0xFFB14CFF.toInt()
        canvas.save()
        canvas.rotate(-angle, cx, cy)
        canvas.drawLine(cx - radius * .80f, cy, cx + radius * .80f, cy, line)
        canvas.restore()
        canvas.drawText(if (hasReading)
            String.format(Locale.getDefault(), "%.1f°", angle)
            else "—", cx, cy + radius * .65f, label)
    }
}
