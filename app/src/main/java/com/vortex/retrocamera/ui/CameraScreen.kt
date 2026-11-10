package com.vortex.retrocamera.ui

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vortex.retrocamera.film.FilmCatalog
import com.vortex.retrocamera.film.PhotoGrade
import kotlin.math.roundToInt

private val Violet = Color(0xFF981FFF)
private val VioletLight = Color(0xFFCE7AFF)
private val Pearl = Color(0xFFF8F4FB)
private val Muted = Color(0xFFD6C6E1)
private val BlackGlass = Color(0xE90D0718)

data class HistogramRgb(
    val red: List<Int> = emptyList(),
    val green: List<Int> = emptyList(),
    val blue: List<Int> = emptyList()
)

data class CameraUiSnapshot(
    val mode: String = "Foto",
    val night: Boolean = false,
    val flash: String = "OFF",
    val film: Int = 0,
    val zoom: Float = 1f,
    val zoomLevels: List<Float> = listOf(1f),
    val grade: PhotoGrade = PhotoGrade(),
    val manualFocus: Boolean = false,
    val manualFocusAvailable: Boolean = false,
    val manualExposure: Boolean = false,
    val manualExposureAvailable: Boolean = false,
    val iso: Int = 400,
    val shutterMs: Long = 8L,
    val busy: Boolean = false,
    val filmPreviews: Map<Int, Bitmap> = emptyMap(),
    val histogram: HistogramRgb = HistogramRgb(),
    val count: Int = 0
)

/**
 * No simulated functionality: all callbacks are backed by the existing
 * MainActivity/CameraX/FilmProcessor code, or explicitly report unsupported.
 */
data class CameraUiActions(
    val takePhoto: () -> Unit,
    val switchLens: () -> Unit,
    val openGallery: () -> Unit,
    val cycleFlash: () -> Unit,
    val toggleNight: () -> Unit,
    val chooseMode: (String) -> Unit,
    val setZoom: (Float) -> Unit,
    val selectFilm: (Int) -> Unit,
    val filtersVisible: (Boolean) -> Unit,
    val setGrade: (String, Float) -> Unit,
    val chooseFocus: (Boolean) -> Unit,
    val setFocusDistance: (Float) -> Unit,
    val proManual: (Boolean) -> Unit,
    val setIso: (Int) -> Unit,
    val setShutter: (Long) -> Unit,
    val showInfo: (String) -> Unit
)

private enum class CameraPanel { SPEC, AI, WB, FILTERS, ADJUST, FOCUS, PRO, EFFECTS }

/** Screen closely reproduces the two reference layouts. Preview never unbinds when a panel opens. */
@Composable
fun VortexCameraScreen(
    preview: PreviewView,
    gallery: ImageView,
    snapshot: CameraUiSnapshot,
    actions: CameraUiActions
) {
    var panel by rememberSaveable { mutableStateOf<CameraPanel?>(null) }
    fun toggle(next: CameraPanel) {
        val target = if (panel == next) null else next
        if (panel == CameraPanel.FILTERS || target == CameraPanel.FILTERS)
            actions.filtersVisible(target == CameraPanel.FILTERS)
        panel = target
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        val compact = maxHeight < 730.dp
        val screenHeight = maxHeight
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // Same CameraX PreviewView instance, never a screenshot or a WebView.
        AndroidView(
            factory = { preview },
            modifier = Modifier.fillMaxSize().testTag("camera_preview")
        )
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color(0xCD05020C),
                .23f to Color.Transparent,
                .67f to Color.Transparent,
                1f to Color(0xF7070410)
            )
        ))
        CameraGrid(Modifier.fillMaxSize())
        CameraReticle(Modifier.align(Alignment.Center).size(110.dp))

        if (landscape) {
            // Small-height devices keep controls scrollable in a side rail.
            Column(
                Modifier.align(Alignment.CenterEnd).width(300.dp).fillMaxHeight()
                    .background(Color(0xF40B0613))
                    .padding(top = topInset, bottom = bottomInset)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Branding(compact = true, actions = actions, onSpec = { toggle(CameraPanel.SPEC) },
                    onAi = { toggle(CameraPanel.AI) })
                if (panel != null) {
                    PanelSurface(Modifier.fillMaxWidth()) {
                        PanelContents(panel!!, snapshot, actions) { toggle(CameraPanel.FILTERS) }
                    }
                }
                FloatingToolbar(snapshot, onChoose = { toggle(it) }, actions = actions)
                ShutterDock(gallery, snapshot.busy, actions, compact = true)
                ModeNavigation(snapshot, actions, compact = true, onRetro = { toggle(CameraPanel.FILTERS) })
            }
            ZoomRail(snapshot, actions, Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
        } else {
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = topInset + 4.dp)
            ) {
                Branding(compact = compact, actions = actions,
                    onSpec = { toggle(CameraPanel.SPEC) },
                    onAi = { toggle(CameraPanel.AI) })
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Chip("AUTO MP  |  4:3  |  JPEG  ⌄", selected = panel == CameraPanel.SPEC,
                        modifier = Modifier.testTag("capture_spec")) { toggle(CameraPanel.SPEC) }
                    RgbHistogram(snapshot.histogram)
                }
            }

            ZoomRail(snapshot, actions, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))

            // Compact panels in the reference float near top left/right, not in the shutter dock.
            AnimatedVisibility(
                visible = panel == CameraPanel.SPEC || panel == CameraPanel.AI,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = topInset + 124.dp),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                PanelSurface(Modifier.fillMaxWidth().heightIn(max = 295.dp)) {
                    if (panel != null) PanelContents(panel!!, snapshot, actions) {
                        toggle(CameraPanel.FILTERS)
                    }
                }
            }

            // The reference's floating toolbar, shutter row and bottom nav
            // remain pinned. Panels expand upward over the live view.
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset + 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = panel != null && panel != CameraPanel.SPEC && panel != CameraPanel.AI,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                        .testTag("expanded_panel"),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val maxPanel = if (compact) screenHeight * .34f else screenHeight * .49f
                    PanelSurface(Modifier.fillMaxWidth().heightIn(max = maxPanel)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            if (panel != null) PanelContents(panel!!, snapshot, actions) {
                                toggle(CameraPanel.FILTERS)
                            }
                        }
                    }
                }
                FloatingToolbar(snapshot, onChoose = { toggle(it) }, actions = actions)
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                ShutterDock(gallery, snapshot.busy, actions, compact = compact)
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                ModeNavigation(snapshot, actions, compact = compact,
                    onRetro = { toggle(CameraPanel.FILTERS) })
            }
        }
    }
}

