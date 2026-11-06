package com.vortex.retrocamera.film

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmArtifactsTest {
    private val analog = FilmCatalog.presets.first { !it.isDigitalCcd }
    private val ccd = FilmCatalog.presets.first { it.isDigitalCcd }

    @Test fun analogLeakCanBeForcedForTesting() {
        val plan = FilmArtifactPlanner.plan(analog, FilmFxSettings(burnChance = 1f, seed = 42))
        assertTrue(plan.burn)
    }

    @Test fun analogLeakCanBeDisabled() {
        val plan = FilmArtifactPlanner.plan(analog, FilmFxSettings(burnChance = 0f, seed = 42))
        assertFalse(plan.burn)
    }

    @Test fun digitalCcdNeverReceivesFilmEdgeBurns() {
        val plan = FilmArtifactPlanner.plan(ccd, FilmFxSettings(burnChance = 1f, seed = 42))
        assertFalse(plan.burn)
    }

    @Test fun fixedSeedProducesReproduciblePlan() {
        val fx = FilmFxSettings(burnChance = .5f, seed = 1987)
        assertEquals(FilmArtifactPlanner.plan(analog, fx), FilmArtifactPlanner.plan(analog, fx))
    }
}
