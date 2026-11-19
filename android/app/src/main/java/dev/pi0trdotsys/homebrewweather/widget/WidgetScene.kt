package dev.pi0trdotsys.homebrewweather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The widget's "weather" background: a small scene behind the numbers that shows what
 * the sky is doing right now — rain streaks while it rains, snow while it snows, a
 * flash and rain in a storm, stars on a clear night — and nothing otherwise.
 *
 * ## Alive only when it has something to move
 *
 * Rain, snow and storms animate: the shared ~60 s blink tick (see [BlinkPrefs]) advances
 * the frame and the tick re-draws this scene, so the streaks are somewhere else each
 * minute. Stars are still, and every other sky is [Kind.NONE]. A widget that isn't
 * precipitating costs the tick nothing extra: [animated] is false, so it neither renders
 * nor sends the bitmap (see WeatherWidgetProvider.handleBlinkTick). No new timer, no
 * extra wake-ups — the tick already existed for the cursor.
 *
 * ## Kept quiet
 *
 * It sits behind text, so everything is low alpha in the widget's own colours, drawn in
 * chunky cells to match the pixel icons. On the paper appearance the colours are ink
 * blues, and a night's stars are left out (stars on paper make no sense).
 *
 * [kindOf] and [marks] are pure and tested (WidgetSceneTest); drawing is checked by eye
 * in the debug preview harness.
 */
object WidgetScene {

    enum class Kind {
        NONE, RAIN, SNOW, STORM, STARS,

        /** The calmer skies, drawn only by the live scene (see [liveKindOf]). */
        SUN, PARTLY, PARTLY_NIGHT, CLOUD, FOG,
    }

    /** A rectangle in grid cells, with how opaque it is (0–255); [tone] 1 draws it in the warm sun colour instead of the scene's own. */
    data class Mark(val x: Int, val y: Int, val w: Int, val h: Int, val alpha: Int, val tone: Int = 0)

    /** A storm flashes on every this-many-th frame (one a few minutes at the 60 s tick). */
    const val FLASH_EVERY = 4

    /** The bitmap is this fraction of the widget's pixel size (like the city map). */
    private const val SCALE = 0.5f

    fun kindOf(weatherKind: String, isDay: Boolean): Kind = when {
        weatherKind == "thunder" -> Kind.STORM
        weatherKind == "rain" -> Kind.RAIN
        weatherKind == "snow" -> Kind.SNOW
        !isDay && (weatherKind == "sun" || weatherKind == "partly") -> Kind.STARS
        else -> Kind.NONE
    }

    /**
     * The scene for the *live* (looping) background: every sky gets one — [kindOf]'s, plus a sun
     * with glinting rays, drifting clouds, and fog, so no weather leaves the widget still. A clear
     * night is stars; a partly cloudy one, stars and a passing cloud.
     */
    fun liveKindOf(weatherKind: String, isDay: Boolean): Kind = when (weatherKind) {
        "thunder", "rain", "snow" -> kindOf(weatherKind, isDay)
        "sun" -> if (isDay) Kind.SUN else Kind.STARS
        "partly" -> if (isDay) Kind.PARTLY else Kind.PARTLY_NIGHT
        "fog" -> Kind.FOG
        else -> Kind.CLOUD
    }

    /** Whether the scene changes from one tick to the next (and so is worth re-drawing at each). */
    fun animated(kind: Kind): Boolean = kind == Kind.RAIN || kind == Kind.SNOW || kind == Kind.STORM

    fun flashing(kind: Kind, frame: Int): Boolean = kind == Kind.STORM && Math.floorMod(frame, FLASH_EVERY) == 0