private fun Modifier.glass(radius: Dp = 20.dp, active: Boolean = false): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.shadow(if (active) 10.dp else 3.dp, shape, ambientColor = Violet)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(if (active) Color(0xE747096F) else Color(0xE724142F), BlackGlass)
            )
        )
        .border(
            if (active) 1.4.dp else .7.dp,
            if (active) VioletLight else Color(0x995B3D70), shape
        )
}

@Composable private fun PanelSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.glass(18.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable private fun Branding(
    compact: Boolean,
    actions: CameraUiActions,
    onSpec: () -> Unit,
    onAi: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(if (compact) 44.dp else 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        VortexMark(Modifier.size(37.dp))
        Text(
            "Vortex Camera", color = Pearl, fontWeight = FontWeight.Bold,
            fontSize = if (compact) 15.sp else 18.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Box(Modifier.glass(8.dp, active = true).clickable {
            actions.chooseMode("Pro")
        }.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text("Pro", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        if (!compact) Spacer(Modifier.width(5.dp))
        TopAction("HDR") { actions.showInfo("HDR ainda não possui processamento implementado.") }
        TopAction("✦AI", onAi)
        TopAction("⚙") { actions.showInfo("Configurações avançadas disponíveis nas barras inferiores.") }
    }
}

@Composable private fun VortexMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension * .40f
        repeat(6) { i ->
            rotate(i * 60f, center) {
                val p = Path().apply {
                    moveTo(center.x, center.y)
                    cubicTo(center.x - radius * .8f, center.y - radius * .15f,
                        center.x - radius * .7f, center.y - radius,
                        center.x + radius * .2f, center.y - radius)
                    cubicTo(center.x + radius * .7f, center.y - radius * .8f,
                        center.x + radius * .65f, center.y - radius * .2f,
                        center.x, center.y)
                    close()
                }
                drawPath(p, Brush.linearGradient(listOf(VioletLight, Violet, Color(0xFF3F0784))))
            }
        }
        drawCircle(Color(0xFF17051E), radius * .18f)
    }
}

@Composable private fun TopAction(title: String, onClick: () -> Unit) {
    Box(
        Modifier.size(width = if (title == "HDR") 47.dp else 44.dp, height = 39.dp)
            .glass(13.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = Pearl, fontSize = if (title == "HDR") 12.sp else 17.sp,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun Chip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier.glass(18.dp, selected).clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Pearl, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            maxLines = 1)
    }
}

@Composable private fun RgbHistogram(rgb: HistogramRgb) {
    Box(
        Modifier.size(123.dp, 64.dp).glass(13.dp).testTag("rgb_histogram")
            .padding(7.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val triples = listOf(
                rgb.red to Color(0x99FF4444),
                rgb.green to Color(0x990AFF77),
                rgb.blue to Color(0xAA3D72FF)
            )
            triples.forEach { (hist, color) ->
                if (hist.isEmpty()) return@forEach
                val maximum = hist.maxOrNull()?.coerceAtLeast(1)?.toFloat() ?: 1f
                val step = size.width / hist.size
                val p = Path().apply {
                    moveTo(0f, size.height)
                    hist.forEachIndexed { i, value ->
                        lineTo(i * step, size.height * (1f - value / maximum))
                    }
                    lineTo(size.width, size.height); close()
                }
                drawPath(p, color)
            }
        }
        if (rgb.red.isEmpty()) Text("RGB", Modifier.align(Alignment.Center),
            fontSize = 10.sp, color = Muted)
    }
}

@Composable private fun CameraGrid(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val line = Color(0x61FFFFFF)
        for (n in 1..2) {
            drawLine(line, Offset(size.width * n / 3f, 0f),
                Offset(size.width * n / 3f, size.height), .8.dp.toPx())
            drawLine(line, Offset(0f, size.height * n / 3f),
                Offset(size.width, size.height * n / 3f), .8.dp.toPx())
        }
    }
}

@Composable private fun CameraReticle(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val line = Color(0xEAFFFFFF)
        val w = size.width; val h = size.height; val gap = w * .25f
        val arm = w * .13f
        val inset = w * .09f
        val c = Offset(w / 2f, h / 2f)
        drawLine(line, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), 2.dp.toPx())
        drawLine(line, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), 2.dp.toPx())
        val p = Path().apply {
            moveTo(inset + arm, inset); lineTo(inset, inset); lineTo(inset, inset + arm)
            moveTo(w - inset - arm, inset); lineTo(w - inset, inset); lineTo(w - inset, inset + arm)
            moveTo(inset + arm, h - inset); lineTo(inset, h - inset); lineTo(inset, h - inset - arm)
            moveTo(w - inset - arm, h - inset); lineTo(w - inset, h - inset); lineTo(w - inset, h - inset - arm)
        }
        drawPath(p, line, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
        drawLine(line, Offset(w * .91f, gap), Offset(w * .91f, h - gap), 1.6.dp.toPx())
        drawCircle(line, 6.dp.toPx(), Offset(w * .91f, c.y))
    }
}

