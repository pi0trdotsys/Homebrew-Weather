package dev.pi0trdotsys.homebrewweather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The widget's "city map" background: contour lines of the terrain around the
 * place, or — where the land is flat — a pixel skyline.
 *
 * ## Which one
 *
 * Contours need relief to mean anything. In Kielce or Estepona the lines trace
 * real hills; across Wrocław (16 m between the highest and lowest point of a 14 km
 * square) they trace noise in the elevation model and look like abstract art that
 * says nothing about the place. So below [RELIEF_MIN_M] — and while a place's
 * terrain is still being fetched, and when it can't be — the picture is a skyline
 * made from the city's name instead. Never an empty background.
 *
 * ## Kept quiet
 *
 * It sits behind text, so every stroke is the theme's own colour at low alpha
 * (and lower again with high contrast on, which is about legibility), nothing
 * brighter than the rain row. Rendered at half the widget's pixel size and scaled
 * up by the ImageView: the lines come out soft, and the bitmap costs a quarter of
 * the memory RemoteViews has to carry across processes.
 *
 * Pure decisions ([kindFor]) are tested in WidgetBackdropArtTest; the drawing is
 * checked by eye in the debug preview harness.
 */
object WidgetBackdropArt {

    /** Contours need at least this much height difference (metres) to be worth drawing. */
    const val RELIEF_MIN_M = 40

    /** The bitmap is this fraction of the widget's pixel size. */
    private const val SCALE = 0.5f

    /** Bilinear enlargement of the 24 x 12 grid before contouring. */
    private const val UPSAMPLE = 6

    enum class Kind { CONTOURS, SKYLINE }

    fun kindFor(grid: Terrain.Grid?): Kind =
        if (grid != null && grid.relief >= RELIEF_MIN_M) Kind.CONTOURS else Kind.SKYLINE

    fun render(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        color: Int,
        cityName: String,
        grid: Terrain.Grid?,
        quiet: Boolean,
    ): Bitmap {
        val w = max(16, (widthPx * SCALE).roundToInt())
        val h = max(16, (heightPx * SCALE).roundToInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val density = context.resources.displayMetrics.density * SCALE
        val damp = if (quiet) 0.55f else 1f
        when (kindFor(grid)) {
            Kind.CONTOURS -> drawContours(canvas, w, h, color, grid!!, density, damp)
            Kind.SKYLINE -> drawSkyline(canvas, w, h, color, cityName, density, damp)
        }
        return bmp
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0x00FFFFFF)

    private fun drawContours(canvas: Canvas, w: Int, h: Int, color: Int, grid: Terrain.Grid, density: Float, damp: Float) {
        val raw = FloatArray(grid.elevation.size) { grid.elevation[it].toFloat() }
        val (field, fw, fh) = Contours.upsample(Contours.smooth(raw, grid.cols, grid.rows), grid.cols, grid.rows, UPSAMPLE)
        val lo = field.min()
        val hi = field.max()
        val step = Contours.niceStep(hi - lo)

        // Fill the widget, keeping the map undistorted: scale to cover and centre.
        val scale = max(w / (fw - 1).toFloat(), h / (fh - 1).toFloat())
        val offX = (w - (fw - 1) * scale) / 2f
        val offY = (h - (fh - 1) * scale) / 2f

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        var level = Math.ceil((lo / step).toDouble()).toFloat() * step
        while (level <= hi) {
            // Every fifth line is a "major" one, as on a topographic map.
            val major = Math.round(level / step) % 5 == 0
            paint.strokeWidth = max(1f, (if (major) 1.3f else 0.8f) * density)
            paint.color = withAlpha(color, ((if (major) 0x55 else 0x30) * damp).toInt())
            for (s in Contours.segments(field, fw, fh, level)) {
                canvas.drawLine(offX + s.x1 * scale, offY + s.y1 * scale, offX + s.x2 * scale, offY + s.y2 * scale, paint)
            }
            level += step
        }
    }

    private fun drawSkyline(canvas: Canvas, w: Int, h: Int, color: Int, name: String, density: Float, damp: Float) {
        val unit = max(2, (density * 1.6f).roundToInt())
        val buildings = Skyline.buildings(name, w / unit + 1, (h * 0.36f / unit).toInt())
        val mass = Paint().apply { style = Paint.Style.FILL; this.color = withAlpha(color, (0x16 * damp).toInt()) }
        val lit = Paint().apply { style = Paint.Style.FILL; this.color = withAlpha(Color.parseColor("#ffb000"), (0x30 * damp).toInt()) }
        for (b in buildings) {
            val left = (b.x * unit).toFloat()
            val top = (h - b.height * unit).toFloat()
            canvas.drawRect(left, top, left + b.width * unit, h.toFloat(), mass)
            for ((wx, wy) in b.windows) {
                val x = left + wx * unit
                val y = top + wy * unit
                canvas.drawRect(x, y, x + unit, y + unit, lit)
            }
        }
    }
}
