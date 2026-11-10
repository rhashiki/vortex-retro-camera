package com.vortex.retrocamera

import android.Manifest
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.content.res.Configuration
import android.view.MotionEvent
import android.util.Size
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.vortex.retrocamera.camera.ManualCamera
import com.vortex.retrocamera.camera.NightFrameFusion
import com.vortex.retrocamera.audio.MechanicalShutter
import com.vortex.retrocamera.film.FilmCatalog
import com.vortex.retrocamera.film.FilmPreset
import com.vortex.retrocamera.film.FilmProcessor
import com.vortex.retrocamera.film.FilmFxSettings
import com.vortex.retrocamera.film.FilmPresentation
import com.vortex.retrocamera.film.PhotoGrade
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Vortex Retro Camera v0.1: native CameraX capture + offline film JPEG processing.
 * Pano/video are intentionally not active before a film-aware pipeline exists.
 */
class MainActivity : ComponentActivity() {
    private enum class Mode { FOTO, PANORAMA, PRO, VIDEO }
    private enum class Flash { OFF, AUTO, ON }

    private val bg = Color.rgb(11, 13, 18)
    private val accent = Color.rgb(230, 199, 160)
    private val muted = Color.rgb(156, 156, 164)
    private val io = Executors.newSingleThreadExecutor()
    private val shutterSound = MechanicalShutter()
    private val handler = Handler(Looper.getMainLooper())
    private var selectedPreset = 0
    private var halfFrame = false
    private var dateStamp = false
    private var manualFocus = false
    private var focusPercent = 0
    private var grainStrength = 1.6f
    private var burnChance = 0.16f
    private var soundEnabled = true
    private var photoGrade = PhotoGrade()
    private var flashSetting = Flash.OFF
    private var nightMode = false
    private var nightUsingOem = false
    private var cameraGeneration = 0
    private var mode = Mode.FOTO
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var timerSeconds = 0
    private var saving = false
    private var capture: ImageCapture? = null
    private var activeCamera: Camera? = null
    private var manual: ManualCamera? = null
    private var iso = 400
    private var exposureNs = 8_000_000L // 1/125s
    private var proManualExposure = false // PRO starts on camera auto-exposure