    /**
     * The scene on a [cols] x [rows] grid at [frame]. The same [seed] always gives the same
     * drops (so a redraw of the same frame is identical); [frame] moves them.
     */
    fun marks(kind: Kind, cols: Int, rows: Int, frame: Int, seed: Int): List<Mark> {
        if (kind == Kind.NONE || cols < 4 || rows < 4) return emptyList()
        val rng = Mulberry32(seed xor kind.ordinal * 0x9E3779B1.toInt())
        val out = ArrayList<Mark>()
        when (kind) {
            Kind.RAIN, Kind.STORM -> {
                val drops = (cols * rows / 70).coerceIn(8, 70)
                repeat(drops) {
                    val x0 = rng.nextInt(0, cols)
                    val y0 = rng.nextInt(0, rows)
                    val speed = rng.nextInt(5, 9) // cells per frame
                    val y = Math.floorMod(y0 + frame * speed, rows)
                    val x = Math.floorMod(x0 - frame * speed / 3, cols)
                    out += Mark(x, y, 1, 3, if (rng.nextBoolean()) 0x55 else 0x38)
                }
            }
            Kind.SNOW -> {
                val flakes = (cols * rows / 110).coerceIn(6, 50)
                repeat(flakes) { i ->
                    val x0 = rng.nextInt(0, cols)
                    val y0 = rng.nextInt(0, rows)
                    val y = Math.floorMod(y0 + frame * 3, rows)
                    val x = Math.floorMod(x0 + Math.floorMod(frame + i, 3) - 1, cols)
                    out += Mark(x, y, 1, 1, if (rng.nextBoolean()) 0x88 else 0x55)
                }
            }
            Kind.STARS -> {
                val stars = (cols * rows / 90).coerceIn(6, 40)
                repeat(stars) {
                    out += Mark(rng.nextInt(0, cols), rng.nextInt(0, rows * 3 / 4), 1, 1, rng.nextInt(0x38, 0x99))
                }
            }
            Kind.NONE, Kind.SUN, Kind.PARTLY, Kind.PARTLY_NIGHT, Kind.CLOUD, Kind.FOG -> Unit
        }
        return out
    }

    /** A jagged bolt from the top of the scene, as cells. */
    fun bolt(cols: Int, rows: Int, seed: Int): List<Mark> {
        if (cols < 8 || rows < 8) return emptyList()
        val rng = Mulberry32(seed + 17)
        var x = cols / 2 + rng.nextInt(-cols / 5, cols / 5)
        var y = 0
        val out = ArrayList<Mark>()
        while (y < rows * 2 / 3) {
            val run = rng.nextInt(2, 4)
            out += Mark(x, y, 1, run, 0x88)
            y += run
            x += if (rng.nextBoolean()) 1 else -1
        }
        return out
    }

    /**
     * The scene as a bitmap for the backdrop view, or null when the sky has none.
     * @param frame the shared blink frame; @param seed any stable per-place number.
     */
    fun render(context: Context, widthPx: Int, heightPx: Int, kind: Kind, frame: Int, seed: Int, pal: Palette): Bitmap? {
        val canvasSpec = spec(context, widthPx, heightPx, kind, pal) ?: return null
        val flash = if (flashing(kind, frame)) 2 else 0
        return canvasSpec.draw(context, marks(kind, canvasSpec.cols, canvasSpec.rows, frame, seed), flash, seed)
    }

    // ---- the looping ("live") scene ------------------------------------------------------

    /** Frames in one loop of the live scene. At the flipper's 500 ms that is a four-second loop. */
    const val LOOP_FRAMES = 8

    /** The frame a storm's flash lights; the next one holds a fainter afterglow. */
    private fun flashFrame(frames: Int) = frames * 5 / 8

    /** 0 none, 1 afterglow, 2 flash — for [frame] of a [frames]-long loop of [kind]. */
    fun loopFlash(kind: Kind, frame: Int, frames: Int = LOOP_FRAMES): Int {
        if (kind != Kind.STORM) return 0
        val f = Math.floorMod(frame, frames)
        return when (f) {
            flashFrame(frames) -> 2
            flashFrame(frames) + 1 -> 1
            else -> 0
        }
    }

