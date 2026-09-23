package dev.pi0trdotsys.homebrewweather.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Direct Kotlin port of src/components/PixelIcon.tsx — same 16x16 character
 * grids and the same color map, rendered into a chunky, nearest-neighbor
 * scaled Bitmap instead of a CSS grid of divs. Keep both in sync.
 *
 * ## Reading the grids
 *
 * Uppercase is the lit/front tone, lowercase the shaded tone of the same
 * material: `W`/`w` cloud, `D`/`d` storm cloud, `Y`/`o` sun, `B`/`b` rain,
 * `G`/`g` fog. Every icon that has volume is drawn in two tones rather than
 * one, because a single flat fill reads as a blob at the sizes these actually
 * render at — see below.
 *
 * ## The size these are really drawn at
 *
 * The grid icons render around 13-20dp and the hero icon around 26-30dp. At
 * xxhdpi that is roughly 2.4-3.8 device pixels per grid cell, so a single cell
 * is a visible dot but fine alternating detail turns to mush. That constraint
 * drove this pass's redesign, after the previous grids were checked on a real
 * device at real size:
 *
 * - the sun was a near-full-grid disc with detached single-pixel ray stubs,
 *   which read as an amoeba; it is now a smaller disc with clearly separated
 *   2x2 rays, so both the body and the rays survive.
 * - rain was a uniform checkerboard of single blue dots across five rows,
 *   which read as blue static; it is now two staggered ranks of 2-tall
 *   vertical dashes, which read as falling streaks. Snow keeps single dots
 *   precisely so the two are distinguishable at a glance.
 * - the storm cloud was `#2a3a2a` against a `#0a0f0a` widget background —
 *   nearly invisible, a dark smudge with a bolt under it. It is now a slate
 *   grey-blue that actually separates from the background while still
 *   reading as "darker than a normal cloud".
 * - fog was a stack of identical bars in a dim green; it is now offset bars
 *   in two grey tones that drift sideways rather than bobbing, which is what
 *   fog does.
 */
object PixelIcons {