    private lateinit var previewView: PreviewView
    private lateinit var previewTint: View
    private lateinit var frameGuide: VortexCompositionOverlay
    private lateinit var histogramView: LumaHistogramView
    private lateinit var zoomStrip: LinearLayout
    private lateinit var filmChooserButton: Button
    private var zoomRatio = 1f
    private val histogramExecutor = Executors.newSingleThreadExecutor()
    private lateinit var focusMarker: View
    private lateinit var viewfinderFrame: FrameLayout
    private lateinit var focusControlButton: Button
    private lateinit var nightButton: Button
    private lateinit var flashButton: Button
    private lateinit var galleryThumb: ImageView
    private lateinit var exposureDisplay: TextView
    private lateinit var filmTagline: TextView
    private lateinit var filmLabel: TextView
    private lateinit var hintLabel: TextView
    private lateinit var saveLabel: TextView
    private lateinit var shutterButton: Button
    private lateinit var proPanel: LinearLayout
    private lateinit var modeStrip: LinearLayout
    private lateinit var presetStrip: LinearLayout
    private lateinit var filmScrollView: HorizontalScrollView

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else toast("A câmera precisa de permissão para funcionar.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        savedInstanceState?.let { state ->
            selectedPreset = state.getInt("selected_preset", 0).coerceIn(0, FilmCatalog.presets.lastIndex)
            halfFrame = state.getBoolean("half_frame")
            dateStamp = state.getBoolean("date_stamp")
            manualFocus = state.getBoolean("manual_focus")
            focusPercent = state.getInt("focus_percent", 0)
            grainStrength = state.getFloat("grain", 1.6f)
            burnChance = state.getFloat("burn", .16f)
            nightMode = state.getBoolean("night_mode")
            timerSeconds = state.getInt("timer", 0)
            flashSetting = Flash.entries.getOrElse(state.getInt("flash", 0)) { Flash.OFF }
            mode = Mode.entries.getOrElse(state.getInt("mode", 0)) { Mode.FOTO }
            zoomRatio = state.getFloat("zoom_ratio", 1f)
            proManualExposure = state.getBoolean("pro_manual_exposure", false)
            if (nightMode && mode == Mode.PRO) mode = Mode.FOTO
            photoGrade = PhotoGrade(
                brightness = state.getFloat("brightness"),
                contrast = state.getFloat("contrast"),
                shadows = state.getFloat("shadows"),
                highlights = state.getFloat("highlights"),
                saturation = state.getFloat("saturation"),
                warmth = state.getFloat("warmth")
            )
        }
        buildInterface()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    /**
     * Premium UI v2: camera-first, edge-to-edge and adaptive by orientation.
     *
     * Essential controls are pinned to a top rail (or right rail in landscape)
     * and cannot be pushed off-screen by Pro controls, film chips or panels.
     * Secondary settings live in scrollable bottom sheets.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected_preset", selectedPreset)
        outState.putBoolean("half_frame", halfFrame)
        outState.putBoolean("date_stamp", dateStamp)
        outState.putBoolean("manual_focus", manualFocus)
        outState.putInt("focus_percent", focusPercent)
        outState.putFloat("grain", grainStrength)
        outState.putFloat("burn", burnChance)
        outState.putBoolean("night_mode", nightMode)
        outState.putInt("timer", timerSeconds)
        outState.putInt("flash", flashSetting.ordinal)
        outState.putInt("mode", mode.ordinal)
        outState.putFloat("zoom_ratio", zoomRatio)
        outState.putBoolean("pro_manual_exposure", proManualExposure)
        outState.putFloat("brightness", photoGrade.brightness)
        outState.putFloat("contrast", photoGrade.contrast)
        outState.putFloat("shadows", photoGrade.shadows)
        outState.putFloat("highlights", photoGrade.highlights)
        outState.putFloat("saturation", photoGrade.saturation)
        outState.putFloat("warmth", photoGrade.warmth)
        super.onSaveInstanceState(outState)
    }

    // Camera-first Android native surface based on supplied visual reference.
    // It is built from actual views and CameraX, not a static screenshot or PWA.
    private fun buildInterface() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF08060E.toInt())
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        viewfinderFrame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(viewfinderFrame, FrameLayout.LayoutParams(-1, -1))
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Color.BLACK)
            isClickable = true
            setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    view.performClick()
                    focusAt(event.x, event.y)
                }
                true
            }
        }
        viewfinderFrame.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        previewTint = View(this).apply { isClickable = false }
        viewfinderFrame.addView(previewTint, FrameLayout.LayoutParams(-1, -1))
        frameGuide = VortexCompositionOverlay(this).apply {
            halfFrame = this@MainActivity.halfFrame
            isClickable = false
        }
        viewfinderFrame.addView(frameGuide, FrameLayout.LayoutParams(-1, -1))
        focusMarker = View(this).apply {
            alpha = 0f
            isClickable = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), 0xFFCE84FF.toInt())
            }
        }
        viewfinderFrame.addView(focusMarker, FrameLayout.LayoutParams(dp(68), dp(68)))

        // Glass top and bottom gradients keep controls legible in sunny scenes.
        viewfinderFrame.addView(View(this).apply {
            isClickable = false
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
                0xE2080612.toInt(), 0x97080612.toInt(), Color.TRANSPARENT
            ))
        }, FrameLayout.LayoutParams(-1, dp(188), Gravity.TOP))
        viewfinderFrame.addView(View(this).apply {
            isClickable = false
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(
                0xF0070610.toInt(), 0xC0070610.toInt(), Color.TRANSPARENT
            ))
        }, FrameLayout.LayoutParams(-1, dp(340), Gravity.BOTTOM))

        // Real zoom controls are populated only from CameraX-supported range.
        zoomStrip = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(5), dp(3), dp(5))
            background = neonPanel(0xB10E0A18.toInt(), 22, 0x8067518A.toInt())
        }
        viewfinderFrame.addView(zoomStrip, FrameLayout.LayoutParams(dp(56), -2, Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply {
            setMargins(0, 0, dp(if (landscape) 265 else 13), 0)
        })

        // Focal indicator/label and real photo exposure status.
        val focusHint = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
        }
        hintLabel = text("TOQUE PARA FOCAR", 9f, 0xFFEEE4F7.toInt()).apply {
            letterSpacing = .08f
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(5), dp(11), dp(5))
            background = neonPanel(0xA40A0812.toInt(), 15, 0x70583B72)
        }
        focusHint.addView(hintLabel)
        viewfinderFrame.addView(focusHint, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply {
            bottomMargin = if (landscape) dp(25) else dp(270)
        })

        val chromeRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
        }

        val titleRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        val mark = text("◉", 24f, 0xFFAF55FF.toInt(), Typeface.BOLD)
        titleRow.addView(mark, LinearLayout.LayoutParams(dp(29), -2))
        val brand = text(if (landscape) "Vortex" else "Vortex Camera",
            if (landscape) 15f else 14f, Color.WHITE, Typeface.BOLD).apply {
            setSingleLine()
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        titleRow.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val proBadge = neonButton("Pro", "Controles profissionais").apply {
            textSize = 11f
            background = neonPanel(0xFF6615B9.toInt(), 10, 0xFFB573FC.toInt())
            setOnClickListener { setMode(Mode.PRO); showProSheet() }
        }
        titleRow.addView(proBadge, LinearLayout.LayoutParams(dp(42), dp(32)).apply {
            setMargins(dp(3), 0, dp(2), 0)
        })
        chromeRoot.addView(titleRow)

        val featureRail = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(1), dp(7), dp(4))
        }
        flashButton = neonButton("ϟ OFF", "Flash desligado, automático ou ligado").apply {
            setOnClickListener { cycleFlash() }
        }
        featureRail.addView(flashButton, LinearLayout.LayoutParams(0, dp(41), 1f).apply {
            setMargins(dp(2), 0, dp(2), 0)
        })
        val hdr = neonButton("HDR", "HDR em desenvolvimento").apply {
            setOnClickListener { toast("HDR inteligente ainda está em desenvolvimento.") }
        }
        featureRail.addView(hdr, LinearLayout.LayoutParams(0, dp(41), 1f).apply {
            setMargins(dp(2), 0, dp(2), 0)
        })
        val ai = neonButton("✦ AI", "Vortex AI Engine ainda em desenvolvimento").apply {
            setOnClickListener {
                toast("A IA offline ainda não foi integrada; nenhuma foto é enviada para a nuvem.")
            }
        }
        featureRail.addView(ai, LinearLayout.LayoutParams(0, dp(41), 1f).apply {
            setMargins(dp(2), 0, dp(2), 0)
        })
        featureRail.addView(neonButton("⚙", "Abrir configurações").apply {
            textSize = 19f
            setOnClickListener { showOptionsSheet() }
        }, LinearLayout.LayoutParams(0, dp(41), 1f).apply {
            setMargins(dp(2), 0, dp(2), 0)
        })
        )
        chromeRoot.addView(featureRail)

        val indicators = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(7), dp(9), dp(6))
        }
        exposureDisplay = text("EXPO 000", 10f, 0xFFECE4F5.toInt(), Typeface.BOLD).apply {
            setSingleLine()
            setPadding(dp(9), dp(10), dp(9), dp(10))
            background = neonPanel(0xAC0A0A13.toInt(), 17, 0x645D496F)
        }
        indicators.addView(exposureDisplay, LinearLayout.LayoutParams(-2, -2))
        indicators.addView(text("  •  4:3  •  JPEG", 10f, 0xFFCDC5D9.toInt()))
        histogramView = LumaHistogramView(this).apply {
            background = neonPanel(0xBE0A0814.toInt(), 13, 0x785F4D81)
        }
        indicators.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        indicators.addView(histogramView, LinearLayout.LayoutParams(dp(111), dp(61)))
        chromeRoot.addView(indicators)

        filmLabel = text(FilmCatalog.presets[selectedPreset].name, 12f, Color.WHITE, Typeface.BOLD)
        filmTagline = text(FilmCatalog.presets[selectedPreset].tagline, 9f, 0xFFD4B7F1.toInt())
        val filmMetadata = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(7))
            background = neonPanel(0xA20B0713.toInt(), 14, 0x745E447B.toInt())
        }
        filmMetadata.addView(filmLabel)
        filmMetadata.addView(filmTagline)

        // Film chooser is detached from camera screen until explicitly opened.
        filmScrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        presetStrip = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        filmScrollView.addView(presetStrip)
        buildPresetChips()

        val lower = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x850B0713.toInt(), 0xEF08060E.toInt())
            )
        }
        val quickEdits = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = neonPanel(0xC40D0C15.toInt(), 20, 0x804E385F.toInt())
            setPadding(dp(5), dp(3), dp(5), dp(3))
        }
        fun addTool(title: String, click: () -> Unit, weight: Float = 1f) {
            quickEdits.addView(neonButton(title, title).apply {
                textSize = 10f
                background = neonPanel(0x00101015, 14)
                setOnClickListener { click() }
            }, LinearLayout.LayoutParams(0, dp(43), weight).apply {
                setMargins(dp(1), 0, dp(1), 0)
            })
        }
        addTool("≡ Ajustes", { showGradeSheet() }, 1.1f)
        addTool("◉ Filtros", { showFilmPicker() }, 1f)
        addTool("✦ Efeitos", { showFilmSheet() }, 1f)
        addTool("▦ Grão", { showFilmSheet() }, .9f)
        lower.addView(quickEdits)
        filmChooserButton = neonButton("▣ Vintage 200  ›", "Selecionar película fotográfica").apply {
            textSize = 11f
            setTextColor(0xFFF6EBFF.toInt())
            background = neonPanel(0xB34F1485.toInt(), 17, 0xFFAB50FF.toInt())
            setOnClickListener { showFilmPicker() }
        }
        lower.addView(filmChooserButton, LinearLayout.LayoutParams(-1, dp(39)).apply {
            setMargins(dp(1), dp(3), dp(1), dp(1))
        })

        val captureDock = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(7), dp(5), dp(6))
        }
        val galleryButton = FrameLayout(this).apply {
            background = neonPanel(0xFF21132F.toInt(), 15, 0xFF8E58C3.toInt())
            clipChildren = true
            clipToOutline = true
            isClickable = true
            isFocusable = true
            contentDescription = "Abrir álbum Vortex"
            setOnClickListener { startActivity(Intent(this@MainActivity, GalleryActivity::class.java)) }
        }
        galleryThumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(android.R.drawable.ic_menu_gallery)
            background = neonPanel(0xFF1E1828.toInt(), 11)
            clipToOutline = true
            contentDescription = "Última foto"
        }
        galleryButton.addView(galleryThumb, FrameLayout.LayoutParams(-1, -1).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        })
        captureDock.addView(galleryButton, LinearLayout.LayoutParams(dp(56), dp(56)))
        captureDock.addView(neonButton("▧", "Abrir galeria").apply {
            textSize = 23f
            setOnClickListener { startActivity(Intent(this@MainActivity, GalleryActivity::class.java)) }
        }, LinearLayout.LayoutParams(dp(43), dp(50)).apply {
            setMargins(dp(6), 0, 0, 0)
        })

        val shutter = FrameLayout(this)
        shutter.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x661E093D)
                setStroke(dp(5), 0xFFBD58FF.toInt())
            }
            elevation = dp(5).toFloat()
        }, FrameLayout.LayoutParams(dp(82), dp(82), Gravity.CENTER))
        shutterButton = Button(this).apply {
            text = ""
            contentDescription = "Fotografar"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFF8F3FA.toInt())
            }
            elevation = dp(9).toFloat()
            setOnClickListener {
                if (mode == Mode.FOTO || mode == Mode.PRO) takePhoto()
                else toast("Este modo ainda está em desenvolvimento.")
            }
        }
        shutter.addView(shutterButton, FrameLayout.LayoutParams(dp(66), dp(66), Gravity.CENTER))
        captureDock.addView(shutter, LinearLayout.LayoutParams(0, dp(89), 1f))

        captureDock.addView(neonButton("▣", "Alternar para modo vídeo").apply {
            textSize = 24f
            setOnClickListener {
                setMode(Mode.VIDEO)
                toast("Captura de vídeo ainda não foi implementada.")
            }
        }, LinearLayout.LayoutParams(dp(43), dp(50)).apply {
            setMargins(0, 0, dp(6), 0)
        })
        captureDock.addView(neonButton("↻", "Alternar câmera").apply {
            textSize = 26f
            setOnClickListener {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                zoomRatio = 1f
                startCamera()
            }
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        lower.addView(captureDock)

        val navRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = neonPanel(0xF50F0A18.toInt(), 22, 0xFF503260.toInt())
            setPadding(dp(3), dp(4), dp(3), dp(4))
        }
        modeStrip = navRow
        // Icons and labels in the spirit of the reference, with Panorama kept accessible.
        val nav = listOf(
            Triple("◉\nRetrô", "RETRO", "Escolher câmera analógica"),
            Triple("▷\nVídeo", "VIDEO", "Modo vídeo"),
            Triple("▣\nFoto", "FOTO", "Modo fotografia"),
            Triple("☷\nPro", "PRO", "Modo profissional"),
            Triple("☾\nNoite", "NIGHT", "Modo noturno"),
            Triple("▱\nPano", "PANO", "Modo panorama")
        )
        nav.forEach { item ->
            val button = neonButton(item.first, item.third).apply {
                tag = when (item.second) {
                    "VIDEO" -> Mode.VIDEO
                    "FOTO" -> Mode.FOTO
                    "PRO" -> Mode.PRO
                    "PANO" -> Mode.PANORAMA
                    else -> item.second
                }
                textSize = if (landscape) 9f else 10f
                setSingleLine(false)
                setOnClickListener {
                    when (item.second) {
                        "RETRO" -> {
                            if (nightMode) toggleNight()
                            setMode(Mode.FOTO)
                            showFilmPicker()
                        }
                        "VIDEO" -> { if (nightMode) toggleNight(); setMode(Mode.VIDEO) }
                        "FOTO" -> { if (nightMode) toggleNight(); setMode(Mode.FOTO) }
                        "PRO" -> {
                            if (nightMode) toggleNight()
                            setMode(Mode.PRO)
                            showProSheet()
                        }
                        "NIGHT" -> { toggleNight(); updateNavState() }
                        "PANO" -> { if (nightMode) toggleNight(); setMode(Mode.PANORAMA) }
                    }
                }
            }
            if (item.second == "NIGHT") nightButton = button
            navRow.addView(button, LinearLayout.LayoutParams(0, dp(64), 1f).apply {
                setMargins(dp(1), 0, dp(1), 0)
            })
        }
        lower.addView(navRow)
        saveLabel = text("LOCAL • OFFLINE", 9f, 0xFFB4A7BE.toInt()).apply {
            gravity = Gravity.CENTER
            setSingleLine()
            setPadding(0, dp(5), 0, dp(2))
        }
        lower.addView(saveLabel)

        if (landscape) {
            // Controls in a right-hand rail; live view remains the full left pane.
            val rail = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = neonPanel(0xEB0C0913.toInt(), 22, 0xB244285C.toInt())
            }
            rail.addView(chromeRoot)
            val scroll = ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                fillViewport = false
            }
            scroll.addView(lower)
            rail.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            root.addView(rail, FrameLayout.LayoutParams(dp(285), -1, Gravity.RIGHT).apply {
                setMargins(0, dp(3), dp(5), dp(3))
            })
        } else {
            root.addView(chromeRoot, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
                setMargins(dp(6), dp(4), dp(6), 0)
            })
            root.addView(lower, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
        updateFilm()
        setMode(mode)
        refreshGalleryThumbnail()
        updateNightButton()
        rebuildZoomControls()
    }

    private fun neonPanel(color: Int, radius: Int, border: Int = color): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
            color, if (Color.alpha(color) < 230) 0x990F0B19.toInt() else 0xFF100A1A.toInt()
        )).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), border)
        }

    private fun neonButton(title: String, accessibility: String): Button =
        Button(this).apply {
            text = title
            contentDescription = accessibility
            isAllCaps = false
            textSize = 11f
            setTextColor(0xFFFAF4FF.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = neonPanel(0xA5110D1B.toInt(), 16, 0x8D56386D.toInt())
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(2), 0, dp(2), 0)
        }

    private fun showFilmPicker() {
        showSheet("Películas e câmeras", { container ->
            container.addView(text(
                "Escolha o perfil. Os controles Pro, Noite e Luz continuam disponíveis.",
                11f, 0xFFD4B9E8.toInt()
            ))
            (filmScrollView.parent as? ViewGroup)?.removeView(filmScrollView)
            buildPresetChips()
            container.addView(filmScrollView, LinearLayout.LayoutParams(-1, dp(62)))
            container.addView(text(
                "Kodak e Fujifilm: simulações autorais de película. Sony: sensor CCD digital.",
                10f, 0xFFAAA0B5.toInt()
            ))
        })
    }

    private fun rebuildZoomControls() {
        if (!::zoomStrip.isInitialized) return
        zoomStrip.removeAllViews()
        val state = activeCamera?.cameraInfo?.zoomState?.value
        val minZoom = state?.minZoomRatio ?: 1f
        val maxZoom = state?.maxZoomRatio ?: 1f
        val options = listOf(.5f, 1f, 2f, 5f).filter {
            it >= minZoom - .01f && it <= maxZoom + .01f
        }.ifEmpty { listOf(1f) }
        zoomRatio = zoomRatio.coerceIn(minZoom, maxZoom)
        options.forEach { ratio ->
            val active = kotlin.math.abs(ratio - zoomRatio) < .04f
            zoomStrip.addView(neonButton(
                if (ratio % 1f == 0f) ratio.toInt().toString() + "×" else "0.5×",
                "Zoom " + ratio + " vezes"
            ).apply {
                background = neonPanel(
                    if (active) 0xFF7118C6.toInt() else 0x9C1C1029.toInt(),
                    22,
                    if (active) 0xFFE5A3FF.toInt() else 0x66563C69
                )
                setOnClickListener {
                    val camera = activeCamera ?: return@setOnClickListener
                    zoomRatio = ratio
                    camera.cameraControl.setZoomRatio(ratio)
                    rebuildZoomControls()
                }
            }, LinearLayout.LayoutParams(dp(49), dp(44)).apply {
                setMargins(0, dp(1), 0, dp(1))
            })
        }
        zoomStrip.visibility = if (maxZoom - minZoom < .1f) View.GONE else View.VISIBLE
    }

    private fun updateNavState() {
        if (!::modeStrip.isInitialized) return
        for (i in 0 until modeStrip.childCount) {
            val btn = modeStrip.getChildAt(i) as? Button ?: continue
            val active = when (btn.tag) {
                "NIGHT" -> nightMode
                "RETRO" -> false
                else -> btn.tag == mode && !nightMode
            }
            btn.setTextColor(if (active) Color.WHITE else 0xFFD4C9DB.toInt())
            btn.background = neonPanel(
                if (active) 0xFF7812BE.toInt() else 0x50140F21,
                19,
                if (active) 0xFFC96EFF.toInt() else Color.TRANSPARENT
            )
        }
    }

    private fun controlWeight() = LinearLayout.LayoutParams(0, dp(45), 1f).apply {
        setMargins(dp(3), dp(2), dp(3), dp(2))
    }

    private fun chromePanel(color: Int, radius: Int, border: Int = color): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(color, 0xF0191A1F.toInt())
        ).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), border)
        }

    private fun premiumControl(title: String, description: String, accessibility: String): Button =
        Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setSingleLine(true)
            setTextColor(0xFFEEE7DF.toInt())
            background = chromePanel(0xFF2A2B31.toInt(), 13, 0xFF53515B.toInt())
            elevation = dp(2).toFloat()
            contentDescription = accessibility + ". " + description
            setPadding(dp(4), 0, dp(4), 0)
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
        }

    private fun showProSheet() {
        showSheet("Controles profissionais") { container ->
            (proPanel.parent as? ViewGroup)?.removeView(proPanel)
            proPanel.visibility = View.VISIBLE
            container.addView(proPanel)
            container.addView(text(
                "ISO e obturador físicos somente quando suportados pelo sensor.",
                10f, muted
            ))
        }
    }

    private fun updateNightButton() {
        if (!::nightButton.isInitialized) return
        nightButton.text = if (nightMode) "☾ ON" else "☾ NOITE"
        nightButton.setTextColor(if (nightMode) 0xFF111217.toInt() else 0xFFF4E7D6.toInt())
        nightButton.background = chromePanel(
            if (nightMode) 0xFFE9C38F.toInt() else 0xFF2A2B31.toInt(),
            13,
            if (nightMode) 0xFFFFE8BE.toInt() else 0xFF53515B.toInt()
        )
    }

    private fun rounded(color: Int, radius: Int, border: Int = color): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(color)
            setStroke(dp(1), border)
        }

    private fun smallButton(label: String, action: (Button) -> Unit): Button = Button(this).apply {
        text = label
        textSize = 10f
        typeface = Typeface.MONOSPACE
        isAllCaps = false
        setTextColor(0xFFDFD6CC.toInt())
        background = rounded(0xFF29292F.toInt(), 10, 0xFF44444A.toInt())
        setOnClickListener { action(this) }
    }

    private fun showSheet(title: String, build: (LinearLayout) -> Unit) {
        val dialog = Dialog(this)
        val surface = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(24))
            background = rounded(0xFF202126.toInt(), 24, 0xFF4D4C51.toInt())
        }
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(text(title, 17f, Color.WHITE, Typeface.BOLD).apply {
            letterSpacing = 0.04f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(smallButton("FECHAR") { dialog.dismiss() },
            LinearLayout.LayoutParams(dp(86), dp(44))
        )
        surface.addView(heading)
        surface.addView(text("RETRO CAMERA BY VORTEX", 9f, accent).apply {
            letterSpacing = 0.16f
            setPadding(0, dp(3), 0, dp(15))
        })
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        build(details)
        val scroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val compactRatio = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 0.72f else 0.49f
                val limit = (resources.displayMetrics.heightPixels * compactRatio).toInt()
                super.onMeasure(
                    widthMeasureSpec,
                    View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST)
                )
            }
        }
        scroll.addView(details)
        surface.addView(scroll)
        dialog.setContentView(surface)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.48f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun rowAction(
        parent: LinearLayout,
        label: String,
        description: String,
        action: (Button) -> Unit
    ): Button {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(text(label, 13f, Color.WHITE, Typeface.BOLD))
        info.addView(text(description, 10f, muted))
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        val button = smallButton("ALTERAR", action)
        row.addView(button, LinearLayout.LayoutParams(dp(92), dp(43)))
        parent.addView(row)
        return button
    }

    private fun cycleFlash() {
        if (nightMode) {
            toast("No modo noturno, o flash fica desligado. Desativa a Noite para usar flash.")
            return
        }
        val available = activeCamera?.cameraInfo?.hasFlashUnit() ?: false
        if (!available) {
            toast("A lente selecionada não possui flash físico.")
            flashSetting = Flash.OFF
        } else {
            flashSetting = when (flashSetting) {
                Flash.OFF -> Flash.AUTO
                Flash.AUTO -> Flash.ON
                Flash.ON -> Flash.OFF
            }
        }
        updateFlash()
    }

    private fun updateFlash() {
        val available = activeCamera?.cameraInfo?.hasFlashUnit() ?: false
        val setting = if (nightMode || !available) Flash.OFF else flashSetting
        capture?.flashMode = when (setting) {
            Flash.OFF -> ImageCapture.FLASH_MODE_OFF
            Flash.AUTO -> ImageCapture.FLASH_MODE_AUTO
            Flash.ON -> ImageCapture.FLASH_MODE_ON
        }
        flashButton.text = when {
            nightMode -> "⚡ NOITE"
            !available -> "⚡ N/A"
            setting == Flash.AUTO -> "⚡ AUTO"
            setting == Flash.ON -> "⚡ ON"
            else -> "⚡ OFF"
        }
    }

    private fun toggleNight() {
        if (saving) return toast("Aguarda a captura atual terminar.")
        nightMode = !nightMode
        if (nightMode && manualFocus) {
            manualFocus = false
            focusControlButton.text = "◎ FOCO"
        }
        if (nightMode && mode != Mode.FOTO) {
            // Night capture is a still-photography feature, never a fake video mode.
            setMode(Mode.FOTO)
        }
        updateNightButton()
        if (nightMode) {
            hintLabel.text = "NOITE: mantenha o celular parado durante a captura"
            toast("Modo noturno: captura com fusão de imagens. Segura o celular firme.")
        }
        startCamera()
    }

    private fun gradeSlider(
        parent: LinearLayout,
        label: String,
        value: Float,
        onChange: (Float) -> Unit
    ) {
        val title = text("$label  ${(value * 100).toInt().let { if (it > 0) "+$it" else "$it" }}",
            12f, accent, Typeface.BOLD).apply {
            setPadding(0, dp(7), 0, 0)
        }
        parent.addView(title)
        parent.addView(SeekBar(this).apply {
            max = 200
            progress = ((value.coerceIn(-1f, 1f) + 1f) * 100).toInt()
            setOnSeekBarChangeListener(simpleSeek { position ->
                val current = (position - 100) / 100f
                onChange(current)
                val number = position - 100
                title.text = "$label  " + (if (number > 0) "+$number" else "$number")
                updateFilm()
            })
        })
    }

    private fun showGradeSheet() {
        showSheet("Laboratório de luz") { content ->
            content.addView(text(
                "Ajustes aplicados à fotografia final, preservando a identidade do filme escolhido.",
                10f, muted
            ).apply { setPadding(0, 0, 0, dp(7)) })
            gradeSlider(content, "BRILHO", photoGrade.brightness) {
                photoGrade = photoGrade.copy(brightness = it)
            }
            gradeSlider(content, "CONTRASTE", photoGrade.contrast) {
                photoGrade = photoGrade.copy(contrast = it)
            }
            gradeSlider(content, "SOMBRAS", photoGrade.shadows) {
                photoGrade = photoGrade.copy(shadows = it)
            }
            gradeSlider(content, "REALCES", photoGrade.highlights) {
                photoGrade = photoGrade.copy(highlights = it)
            }
            gradeSlider(content, "SATURAÇÃO", photoGrade.saturation) {
                photoGrade = photoGrade.copy(saturation = it)
            }
            gradeSlider(content, "TEMPERATURA", photoGrade.warmth) {
                photoGrade = photoGrade.copy(warmth = it)
            }
            content.addView(smallButton("RESTAURAR PADRÕES") {
                photoGrade = PhotoGrade()
                updateFilm()
                toast("Ajustes zerados. Reabre o painel para atualizar os controles.")
            }, LinearLayout.LayoutParams(-1, dp(48)))
            content.addView(text(
                "A prévia mostra cor/contraste aproximados; sombras e realces completos são processados ao fotografar.",
                9f, muted
            ))
        }
    }

    private fun showOptionsSheet() {
        showSheet("Ajustes da câmera") { content ->
            rowAction(content, "Textura da película", "Granulação e queima de filme") {
                showFilmSheet()
            }.text = "ABRIR"

            rowAction(content, "Half Frame", "Enquadramento vertical 3:4") { button ->
                halfFrame = !halfFrame
                frameGuide.halfFrame = halfFrame
                button.text = if (halfFrame) "ATIVADO" else "DESL."
            }.text = if (halfFrame) "ATIVADO" else "DESL."

            rowAction(content, "Data retrô", "Carimbo âmbar com a data real") { button ->
                dateStamp = !dateStamp
                button.text = if (dateStamp) "ATIVADO" else "DESL."
            }.text = if (dateStamp) "ATIVADO" else "DESL."

            rowAction(content, "Temporizador", "0, 3 ou 10 segundos") { button ->
                timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
                button.text = "${timerSeconds}s"
            }.text = "${timerSeconds}s"

            rowAction(content, "Som mecânico", "Disparo + avanço do filme sintetizados") { button ->
                soundEnabled = !soundEnabled
                button.text = if (soundEnabled) "LIGADO" else "MUDO"
            }.text = if (soundEnabled) "LIGADO" else "MUDO"
        }
    }

    private fun showFilmSheet() {
        showSheet("Textura da película") { content ->
            val grainLabel = text("GRANULAÇÃO • ${(grainStrength * 100).toInt()}%", 12f, accent)
            content.addView(grainLabel)
            content.addView(SeekBar(this).apply {
                max = 300
                progress = (grainStrength * 100).toInt()
                setOnSeekBarChangeListener(simpleSeek { value ->
                    grainStrength = value / 100f
                    grainLabel.text = "GRANULAÇÃO • ${value}%"
                })
            })
            content.addView(text("De película suave até grão mais pronunciado.", 10f, muted))
            val burnLabel = text("QUEIMA DE FILME • ${(burnChance * 100).toInt()}% das fotos", 12f, accent).apply {
                setPadding(0, dp(20), 0, 0)
            }
            content.addView(burnLabel)
            content.addView(SeekBar(this).apply {
                max = 50
                progress = (burnChance * 100).toInt()
                setOnSeekBarChangeListener(simpleSeek { value ->
                    burnChance = value / 100f
                    burnLabel.text = "QUEIMA DE FILME • ${value}% das fotos"
                })
            })
            content.addView(text("Vazamentos de luz nas bordas sorteados por foto. Em sensores CCD digitais, este efeito não é aplicado.", 10f, muted))
        }
    }

    private fun showFocusSheet() {
        showSheet("Foco da lente") { content ->
            val status = text(
                if (manualFocus) "MF • FOCO MANUAL" else "AF • TOQUE NO VISOR PARA FOCAR",
                12f, accent
            )
            content.addView(status)
            content.addView(text(
                "AF usa o ponto tocado e medição de luz. MF usa a distância óptica da lente, quando o sensor oferece este controle.",
                10f, muted
            ).apply { setPadding(0, dp(4), 0, dp(14)) })
            val slider = SeekBar(this).apply {
                max = 100
                progress = focusPercent
                isEnabled = manualFocus && (manual?.supportsManualFocus == true)
                alpha = if (isEnabled) 1f else 0.35f
            }
            val distanceLabel = text(
                "∞ INFINITO     —     PERTO / MACRO",
                10f, muted
            )
            rowAction(content, "Autofoco", "Foco contínuo + toque na cena") { _ ->
                manualFocus = false
                manual?.setAutoFocus()
                focusControlButton.text = "◎ FOCO"
                slider.isEnabled = false
                slider.alpha = 0.35f
                status.text = "AF • TOQUE NO VISOR PARA FOCAR"
            }.text = "AF"

            rowAction(content, "Foco manual", "Ajuste de dioptrias por deslizamento") { _ ->
                if (nightMode) {
                    toast("Desative o modo noturno para usar o foco manual.")
                    return@rowAction
                }
                val controller = manual
                if (controller?.supportsManualFocus != true) {
                    toast("Esta lente não permite foco manual por hardware.")
                } else {
                    manualFocus = true
                    controller.setManualFocus(focusPercent / 100f)
                    focusControlButton.text = "◎ MF"
                    slider.isEnabled = true
                    slider.alpha = 1f
                    status.text = "MF • DESLIZE PARA AJUSTAR"
                }
            }.text = "MF"

            content.addView(slider)
            content.addView(distanceLabel)
            slider.setOnSeekBarChangeListener(simpleSeek { value ->
                focusPercent = value
                if (manualFocus) manual?.setManualFocus(value / 100f)
            })
        }
    }

    private fun focusAt(x: Float, y: Float) {
        if (manualFocus) {
            // MF uses a fixed optical distance. Ignore viewport taps silently;
            // do not flood the screen with toasts as the user composes a shot.
            return
        }
        val camera = activeCamera ?: return
        val markerLayout = focusMarker.layoutParams as FrameLayout.LayoutParams
        markerLayout.leftMargin = (x.toInt() - dp(34)).coerceIn(0, (viewfinderFrame.width - dp(68)).coerceAtLeast(0))
        markerLayout.topMargin = (y.toInt() - dp(34)).coerceIn(0, (viewfinderFrame.height - dp(68)).coerceAtLeast(0))
        focusMarker.layoutParams = markerLayout
        focusMarker.animate().cancel()
        focusMarker.scaleX = 1.28f
        focusMarker.scaleY = 1.28f
        focusMarker.alpha = 1f
        focusMarker.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
        val point = previewView.meteringPointFactory.createPoint(x, y, 0.15f)
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        val future = camera.cameraControl.startFocusAndMetering(action)
        future.addListener({
            val success = try { future.get().isFocusSuccessful } catch (_: Exception) { false }
            if (!isFinishing && !isDestroyed) {
                hintLabel.text = if (success) "FOCO TRAVADO  ✓" else "AF: AJUSTE EM ANDAMENTO"
                focusMarker.animate().alpha(0f).setStartDelay(550).setDuration(450).start()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun buildPresetChips() {
        presetStrip.removeAllViews()
        var selectedView: Button? = null
        FilmCatalog.presets.forEachIndexed { index, preset ->
            val active = index == selectedPreset
            val abbreviated = preset.name
                .replace("KODAK ", "K. ")
                .replace("FUJICOLOR ", "FUJI ")
                .replace("FUJI ", "F. ")
                .replace("VORTEX ", "V. ")
            val chip = premiumControl(abbreviated, preset.tagline, "Película " + preset.name).apply {
                textSize = 10f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(if (active) 0xFF171414.toInt() else 0xFFE1DBD2.toInt())
                background = chromePanel(
                    if (active) 0xFFF1D1A5.toInt() else 0xFF34343C.toInt(),
                    12,
                    if (active) 0xFFFFEAD0.toInt() else 0xFF53525B.toInt()
                )
                setOnClickListener {
                    selectedPreset = index
                    updateFilm()
                    buildPresetChips()
                }
            }
            if (active) selectedView = chip
            presetStrip.addView(chip, LinearLayout.LayoutParams(-2, dp(42)).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        selectedView?.let { selected ->
            filmScrollView.post {
                if (!isFinishing && !isDestroyed && selected.parent === presetStrip) {
                    val target = selected.left + selected.width / 2 - filmScrollView.width / 2
                    filmScrollView.smoothScrollTo(target.coerceAtLeast(0), 0)
                }
            }
        }
    }

    private fun setMode(next: Mode) {
        if (next == Mode.PRO && mode != Mode.PRO) {
            // Enter Pro with AE enabled rather than forcing ISO 400 / 1/125s.
            proManualExposure = false
            if (::proPanel.isInitialized) buildProPanel()
        }
        val leavingNight = nightMode && next == Mode.PRO
        if (leavingNight) {
            nightMode = false
            nightUsingOem = false
            if (::nightButton.isInitialized) updateNightButton()
        }
        mode = next
        if (leavingNight) {
            updateNightButton()
            startCamera()
        }
        for (i in 0 until modeStrip.childCount) {
            val btn = modeStrip.getChildAt(i) as Button
            val selected = btn.tag == mode
            btn.setTextColor(if (selected) 0xFF211B17.toInt() else 0xFFE3D7CA.toInt())
            btn.background = chromePanel(
                if (selected) 0xFFE8C597.toInt() else 0xFF292A31.toInt(), 10,
                if (selected) 0xFFFFE8C3.toInt() else 0xFF4A4A53.toInt()
            )
        }
        // Pro controls are in a dedicated sheet; never consume viewfinder height.
        proPanel.visibility = if (mode == Mode.PRO) View.VISIBLE else View.GONE
        hintLabel.text = when (mode) {
            Mode.FOTO -> "FOTO · escolha o filme e capture"
            Mode.PRO -> "PRO · sensores manuais quando suportados"
            Mode.PANORAMA -> "PANORAMA · em desenvolvimento (M4)"
            Mode.VIDEO -> "VÍDEO RETRÔ · em desenvolvimento (M5)"
        }
        shutterButton.isEnabled = (mode == Mode.FOTO || mode == Mode.PRO) && !saving
        if (mode == Mode.PRO) applyManual() else {
            if (manualFocus) manual?.clearExposure() else manual?.reset()
        }
    }

    private fun buildProPanel() {
        proPanel.removeAllViews()
        proPanel.orientation = LinearLayout.VERTICAL

        val status = text("", 13f, accent, Typeface.BOLD).apply {
            setPadding(0, dp(4), 0, dp(8))
        }
        proPanel.addView(status)

        val aeToggle = smallButton("EXPOSIÇÃO AUTOMÁTICA") {}
        proPanel.addView(aeToggle, LinearLayout.LayoutParams(-1, dp(50)))

        val manualControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(4))
        }
        val isoText = text("", 12f, accent, Typeface.BOLD)
        manualControls.addView(isoText)
        manualControls.addView(SeekBar(this).apply {
            max = 15
            progress = (iso / 100 - 1).coerceIn(0, 15)
            setOnSeekBarChangeListener(simpleSeek { value ->
                iso = (value + 1) * 100
                isoText.text = "ISO  $iso"
                if (proManualExposure) applyManual()
            })
        })
        val times = listOf(
            1_000_000L, 2_000_000L, 4_000_000L, 8_000_000L,
            16_000_000L, 33_000_000L, 66_000_000L, 100_000_000L,
            200_000_000L, 500_000_000L
        )
        val labels = listOf(
            "1/1000", "1/500", "1/250", "1/125", "1/60",
            "1/30", "1/15", "1/10", "1/5", "1/2"
        )
        val shutterText = text("", 12f, accent, Typeface.BOLD).apply {
            setPadding(0, dp(8), 0, 0)
        }
        manualControls.addView(shutterText)
        manualControls.addView(SeekBar(this).apply {
            max = times.lastIndex
            progress = times.indexOf(exposureNs).coerceAtLeast(0)
            setOnSeekBarChangeListener(simpleSeek { index ->
                exposureNs = times[index]
                shutterText.text = "OBTURADOR  ${labels[index]}s"
                if (proManualExposure) applyManual()
            })
        })
        manualControls.addView(text(
            "Valores manuais fixam a exposição. Em ambientes escuros, aumente o tempo do obturador ou o ISO; segure o celular firme.",
            10f, muted
        ))
        proPanel.addView(manualControls)

        fun refresh() {
            val available = manual?.supportsManualSensor == true
            if (manual == null) {
                status.text = "AE • PREPARANDO SENSOR..."
                aeToggle.text = "AUTO"
                manualControls.visibility = View.GONE
            } else if (!available) {
                status.text = "AE AUTOMÁTICO  •  SENSOR SEM CONTROLE MANUAL"
                aeToggle.text = "AUTO  ✓"
                manualControls.visibility = View.GONE
                proManualExposure = false
            } else if (proManualExposure) {
                status.text = "M  •  EXPOSIÇÃO MANUAL"
                aeToggle.text = "VOLTAR AO AUTO (AE)"
                manualControls.visibility = View.VISIBLE
            } else {
                status.text = "AE  •  EXPOSIÇÃO AUTOMÁTICA"
                aeToggle.text = "ATIVAR ISO / OBTURADOR MANUAIS"
                manualControls.visibility = View.GONE
            }
        }

        aeToggle.setOnClickListener {
            if (manual?.supportsManualSensor != true) {
                toast("ISO e obturador manuais não estão disponíveis nesta lente.")
                refresh()
                return@setOnClickListener
            }
            proManualExposure = !proManualExposure
            refresh()
            applyManual()
        }
        isoText.text = "ISO  $iso"
        val activeIndex = times.indexOf(exposureNs).coerceAtLeast(0)
        shutterText.text = "OBTURADOR  ${labels[activeIndex]}s"
        refresh()
    }

    private fun simpleSeek(onUpdate: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onUpdate(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun updateFilm() {
        val preset = FilmCatalog.presets[selectedPreset]
        filmLabel.text = preset.name
        filmTagline.text = preset.tagline.uppercase(Locale.ROOT)
        frameGuide.warmTint = preset.red >= preset.blue
        // GPU color-matrix approximation. The final saved JPEG uses FilmProcessor.
        val contrast = (preset.contrast * 0.91f * (1f + photoGrade.contrast * .52f))
            .coerceIn(0.5f, 1.85f)
        val sat = (1f + photoGrade.saturation * .70f).coerceIn(0f, 1.75f)
        val desat = 1f - sat
        val offset = (1f - contrast) * 128f + preset.lift * 110f +
            photoGrade.brightness * 36f
        val warmth = photoGrade.warmth * 17f
        val colorMatrix = floatArrayOf(
            (sat + desat * .2126f) * preset.red * contrast,
            desat * .7152f * contrast,
            desat * .0722f * contrast, 0f, offset + warmth,
            desat * .2126f * contrast,
            (sat + desat * .7152f) * preset.green * contrast,
            desat * .0722f * contrast, 0f, offset,
            desat * .2126f * contrast,
            desat * .7152f * contrast,
            (sat + desat * .0722f) * preset.blue * contrast, 0f, offset - warmth,
            0f, 0f, 0f, 1f, 0f
        )
        try {
            previewView.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply {
                colorFilter = ColorMatrixColorFilter(colorMatrix)
            })
        } catch (_: RuntimeException) {
            previewView.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        previewTint.setBackgroundColor(
            if (preset.red > preset.blue) 0xFFCF8B57.toInt() else 0xFF459F96.toInt()
        )
        previewTint.alpha = 0.035f
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val generation = ++cameraGeneration
        capture = null
        if (::shutterButton.isInitialized) shutterButton.isEnabled = false
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (generation != cameraGeneration || isFinishing || isDestroyed) return@addListener
            try {
                val provider = future.get()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                if (nightMode && mode == Mode.FOTO) {
                    val managerFuture = ExtensionsManager.getInstanceAsync(applicationContext, provider)
                    managerFuture.addListener({
                        if (generation == cameraGeneration && !isFinishing && !isDestroyed) {
                            try {
                                val manager = managerFuture.get()
                                val supported = manager.isExtensionAvailable(selector, ExtensionMode.NIGHT)
                                val selected = if (supported)
                                    manager.getExtensionEnabledCameraSelector(selector, ExtensionMode.NIGHT)
                                else selector
                                bindCamera(provider, selected, supported, generation)
                            } catch (_: Exception) {
                                bindCamera(provider, selector, false, generation)
                            }
                        }
                    }, ContextCompat.getMainExecutor(this))
                } else {
                    bindCamera(provider, selector, false, generation)
                }
            } catch (e: Exception) {
                toast("Falha ao iniciar câmera: ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera(
        provider: ProcessCameraProvider,
        selector: CameraSelector,
        useOemNight: Boolean,
        generation: Int
    ) {
        if (generation != cameraGeneration || isDestroyed || isFinishing) return
        try {
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val photo = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            provider.unbindAll()
            val bound = provider.bindToLifecycle(this, selector, preview, photo)
            capture = photo
            activeCamera = bound
            nightUsingOem = nightMode && useOemNight
            manual = try { ManualCamera(bound) } catch (_: Exception) { null }
            if (manualFocus && !nightMode) {
                if (manual?.setManualFocus(focusPercent / 100f) == false) {
                    manualFocus = false
                    focusControlButton.text = "◎ FOCO"
                }
            }
            if (mode == Mode.PRO && !nightMode) {
                buildProPanel()
                applyManual()
            }
            if (nightMode && !nightUsingOem) {
                // Small physical AE boost; vendor Night mode handles exposure internally.
                val state = bound.cameraInfo.exposureState
                if (state.isExposureCompensationSupported) {
                    val range = state.exposureCompensationRange
                    val index = 2.coerceIn(range.lower, range.upper)
                    bound.cameraControl.setExposureCompensationIndex(index)
                }
                hintLabel.text = "NOITE • 3 QUADROS • MANTENHA FIRME"
            } else if (nightUsingOem) {
                hintLabel.text = "NOITE • PROCESSAMENTO DO FABRICANTE"
            }
            shutterButton.isEnabled = (mode == Mode.FOTO || mode == Mode.PRO) && !saving
            updateFlash()
        } catch (e: Exception) {
            if (useOemNight) {
                // A device may advertise the extension but still reject binding.
                val normalSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                bindCamera(provider, normalSelector, false, generation)
            } else {
                capture = null
                toast("Câmera indisponível: ${e.localizedMessage}")
            }
        }
    }

    private fun applyManual() {
        if (mode != Mode.PRO) return
        val cameraControl = manual ?: return
        if (!proManualExposure || !cameraControl.supportsManualSensor) {
            // Preserve optical MF if active; only return exposure to CameraX AE.
            cameraControl.clearExposure()
            hintLabel.text = if (!cameraControl.supportsManualSensor)
                "PRO · AE AUTO • manual indisponível nesta lente"
            else "PRO · AE AUTO • toque em PRO para ajustar"
            return
        }
        cameraControl.apply(iso, exposureNs)
        hintLabel.text = "PRO · M  ISO $iso · ${exposureNs / 1_000_000} ms"
    }

    private fun takePhoto() {
        if (saving) return
        val imageCapture = capture ?: return toast("Câmera ainda inicializando")
        if (timerSeconds > 0) {
            saveLabel.text = "TEMPORIZADOR · ${timerSeconds}s"
            shutterButton.isEnabled = false
            handler.postDelayed({ shutterButton.isEnabled = true; captureNow(imageCapture) }, timerSeconds * 1000L)
        } else captureNow(imageCapture)
    }

    private data class CaptureRecipe(
        val film: FilmPreset,
        val fx: FilmFxSettings,
        val halfFrame: Boolean,
        val dateStamp: Boolean,
        val capturedAt: Long
    )

    private fun captureNow(imageCapture: ImageCapture) {
        if (saving || isFinishing || isDestroyed) return
        saving = true
        shutterButton.isEnabled = false
        saveLabel.text = if (nightMode) "CAPTURA NOTURNA • NÃO MOVA" else "PROCESSANDO FILME…"
        val capturedGrade = if (nightMode && !nightUsingOem) {
            // Subtle low-light shadow boost after actual frame fusion.
            photoGrade.copy(shadows = maxOf(photoGrade.shadows, .16f))
        } else photoGrade
        val recipe = CaptureRecipe(
            FilmCatalog.presets[selectedPreset],
            FilmFxSettings(
                grainStrength = if (nightMode) grainStrength * .70f else grainStrength,
                burnChance = burnChance,
                seed = kotlin.random.Random.nextInt(),
                grade = capturedGrade
            ),
            halfFrame, dateStamp, System.currentTimeMillis()
        )
        if (soundEnabled) shutterSound.play()
        shutterButton.animate().scaleX(.88f).scaleY(.88f).setDuration(80).withEndAction {
            shutterButton.animate().scaleX(1f).scaleY(1f).setDuration(170).start()
        }.start()
        if (nightMode && !nightUsingOem) {
            captureNightSequence(imageCapture, recipe)
        } else {
            captureSingle(imageCapture, recipe)
        }
    }

    private fun captureSingle(imageCapture: ImageCapture, recipe: CaptureRecipe) {
        val temporary = try {
            File.createTempFile("vortex-photo-", ".jpg", cacheDir)
        } catch (e: Exception) {
            handleCaptureError(e)
            return
        }
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(temporary).build(), io,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        val source = FilmProcessor.readUpright(temporary)
                        processAndSave(source, recipe)
                    } catch (e: Exception) {
                        handleCaptureError(e)
                    } finally {
                        temporary.delete()
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    temporary.delete()
                    handleCaptureError(exception)
                }
            }
        )
    }

    private fun captureNightSequence(imageCapture: ImageCapture, recipe: CaptureRecipe) {
        val frames = mutableListOf<File>()
        fun finishWithError(e: Exception) {
            frames.forEach { it.delete() }
            handleCaptureError(e)
        }
        fun nextFrame() {
            val file = try {
                File.createTempFile("vortex-night-", ".jpg", cacheDir)
            } catch (e: Exception) {
                finishWithError(e)
                return
            }
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(), io,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        frames.add(file)
                        if (frames.size < 3) {
                            runOnUiThread { saveLabel.text = "NOITE • QUADRO ${frames.size}/3" }
                            nextFrame()
                        } else {
                            try {
                                val merged = NightFrameFusion.merge(frames)
                                processAndSave(merged, recipe)
                            } catch (e: Exception) {
                                handleCaptureError(e)
                            } finally {
                                frames.forEach { it.delete() }
                            }
                        }
                    }
                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        finishWithError(exception)
                    }
                }
            )
        }
        nextFrame()
    }

    private fun processAndSave(source: android.graphics.Bitmap, recipe: CaptureRecipe) {
        try {
            val filtered = FilmProcessor.process(source, recipe.film, recipe.fx)
            try {
                val presented = FilmPresentation.render(
                    filtered, recipe.halfFrame, recipe.dateStamp, recipe.capturedAt
                )
                try {
                    val uri = saveToGallery(presented)
                    runOnUiThread { if (!isDestroyed) onPhotoSaved(uri) }
                } finally {
                    if (presented !== filtered) presented.recycle()
                }
            } finally {
                filtered.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    private fun handleCaptureError(error: Exception) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                finishCapture("Falha na captura • tente novamente")
                toast(error.localizedMessage ?: "Erro ao fotografar")
            }
        }
    }

    private fun saveToGallery(image: android.graphics.Bitmap): Uri {
        val filename = "Vortex_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Vortex Retro Camera")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, stream)) { "JPEG encode failed" }
            } ?: error("Cannot open output file")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun onPhotoSaved(uri: Uri) {
        val prefs = getSharedPreferences("vortex_prefs", MODE_PRIVATE)
        val count = prefs.getInt("exposure_count", 0) + 1
        prefs.edit().putString("latest_photo_uri", uri.toString())
            .putInt("exposure_count", count).apply()
        exposureDisplay.text = "EXPO " + count.toString().padStart(3, '0')
        finishCapture("REVELADA ✓   •   TOQUE NA MINIATURA PARA ABRIR")
        refreshGalleryThumbnail(uri)
    }

    private fun refreshGalleryThumbnail(photoUri: Uri? = null) {
        val prefs = getSharedPreferences("vortex_prefs", MODE_PRIVATE)
        exposureDisplay.text = "EXPO " + prefs.getInt("exposure_count", 0)
            .toString().padStart(3, '0')
        val target = photoUri ?: prefs.getString("latest_photo_uri", null)?.let { Uri.parse(it) }
        if (target == null) return
        io.execute {
            val bitmap = try {
                contentResolver.loadThumbnail(target, Size(dp(120), dp(120)), null)
            } catch (_: Exception) { null }
            if (bitmap != null) runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    galleryThumb.setImageBitmap(bitmap)
                    galleryThumb.animate().scaleX(0.96f).scaleY(0.96f)
                        .setDuration(90).withEndAction {
                            galleryThumb.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                        }.start()
                }
            }
        }
    }

    private fun finishCapture(message: String) {
        saving = false
        shutterButton.isEnabled = mode == Mode.FOTO || mode == Mode.PRO
        saveLabel.text = message
    }

    private fun text(value: String, sizeSp: Float, color: Int, weight: Int = Typeface.NORMAL) =
        TextView(this).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            setTypeface(Typeface.DEFAULT, weight)
        }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onResume() {
        super.onResume()
        if (::galleryThumb.isInitialized) refreshGalleryThumbnail()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        shutterSound.release()
        io.shutdown()
        super.onDestroy()
    }
}
