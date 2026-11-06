package com.vortex.retrocamera.film

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoGradeTest {
    private fun grade(r: Float, g: Float, b: Float, settings: PhotoGrade): FloatArray =
        FloatArray(3).also { ColorGrader.apply(r, g, b, settings, it) }

    @Test fun neutralIsIdentity() {
        val result = grade(.22f, .49f, .75f, PhotoGrade())
        assertEquals(.22f, result[0], .0001f)
        assertEquals(.49f, result[1], .0001f)
        assertEquals(.75f, result[2], .0001f)
    }

    @Test fun brightnessRaisesMidtones() {
        val result = grade(.3f, .3f, .3f, PhotoGrade(brightness = .7f))
        assertTrue(result[0] > .3f)
    }

    @Test fun liftingShadowsIsStrongerInTheDark() {
        val options = PhotoGrade(shadows = .6f)
        val dark = grade(.15f, .15f, .15f, options)[0] - .15f
        val bright = grade(.85f, .85f, .85f, options)[0] - .85f
        assertTrue(dark > bright)
    }

    @Test fun warmthShiftsRedAboveBlue() {
        val result = grade(.5f, .5f, .5f, PhotoGrade(warmth = .7f))
        assertTrue(result[0] > result[2])
    }

    @Test fun outputStaysInPixelBounds() {
        val result = grade(.95f, .10f, .25f, PhotoGrade(
            brightness = 1f, contrast = 1f, shadows = 1f,
            highlights = 1f, saturation = 1f, warmth = 1f
        ))
        assertTrue(result.all { it in 0f..1f })
    }
}