    val ICONS: Map<String, List<String>> = mapOf(
        // Disc rows 5-10, lit on top (Y) and shaded underneath (o), with eight
        // separated rays. The gap between disc and rays is deliberate: rays
        // touching the body merge into one round blob at 13dp.
        "sun" to listOf(
            "................",
            ".......YY.......",
            ".......YY.......",
            "..YY........YY..",
            "..YY........YY..",
            ".....YYYYYY.....",
            "....YYYYYYYY....",
            ".YY.YYYYYYYY.YY.",
            ".YY.YYYYYYYY.YY.",
            "....oooooooo....",
            ".....oooooo.....",
            "..YY........YY..",
            "..YY........YY..",
            ".......YY.......",
            ".......YY.......",
            "................",
        ),
        // Crescent opening right, shaded along the inner edge, with two cyan
        // stars. The old moon drew its crescent bite as an opaque dark shape,
        // which on a transparent widget background looked like a dirty patch
        // instead of a gap — here the bite is simply not drawn.
        "moon" to listOf(
            "................",
            "......WWWW......",
            "....WWWWWWW.....",
            "...WWWWww....C..",
            "..WWWWWw........",
            "..WWWWw.........",
            ".WWWWWw.........",
            ".WWWWWw.........",
            ".WWWWWw.....C...",
            ".WWWWWw.........",
            "..WWWWw.........",
            "..WWWWWw........",
            "...WWWWww.......",
            "....WWWWWWW.....",
            "......WWWW......",
            "................",
        ),
        "partly" to listOf(
            "................",
            ".....YYYY.......",
            "..Y..YYYY..Y....",
            "...YYYYYYYY.....",
            "..YYYYYYYYYY....",
            "..YYYYY.WWWW....",
            "...YYY.WWWWWWW..",
            ".....WWWWWWWWWW.",
            "...WWWWWWWWWWWWW",
            "..WWWWWWWWWWWWWW",
            "..wwwwwwwwwwwwww",
            "...wwwwwwwwwwww.",
            "................",
            "................",
            "................",
            "................",
        ),
        "cloud" to listOf(
            "................",
            "................",
            "................",
            ".......WWWW.....",
            ".....WWWWWWWW...",
            "....WWWWWWWWWW..",
            "..WWWWWWWWWWWWW.",
            ".WWWWWWWWWWWWWWW",
            ".WWWWWWWWWWWWWWW",
            ".WWWWWWWWWWWWWWW",
            ".wwwwwwwwwwwwwww",
            "..wwwwwwwwwwwww.",
            "................",
            "................",
            "................",
            "................",
        ),
        // Offset bars in two tones. Drifts horizontally (see FOG_DRIFT), not
        // vertically like the cloud kinds — fog moves sideways.
        "fog" to listOf(
            "................",
            "................",
            "..GGGGGGGGGG....",
            "....gggggggggg..",
            "................",
            ".GGGGGGGGGGGG...",
            "...gggggggggggg.",
            "................",
            "GGGGGGGGGGGGGG..",
            "..gggggggggggggg",
            "................",
            ".GGGGGGGGGGGG...",
            "...gggggggggg...",
            "................",
            "..GGGGGGGGGG....",
            "................",
        ),
        // Compact cloud in rows 1-8 so the whole precipitation band (rows
        // 9-15, see PRECIP_BAND_*) is free for the animated streaks.
        "rain" to listOf(
            "................",
            ".......WWWW.....",
            ".....WWWWWWWW...",
            "....WWWWWWWWWW..",
            "..WWWWWWWWWWWWW.",
            ".WWWWWWWWWWWWWWW",
            ".WWWWWWWWWWWWWWW",
            ".wwwwwwwwwwwwwww",
            "..wwwwwwwwwwwww.",
            "..B...B...B...B.",
            "..B...B...B...B.",
            "................",
            "....b...b...b...",
            "....b...b...b...",
            "................",
            "................",
        ),
        // Single dots, not the 2-tall dashes rain uses — that difference is
        // the whole reason the two icons are still distinguishable once the
        // cloud above them is identical and only ~50px wide.
        "snow" to listOf(
            "................",
            ".......WWWW.....",
            ".....WWWWWWWW...",
            "....WWWWWWWWWW..",
            "..WWWWWWWWWWWWW.",
            ".WWWWWWWWWWWWWWW",
            ".WWWWWWWWWWWWWWW",
            ".wwwwwwwwwwwwwww",
            "..wwwwwwwwwwwww.",
            "..S...S...S...S.",
            "................",
            "....S...S...S...",
            "................",
            "..S...S...S...S.",
            "................",
            "................",
        ),
        "thunder" to listOf(
            "................",
            ".......DDDD.....",
            ".....DDDDDDDD...",
            "....DDDDDDDDDD..",
            "..DDDDDDDDDDDDD.",
            ".DDDDDDDDDDDDDDD",
            ".DDDDDDDDDDDDDDD",
            ".ddddddddddddddd",
            "..ddddddddddddd.",
            "........LLL.....",
            ".......LLL......",
            "......LLL.......",
            "....LLLLLLL.....",
            "......LLL.......",
            ".....LLL........",
            "....LL..........",
        ),
    )

    private val COLORS: Map<Char, Int> = mapOf(
        'Y' to Color.parseColor("#ffd23f"), // sun, lit
        'o' to Color.parseColor("#f2a81c"), // sun, shaded underside
        'W' to Color.parseColor("#e8eeea"), // cloud, lit
        'w' to Color.parseColor("#9fb0aa"), // cloud, shaded underside
        'D' to Color.parseColor("#6b7c84"), // storm cloud, lit
        'd' to Color.parseColor("#45545b"), // storm cloud, shaded underside
        'B' to Color.parseColor("#6fbaff"), // rain, near streaks
        'b' to Color.parseColor("#3d86cc"), // rain, far streaks
        'S' to Color.parseColor("#eaf6ff"), // snow
        'G' to Color.parseColor("#9aada4"), // fog, near bars
        'g' to Color.parseColor("#5d6f66"), // fog, far bars
        'L' to Color.parseColor("#ffb000"), // lightning, struck (widget amber)
        'l' to Color.parseColor("#7a5300"), // lightning, between strikes
        'C' to Color.parseColor("#55ffff"), // stars (widget cyan)
        'K' to Color.parseColor("#000000"),
    )

