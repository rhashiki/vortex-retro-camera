package com.vortex.retrocamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.vortex.retrocamera.film.FilmCatalog

/**
 * Native, collapsible, image-led film picker. It is GONE until explicitly opened.
 * Previews are supplied by the host using the actual film processor on a small
 * snapshot of CameraX's viewport; no stock images and no network requests.
 */
class FilmFilterDrawer(
    context: Context,
    private val onSelect: (Int) -> Unit,
    private val onClose: () -> Unit
) : LinearLayout(context) {
    private data class Tile(val border: FrameLayout, val image: ImageView, val label: TextView)
    private val tiles = mutableListOf<Tile>()
    private val scroller = HorizontalScrollView(context)
    private val strip = LinearLayout(context)
    private val subtitle = TextView(context)
    private var selected = 0

    init {
        orientation = VERTICAL
        visibility = View.GONE
        contentDescription = "Carrossel de filtros e efeitos"
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dp(1), 0, dp(1), 0)
        clipChildren = false

        // Header and close action are owned by the camera's single, tappable
        // "Filtros & Efeitos" bar; do not duplicate them inside the drawer.
        scroller.isHorizontalScrollBarEnabled = false
        scroller.overScrollMode = View.OVER_SCROLL_NEVER
        scroller.isFillViewport = false
        strip.orientation = HORIZONTAL
        strip.gravity = Gravity.CENTER_VERTICAL
        strip.setPadding(dp(2), dp(5), dp(2), dp(4))
        scroller.addView(strip)
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, dp(116)))

        FilmCatalog.presets.forEachIndexed { index, preset ->
            val holder = FrameLayout(context).apply {
                isClickable = true
                isFocusable = true
                contentDescription = "Película " + preset.name
                setOnClickListener { onSelect(index) }
            }
            val picture = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF3B2459.toInt(), 0xFF17111F.toInt())
                ).apply { cornerRadius = dp(10).toFloat() }
                clipToOutline = true
                contentDescription = "Prévia do filtro " + preset.name
            }
            holder.addView(picture, FrameLayout.LayoutParams(-1, -1).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
            val fade = View(context).apply {
                isClickable = false
                background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(
                    0xEE0A0810.toInt(), 0x440A0810, Color.TRANSPARENT
                ))
            }
            holder.addView(fade, FrameLayout.LayoutParams(-1, dp(38), Gravity.BOTTOM))
            val name = TextView(context).apply {
                text = shortName(preset.name)
                textSize = 10f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(Color.WHITE)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            holder.addView(name, FrameLayout.LayoutParams(-1, dp(24), Gravity.BOTTOM).apply {
                setMargins(dp(6), 0, dp(6), dp(3))
            })
            strip.addView(holder, LayoutParams(dp(98), dp(104)).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
            tiles.add(Tile(holder, picture, name))
        }
        setSelected(0, false)
    }

    fun setSelected(index: Int, scrollToSelection: Boolean = true) {
        selected = index.coerceIn(0, tiles.lastIndex)
        tiles.forEachIndexed { i, tile ->
            val active = i == selected
            tile.border.background = panel(
                if (active) 0xFF3F145E.toInt() else 0xFF17121D.toInt(),
                13,
                if (active) 0xFFC35DFF.toInt() else 0xFF4B365C.toInt(),
                if (active) 3 else 1
            )
            tile.border.elevation = if (active) dp(6).toFloat() else 0f
            tile.border.contentDescription = "Película " +
                FilmCatalog.presets[i].name + if (active) ", selecionada" else ""
        }
        if (scrollToSelection && visibility == View.VISIBLE) {
            val current = tiles[selected].border
            scroller.post {
                if (current.parent === strip) {
                    scroller.smoothScrollTo(
                        (current.left + current.width / 2 - scroller.width / 2).coerceAtLeast(0), 0
                    )
                }
            }
        }
    }

    /** Receives ownership of one small thumbnail bitmap per film while the panel is open. */
    fun showPreview(index: Int, image: Bitmap) {
        if (index !in tiles.indices) return
        tiles[index].image.setImageBitmap(image)
    }

    fun showPreviewUnavailable() {
        subtitle.text = "Aguardando imagem da câmera"
    }

    fun showPreviewReady() {
        subtitle.text = "Prévia aproximada de cada película"
    }

    fun clearPreviews() {
        tiles.forEach { it.image.setImageDrawable(null) }
        subtitle.text = "Prévia do visor • toque para aplicar"
    }

    private fun shortName(value: String): String = value
        .replace("KODAK ", "Kodak ")
        .replace("ULTRAMAX ", "UltraMax ")
        .replace("FUJI ", "Fuji ")
        .replace("FUJICOLOR ", "Fuji ")
        .replace("SUPERIA ", "Superia ")
        .replace("VORTEX ", "Vortex ")
        .replace("SONY ", "Sony ")

    private fun panel(fill: Int, radius: Int, stroke: Int, thickness: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(fill, 0xFF0C0A13.toInt())).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(thickness), stroke)
        }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
}
