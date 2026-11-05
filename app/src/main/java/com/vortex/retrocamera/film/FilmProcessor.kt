package com.vortex.retrocamera.film

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Offline image processor. This first milestone applies a CPU recipe to saved JPEG files.
 * Preview has only an indicative tint; true GPU parity is a subsequent milestone.
 */
object FilmProcessor {
    private const val MAX_LONG_EDGE = 4096

    fun readUpright(file: File): Bitmap {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        require(opts.outWidth > 0 && opts.outHeight > 0) { "Unsupported image" }
        var sample = 1
        while (max(opts.outWidth / sample, opts.outHeight / sample) > MAX_LONG_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: error("Failed decoding JPEG")
        val orientation = ExifInterface(file.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
        val transform = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> transform.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> transform.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> transform.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transform.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> transform.preScale(1f, -1f)
            else -> return decoded
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
            .also { if (it !== decoded) decoded.recycle() }
    }

    fun process(input: Bitmap, preset: FilmPreset): Bitmap {
        val width = input.width
        val height = input.height
        val pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)
        // Deterministic noise means the same preset/photo produces the same output.
        var seed = 0x4567AC19
        val cx = (width - 1) / 2f
        val cy = (height - 1) / 2f
        for (y in 0 until height) {
            val fy = if (cy > 0f) (y - cy) / cy else 0f
            for (x in 0 until width) {
                val i = y * width + x
                val p = pixels[i]
                val fx = if (cx > 0f) (x - cx) / cx else 0f
                val falloff = ((fx * fx + fy * fy) * 0.5f).coerceIn(0f, 1f)
                seed = seed xor (seed shl 13)
                seed = seed xor (seed ushr 17)
                seed = seed xor (seed shl 5)
                val noise = ((seed and 255) / 255f - 0.5f) * preset.grain
                val darkening = 1f - preset.vignette * falloff.pow(1.7f)
                val r = channel(p shr 16 and 255, preset.red, preset, noise, darkening)
                val g = channel(p shr 8 and 255, preset.green, preset, noise, darkening)
                val b = channel(p and 255, preset.blue, preset, noise, darkening)
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun channel(byte: Int, colorFactor: Float, film: FilmPreset, noise: Float, falloff: Float): Int {
        val original = byte / 255f
        var v = ((original - 0.5f) * film.contrast + 0.5f + film.lift) * colorFactor
        // Approximate shoulder / toe response, gentler than hard clipping.
        v = when {
            v < 0f -> 0f
            v > 1f -> 1f
            else -> v * v * (3f - 2f * v) * 0.18f + v * 0.82f
        }
        v = (v + noise) * falloff
        return (min(1f, max(0f, v)) * 255f + 0.5f).toInt()
    }
}