    private const val BLANK_ROW = "................"

    /** The rows below the cloud body where rain/snow precipitation pixels
     * live (see the "rain"/"snow" grids above) — everything from just below
     * the cloud body to the bottom edge of the 16-row grid. */
    private const val PRECIP_BAND_START = 9
    private const val PRECIP_BAND_END = 15

    /** Outermost pixel of each of the sun's eight rays, dropped on the
     * "retracted" twinkle frame so the rays pulse in and out. Only the tip of
     * each ray, not the whole ray: blanking more collapsed the icon into a
     * plain diamond, which is what the previous grid's twinkle frame did.
     * Hand-picked against the "sun" grid above, so they move with it. */
    private val SUN_RAY_TIPS: List<Pair<Int, Int>> = listOf(
        1 to 7, 1 to 8, // top
        14 to 7, 14 to 8, // bottom
        7 to 1, 8 to 1, // left
        7 to 14, 8 to 14, // right
        3 to 2, 3 to 13, // upper diagonals
        12 to 2, 12 to 13, // lower diagonals
    )

    /** 4-frame vertical-bob offset cycle (source-grid pixels) used by the
     * static-shaped kinds (cloud/partly/moon): 0, -1, 0, +1. */
    private val BOB_OFFSETS = intArrayOf(0, -1, 0, 1)

    /** 4-frame horizontal drift for "fog": 0, +1, 0, -1. */
    private val FOG_DRIFT = intArrayOf(0, 1, 0, -1)

    /** Shifts the rows in [start..end] (inclusive) of [grid] down by [shift]
     * rows, wrapping within that band only — used for the falling-rain /
     * drifting-snow effect. Rows outside the band are returned untouched. */
    private fun shiftBand(grid: List<String>, start: Int, end: Int, shift: Int): List<String> {
        val bandSize = end - start + 1
        val band = grid.subList(start, end + 1)
        val shifted = (0 until bandSize).map { i ->
            val srcIdx = ((i - shift) % bandSize + bandSize) % bandSize
            band[srcIdx]
        }
        return grid.subList(0, start) + shifted + grid.subList(end + 1, grid.size)
    }

    /** Shifts the whole 16-row grid up/down by [offset] rows; rows that would
     * land outside 0..15 are simply left blank rather than wrapping. */
    private fun shiftRows(grid: List<String>, offset: Int): List<String> =
        (0 until grid.size).map { i ->
            val src = i - offset
            if (src in grid.indices) grid[src] else BLANK_ROW
        }

    /** Shifts every row left/right by [offset] columns, wrapping around the
     * row — the bars are full-width-ish, so wrapping reads as continuous
     * drift rather than as pixels falling off an edge. */
    private fun shiftCols(grid: List<String>, offset: Int): List<String> =
        grid.map { row ->
            val n = row.length
            val chars = CharArray(n)
            for (i in 0 until n) {
                chars[i] = row[((i - offset) % n + n) % n]
            }
            String(chars)
        }

    /** Returns [grid] with the given (row, col) cells blanked out. */
    private fun blankCells(grid: List<String>, cells: List<Pair<Int, Int>>): List<String> {
        val byRow = cells.groupBy({ it.first }, { it.second })
        return grid.mapIndexed { rowIdx, row ->
            val cols = byRow[rowIdx] ?: return@mapIndexed row
            val chars = row.toCharArray()
            cols.forEach { c -> if (c in chars.indices) chars[c] = '.' }
            String(chars)
        }
    }

