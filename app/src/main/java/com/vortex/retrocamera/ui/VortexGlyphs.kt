package com.vortex.retrocamera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight vector outlines. Drawn natively with Compose Canvas to match the
 * reference camera/video/film/sliders icons without icon-font glyph fallbacks.
 */
@Composable
fun VortexGlyph(
    type: String,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF5EEFC)
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.75.dp.toPx()
        fun point(x: Float, y: Float) = Offset(x * w, y * h)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, point(x1, y1), point(x2, y2), stroke, StrokeCap.Round)
        when (type) {
            "Foto", "camera", "Captura" -> {
                val frame = Stroke(stroke)
                drawRoundRect(color, point(.13f, .27f), Size(w * .74f, h * .56f),
                    CornerRadius(w * .075f), style = frame)
                val top = Path().apply {
                    moveTo(w * .30f, h * .27f)
                    lineTo(w * .35f, h * .16f)
                    lineTo(w * .59f, h * .16f)
                    lineTo(w * .65f, h * .27f)
                }
                drawPath(top, color, style = Stroke(stroke, cap = StrokeCap.Round))
                drawCircle(color, w * .16f, point(.50f, .54f), style = Stroke(stroke))
                drawCircle(color, w * .028f, point(.77f, .38f))
            }
            "Vídeo", "video" -> {
                drawRoundRect(color, point(.10f, .28f), Size(w * .53f, h * .47f),
                    CornerRadius(w * .07f), style = Stroke(stroke))
                val lens = Path().apply {
                    moveTo(w * .67f, h * .42f)
                    lineTo(w * .91f, h * .28f)
                    lineTo(w * .91f, h * .75f)
                    lineTo(w * .67f, h * .61f)
                    close()
                }
                drawPath(lens, color, style = Stroke(stroke))
            }
            "Retrô", "Filtros" -> {
                drawCircle(color, w * .19f, point(.37f, .42f), style = Stroke(stroke))
                drawCircle(color, w * .19f, point(.62f, .42f), style = Stroke(stroke))
                drawCircle(color, w * .19f, point(.50f, .65f), style = Stroke(stroke))
            }
            "Pro", "Ajustes" -> {
                for (i in 0..2) {
                    val y = .26f + i * .24f
                    line(.12f, y, .88f, y)
                    val x = when (i) { 0 -> .36f; 1 -> .66f; else -> .44f }
                    drawCircle(color, w * .10f, point(x, y), style = Stroke(stroke))
                    drawCircle(Color(0xFF130A1A), w * .058f, point(x, y))
                }
            }
            "Noite" -> {
                val moon = Path().apply {
                    moveTo(w * .65f, h * .09f)
                    cubicTo(w * .36f, h * .06f, w * .13f, h * .30f, w * .18f, h * .60f)
                    cubicTo(w * .24f, h * .86f, w * .53f, h * .95f, w * .76f, h * .77f)
                    cubicTo(w * .48f, h * .78f, w * .31f, h * .45f, w * .65f, h * .09f)
                    close()
                }
                drawPath(moon, color)
            }
            "Flash" -> {
                val bolt = Path().apply {
                    moveTo(w * .56f, h * .06f)
                    lineTo(w * .27f, h * .52f)
                    lineTo(w * .46f, h * .52f)
                    lineTo(w * .39f, h * .94f)
                    lineTo(w * .76f, h * .40f)
                    lineTo(w * .56f, h * .40f)
                    close()
                }
                drawPath(bolt, color)
            }
            "Foco" -> {
                drawCircle(color, w * .30f, point(.5f, .5f), style = Stroke(stroke))
                drawCircle(color, w * .08f, point(.5f, .5f))
                line(.5f, .07f, .5f, .19f)
                line(.5f, .81f, .5f, .93f)
                line(.07f, .5f, .19f, .5f)
                line(.81f, .5f, .93f, .5f)
            }
            "Medição" -> {
                line(.08f,.31f,.08f,.13f);line(.08f,.13f,.27f,.13f)
                line(.73f,.13f,.92f,.13f);line(.92f,.13f,.92f,.31f)
                line(.08f,.69f,.08f,.87f);line(.08f,.87f,.27f,.87f)
                line(.73f,.87f,.92f,.87f);line(.92f,.69f,.92f,.87f)
                drawCircle(color, w * .10f, point(.5f,.5f), style=Stroke(stroke))
            }
            else -> {
                drawCircle(color, w * .30f, point(.5f,.5f), style=Stroke(stroke))
            }
        }
    }
}
