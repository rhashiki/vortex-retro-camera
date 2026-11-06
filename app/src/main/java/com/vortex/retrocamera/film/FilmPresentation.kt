package com.vortex.retrocamera.film

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * The presentation stage runs AFTER film color processing.
 * A single Half Frame image uses the portrait 3:4 gate; pairing two exposures
 * into a 35 mm diptych is a separate feature.
 *
 * Date stamps represent the REAL capture date, not a fictional year.
 */
object FilmPresentation {
    fun render(
        image: Bitmap,
        halfFrame: Boolean,
        dateStamp: Boolean,
        capturedAtMillis: Long = System.currentTimeMillis()
    ): Bitmap {
        val framed = if (halfFrame) cropToPortraitGate(image) else image
        if (!dateStamp) return framed
        val stamped = framed.copy(Bitmap.Config.ARGB_8888, true)
        if (framed !== image) framed.recycle()
        val canvas = Canvas(stamped)
        val base = min(stamped.width, stamped.height).toFloat()
        val typeSize = (base * 0.028f).coerceAtLeast(15f)
        val margin = (base * 0.039f).coerceAtLeast(20f)
        val date = SimpleDateFormat("yy  MM  dd", Locale.US).format(Date(capturedAtMillis))
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(130, 60, 17, 0)
            textSize = typeSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 171, 83)
            textSize = typeSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val x = stamped.width - margin - text.measureText(date)
        val y = stamped.height - margin
        canvas.drawText(date, x + typeSize * 0.035f, y + typeSize * 0.04f, shadow)
        canvas.drawText(date, x, y, text)
        return stamped
    }

    private fun cropToPortraitGate(image: Bitmap): Bitmap {
        val aspect = 3f / 4f
        val w = image.width
        val h = image.height
        val rectW: Int
        val rectH: Int
        if (w.toFloat() / h > aspect) {
            rectH = h
            rectW = (h * aspect).toInt().coerceAtLeast(1)
        } else {
            rectW = w
            rectH = (w / aspect).toInt().coerceAtLeast(1)
        }
        if (rectW == w && rectH == h) return image
        return Bitmap.createBitmap(image, (w - rectW) / 2, (h - rectH) / 2, rectW, rectH)
    }
}