    /** Returns [grid] with every occurrence of [from] recoloured to [to]. */
    private fun recolor(grid: List<String>, from: Char, to: Char): List<String> =
        grid.map { row -> row.replace(from, to) }

    /**
     * Applies this render's per-frame animation transform to the base 16x16
     * grid for [kind]. Frame count/cadence is deliberately small (2-4) and
     * driven by the ~60s BlinkAlarm tick (see WeatherWidgetProvider /
     * BlinkAlarm.kt) rather than any smooth/continuous animation.
     */
    private fun transformGrid(kind: String, grid: List<String>, frame: Int): List<String> {
        val f = ((frame % 4) + 4) % 4
        return when (kind) {
            // Falling rain / drifting snow: shift only the precip band down
            // one row per frame, wrapping within that band. Cloud-body rows
            // above the band are part of the untouched prefix and never move.
            "rain", "snow" -> shiftBand(grid, PRECIP_BAND_START, PRECIP_BAND_END, f)
            // Twinkle: alternate full rays / retracted ray tips every other frame.
            "sun" -> if (f % 2 == 1) blankCells(grid, SUN_RAY_TIPS) else grid
            // Flash: the bolt dims between strikes rather than disappearing.
            // Blanking it outright (what this used to do) meant that for half
            // of every ~60s blink cycle the thunder icon was a plain grey
            // cloud with nothing under it — indistinguishable from "cloud" at
            // a glance, on an icon whose entire job is to say "storm".
            "thunder" -> if (f % 2 == 1) recolor(grid, 'L', 'l') else grid
            // Fog drifts sideways instead of bobbing.
            "fog" -> shiftCols(grid, FOG_DRIFT[f])
            // Gentle 1px vertical bob over a 4-frame cycle (0, -1, 0, +1).
            "cloud", "partly", "moon" -> shiftRows(grid, BOB_OFFSETS[f])
            else -> grid
        }
    }

    /**
     * Renders a 16x16 pixel-icon "kind" into a chunky bitmap of [finalSize]x[finalSize]
     * pixels (default 96), each source pixel drawn as a filled, non-antialiased
     * rect so the result stays crisp/pixelated like the web CSS-grid version.
     *
     * [frame] selects a subtle per-kind animation frame (see [transformGrid]),
     * advanced roughly once a minute by the widget's existing blink tick —
     * there is no new alarm/timer here, this just reads whatever frame value
     * was last persisted.
     */
    fun render(kind: String, finalSize: Int = 96, frame: Int = 0): Bitmap {
        val baseGrid = ICONS[kind] ?: ICONS.getValue("cloud")
        val grid = transformGrid(kind, baseGrid, frame)
        val bmp = Bitmap.createBitmap(finalSize, finalSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
            style = Paint.Style.FILL
        }
        // Cell boundaries are snapped to whole device pixels (Math.round on each
        // edge, not a flat "+0.5f" fudge on a fractional cell size) so every cell
        // gets a consistent 1 or 2px width with no overlap/gap between neighbors.
        // At small render sizes (finalSize not an exact multiple of 16, e.g. the
        // 4-day grid's 13dp icons) the previous fractional-px + flat-overlap
        // approach produced inconsistent per-cell coverage — most visible on the
        // "rain"/"snow" icons' fine alternating-dot precipitation band, which
        // real-device screenshots showed rendering as a blurry checkered patch
        // rather than clean dots. Snapping each edge independently keeps every
        // icon crisp regardless of how finalSize divides by 16.
        val px = finalSize / 16f
        for (y in grid.indices) {
            val row = grid[y]
            val top = Math.round(y * px)
            val bottom = Math.round((y + 1) * px)
            for (x in row.indices) {
                val ch = row[x]
                if (ch == '.') continue
                val color = COLORS[ch] ?: Color.parseColor("#33ff66")
                paint.color = color
                val left = Math.round(x * px)
                val right = Math.round((x + 1) * px)
                canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
            }
        }
        return bmp
    }
}