    /**
     * Frame [frame] of a seamless loop of [frames]: running [frames] frames on from here
     * lands exactly where it started, so the launcher can flip through them forever.
     *
     * The rules that make it seamless: a drop falls a whole number of grid heights per loop
     * and wraps top to bottom (and its sideways drift is a function of where it is, so it
     * changes only at the wrap, when the drop re-enters at the top anyway); a flake sways by
     * a whole number of cycles; a star's glow is a whole number of sine periods.
     */
    fun loop(kind: Kind, cols: Int, rows: Int, frame: Int, frames: Int = LOOP_FRAMES, seed: Int): List<Mark> {
        if (kind == Kind.NONE || cols < 4 || rows < 4 || frames < 2) return emptyList()
        val phase = Math.floorMod(frame, frames).toDouble() / frames
        val tau = 2 * Math.PI
        val rng = Mulberry32(seed xor kind.ordinal * 0x9E3779B1.toInt())
        val out = ArrayList<Mark>()
        when (kind) {
            Kind.RAIN, Kind.STORM -> {
                val drops = (cols * rows / 70).coerceIn(8, 70)
                repeat(drops) {
                    val x0 = rng.nextInt(0, cols)
                    val y0 = rng.nextInt(0, rows)
                    val laps = rng.nextInt(1, 3) // grid heights per loop: a fast drop and a slow one
                    val alpha = if (rng.nextBoolean()) 0x55 else 0x38
                    val y = Math.floorMod(y0 + Math.round(phase * laps * rows).toInt(), rows)
                    out += Mark(Math.floorMod(x0 - y / 3, cols), y, 1, 3, alpha)
                }
            }
            Kind.SNOW -> {
                val flakes = (cols * rows / 110).coerceIn(6, 50)
                repeat(flakes) {
                    val x0 = rng.nextInt(0, cols)
                    val y0 = rng.nextInt(0, rows)
                    val sway = rng.nextInt(1, 3)
                    val offset = rng.nextDouble() * tau
                    val alpha = if (rng.nextBoolean()) 0x88 else 0x55
                    val y = Math.floorMod(y0 + Math.round(phase * rows).toInt(), rows)
                    val x = Math.floorMod(x0 + Math.round(Math.sin(tau * phase * sway + offset)).toInt(), cols)
                    out += Mark(x, y, 1, 1, alpha)
                }
            }
            Kind.STARS -> {
                val stars = (cols * rows / 90).coerceIn(6, 40)
                repeat(stars) {
                    val x = rng.nextInt(0, cols)
                    val y = rng.nextInt(0, rows * 3 / 4)
                    val base = rng.nextInt(0x38, 0x99)
                    val period = rng.nextInt(1, 3)
                    val offset = rng.nextDouble() * tau
                    val glow = 0.55 + 0.45 * Math.sin(tau * phase * period + offset)
                    out += Mark(x, y, 1, 1, (base * glow).toInt().coerceIn(8, 255))
                }
            }
            Kind.SUN -> sun(out, cols, rows, frame, phase, tau, rng, 1.0)
            Kind.PARTLY -> {
                sun(out, cols, rows, frame, phase, tau, rng, 0.7)
                clouds(out, cols, rows, phase, 1, 0x26)
            }
            Kind.PARTLY_NIGHT -> {
                repeat((cols * rows / 140).coerceIn(5, 30)) {
                    val x = rng.nextInt(0, cols)
                    val y = rng.nextInt(0, rows * 3 / 4)
                    val base = rng.nextInt(0x38, 0x80)
                    val period = rng.nextInt(1, 3)
                    val offset = rng.nextDouble() * tau
                    out += Mark(x, y, 1, 1, (base * (0.55 + 0.45 * Math.sin(tau * phase * period + offset))).toInt().coerceIn(8, 255))
                }
                clouds(out, cols, rows, phase, 1, 0x2A)
            }
            Kind.CLOUD -> clouds(out, cols, rows, phase, 2, 0x2A)
            Kind.FOG -> fog(out, cols, rows, phase)
            Kind.NONE -> Unit
        }
        return out
    }

