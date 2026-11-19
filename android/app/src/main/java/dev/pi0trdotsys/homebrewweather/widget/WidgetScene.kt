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

    enum class Kind { NONE, RAIN, SNOW, STORM, STARS }

    /** A rectangle in grid cells, with how opaque it is (0–255). */
    data class Mark(val x: Int, val y: Int, val w: Int, val h: Int, val alpha: Int)

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
            Kind.NONE -> Unit
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
            Kind.NONE -> Unit
        }
        return out
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
            else -> 0xDDEEFF
        }
        private val boltRgb = if (pal.paper) 0xD98200 else 0xFFD060
        private val washRgb = if (pal.paper) 0xD98200 else 0xFFB000

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
                paint.color = (m.alpha shl 24) or base
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
