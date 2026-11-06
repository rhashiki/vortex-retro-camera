package com.vortex.retrocamera.film

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * User-controlled post-capture tonal rendering, independent of film emulation.
 * Each parameter is normalized to [-1, 1]. Zero is a no-op.
 * These sliders change output pixels, not the physical exposure of the sensor.
 */
data class PhotoGrade(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f
) {
    fun normalized(): PhotoGrade = copy(
        brightness = brightness.coerceIn(-1f, 1f),
        contrast = contrast.coerceIn(-1f, 1f),
        shadows = shadows.coerceIn(-1f, 1f),
        highlights = highlights.coerceIn(-1f, 1f),
        saturation = saturation.coerceIn(-1f, 1f),
        warmth = warmth.coerceIn(-1f, 1f)
    )

    fun isNeutral(): Boolean =
        brightness == 0f && contrast == 0f && shadows == 0f &&
        highlights == 0f && saturation == 0f && warmth == 0f
}

/**
 * Reusable output vector minimizes allocations in the image processing loop.
 * Shadows and highlights use smooth, opposing luminance masks.
 */
object ColorGrader {
    fun apply(r: Float, g: Float, b: Float, grade: PhotoGrade, output: FloatArray) {
        require(output.size >= 3)
        if (grade.isNeutral()) {
            output[0] = r
            output[1] = g
            output[2] = b
            return
        }
        val luma = (r * 0.2126f + g * 0.7152f + b * 0.0722f).coerceIn(0f, 1f)
        val sh = (1f - luma).pow(2.4f)
        val hi = luma.pow(2.4f)
        // Smooth tonal masks: lifted blacks and protected highlights are separate.
        val tone = grade.brightness * 0.22f +
            grade.shadows * 0.31f * sh +
            grade.highlights * 0.31f * hi
        val contrast = 1f + grade.contrast * 0.63f
        val sat = (1f + grade.saturation * 0.87f).coerceAtLeast(0f)
        fun corrected(value: Float, warmth: Float): Float {
            val contrasted = (value - 0.5f) * contrast + 0.5f + tone
            val colorful = luma + (contrasted - luma) * sat
            return (colorful + warmth).coerceIn(0f, 1f)
        }
        output[0] = corrected(r, grade.warmth * 0.09f)
        output[1] = corrected(g, 0f)
        output[2] = corrected(b, -grade.warmth * 0.09f)
    }
}