    // Pixel clouds: rows of (start, length) in units, top to bottom.
    private val CLOUD_BIG = listOf(2 to 4, 1 to 6, 0 to 8)
    private val CLOUD_SMALL = listOf(1 to 3, 0 to 5)

    /** Adds [m] clipped to the grid (a mark half off the edge is cut, so every mark stays inside). */
    private fun clipped(out: MutableList<Mark>, cols: Int, rows: Int, x: Int, y: Int, w: Int, h: Int, alpha: Int, tone: Int = 0) {
        val x0 = max(0, x)
        val y0 = max(0, y)
        val x1 = minOf(cols, x + w)
        val y1 = minOf(rows, y + h)
        if (x1 > x0 && y1 > y0) out += Mark(x0, y0, x1 - x0, y1 - y0, alpha, tone)
    }

    /**
     * [rowCount] rows of drifting clouds. Each row repeats one cloud shape every `spacing` cells and
     * moves exactly one spacing per loop, so the loop closes; the lower row has a smaller
     * spacing, so it drifts slower.
     */
    private fun clouds(out: MutableList<Mark>, cols: Int, rows: Int, phase: Double, rowCount: Int, alpha: Int) {
        val unit = max(2, rows / 20)
        for (row in 0 until rowCount) {
            val shape = if (row == 0) CLOUD_BIG else CLOUD_SMALL
            val width = (shape.maxOf { it.first + it.second }) * unit
            val spacing = max(width * 2, if (row == 0) cols * 2 / 3 else cols / 2)
            val shift = Math.round(phase * spacing).toInt()
            val y = rows * (if (row == 0) 12 else 46) / 100
            var k = -1
            while (k * spacing - shift < cols) {
                val x = k * spacing - shift + row * spacing / 3
                shape.forEachIndexed { r, (start, len) ->
                    clipped(out, cols, rows, x + start * unit, y + r * unit, len * unit, unit, alpha)
                }
                k++
            }
        }
    }

    /** Bands of mist: long dashes that slide along, alternate rows in opposite directions. */
    private fun fog(out: MutableList<Mark>, cols: Int, rows: Int, phase: Double) {
        val bands = 6
        val thick = max(1, rows / 24)
        for (b in 0 until bands) {
            val length = max(8, cols / 4 + (b % 3) * (cols / 12))
            val gap = max(6, cols / 5 + (b % 2) * (cols / 10))
            val period = length + gap
            val dir = if (b % 2 == 0) 1 else -1
            val shift = Math.round(phase * period).toInt() * dir
            val y = rows * (10 + b * 15) / 100
            var k = -2
            while (k * period + shift < cols) {
                val x = k * period + shift + b * period / 5
                clipped(out, cols, rows, x, y, length, thick, if (b % 2 == 0) 0x22 else 0x16)
                k++
            }
        }
    }

