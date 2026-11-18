package dev.pi0trdotsys.homebrewweather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
    fun icon(context: Context, kind: String, sizePx: Int, frame: Int, light: Boolean = false): Bitmap =
        sized(context, PixelIcons.render(kind, max(4, sizePx), frame, light))

    /** The curve's stretch: how many hourly points it plots, "now" included. */
    const val CURVE_HOURS = 24

    /** Fewer valid temperatures than this and there is no curve worth drawing. */
    const val CURVE_MIN_POINTS = 6

    /**
     * The next 24 hours as one line: temperature as the curve, rain as columns
     * rising from the floor, "now" as the white square at the left end.
     *
     * Replaces the four per-day range bars of the full density. Those restated
     * the day's two figures printed right above them — on a settled spell all
     * four came out the same bar — while this shows what the numbers cannot: the
     * shape of the day, when it is warmest, and when the rain falls against it.
     * It is the one view in the widget that answers "when" for temperature the
     * way the rain windows do for rain.
     *
     * Same pixel language as the icons and the rain row: hard-edged lines
     * (no antialiasing), labels in [PixelFont]. Cyan line like the night figure,
     * the high labelled in amber like the day figure; rain columns are cyan, a
     * storm's amber, translucent so the line stays readable over them. A column
     * is as tall as the hour's rainfall when it is known (4 mm/h and up is
     * full height, the downpour line), else a fixed middling height.
     *
     * Returns null without [CURVE_MIN_POINTS] usable temperatures.
     *
     * The line and the two labels take their colour from [TempColor] — the same
     * scale as the hero number — so the curve itself reads cold-to-warm along its
     * length. Hours after dark get a faint blue band behind everything, and the
     * sun gets a small marker at the minute it rises and sets, so the curve says
     * "when it gets dark" as well as "how warm".
     *
     * @param hours the forecast hours from now; the first [CURVE_HOURS] are used.
     * @param daily where the sunrise and sunset markers come from (the first two days).
     * @param pal what to draw with; on the paper appearance every colour is darkened to
     *   read on light, and the label plates are paper. Null is the dark widget.
     */
    fun tempCurve(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        hours: List<WeatherApi.HourlyEntry>,
        daily: List<WeatherApi.DailyEntry> = emptyList(),
        pal: Palette? = null,
    ): Bitmap? {
        val pts = hours.take(CURVE_HOURS)
        if (pts.count { !it.temperature.isNaN() } < CURVE_MIN_POINTS) return null

        val w = max(40, widthPx)
        val h = max(16, heightPx)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val density = context.resources.displayMetrics.density
        val px = max(1f, density.roundToInt().toFloat())
        val paint = Paint().apply {
            isAntiAlias = false
            style = Paint.Style.FILL
        }

        val paper = pal?.paper == true
        // A colour for the line and fine marks (3:1 is enough for graphics), for text (4.5:1),
        // and with a given alpha for the translucent fills.
        fun fine(c: Int) = pal?.adapt(c, 3.0) ?: c
        fun text(c: Int) = pal?.adapt(c) ?: c
        fun alpha(c: Int, a: Int) = (a shl 24) or (c and 0xFFFFFF)
        val amber = fine(Color.parseColor("#ffb000"))
        val cyanInk = fine(Color.parseColor("#55ffff"))
        val amberInk = fine(Color.parseColor("#ffb000"))
        val ink = if (paper) 0xFF1B261B.toInt() else Color.WHITE
        val padX = (3 * px)
        val floor = h - px
        val scale = max(1, ((h * 0.3f) / PixelFont.ROWS).toInt())
        val labelH = PixelFont.ROWS * scale
        // Leave room above the plot for the high's label, none below the floor.
        val top = labelH + px
        val plotH = max(4f, floor - top)

        val n = pts.size
        fun xOf(i: Int) = padX + i * (w - 2 * padX) / max(1, n - 1)
        fun xOfF(i: Float) = padX + i * (w - 2 * padX) / max(1, n - 1)

        val known = pts.mapNotNull { it.temperature.takeIf { t -> !t.isNaN() } }
        var lo = known.min()
        var hi = known.max()
        if (hi - lo < 2.0) { // a near-flat day shouldn't look like a mountain range
            val mid = (hi + lo) / 2
            lo = mid - 1.0
            hi = mid + 1.0
        }
        fun yOf(t: Double) = (top + (hi - t) / (hi - lo) * plotH).toFloat()

        // Night: a faint band behind everything, for each hour after dark.
        paint.color = if (paper) 0x263B5BC8 else 0x1E5577FF
        pts.forEachIndexed { i, hour ->
            if (hour.isDay == false) {
                canvas.drawRect(maxOf(0f, xOfF(i - 0.5f)), top - px, minOf(w.toFloat(), xOfF(i + 0.5f)), floor, paint)
            }
        }

        // Rain columns first, so the line is drawn over them.
        pts.forEachIndexed { i, hour ->
            if (hour.precipitationProbability < RainWindow.LIKELY_POP) return@forEachIndexed
            val frac = if (hour.precipitationMm.isNaN()) 0.35f
            else (hour.precipitationMm / RainWindow.HEAVY_FROM_MM).toFloat().coerceIn(0.15f, 1f)
            val colH = max(2f, frac * plotH * 0.7f)
            val storm = Wmo.wmoToKind(hour.weatherCode) == "thunder"
            paint.color = alpha(if (storm) amberInk else cyanInk, 0x66)
            val x = xOf(i)
            val half = max(px, (w - 2 * padX) / max(1, n - 1) / 2f - px)
            canvas.drawRect(x - half, floor - colH, x + half, floor, paint)
        }

        // Floor, with a tick every six hours (by the location's own clock).
        paint.color = if (paper) 0x442B3B2B else 0x3355FFFF
        canvas.drawRect(0f, floor, w.toFloat(), floor + px, paint)
        pts.forEachIndexed { i, hour ->
            val hh = hour.time.substringAfter('T', "").take(2).toIntOrNull() ?: return@forEachIndexed
            if (hh % 6 == 0) canvas.drawRect(xOf(i), floor - 2 * px, xOf(i) + px, floor, paint)
        }

        // The line, hour to hour; a missing hour is bridged to the next known one.
        // Each stretch is coloured by the mean temperature of its two ends.
        paint.strokeWidth = px
        var prev: Triple<Float, Float, Double>? = null
        pts.forEachIndexed { i, hour ->
            if (hour.temperature.isNaN()) return@forEachIndexed
            val here = Triple(xOf(i), yOf(hour.temperature), hour.temperature)
            prev?.let {
                paint.color = fine(TempColor.argb((it.third + here.third) / 2))
                canvas.drawLine(it.first, it.second, here.first, here.second, paint)
            }
            prev = here
        }

        // The sun: a small marker where it rises and sets inside the window.
        val first = LocalIso.minutes(pts[0].time)
        if (first != null) {
            paint.color = amber
            daily.take(2).flatMap { listOfNotNull(it.sunrise, it.sunset) }.forEach { iso ->
                val at = LocalIso.minutes(iso)?.let { (it - first) / 60f } ?: return@forEach
                if (at < 0f || at > n - 1) return@forEach
                val cx = xOfF(at)
                val cy = floor - 3 * px
                canvas.drawRect(cx - px, cy - px, cx + 2 * px, cy + 2 * px, paint) // the disc
                canvas.drawRect(cx, cy - 3 * px, cx + px, cy - 2 * px, paint) // rays: up,
                canvas.drawRect(cx, cy + 2 * px, cx + px, cy + 3 * px, paint) // down,
                canvas.drawRect(cx - 3 * px, cy, cx - 2 * px, cy + px, paint) // left,
                canvas.drawRect(cx + 3 * px, cy, cx + 4 * px, cy + px, paint) // right
            }
        }

        // "Now": a white square on the first point.
        pts.indexOfFirst { !it.temperature.isNaN() }.takeIf { it >= 0 }?.let { i ->
            paint.color = ink
            val x = xOf(i)
            val y = yOf(pts[i].temperature)
            canvas.drawRect(x - px, y - px, x + 2 * px, y + 2 * px, paint)
        }

        // Labels at the high and the low, in whole degrees.
        fun label(text: String, color: Int, cx: Float, y: Float) {
            val glyphs = PixelFont.render(text.replace('-', '–'), color, scale, labelH)
            val x = (cx - glyphs.width / 2f).coerceIn(0f, (w - glyphs.width).toFloat())
            val yy = y.coerceIn(0f, (h - labelH).toFloat())
            // A plate in the widget's own background colour, so the label stays
            // readable where the line passes behind it.
            paint.color = alpha(pal?.background ?: Palette.DARK_BG, 0xE6)
            canvas.drawRect(x - px, yy, x + glyphs.width + px, yy + labelH, paint)
            canvas.drawBitmap(glyphs, x, yy, null)
        }
        val iHi = pts.indices.filter { !pts[it].temperature.isNaN() }.maxBy { pts[it].temperature }
        val iLo = pts.indices.filter { !pts[it].temperature.isNaN() }.minBy { pts[it].temperature }
        label("${Math.round(pts[iHi].temperature)}°", text(TempColor.argb(pts[iHi].temperature)), xOf(iHi), yOf(pts[iHi].temperature) - labelH - px)
        if (iLo != iHi) {
            label("${Math.round(pts[iLo].temperature)}°", text(TempColor.argb(pts[iLo].temperature)), xOf(iLo), yOf(pts[iLo].temperature) + 2 * px)
        }

        return sized(context, bmp)
    }

    fun dpToPx(context: Context, dp: Float): Int =
        (dp * context.resources.displayMetrics.density).roundToInt()
}
