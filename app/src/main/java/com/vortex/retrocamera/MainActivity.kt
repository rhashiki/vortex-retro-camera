package com.vortex.retrocamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(12))
            addView(text("VORTEX", 23f, Color.WHITE, Typeface.BOLD))
            addView(text("RETRO CAMERA  ·  by Vortex", 10f, muted).apply { letterSpacing = .14f })
        })

        val viewport = FrameLayout(this)
        root.addView(viewport, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Color.BLACK)
        }
        viewport.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        previewTint = View(this).apply { isClickable = false }
        viewport.addView(previewTint, FrameLayout.LayoutParams(-1, -1))

        filmLabel = text("KODAK GOLD 200", 12f, Color.WHITE, Typeface.BOLD)
        val filmBadge = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x990B0D12.toInt())
            setPadding(dp(14), dp(9), dp(14), dp(9))
            addView(filmLabel)
            addView(text("PRÉVIA INDICATIVA · FILTRO AO SALVAR", 9f, Color.LTGRAY))
        }
        viewport.addView(filmBadge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(12), dp(14), dp(12), dp(14))
        })

        hintLabel = text("", 12f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(0xAA0B0D12.toInt())
        }
        viewport.addView(hintLabel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(12))
        })
        modeStrip = LinearLayout(this).apply {
            setPadding(dp(12), dp(12), dp(12), dp(10))
            gravity = Gravity.CENTER
        }
        root.addView(modeStrip)
        for (m in Mode.entries) {
            val button = Button(this).apply {
                text = m.name
                isAllCaps = false
                textSize = 11f
                setOnClickListener { setMode(m) }
                tag = m
            }
            modeStrip.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        presetStrip = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        scroll.addView(presetStrip)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(58)))
        buildPresetChips()

        proPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(6))
            visibility = View.GONE
        }
        root.addView(proPanel)
        buildProPanel()

        val actionRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(4), dp(18), dp(5))
        }
        val flip = Button(this).apply {
            text = "↺"
            contentDescription = "Trocar câmera"
            textSize = 21f
            setOnClickListener {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                startCamera()
            }
        }
        actionRow.addView(flip, LinearLayout.LayoutParams(0, dp(68), 1f))
        shutterButton = Button(this).apply {
            text = "●"
            textSize = 38f
            setTextColor(accent)
            contentDescription = "Fotografar"
            setOnClickListener {
                if (mode == Mode.FOTO || mode == Mode.PRO) takePhoto()
                else toast("Este modo entra em uma etapa posterior do desenvolvimento.")
            }
        }
        actionRow.addView(shutterButton, LinearLayout.LayoutParams(0, dp(74), 2f))
        val timer = Button(this).apply {
            text = "0s"
            setOnClickListener {
                timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
                text = "${timerSeconds}s"
            }
        }
        actionRow.addView(timer, LinearLayout.LayoutParams(0, dp(68), 1f))
        root.addView(actionRow)
        saveLabel = text("OFFLINE  •  SALVAR NA GALERIA", 10f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(11))
        }
        root.addView(saveLabel)
        setMode(Mode.FOTO)
        updateFilm()
    }

    private fun buildPresetChips() {
        presetStrip.removeAllViews()
        FilmCatalog.presets.forEachIndexed { index, preset ->
            val active = index == selectedPreset
            val chip = Button(this).apply {
                text = preset.name.replace("KODAK ", "K ").replace("FUJI ", "F ")
                textSize = 10f
                isAllCaps = false
                setTextColor(if (active) bg else Color.LTGRAY)
                setBackgroundColor(if (active) accent else 0xFF262932.toInt())
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
            btn.setTextColor(if (selected) accent else muted)
            btn.alpha = if (selected) 1f else .7f
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
        // Illustrative tint ONLY. Full film recipe is applied after capture.
        val tint = when {
            preset.isDigitalCcd -> Color.rgb(90, 75, 155)
            preset.blue > preset.red -> Color.rgb(43, 97, 89)
            else -> Color.rgb(160, 104, 44)
        }
        previewTint.setBackgroundColor(tint)
        previewTint.alpha = .10f
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
        val tempFile = File.createTempFile("vortex-", ".jpg", cacheDir)
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(tempFile).build(), io,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        val source = FilmProcessor.readUpright(tempFile)
                        val result = try { FilmProcessor.process(source, preset) }
                        finally { source.recycle() }
                        try { saveToGallery(result) }
                        finally { result.recycle() }
                        runOnUiThread { finishCapture("FOTO SALVA NA GALERIA ✓") }
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

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        io.shutdown()
        super.onDestroy()
    }
}
