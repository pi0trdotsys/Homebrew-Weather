package dev.pi0trdotsys.homebrewweather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Small runtime-drawn widget graphics that XML drawables can't express, and
 * the density bookkeeping that makes runtime bitmaps size themselves.
 */
object WidgetGraphics {

    /**
     * Tags [bitmap] with the device's real density and returns it.
     *
     * This is what makes the widget's icons size themselves. `RemoteViews` can't
     * set a view's layout size below API 31 (`setViewLayoutWidth`/`Height` are
     * API 31+), which is exactly why the old code had to hard-code every icon's
     * dp in XML and keep a second layout file around for small widgets. But an
     * `ImageView` with `wrap_content` measures to its drawable's *intrinsic*
     * size, and a `BitmapDrawable`'s intrinsic size is
     * `bitmap.width * targetDensity / bitmap.density` — so a bitmap tagged with
     * the target density measures at exactly its own pixel count, on every API
     * level. Generate the bitmap at the pixel size you want and the view follows.
     *
     * `Bitmap.createBitmap` already defaults to `DisplayMetrics.DENSITY_DEVICE`,
     * which usually matches — but not when the user has changed display size, or
     * on a secondary display, where the default is the *device* density and the
     * resources' density is the effective one. Setting it explicitly removes the
     * difference.
     */
    fun sized(context: Context, bitmap: Bitmap): Bitmap {
        bitmap.density = context.resources.displayMetrics.densityDpi
        return bitmap
    }

    /** Renders a pixel icon at exactly [sizePx] px, ready for a wrap_content ImageView. */
    fun icon(context: Context, kind: String, sizePx: Int, frame: Int): Bitmap =
        sized(context, PixelIcons.render(kind, max(4, sizePx), frame))

    /**
     * One day column's temperature-range bar: where that day's low..high sits
     * inside the whole 4-day range.
     *
     * Reading four "24°/14°" pairs and mentally ranking them is work; four bars
     * offset against a shared track is not. It's the one piece of information
     * the grid already had but never actually *showed*, and as a row of aligned
     * segments it also gives the grid a horizontal rhythm the stacked text
     * rows never had.
     *
     * The segment is drawn cyan at the cold end grading to amber at the warm
     * end — the same cold/warm split [WeatherWidgetProvider.tempSpannable]
     * already uses for the night/day figures directly above it, so the bar
     * restates the colors rather than introducing new ones. Ends get a bright
     * 1px cap so a near-flat day (min ≈ max) is still visible as a tick instead
     * of vanishing.
     *
     * [globalMin]/[globalMax] are the extremes across all four days, so the
     * bars share one scale and can be compared to each other. A degenerate
     * range (all four days identical) falls back to a centered full-width
     * segment rather than dividing by zero.
     */
    fun rangeBar(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        dayMin: Double,
        dayMax: Double,
        globalMin: Double,
        globalMax: Double,
    ): Bitmap {
        val w = max(8, widthPx)
        val h = max(2, heightPx)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply {
            isAntiAlias = false
            style = Paint.Style.FILL
        }

        // The track is a 1px axis line rather than a filled bar. Filled, it read
        // as a second bar competing with the segment on top of it and the whole
        // row turned into indistinct stripes; as a hairline the segment sits
        // *on* a scale, which is what it actually means.
        paint.color = 0x3355FFFF
        val axisTop = ((h - 1) / 2).toFloat()
        canvas.drawRect(0f, axisTop, w.toFloat(), axisTop + 1f, paint)

        val span = globalMax - globalMin
        val (startFrac, endFrac) = if (span < 0.5) {
            0.15f to 0.85f
        } else {
            val s = ((dayMin - globalMin) / span).toFloat().coerceIn(0f, 1f)
            val e = ((dayMax - globalMin) / span).toFloat().coerceIn(0f, 1f)
            s to e
        }

        var left = startFrac * w
        var right = endFrac * w
        // Guarantee a visible segment even for a flat day.
        if (right - left < 2f) {
            val mid = (left + right) / 2f
            left = (mid - 1f).coerceAtLeast(0f)
            right = (left + 2f).coerceAtMost(w.toFloat())
        }

        val cold = Color.parseColor("#55ffff")
        val warm = Color.parseColor("#ffb000")
        paint.shader = LinearGradient(left, 0f, right, 0f, cold, warm, Shader.TileMode.CLAMP)
        canvas.drawRect(left, 0f, right, h.toFloat(), paint)
        paint.shader = null

        // End caps: 1px of the pure hue at each end, so the extremes of the
        // gradient read as deliberate ticks rather than a fade-out.
        paint.color = cold
        canvas.drawRect(left, 0f, left + 1f, h.toFloat(), paint)
        paint.color = warm
        canvas.drawRect(right - 1f, 0f, right, h.toFloat(), paint)

        return sized(context, bmp)
    }

    fun dpToPx(context: Context, dp: Float): Int =
        (dp * context.resources.displayMetrics.density).roundToInt()
}