    /**
     * A pixel sun in the top-right: a disc, eight rays that alternate between the straight four and
     * the diagonal four every frame, and a few warm glints that pulse.
     */
    private fun sun(out: MutableList<Mark>, cols: Int, rows: Int, frame: Int, phase: Double, tau: Double, rng: Mulberry32, size: Double) {
        val r = max(3, (rows * 0.11 * size).toInt())
        val cx = cols - cols / 7 - r
        val cy = rows / 5 + r / 2
        // The disc, one horizontal run per row.
        for (dy in -r..r) {
            val half = Math.round(Math.sqrt((r * r - dy * dy).toDouble())).toInt()
            clipped(out, cols, rows, cx - half, cy + dy, 2 * half + 1, 1, 0x42, 1)
        }
        // Rays: alternate straight and diagonal.
        val gap = 2
        val len = max(2, r / 2)
        val straight = Math.floorMod(frame, 2) == 0
        val dirs = if (straight) listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0) else listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
        for ((dx, dy) in dirs) {
            for (i in 0 until len) {
                val d = r + gap + i
                clipped(out, cols, rows, cx + dx * d, cy + dy * d, 1, 1, 0x78, 1)
            }
        }
        // Glints across the sky.
        repeat(6) {
            val x = rng.nextInt(0, cols)
            val y = rng.nextInt(0, rows * 2 / 3)
            val period = rng.nextInt(1, 3)
            val offset = rng.nextDouble() * tau
            val a = (0x50 * (0.5 + 0.5 * Math.sin(tau * phase * period + offset))).toInt()
            if (a > 6) clipped(out, cols, rows, x, y, 1, 1, a, 1)
        }
    }

    /**
     * The loop as [frames] bitmaps for the launcher's flipper, empty when the sky has no scene.
     * Same look as [render], just one bitmap per frame.
     */
    fun renderLoop(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        kind: Kind,
        seed: Int,
        pal: Palette,
        frames: Int = LOOP_FRAMES,
    ): List<Bitmap> {
        val spec = spec(context, widthPx, heightPx, kind, pal) ?: return emptyList()
        return List(frames) { f ->
            spec.draw(context, loop(kind, spec.cols, spec.rows, f, frames, seed), loopFlash(kind, f, frames), seed)
        }
    }

    // ---- drawing --------------------------------------------------------------------------

    /** The pixel grid and colours for one scene on one widget, shared by every frame of it. */
    private class Spec(val kind: Kind, val pal: Palette, val w: Int, val h: Int, val cell: Int) {
        val cols = w / cell
        val rows = h / cell
        private val base = when (kind) {
            Kind.RAIN, Kind.STORM -> if (pal.paper) 0x2F7FD0 else 0x55FFFF
            Kind.SNOW -> if (pal.paper) 0x6F9CC4 else 0xDFEFFF
            Kind.SUN -> if (pal.paper) 0xE59400 else 0xFFB000
            Kind.PARTLY -> if (pal.paper) 0x8FA0AD else 0xCFE0F0
            Kind.CLOUD, Kind.PARTLY_NIGHT -> if (pal.paper) 0x6F8294 else 0xC8D8E8
            Kind.FOG -> if (pal.paper) 0x7D8F87 else 0xA8B8B0
            else -> 0xDDEEFF
        }
        private val boltRgb = if (pal.paper) 0xD98200 else 0xFFD060
        private val washRgb = if (pal.paper) 0xD98200 else 0xFFB000
        private val sunRgb = if (pal.paper) 0xE59400 else 0xFFB000

        /** [flash]: 0 none, 1 afterglow (faint wash), 2 flash (wash and bolt). */
        fun draw(context: Context, marks: List<Mark>, flash: Int, seed: Int): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
            fun rect(m: Mark) = canvas.drawRect(
                (m.x * cell).toFloat(), (m.y * cell).toFloat(),
                ((m.x + m.w) * cell).toFloat(), ((m.y + m.h) * cell).toFloat(), paint,
            )
            if (flash > 0) {
                paint.color = ((if (flash == 2) 0x0E else 0x07) shl 24) or washRgb
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            for (m in marks) {
                paint.color = (m.alpha shl 24) or (if (m.tone == 1) sunRgb else base)
                rect(m)
            }
            if (flash == 2) {
                paint.color = (0x66 shl 24) or boltRgb
                bolt(cols, rows, seed).forEach(::rect)
            }
            return WidgetGraphics.sized(context, bmp)
        }
    }

    /** The grid for a scene, or null where there is none to draw (no scene, or stars on paper). */
    private fun spec(context: Context, widthPx: Int, heightPx: Int, kind: Kind, pal: Palette): Spec? {
        if (kind == Kind.NONE) return null
        if (kind == Kind.STARS && pal.paper) return null
        val w = max(16, (widthPx * SCALE).roundToInt())
        val h = max(16, (heightPx * SCALE).roundToInt())
        val cell = max(2, (context.resources.displayMetrics.density * SCALE * 1.5f).roundToInt())
        return Spec(kind, pal, w, h, cell)
    }
}
