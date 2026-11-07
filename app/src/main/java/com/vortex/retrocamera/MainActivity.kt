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

    private lateinit var previewView: PreviewView
    private lateinit var previewTint: View
    private lateinit var frameGuide: RetroViewfinderOverlay
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

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else toast("A câmera precisa de permissão para funcionar.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = bg
        window.navigationBarColor = bg
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
    private fun buildInterface() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF090A0D.toInt())
            clipChildren = false
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        // Live, uninterrupted optical viewport.
        viewfinderFrame = FrameLayout(this).apply {
            setBackgroundColor(0xFF030405.toInt())
            clipChildren = true
        }
        root.addView(viewfinderFrame, FrameLayout.LayoutParams(-1, -1))
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Color.BLACK)
            isClickable = true
        }
        viewfinderFrame.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        previewView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick()
                focusAt(event.x, event.y)
            }
            true
        }
        previewTint = View(this).apply { isClickable = false }
        viewfinderFrame.addView(previewTint, FrameLayout.LayoutParams(-1, -1))
        frameGuide = RetroViewfinderOverlay(this).apply { isClickable = false }
        viewfinderFrame.addView(frameGuide, FrameLayout.LayoutParams(-1, -1))

        // Floating focus point remains above film preview, below toolbars.
        focusMarker = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), 0xFFF0D5A6.toInt())
            }
            alpha = 0f
            isClickable = false
        }
        viewfinderFrame.addView(focusMarker, FrameLayout.LayoutParams(dp(68), dp(68)))

        viewfinderFrame.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xA9000000.toInt(), 0x22000000, Color.TRANSPARENT)
            )
            isClickable = false
        }, FrameLayout.LayoutParams(-1, dp(195), Gravity.TOP))

        viewfinderFrame.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xCC08090C.toInt(), 0x3808090C, Color.TRANSPARENT)
            )
            isClickable = false
        }, FrameLayout.LayoutParams(-1, dp(305), Gravity.BOTTOM))

        val filmPlate = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(9), dp(13), dp(10))
            background = chromePanel(0xB7101115.toInt(), 13, 0x605F6068)
            elevation = dp(3).toFloat()
        }
        filmLabel = text("KODAK GOLD 200", 12f, Color.WHITE, Typeface.BOLD).apply {
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.035f
        }
        filmTagline = text("SOL DE VERÃO • 2001", 9f, 0xFFE6CBA8.toInt()).apply {
            letterSpacing = 0.07f
            setPadding(0, dp(3), 0, 0)
        }
        filmPlate.addView(filmLabel)
        filmPlate.addView(filmTagline)
        viewfinderFrame.addView(filmPlate, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(15), if (landscape) dp(13) else dp(124), 0, 0)
        })
        hintLabel = text("", 10f, 0xFFF6E8D4.toInt(), Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(7), dp(11), dp(7))
            background = chromePanel(0xA60E0F12.toInt(), 11, 0x504F545C)
        }
        viewfinderFrame.addView(hintLabel, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply {
            bottomMargin = if (landscape) dp(15) else dp(254)
        })

        val chrome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = chromePanel(0xF016171C.toInt(), 25, 0xFF5E5E67.toInt())
            elevation = dp(10).toFloat()
            clipChildren = false
        }

        // Top brand row; flashes and Night have permanent, discoverable locations.
        val brand = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(7), dp(13), dp(4))
        }
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brandText.addView(text("VORTEX", if (landscape) 16f else 19f,
            0xFFF4F0EA.toInt(), Typeface.BOLD).apply {
            letterSpacing = 0.18f
        })
        brandText.addView(text("R E T R O   C A M E R A", 8f, 0xFFC3AB91.toInt()).apply {
            letterSpacing = 0.06f
        })
        brand.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        exposureDisplay = text("EXPO 000", 10f, 0xFFE6CCAA.toInt(), Typeface.BOLD).apply {
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            background = chromePanel(0xFF2A2B30.toInt(), 9, 0xFF50505A.toInt())
            setPadding(dp(7), dp(8), dp(7), dp(8))
        }
        brand.addView(exposureDisplay, LinearLayout.LayoutParams(-2, -2))
        chrome.addView(brand)
        val topHairline = View(this).apply { setBackgroundColor(0xFF323238.toInt()) }
        chrome.addView(topHairline, LinearLayout.LayoutParams(-1, dp(1)).apply {
            setMargins(dp(13), 0, dp(13), dp(5))
        })

        flashButton = premiumControl("⚡ OFF", "Flash físico", "Flash")
        flashButton.setOnClickListener { cycleFlash() }
        nightButton = premiumControl("☾ NOITE", "Ativar modo noturno", "Noite")
        nightButton.setOnClickListener { toggleNight() }
        focusControlButton = premiumControl("◎ FOCO", "Foco automático e manual", "Foco")
        focusControlButton.setOnClickListener { showFocusSheet() }
        val lightButton = premiumControl("◐ LUZ", "Brilho, contraste, sombras e cor", "Laboratório de luz")
        lightButton.setOnClickListener { showGradeSheet() }

        if (landscape) {
            // Narrow rail: 2 × 2 fixed quick-action grid above the scroll area.
            val rowA = LinearLayout(this)
            val rowB = LinearLayout(this)
            rowA.addView(nightButton, controlWeight())
            rowA.addView(flashButton, controlWeight())
            rowB.addView(focusControlButton, controlWeight())
            rowB.addView(lightButton, controlWeight())
            chrome.addView(rowA)
            chrome.addView(rowB)
        } else {
            val actionLine = LinearLayout(this).apply {
                setPadding(dp(7), 0, dp(7), dp(7))
            }
            listOf(nightButton, flashButton, focusControlButton, lightButton).forEach {
                actionLine.addView(it, LinearLayout.LayoutParams(0, dp(49), 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                })
            }
            chrome.addView(actionLine)
        }

        // Film selection, mode carousel, image gallery, and central shutter.
        val filmAndModes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(5), dp(7), dp(2))
        }
        val filmHeading = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), 0, dp(6), dp(4))
        }
        filmHeading.addView(text("FILM STOCK", 10f, 0xFFDBBF9F.toInt(), Typeface.BOLD).apply {
            letterSpacing = .12f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        filmHeading.addView(text("35MM  •  2000s", 9f, muted))
        filmAndModes.addView(filmHeading)
        val filmScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            fillViewport = false
        }
        presetStrip = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        filmScroll.addView(presetStrip)
        filmAndModes.addView(filmScroll, LinearLayout.LayoutParams(-1, dp(47)))
        buildPresetChips()

        val selectorLabel = text("CAPTURE MODE", 9f, muted, Typeface.BOLD).apply {
            letterSpacing = 0.11f
            setPadding(dp(9), dp(7), 0, dp(2))
        }
        filmAndModes.addView(selectorLabel)
        modeStrip = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(2), dp(2), dp(5))
            background = chromePanel(0xFF121317.toInt(), 14)
        }
        Mode.entries.forEach { m ->
            val button = premiumControl(
                when (m) {
                    Mode.FOTO -> "FOTO"
                    Mode.PANORAMA -> "PANO"
                    Mode.PRO -> "PRO"
                    Mode.VIDEO -> "VÍDEO"
                },
                "Modo " + m.name.lowercase(Locale.ROOT),
                "Modo " + m.name
            ).apply {
                tag = m
                textSize = 10f
                setOnClickListener {
                    setMode(m)
                    if (m == Mode.PRO) showProSheet()
                }
            }
            modeStrip.addView(button, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                setMargins(dp(2), dp(2), dp(2), 0)
            })
        }
        filmAndModes.addView(modeStrip)

        proPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        buildProPanel()

        val captureDock = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(3), dp(13), dp(3))
        }
        val galleryButton = FrameLayout(this).apply {
            background = chromePanel(0xFF303038.toInt(), 15, 0xFFB4A08A.toInt())
            elevation = dp(4).toFloat()
            clipChildren = true
            clipToOutline = true
            isClickable = true
            isFocusable = true
            contentDescription = "Abrir galeria de fotos tiradas"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, GalleryActivity::class.java))
            }
        }
        galleryThumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(android.R.drawable.ic_menu_gallery)
            background = rounded(0xFF292A30.toInt(), 11)
            clipToOutline = true
            contentDescription = "Última fotografia salva"
        }
        galleryButton.addView(galleryThumb, FrameLayout.LayoutParams(-1, -1).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        })
        captureDock.addView(galleryButton, LinearLayout.LayoutParams(dp(62), dp(62)))

        val shutterContainer = FrameLayout(this).apply {
            contentDescription = "Disparador da câmera"
        }
        shutterContainer.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(0xFFDBB98C.toInt(), 0xFF45444A.toInt(), 0xFFFAE4C3.toInt())
                gradientType = GradientDrawable.LINEAR_GRADIENT
                setStroke(dp(2), 0xFFE3D3B7.toInt())
            }
            elevation = dp(3).toFloat()
        }, FrameLayout.LayoutParams(dp(83), dp(83), Gravity.CENTER))
        shutterButton = Button(this).apply {
            text = ""
            contentDescription = "Tirar fotografia"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(0xFFF8F6F1.toInt(), 0xFFBABEC5.toInt())
                gradientType = GradientDrawable.LINEAR_GRADIENT
                setStroke(dp(2), 0xFF515158.toInt())
            }
            elevation = dp(6).toFloat()
            setOnClickListener {
                if (mode == Mode.FOTO || mode == Mode.PRO) takePhoto()
                else toast("Este modo ainda está em desenvolvimento.")
            }
        }
        shutterContainer.addView(shutterButton, FrameLayout.LayoutParams(dp(68), dp(68), Gravity.CENTER))
        captureDock.addView(shutterContainer, LinearLayout.LayoutParams(0, dp(92), 1f))

        val flip = premiumControl("↻", "Alternar câmera frontal e traseira", "Trocar câmera")
        flip.textSize = 27f
        flip.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        }
        captureDock.addView(flip, LinearLayout.LayoutParams(dp(62), dp(62)))

        saveLabel = text("A T U A L   •   LOCAL / OFFLINE", 9f, muted).apply {
            letterSpacing = .09f
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(8), 0, dp(8), dp(9))
        }

        if (landscape) {
            val centerScroll = ScrollView(this).apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(filmAndModes)
            }
            chrome.addView(centerScroll, LinearLayout.LayoutParams(-1, 0, 1f))
            chrome.addView(captureDock)
            chrome.addView(saveLabel)
            root.addView(chrome, FrameLayout.LayoutParams(dp(236), -1, Gravity.RIGHT).apply {
                setMargins(0, dp(5), dp(6), dp(5))
            })
        } else {
            chrome.addView(filmAndModes)
            chrome.addView(captureDock)
            chrome.addView(saveLabel)
            val lower = chrome
            root.addView(lower, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                setMargins(dp(7), 0, dp(7), dp(5))
            })
            // Top glass overlay is independent from bottom and cannot scroll away.
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = chromePanel(0xDD15161A.toInt(), 18, 0x884F5159.toInt())
            }
            // Move header and always-visible quick actions into their own top card.
            chrome.removeView(brand)
            chrome.removeView(topHairline)
            val quick = chrome.getChildAt(0)
            chrome.removeView(quick)
            top.addView(brand)
            top.addView(topHairline)
            top.addView(quick)
            root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
                setMargins(dp(9), dp(6), dp(9), 0)
            })
        }

        setMode(mode)
        updateFilm()
        refreshGalleryThumbnail()
        updateNightButton()
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
                val limit = (resources.displayMetrics.heightPixels * 0.63f).toInt()
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
        nightButton.text = if (nightMode) "☾ LIGADO" else "☾ NOITE"
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
                focusControlButton.text = "◎ FOCO  AF"
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
                    focusControlButton.text = "◎ FOCO  MF"
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
            toast("Foco manual ativo. Ajusta pelo botão FOCO.")
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
        FilmCatalog.presets.forEachIndexed { index, preset ->
            val active = index == selectedPreset
            val chip = Button(this).apply {
                text = preset.name.replace("KODAK ", "K ").replace("FUJI ", "F ")
                textSize = 10f
                isAllCaps = false
                setTextColor(if (active) bg else 0xFFD1CBC5.toInt())
                background = rounded(
                    if (active) accent else 0xFF2B2D33.toInt(),
                    11,
                    if (active) 0xFFFFDEB9.toInt() else 0xFF4F4F59.toInt()
                )
                setOnClickListener { selectedPreset = index; updateFilm(); buildPresetChips() }
            }
            presetStrip.addView(chip, LinearLayout.LayoutParams(-2, dp(49)).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
    }

    private fun setMode(next: Mode) {
        val leavingNight = nightMode && next == Mode.PRO
        if (leavingNight) {
            nightMode = false
            nightUsingOem = false
            if (::nightButton.isInitialized) nightButton.text = "☾ NOITE"
        }
        mode = next
        if (leavingNight) startCamera()
        for (i in 0 until modeStrip.childCount) {
            val btn = modeStrip.getChildAt(i) as Button
            val selected = btn.tag == mode
            btn.setTextColor(if (selected) bg else muted)
            btn.background = rounded(
                if (selected) accent else 0xFF27272D.toInt(), 10,
                if (selected) 0xFFF4D6AF.toInt() else 0xFF45434B.toInt()
            )
        }
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
        val isoText = text("ISO  $iso", 11f, accent)
        proPanel.addView(isoText)
        proPanel.addView(SeekBar(this).apply {
            max = 15 // ISO 100..1600
            progress = 3
            setOnSeekBarChangeListener(simpleSeek { value ->
                iso = (value + 1) * 100
                isoText.text = "ISO  $iso"
                applyManual()
            })
        })
        val shutterText = text("OBTURADOR  1/125s", 11f, accent)
        proPanel.addView(shutterText)
        val times = listOf(1_000_000L, 2_000_000L, 4_000_000L, 8_000_000L, 16_000_000L, 33_000_000L, 66_000_000L, 100_000_000L)
        val labels = listOf("1/1000", "1/500", "1/250", "1/125", "1/60", "1/30", "1/15", "1/10")
        proPanel.addView(SeekBar(this).apply {
            max = times.lastIndex
            progress = 3
            setOnSeekBarChangeListener(simpleSeek { index ->
                exposureNs = times[index]
                shutterText.text = "OBTURADOR  ${labels[index]}s"
                applyManual()
            })
        })
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
            if (mode == Mode.PRO && !nightMode) applyManual()
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
        if (!cameraControl.supportsManualSensor) {
            hintLabel.text = "PRO · este sensor não permite ISO/obturador manuais"
            return
        }
        cameraControl.apply(iso, exposureNs)
        hintLabel.text = "PRO · ISO $iso · exposição ${exposureNs / 1_000_000} ms"
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
