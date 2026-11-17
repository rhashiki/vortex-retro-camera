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
    val isDigitalCcd: Boolean = false,
    val saturation: Float = 1.0f,
    val monochrome: Boolean = false,
    val fadedBlacks: Float = 0f,
    val shadowTintR: Float = 0f,
    val shadowTintG: Float = 0f,
    val shadowTintB: Float = 0f,
    val highlightTintR: Float = 0f,
    val highlightTintG: Float = 0f,
    val highlightTintB: Float = 0f
)

object FilmCatalog {
    val presets = listOf(
        // Primary reference UI collection — seven visibly distinct FILM looks.
        FilmPreset("ORIGINAL", "Cor natural • sem emulação", 1.0f, 1f, 1f, 1f,
            0f, 0f, 0f, isDigitalCcd = true),
        FilmPreset("VIVID", "Cores vivas • contraste alto", 1.27f, 1.10f, 1.065f, 1.065f,
            -0.015f, 0.016f, 0.085f, saturation = 1.42f,
            highlightTintR = .025f, highlightTintG = .006f),
        FilmPreset("CINEMATIC", "Luzes âmbar • sombras azul petróleo", 1.24f, 1.035f, 1.00f, .95f,
            -.018f, .054f, .20f, saturation = .88f, fadedBlacks = .04f,
            shadowTintR = -.022f, shadowTintG = .033f, shadowTintB = .080f,
            highlightTintR = .090f, highlightTintG = .020f, highlightTintB = -.032f),
        FilmPreset("RETRO", "Kodachrome imaginário • cores quentes", 1.16f, 1.13f, 1.035f, .84f,
            .035f, .085f, .24f, saturation = .92f, fadedBlacks = .095f,
            highlightTintR = .042f, highlightTintG = .018f),
        FilmPreset("FILME", "Emulsão envelhecida • contraste suave", 1.02f, 1.02f, 1.02f, .96f,
            .021f, .083f, .14f, saturation = .78f, fadedBlacks = .14f,
            shadowTintB = .032f, highlightTintR = .024f),
        FilmPreset("URBAN", "Concreto frio • azuis profundos", 1.30f, .92f, 1.06f, 1.12f,
            -.027f, .046f, .23f, saturation = 1.08f,
            shadowTintR = -.040f, shadowTintG = .020f, shadowTintB = .065f,
            highlightTintR = -.012f, highlightTintB = .025f),
        FilmPreset("NOIR", "Preto e branco • grão de película", 1.33f, 1f, 1f, 1f,
            -.02f, .106f, .28f, saturation = 0f, monochrome = true,
            fadedBlacks = .045f),
        FilmPreset("KODAK GOLD 200", "Sol de verão • 2001", 1.13f, 1.09f, 1.02f, 0.89f, 0.022f, 0.045f, 0.14f),
        FilmPreset("KODAK ULTRAMAX 400", "Aniversário com flash • 2002", 1.19f, 1.08f, 1.01f, 0.93f, 0.017f, 0.07f, 0.19f),
        FilmPreset("FUJI SUPERIA 400", "Férias de família • 2003", 1.12f, 0.96f, 1.045f, 1.075f, 0.014f, 0.065f, 0.14f),
        FilmPreset("FUJICOLOR C200", "Álbum de verão • 2004", 1.085f, 0.98f, 1.04f, 1.04f, 0.03f, 0.043f, 0.10f),
        FilmPreset("KODAK COLORPLUS", "Retratos de domingo • 1999", 1.10f, 1.075f, 1.025f, 0.90f, 0.03f, 0.052f, 0.16f),
        FilmPreset("SONY CCD 2004", "Cyber-shot digital • 2004", 1.24f, 1.04f, 1.00f, 1.10f, 0.00f, 0.025f, 0.06f, isDigitalCcd = true),
        // Original Vortex street-film interpretations; NOT proprietary calibrated film stocks.
        FilmPreset("VORTEX AMBAR 200", "Ruas douradas • 2001", 1.07f, 1.11f, 1.035f, 0.93f, 0.055f, 0.045f, 0.08f),
        FilmPreset("VORTEX PORCELANA 400", "Luz de Lisboa • 2002", 0.95f, 1.04f, 1.02f, 1.00f, 0.07f, 0.060f, 0.065f),
        FilmPreset("VORTEX MEL 200", "Sol de verão • 2003", 1.04f, 1.095f, 1.05f, 0.92f, 0.075f, 0.045f, 0.08f),
        FilmPreset("VORTEX JADE 400", "Ruelas vibrantes • 2004", 1.10f, 0.98f, 1.06f, 1.02f, 0.04f, 0.07f, 0.10f)
    )
}
