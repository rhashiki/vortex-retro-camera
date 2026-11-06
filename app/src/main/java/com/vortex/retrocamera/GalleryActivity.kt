package com.vortex.retrocamera

import android.app.Dialog
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * In-app gallery of photos produced by Vortex Retro Camera.
 * Uses the app's own MediaStore files, requiring no blanket gallery permission
 * on Android 10+ (minSdk 29). No cloud, login, or external gallery app required.
 */
class GalleryActivity : ComponentActivity() {
    private val images = mutableListOf<Uri>()
    private val worker = Executors.newFixedThreadPool(2)
    private val thumbs = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = max(1, value.byteCount / 1024)
    }
    private lateinit var grid: GridView
    private lateinit var label: TextView
    private lateinit var galleryAdapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = 0xFF121315.toInt()
        window.navigationBarColor = 0xFF121315.toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121315.toInt())
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        val head = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(18), dp(14), dp(14))
        }
        head.addView(Button(this).apply {
            text = "‹"
            textSize = 22f
            contentDescription = "Voltar à câmera"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(62), dp(52)))
        val nameBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nameBlock.addView(TextView(this).apply {
            text = "REVELAÇÕES"
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            textSize = 18f
            letterSpacing = 0.12f
        })
        label = TextView(this).apply {
            text = "VORTEX  •  ALBUM"
            setTextColor(0xFFE9C69D.toInt())
            textSize = 10f
        }
        nameBlock.addView(label)
        head.addView(nameBlock, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(head)
        grid = GridView(this).apply {
            numColumns = 3
            verticalSpacing = dp(5)
            horizontalSpacing = dp(5)
            setPadding(dp(5), dp(5), dp(5), dp(5))
            clipToPadding = false
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setBackgroundColor(0xFF141417.toInt())
        }
        galleryAdapter = object : BaseAdapter() {
            override fun getCount(): Int = images.size
            override fun getItem(position: Int): Any = images[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val cell = (convertView as? ImageView) ?: ImageView(this@GalleryActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(0xFF323134.toInt())
                    layoutParams = android.widget.AbsListView.LayoutParams(-1, dp(142))
                }
                val uri = images[position]
                val key = uri.toString()
                cell.tag = key
                cell.setImageDrawable(null)
                val cached = thumbs.get(key)
                if (cached != null && !cached.isRecycled) {
                    cell.setImageBitmap(cached)
                } else {
                    worker.execute {
                        val thumb = try {
                            contentResolver.loadThumbnail(uri, Size(256, 256), null)
                        } catch (_: Exception) { null }
                        if (thumb != null) {
                            thumbs.put(key, thumb)
                            runOnUiThread {
                                if (!isFinishing && cell.tag == key) cell.setImageBitmap(thumb)
                            }
                        }
                    }
                }
                return cell
            }
        }
        grid.adapter = galleryAdapter
        grid.setOnItemClickListener { _, _, position, _ -> openViewer(position) }
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    override fun onResume() {
        super.onResume()
        loadPhotos()
    }

    private fun loadPhotos() {
        images.clear()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Images.Media._ID)
        val where = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val folder = "${Environment.DIRECTORY_PICTURES}/Vortex Retro Camera/%"
        try {
            contentResolver.query(
                collection,
                columns,
                where,
                arrayOf(folder, "Vortex_%"),
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    images.add(ContentUris.withAppendedId(collection, cursor.getLong(idIndex)))
                }
            }
        } catch (_: SecurityException) {
            Toast.makeText(this, "Sem acesso às fotos criadas pelo aplicativo.", Toast.LENGTH_LONG).show()
        }
        label.text = "${images.size} FOTOS  ·  VORTEX RETRO CAMERA"
        galleryAdapter.notifyDataSetChanged()
    }

    private fun openViewer(startPosition: Int) {
        if (images.isEmpty()) return
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0B0D.toInt())
        }
        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            contentDescription = "Fotografia selecionada"
        }
        root.addView(photo, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(4), dp(5), dp(18))
        }
        var position = startPosition
        fun display() {
            val selected = images[position]
            photo.setImageDrawable(null)
            photo.tag = selected.toString()
            worker.execute {
                val bitmap = try {
                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(contentResolver, selected)
                    ) { decoder, info, _ ->
                        val width = info.size.width
                        val height = info.size.height
                        val scale = max(width, height).toFloat() / 2200f
                        if (scale > 1f) {
                            decoder.setTargetSize((width / scale).toInt(), (height / scale).toInt())
                        }
                    }
                } catch (_: Exception) { null }
                runOnUiThread {
                    if (dialog.isShowing && photo.tag == selected.toString()) {
                        if (bitmap != null) photo.setImageBitmap(bitmap)
                        else Toast.makeText(this, "Não foi possível abrir a foto.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        val previous = Button(this).apply {
            text = "‹"
            contentDescription = "Foto anterior"
            setOnClickListener { position = (position + 1) % images.size; display() }
        }
        controls.addView(previous, LinearLayout.LayoutParams(0, dp(54), 1f))
        controls.addView(Button(this).apply {
            text = "PARTILHAR"
            textSize = 10f
            setOnClickListener {
                val uri = images[position]
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, "Compartilhar foto"))
            }
        }, LinearLayout.LayoutParams(0, dp(54), 2f))
        controls.addView(Button(this).apply {
            text = "FECHAR"
            textSize = 10f
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(0, dp(54), 2f))
        controls.addView(Button(this).apply {
            text = "›"
            contentDescription = "Próxima foto"
            setOnClickListener { position = (position - 1 + images.size) % images.size; display() }
        }, LinearLayout.LayoutParams(0, dp(54), 1f))
        root.addView(controls)
        dialog.setContentView(root)
        dialog.show()
        display()
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
