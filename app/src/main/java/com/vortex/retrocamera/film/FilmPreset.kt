package com.vortex.retrocamera.film

/** Film recipes are original approximations, not calibrated profiles from manufacturers. */
data class FilmPreset(
    val name: String,
    val tagline: String,
    val contrast: Float,
    val red: Float,
    val green: Float,
    val blue: Float,
    val lift: Float,
    val grain: Float,
    val vignette: Float,
    val isDigitalCcd: Boolean = false
)

object FilmCatalog {
    val presets = listOf(
        FilmPreset("KODAK GOLD 200", "Sol de verão • 2001", 1.13f, 1.09f, 1.02f, 0.89f, 0.022f, 0.045f, 0.14f),
        FilmPreset("KODAK ULTRAMAX 400", "Aniversário com flash • 2002", 1.19f, 1.08f, 1.01f, 0.93f, 0.017f, 0.07f, 0.19f),
        FilmPreset("FUJI SUPERIA 400", "Férias de família • 2003", 1.12f, 0.96f, 1.045f, 1.075f, 0.014f, 0.065f, 0.14f),
        FilmPreset("FUJICOLOR C200", "Álbum de verão • 2004", 1.085f, 0.98f, 1.04f, 1.04f, 0.03f, 0.043f, 0.10f),
        FilmPreset("KODAK COLORPLUS", "Retratos de domingo • 1999", 1.10f, 1.075f, 1.025f, 0.90f, 0.03f, 0.052f, 0.16f),
        FilmPreset("SONY CCD 2004", "Cyber-shot digital • 2004", 1.24f, 1.04f, 1.00f, 1.10f, 0.00f, 0.025f, 0.06f, isDigitalCcd = true)
    )
}
