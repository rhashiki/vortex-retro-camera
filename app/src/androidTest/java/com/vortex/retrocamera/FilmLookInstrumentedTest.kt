package com.vortex.retrocamera

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vortex.retrocamera.film.FilmCatalog
import com.vortex.retrocamera.film.FilmFxSettings
import com.vortex.retrocamera.film.FilmProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Pixel tests with an actual Android Bitmap; validates exported results, not UI tints. */
@RunWith(AndroidJUnit4::class)
class FilmLookInstrumentedTest {
    @Test fun originalPreservesColorAndNoirIsMonochrome() {
        val source = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        for (y in 0 until 32) for (x in 0 until 32) {
            source.setPixel(x, y, 0xFF000000.toInt() or
                ((x * 7 + 15) shl 16) or
                ((y * 7 + 12) shl 8) or
                ((x * 4 + y * 3 + 10) and 255))
        }
        val fx = FilmFxSettings(grainStrength = 0f, burnChance = 0f, seed = 83)
        val natural = FilmProcessor.process(source, FilmCatalog.presets[0], fx)
        val noir = FilmProcessor.process(source, FilmCatalog.presets[6], fx)
        try {
            for (y in 0 until 32 step 5) for (x in 0 until 32 step 5) {
                assertEquals("Natural mode should not shift colors",
                    source.getPixel(x, y), natural.getPixel(x, y))
                val p = noir.getPixel(x, y)
                assertEquals("Noir red-green", p shr 16 and 255, p shr 8 and 255)
                assertEquals("Noir green-blue", p shr 8 and 255, p and 255)
            }
        } finally {
            natural.recycle(); noir.recycle(); source.recycle()
        }
    }

    @Test fun vividCinematicUrbanAndRetroProduceDistinctPixels() {
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        source.eraseColor(0xFF9A735B.toInt())
        val fx = FilmFxSettings(grainStrength = 0f, burnChance = 0f, seed = 9)
        val results = (0..6).map { idx ->
            FilmProcessor.process(source, FilmCatalog.presets[idx], fx)
        }
        try {
            val colors = results.map { it.getPixel(8, 8) }
            assertTrue("The seven visual reference presets must differ",
                colors.distinct().size >= 6)
            assertTrue("Retro must change original JPEG color",
                colors[3] != colors[0])
            assertTrue("Urban must differ from Cinematic",
                colors[5] != colors[2])
        } finally {
            results.forEach { it.recycle() }
            source.recycle()
        }
    }
}