@Composable private fun ZoomRail(snapshot: CameraUiSnapshot, actions: CameraUiActions, modifier: Modifier) {
    if (snapshot.zoomLevels.size <= 1) return
    Column(modifier.glass(25.dp).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        snapshot.zoomLevels.reversed().forEach { level ->
            val active = kotlin.math.abs(level - snapshot.zoom) < .05f
            Box(Modifier.size(43.dp).clip(CircleShape)
                .background(if (active) Violet else Color.Transparent)
                .clickable { actions.setZoom(level) }, contentAlignment = Alignment.Center) {
                Text(if (level % 1f == 0f) "${level.toInt()}×" else "${level}×",
                    color = Pearl, fontSize = 12.sp)
            }
        }
    }
}

@Composable private fun FloatingToolbar(
    snapshot: CameraUiSnapshot,
    onChoose: (CameraPanel) -> Unit,
    actions: CameraUiActions
) {
    Row(
        Modifier.fillMaxWidth(.91f).height(55.dp).glass(26.dp)
            .testTag("floating_controls"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Tool("ϟ", if (snapshot.flash == "OFF") "Flash" else snapshot.flash, {
            actions.cycleFlash()
        })
        Tool("◎", "Foco", { onChoose(CameraPanel.FOCUS) })
        Tool("WB", "WB", { onChoose(CameraPanel.WB) }, selected = false)
        Tool("▣", "Medição", {
            actions.showInfo("Toque no visor para medição e foco CameraX.")
        })
        Tool("☷", "Ajustes", { onChoose(CameraPanel.ADJUST) })
    }
}

@Composable private fun Tool(symbol: String, label: String, onClick: () -> Unit, selected: Boolean = false) {
    Column(Modifier.widthIn(min = 51.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(symbol, fontSize = if (symbol == "WB") 15.sp else 23.sp,
            color = if (selected) VioletLight else Pearl)
        Text(label, fontSize = 10.sp, color = Pearl, maxLines = 1)
    }
}

@Composable private fun ShutterDock(gallery: ImageView, busy: Boolean, actions: CameraUiActions, compact: Boolean) {
    Row(Modifier.fillMaxWidth().height(if (compact) 86.dp else 106.dp)
        .padding(horizontal = 14.dp).testTag("shutter_dock"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier.size(if (compact) 52.dp else 64.dp)
            .glass(15.dp).clickable { actions.openGallery() }, contentAlignment = Alignment.Center) {
            AndroidView(factory = { gallery }, modifier = Modifier.fillMaxSize().padding(3.dp))
        }
        RoundAction("↺", actions.switchLens, 45.dp)
        Box(Modifier.size(if (compact) 75.dp else 94.dp)
            .shadow(12.dp, CircleShape, ambientColor = Violet, spotColor = VioletLight)
            .border(5.dp, Violet, CircleShape)
            .padding(8.dp).clip(CircleShape).background(Pearl)
            .clickable(enabled = !busy) { actions.takePhoto() }
            .testTag("shutter_button"))
        RoundAction("▣", {
            actions.showInfo("O motor de gravação de vídeo ainda não foi implementado.")
        }, 45.dp)
        RoundAction("◉", { actions.showInfo("Filtros disponíveis pelo modo Retrô.") }, 45.dp)
    }
}

@Composable private fun RoundAction(text: String, onClick: () -> Unit, size: Dp) {
    Box(Modifier.size(size).glass(size / 2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(text, fontSize = 23.sp, color = Pearl)
    }
}

@Composable private fun ModeNavigation(
    snapshot: CameraUiSnapshot,
    actions: CameraUiActions,
    compact: Boolean,
    onRetro: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(.96f).height(if (compact) 62.dp else 70.dp)
            .glass(27.dp).padding(3.dp).testTag("mode_navigation"),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
    ) {
        val entries = listOf("Retrô" to "▣", "Vídeo" to "▷", "Foto" to "▣",
            "Pro" to "☷", "Noite" to "☾")
        entries.forEach { (name, icon) ->
            val selected = if (name == "Noite") snapshot.night
                else name == snapshot.mode && !snapshot.night
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .glass(22.dp, selected)
                    .testTag("mode_" + name.lowercase())
                    .semantics {
                        if (name == "Retrô") contentDescription = "Abrir filtros retrô"
                    }
                    .clickable {
                        when (name) {
                            "Retrô" -> onRetro()
                            else -> actions.chooseMode(name)
                        }
                    }.padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(icon, color = Pearl, fontSize = 22.sp, lineHeight = 22.sp)
                Text(name, color = Pearl, fontSize = 11.sp, lineHeight = 12.sp)
            }
        }
    }
}

@Composable private fun PanelContents(
    panel: CameraPanel,
    snapshot: CameraUiSnapshot,
    actions: CameraUiActions,
    closeFilters: () -> Unit
) {
    when (panel) {
        CameraPanel.SPEC -> {
            PanelHeader("Resolução · proporção · formato")
            Text("4:3  |  JPEG — formato de captura atual", color = Muted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Chip("4:3", true) {}
                Chip("16:9", false) { actions.showInfo("Recorte 16:9 ainda não disponível.") }
                Chip("1:1", false) { actions.showInfo("Recorte 1:1 ainda não disponível.") }
                Chip("RAW", false) { actions.showInfo("Exportação RAW ainda não implementada.") }
            }
            Text("A resolução em MP depende da lente e do sensor.", color = Muted, fontSize = 10.sp)
        }
        CameraPanel.AI -> {
            PanelHeader("Vortex AI • offline")
            Text("Melhoria, remoção de objetos e céu: ainda não integrados ao APK.",
                color = Muted, fontSize = 12.sp)
            Text("Nenhuma imagem é enviada a serviços externos.", color = VioletLight, fontSize = 11.sp)
        }
        CameraPanel.WB -> {
            PanelHeader("Balanço de Branco • ajuste tonal")
            Text("Ajuste digital de temperatura aplicado à foto salva e à prévia.",
                color = Muted, fontSize = 11.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Auto" to 0f, "Sol" to .15f, "Nublado" to .3f,
                    "Sombra" to .5f, "Tungstênio" to -.50f, "Frio" to -.3f
                ).forEach { (name, warmth) ->
                    Chip(name, kotlin.math.abs(snapshot.grade.warmth - warmth) < .04f) {
                        actions.setGrade("warmth", warmth)
                    }
                }
            }
            Text("Temperatura criativa", color = Pearl, fontSize = 11.sp)
            PurpleSlider(snapshot.grade.warmth, -1f..1f) {
                actions.setGrade("warmth", it)
            }
        }
        CameraPanel.FILTERS -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Filtros", Modifier.weight(1f), color = Pearl,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Chip("Fechar ⌃", false, onClick = closeFilters)
            }
            LazyRow(Modifier.fillMaxWidth().testTag("filter_panel"),
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                itemsIndexed(FilmCatalog.presets) { index, preset ->
                    val selected = index == snapshot.film
                    Column(
                        Modifier.width(91.dp).glass(12.dp, selected).clickable {
                            actions.selectFilm(index)
                        }.padding(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val bmp = snapshot.filmPreviews[index]
                        Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(
                                listOf(Color(0xFF4F285E), Color(0xFFBE7367), Color(0xFF0C0D19))
                            )), contentAlignment = Alignment.Center) {
                            if (bmp != null && !bmp.isRecycled) {
                                Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop)
                            } else Text("◉", color = VioletLight, fontSize = 22.sp)
                        }
                        Text(preset.name.replace("KODAK ", "Kodak ")
                            .replace("FUJI ", "Fuji ").replace("VORTEX ", ""),
                            fontSize = 9.sp, color = Pearl, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Chip("Efeitos", false) { actions.showInfo("Grão e queima no laboratório de efeitos.") }
                Chip("Cores", false) { actions.showInfo("Cor disponível em Ajustes e WB.") }
                Chip("LUTs", false) { actions.showInfo("Importação de LUT ainda não disponível.") }
            }
        }
        CameraPanel.ADJUST -> {
            PanelHeader("Ajustes")
            listOf(
                Triple("Brilho", "brightness", snapshot.grade.brightness),
                Triple("Contraste", "contrast", snapshot.grade.contrast),
                Triple("Saturação", "saturation", snapshot.grade.saturation),
                Triple("Sombras", "shadows", snapshot.grade.shadows),
                Triple("Realces", "highlights", snapshot.grade.highlights),
                Triple("Temperatura", "warmth", snapshot.grade.warmth)
            ).forEach { (name, key, value) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.width(94.dp), color = Pearl, fontSize = 12.sp)
                    PurpleSlider(value, -1f..1f, Modifier.weight(1f)) {
                        actions.setGrade(key, it)
                    }
                    Text("${(value * 100).roundToInt()}", color = Muted,
                        fontSize = 10.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                }
            }
        }
        CameraPanel.FOCUS -> {
            PanelHeader("Foco")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("AF", !snapshot.manualFocus) { actions.chooseFocus(false) }
                Chip("MF", snapshot.manualFocus) {
                    if (snapshot.manualFocusAvailable) actions.chooseFocus(true)
                    else actions.showInfo("Esta lente não suporta foco manual.")
                }
            }
            if (snapshot.manualFocus) {
                Text("Distância óptica", color = Muted, fontSize = 11.sp)
                PurpleSlider(.5f, 0f..1f) { actions.setFocusDistance(it) }
            }
            Text("Toque no visor para foco e medição automáticos.", color = Muted, fontSize = 11.sp)
        }
        CameraPanel.PRO -> {
            PanelHeader("Controle Pro")
            Text(if (snapshot.manualExposure) "Exposição física manual" else "AE automática",
                color = Muted, fontSize = 12.sp)
            Chip(if (snapshot.manualExposure) "Voltar ao Auto" else "Ativar ISO/obturador",
                snapshot.manualExposure) {
                if (snapshot.manualExposureAvailable)
                    actions.proManual(!snapshot.manualExposure)
                else actions.showInfo("Sensor sem controle manual de ISO/obturador.")
            }
            if (snapshot.manualExposure && snapshot.manualExposureAvailable) {
                Text("ISO ${snapshot.iso}", color = Pearl, fontSize = 12.sp)
                PurpleSlider(snapshot.iso.toFloat(), 100f..1600f) {
                    actions.setIso((it / 100).roundToInt() * 100)
                }
                Text("Obturador • ${snapshot.shutterMs} ms", color = Pearl, fontSize = 12.sp)
                PurpleSlider(snapshot.shutterMs.toFloat(), 1f..500f) {
                    actions.setShutter(it.toLong())
                }
            }
        }
        CameraPanel.EFFECTS -> {
            PanelHeader("Efeitos de película")
            Text("Granulação e vazamento de luz são aplicados ao JPEG pela engine atual.",
                color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable private fun PanelHeader(text: String) {
    Text(text, color = Pearl, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable private fun PurpleSlider(
    value: Float, range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier, changed: (Float) -> Unit
) {
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = changed,
        valueRange = range, modifier = modifier.height(35.dp),
        colors = SliderDefaults.colors(
            thumbColor = VioletLight, activeTrackColor = Violet,
            inactiveTrackColor = Color(0xFF42354C)
        )
    )
}
