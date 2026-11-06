package com.vortex.retrocamera.camera

import android.graphics.Bitmap
import com.vortex.retrocamera.film.FilmProcessor
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Real three-frame noise-reduction fallback when the OEM CameraX Night extension
 * is not exposed. Captures are independent images acquired in sequence; they are
 * registered by small translations before averaging only matching regions.
 *
 * This is deliberately not marketed as equivalent to Google's/Samsung's night
 * fusion. Movement, extreme darkness, or varying exposure may degrade results.
 */
object NightFrameFusion {
    private const val MAX_EDGE = 2048

    fun merge(files: List<File>): Bitmap {
        require(files.isNotEmpty()) { "No frames to merge" }
        val base = readScaled(files.first())
        val width = base.width
        val height = base.height
        val first = IntArray(width * height)
        base.getPixels(first, 0, width, 0, 0, width, height)
        base.recycle()

        if (files.size == 1) return Bitmap.createBitmap(first, width, height, Bitmap.Config.ARGB_8888)
        val result = first.copyOf()
        val count = ByteArray(result.size) { 1 }
        for (index in 1 until files.size) {
            val decoded = readScaled(files[index])
            val other = if (decoded.width == width && decoded.height == height) decoded else {
                Bitmap.createScaledBitmap(decoded, width, height, true).also { decoded.recycle() }
            }
            val current = IntArray(width * height)
            other.getPixels(current, 0, width, 0, 0, width, height)
            other.recycle()
            val (shiftX, shiftY) = findTranslation(first, current, width, height)
            for (y in 0 until height) {
                val oy = y + shiftY
                if (oy !in 0 until height) continue
                for (x in 0 until width) {
                    val ox = x + shiftX
                    if (ox !in 0 until width) continue
                    val target = y * width + x
                    val original = first[target]
                    val next = current[oy * width + ox]
                    // Scene motion rejection: prioritize the first exposure.
                    if (luminanceDistance(original, next) > 36) continue
                    val n = count[target].toInt()
                    val prev = result[target]
                    val r = ((((prev shr 16) and 255) * n + ((next shr 16) and 255)) / (n + 1))
                    val g = ((((prev shr 8) and 255) * n + ((next shr 8) and 255)) / (n + 1))
                    val b = (((prev and 255) * n + (next and 255)) / (n + 1))
                    result[target] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    count[target] = (n + 1).toByte()
                }
            }
        }
        return Bitmap.createBitmap(result, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun readScaled(file: File): Bitmap {
        val original = FilmProcessor.readUpright(file)
        val big = max(original.width, original.height)
        if (big <= MAX_EDGE) return original
        val scale = MAX_EDGE.toFloat() / big
        val newW = max(1, (original.width * scale).roundToInt())
        val newH = max(1, (original.height * scale).roundToInt())
        return Bitmap.createScaledBitmap(original, newW, newH, true).also {
            if (it !== original) original.recycle()
        }
    }

    private fun luminance(pixel: Int): Int =
        (((pixel shr 16 and 255) * 54 +
            (pixel shr 8 and 255) * 183 +
            (pixel and 255) * 19) shr 8)

    private fun luminanceDistance(a: Int, b: Int): Int =
        abs(luminance(a) - luminance(b))

    private fun findTranslation(
        first: IntArray,
        second: IntArray,
        width: Int,
        height: Int
    ): Pair<Int, Int> {
        if (width < 80 || height < 80) return 0 to 0
        var bestX = 0
        var bestY = 0
        var best = Double.MAX_VALUE

        fun score(dx: Int, dy: Int): Double {
            val margin = 30
            var sum = 0.0
            var hits = 0
            for (y in margin until height - margin step 27) {
                val yy = y + dy
                if (yy !in 0 until height) continue
                for (x in margin until width - margin step 27) {
                    val xx = x + dx
                    if (xx !in 0 until width) continue
                    sum += luminanceDistance(first[y * width + x], second[yy * width + xx])
                    hits++
                }
            }
            if (hits == 0) return Double.MAX_VALUE
            return sum / hits + (abs(dx) + abs(dy)) * 0.07
        }

        for (dy in -9..9 step 3) for (dx in -9..9 step 3) {
            val value = score(dx, dy)
            if (value < best) { best = value; bestX = dx; bestY = dy }
        }
        val coarseX = bestX
        val coarseY = bestY
        for (dy in (coarseY - 2)..(coarseY + 2))
            for (dx in (coarseX - 2)..(coarseX + 2)) {
                val value = score(dx, dy)
                if (value < best) { best = value; bestX = dx; bestY = dy }
            }
        return bestX to bestY
    }
}
