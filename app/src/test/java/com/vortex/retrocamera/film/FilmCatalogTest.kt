package com.vortex.retrocamera.film

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmCatalogTest {
    @Test fun filmPresetsHaveUniqueNames() {
        assertEquals(FilmCatalog.presets.size, FilmCatalog.presets.map { it.name }.distinct().size)
    }
    @Test fun filmIntensityParametersAreWithinSafeBounds() {
        FilmCatalog.presets.forEach {
            assertTrue(it.contrast in 0.5f..2f)
            assertTrue(it.grain in 0f..0.3f)
            assertTrue(it.vignette in 0f..0.8f)
        }
    }
    @Test fun digitalSensorIsNotDescribedAsFilm() {
        assertTrue(FilmCatalog.presets.any { it.isDigitalCcd && it.name.contains("CCD") })
    }
}
