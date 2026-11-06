package com.vortex.retrocamera.film

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Independent controls: material texture and rare, imperfect film edge exposures.
 * A single capture seed is preserved through processing for reproducible renders.
 */
data class FilmFxSettings(
    val grainStrength: Float = 1.6f, // 0..3
    val burnChance: Float = 0.16f,   // 0..1 (only analog film)
    val seed: Int = System.nanoTime().toInt(),
    val grade: PhotoGrade = PhotoGrade()
)

data class FilmArtifactPlan(
    val burn: Boolean,
    val fromLeft: Boolean,
    val spread: Float,
    val intensity: Float
)

object FilmArtifactPlanner {
    fun plan(preset: FilmPreset, fx: FilmFxSettings): FilmArtifactPlan {
        val rng = Random(fx.seed.toLong())
        val burn = !preset.isDigitalCcd && rng.nextFloat() < fx.burnChance.coerceIn(0f, 1f)
        return FilmArtifactPlan(
            burn = burn,
            fromLeft = rng.nextBoolean(),
            spread = 0.19f + rng.nextFloat() * 0.28f,
            intensity = 0.55f + rng.nextFloat() * 0.43f
        )
    }
}

/**
 * CPU film emulation for exported photographs; the viewport applies only a GPU
 * tonal approximation. Physically calibrated film chemistry remains future work.
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

    fun process(input: Bitmap, preset: FilmPreset, fx: FilmFxSettings = FilmFxSettings()): Bitmap {
        val width = input.width
        val height = input.height
        val pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)

        val plan = FilmArtifactPlanner.plan(preset, fx)
        var noiseSeed = fx.seed xor 0x4567AC19
        val cx = (width - 1) / 2f
        val cy = (height - 1) / 2f
        val intensity = fx.grainStrength.coerceIn(0f, 3f)
        val leakWidth = plan.spread
        val grade = fx.grade.normalized()
        val graded = FloatArray(3)
        for (y in 0 until height) {
            val fy = if (cy > 0f) (y - cy) / cy else 0f
            val rowLeak = if (plan.burn) {
                // Uneven exposed edge: upper/lower variations instead of a sterile rectangle.
                val irregular = 1f - abs(fy) * 0.27f
                irregular * (1f + 0.10f * kotlin.math.sin(y / 83f))
            } else 0f
            for (x in 0 until width) {
                val i = y * width + x
                val p = pixels[i]
                val fxn = if (cx > 0f) (x - cx) / cx else 0f
                val falloff = ((fxn * fxn + fy * fy) * 0.5f).coerceIn(0f, 1f)

                val sourceR = (p shr 16 and 255) / 255f
                val sourceG = (p shr 8 and 255) / 255f
                val sourceB = (p and 255) / 255f
                val luminance = (sourceR * .2126f + sourceG * .7152f + sourceB * .0722f)
                    .coerceIn(0f, 1f)

                noiseSeed = noiseSeed xor (noiseSeed shl 13)
                noiseSeed = noiseSeed xor (noiseSeed ushr 17)
                noiseSeed = noiseSeed xor (noiseSeed shl 5)
                val noise1 = ((noiseSeed and 0xffff) / 65535f - .5f)
                noiseSeed = noiseSeed xor (noiseSeed shl 13)
                noiseSeed = noiseSeed xor (noiseSeed ushr 17)
                noiseSeed = noiseSeed xor (noiseSeed shl 5)
                val noise2 = ((noiseSeed and 0xffff) / 65535f - .5f)
                val grainFalloff = 0.42f + 1.13f * (1f - luminance) * (0.4f + luminance)
                val grain = (noise1 * 0.76f + noise2 * 0.24f) *
                    preset.grain * intensity * grainFalloff
                val darkening = 1f - preset.vignette * falloff.pow(1.7f)

                // Apply color grading before film-light leaks; the effect remains
                // part of the photograph rather than a separate UI-only filter.
                ColorGrader.apply(
                    channel(sourceR, preset.red, preset, grain, darkening),
                    channel(sourceG, preset.green, preset, grain, darkening),
                    channel(sourceB, preset.blue, preset, grain, darkening),
                    grade, graded
                )
                var r = graded[0]
                var g = graded[1]
                var b = graded[2]

                if (plan.burn) {
                    val edge = if (plan.fromLeft) x.toFloat() / width
                               else (width - x).toFloat() / width
                    if (edge < leakWidth) {
                        val position = (1f - edge / leakWidth).coerceIn(0f, 1f)
                        val strength = position * position * rowLeak * plan.intensity
                        // Warm orange/red edge exposure and a narrow pale-yellow core.
                        val hot = (strength * 1.15f).coerceIn(0f, 1f)
                        r = r + (1f - r) * hot
                        g = g + (0.72f - g) * (strength * 0.65f).coerceIn(0f, 1f)
                        b = b * (1f - strength * 0.88f).coerceAtLeast(0f)
                        if (edge < leakWidth * 0.20f) {
                            val flare = ((1f - edge / (leakWidth * 0.20f)) * .70f).coerceIn(0f, .70f)
                            r = r + (1f - r) * flare
                            g = g + (1f - g) * flare * .75f
                        }
                    }
                }
                val red = (r.coerceIn(0f, 1f) * 255f + .5f).toInt()
                val green = (g.coerceIn(0f, 1f) * 255f + .5f).toInt()
                val blue = (b.coerceIn(0f, 1f) * 255f + .5f).toInt()
                pixels[i] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun channel(
        source: Float,
        colorFactor: Float,
        film: FilmPreset,
        grain: Float,
        falloff: Float
    ): Float {
        var v = ((source - .5f) * film.contrast + .5f + film.lift) * colorFactor
        v = when {
            v < 0f -> 0f
            v > 1f -> 1f
            else -> v * v * (3f - 2f * v) * .18f + v * .82f
        }
        return (v + grain) * falloff
    }
}
