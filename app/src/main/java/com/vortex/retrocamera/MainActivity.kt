package com.vortex.retrocamera

import android.Manifest
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.vortex.retrocamera.camera.ManualCamera
import com.vortex.retrocamera.film.FilmCatalog
import com.vortex.retrocamera.film.FilmPreset
import com.vortex.retrocamera.film.FilmProcessor
import com.vortex.retrocamera.film.FilmPresentation
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Vortex Retro Camera v0.1: native CameraX capture + offline film JPEG processing.
 * Pano/video are intentionally not active before a film-aware pipeline exists.
 */
class MainActivity : ComponentActivity() {
    private enum class Mode { FOTO, PANORAMA, PRO, VIDEO }

    private val bg = Color.rgb(11, 13, 18)
    private val accent = Color.rgb(230, 199, 160)
    private val muted = Color.rgb(156, 156, 164)
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var selectedPreset = 0
    private var halfFrame = false
    private var dateStamp = false
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
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildInterface()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        setContentView(root)

        // Physical-camera-inspired matte graphite chassis.
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(11), dp(18), dp(11))
            background = rounded(0xFF1F2025.toInt(), 14, 0xFF55525A.toInt())
        }
        val brandRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        brandRow.addView(text("●", 12f, 0xFFDB503B.toInt()))
        brandRow.addView(text("  VORTEX", 19f, Color.WHITE, Typeface.BOLD).apply {
            letterSpacing = 0.12f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        exposureDisplay = text("EXPO 000", 11f, accent, Typeface.BOLD).apply {
            typeface = Typeface.MONOSPACE
        }
        brandRow.addView(exposureDisplay)
        topBar.addView(brandRow)
        topBar.addView(text("RETRO CAMERA    •    FILM SERIES 2000", 9f, muted).apply {
            letterSpacing = 0.12f
        })
        root.addView(topBar, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(9), dp(4), dp(9), dp(8))
        })

        val viewFrame = FrameLayout(this).apply {
            background = rounded(0xFF050506.toInt(), 20, 0xFF706C70.toInt())
            clipToOutline = true
        }
        root.addView(viewFrame, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            setMargins(dp(10), 0, dp(10), dp(5))
        })
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Color.BLACK)
        }
        viewFrame.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        previewTint = View(this).apply { isClickable = false }
        viewFrame.addView(previewTint, FrameLayout.LayoutParams(-1, -1))
        frameGuide = RetroViewfinderOverlay(this).apply { isClickable = false }
        viewFrame.addView(frameGuide, FrameLayout.LayoutParams(-1, -1))

        val filmBadge = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xC20B0C0F.toInt(), 9, 0x664C4C52)
            setPadding(dp(11), dp(8), dp(11), dp(8))
        }
        filmLabel = text("KODAK GOLD 200", 12f, Color.WHITE, Typeface.BOLD).apply {
            typeface = Typeface.MONOSPACE
        }
        filmTagline = text("SOL DE VERÃO • 2001", 9f, 0xFFDFC4A0.toInt())
        filmBadge.addView(filmLabel)
        filmBadge.addView(filmTagline)
        viewFrame.addView(filmBadge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(12), dp(12), dp(12), dp(12))
        })
        hintLabel = text("PRÉVIA RETRÔ APROXIMADA • EFEITO FINAL AO SALVAR", 9f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            background = rounded(0xAB0B0C10.toInt(), 9)
            setPadding(dp(6), dp(8), dp(6), dp(8))
        }
        viewFrame.addView(hintLabel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(12))
        })

        val stripHeading = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(16), 0)
        }
        stripHeading.addView(text("ESCOLHA A PELÍCULA", 10f, 0xFFE6C9A7.toInt(), Typeface.BOLD).apply {
            letterSpacing = 0.12f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        stripHeading.addView(text("35 MM  /  RETRÔ", 9f, muted))
        root.addView(stripHeading)
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        presetStrip = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(3), dp(7), dp(3))
        }
        scroll.addView(presetStrip)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(57)))
        buildPresetChips()

        val settingsRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), dp(3))
        }
        settingsRow.addView(smallButton("HALF FRAME OFF") {
            halfFrame = !halfFrame
            frameGuide.halfFrame = halfFrame
            it.text = if (halfFrame) "HALF FRAME ON" else "HALF FRAME OFF"
        }, LinearLayout.LayoutParams(0, dp(38), 1f))
        settingsRow.addView(smallButton("DATA OFF") {
            dateStamp = !dateStamp
            it.text = if (dateStamp) "DATA ON" else "DATA OFF"
        }, LinearLayout.LayoutParams(0, dp(38), 1f))
        settingsRow.addView(smallButton("◷ 0s") {
            timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
            it.text = "◷ " + timerSeconds + "s"
        }, LinearLayout.LayoutParams(0, dp(38), 1f))
        root.addView(settingsRow)

        modeStrip = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(13), dp(2), dp(13), dp(2))
        }
        root.addView(modeStrip)
        for (m in Mode.entries) {
            val button = Button(this).apply {
                tag = m
                text = when (m) {
                    Mode.FOTO -> "FOTO"
                    Mode.PANORAMA -> "PANORAMA"
                    Mode.PRO -> "PRO"
                    Mode.VIDEO -> "VÍDEO"
                }
                isAllCaps = false
                textSize = 10f
                background = rounded(0xFF27272D.toInt(), 10, 0xFF45434B.toInt())
                setOnClickListener { setMode(m) }
            }
            modeStrip.addView(button, LinearLayout.LayoutParams(0, dp(39), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }

        proPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
            visibility = View.GONE
        }
        root.addView(proPanel)
        buildProPanel()

        val actionRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(5), dp(13), dp(5))
            background = rounded(0xFF202126.toInt(), 20, 0xFF47464D.toInt())
        }
        val galleryButton = FrameLayout(this).apply {
            background = rounded(0xFF333237.toInt(), 13, accent)
            isClickable = true
            isFocusable = true
            contentDescription = "Abrir as fotos tiradas no Vortex Retro Camera"
            setOnClickListener { startActivity(Intent(this@MainActivity, GalleryActivity::class.java)) }
        }
        galleryThumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Galeria de fotografias"
            setImageResource(android.R.drawable.ic_menu_gallery)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        galleryButton.addView(galleryThumb, FrameLayout.LayoutParams(-1, -1))
        actionRow.addView(galleryButton, LinearLayout.LayoutParams(dp(59), dp(59)))

        val center = FrameLayout(this)
        shutterButton = Button(this).apply {
            text = "●"
            textSize = 32f
            setTextColor(0xFF25252A.toInt())
            contentDescription = "Disparar fotografia"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFF2EDE4.toInt())
                setStroke(dp(5), 0xFFA8A3A0.toInt())
            }
            setOnClickListener {
                if (mode == Mode.FOTO || mode == Mode.PRO) takePhoto()
                else toast("Este modo ainda está em desenvolvimento.")
            }
        }
        center.addView(shutterButton, FrameLayout.LayoutParams(dp(78), dp(78), Gravity.CENTER))
        actionRow.addView(center, LinearLayout.LayoutParams(0, dp(84), 1f))
        actionRow.addView(smallButton("⟳") {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        }.apply { contentDescription = "Alternar câmera frontal e traseira"; textSize = 24f },
            LinearLayout.LayoutParams(dp(59), dp(59)))
        root.addView(actionRow, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(10), dp(2), dp(10), dp(4))
        })

        saveLabel = text("TOQUE NA MINIATURA PARA ABRIR SUAS FOTOS", 9f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(saveLabel)
        setMode(Mode.FOTO)
        updateFilm()
        refreshGalleryThumbnail()
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
        mode = next
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
        if (mode == Mode.PRO) applyManual() else manual?.reset()
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
        val contrast = (preset.contrast * 0.91f).coerceIn(0.76f, 1.32f)
        val offset = (1f - contrast) * 128f + preset.lift * 110f
        val colorMatrix = floatArrayOf(
            preset.red * contrast, 0f, 0f, 0f, offset,
            0f, preset.green * contrast, 0f, 0f, offset,
            0f, 0f, preset.blue * contrast, 0f, offset,
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
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val photo = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                    .build()
                provider.unbindAll()
                val bound = provider.bindToLifecycle(this, selector, preview, photo)
                capture = photo
                activeCamera = bound
                manual = ManualCamera(bound)
                if (mode == Mode.PRO) applyManual()
            } catch (e: Exception) {
                toast("Não foi possível iniciar a câmera: ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(this))
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

    private fun captureNow(imageCapture: ImageCapture) {
        if (saving || isFinishing || isDestroyed) return
        saving = true
        shutterButton.isEnabled = false
        saveLabel.text = "PROCESSANDO FILME…"
        val preset: FilmPreset = FilmCatalog.presets[selectedPreset]
        val useHalfFrame = halfFrame
        val useDateStamp = dateStamp
        val capturedAt = System.currentTimeMillis()
        val tempFile = File.createTempFile("vortex-", ".jpg", cacheDir)
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(tempFile).build(), io,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        val source = FilmProcessor.readUpright(tempFile)
                        val result = try { FilmProcessor.process(source, preset) }
                        finally { source.recycle() }
                        try {
                            val presented = FilmPresentation.render(
                                result, useHalfFrame, useDateStamp, capturedAt
                            )
                            try {
                                val photoUri = saveToGallery(presented)
                                runOnUiThread { onPhotoSaved(photoUri) }
                            } finally {
                                if (presented !== result) presented.recycle()
                            }
                        } finally { result.recycle() }
                    } catch (e: Exception) {
                        runOnUiThread { finishCapture("Falha ao salvar foto"); toast(e.localizedMessage ?: "Erro") }
                    } finally { tempFile.delete() }
                }
                override fun onError(exception: ImageCaptureException) {
                    tempFile.delete()
                    runOnUiThread { finishCapture("Erro ao fotografar"); toast(exception.localizedMessage ?: "Erro") }
                }
            }
        )
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
                if (!isFinishing && !isDestroyed) galleryThumb.setImageBitmap(bitmap)
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
        io.shutdown()
        super.onDestroy()
    }
}
