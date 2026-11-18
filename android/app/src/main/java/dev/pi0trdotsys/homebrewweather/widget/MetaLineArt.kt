package dev.pi0trdotsys.homebrewweather.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** The small pixel icons that stand in for words on the widget's meta line. 7 x 7 cells. */
enum class MetaGlyph(val rows: Array<String>) {
    SUN(arrayOf("...#...", ".#...#.", "..###..", "#.###.#", "..###..", ".#...#.", "...#...")),
    WIND(arrayOf(".......", "#####..", "......#", "#######", "......#", "####...", ".......")),
    POLLEN(arrayOf("..#.#..", ".#.#.#.", "..###..", ".#.#.#.", "..#.#..", "...#...", "...#...")),
    DROP(arrayOf("...#...", "..###..", ".#####.", "#######", "#######", ".#####.", "..###..")),
    DROP_HOLLOW(arrayOf("...#...", "..#.#..", ".#...#.", "#.....#", "#.....#", ".#...#.", "..###..")),
    WARNING(arrayOf("...#...", "..#.#..", "..#.#..", ".#.#.#.", ".#...#.", "#..#..#", "#######")),
    THERMOMETER(arrayOf("..###..", "..#.#..", "..#.#..", "..#.#..", ".#####.", ".#####.", "..###..")),
    SUNRISE(arrayOf("...#...", "..###..", "...#...", ".......", "..###..", ".#####.", "#######")),
    SUNSET(arrayOf("..###..", ".#####.", "#######", ".......", "...#...", "..###..", "...#...")),
    ;

    val width: Int get() = rows[0].length
}

/** What colour a readout is drawn in: dim for the ordinary ones, a hue where it means something. */
enum class MetaTint { DIM, CYAN, AMBER, CRIMSON, GREEN }

/**
 * One readout on the meta line: an icon, a short value, and the full plain-text
 * form [plain] (what the line used to say, and what TalkBack reads).
 */
data class Readout(val glyph: MetaGlyph?, val value: String, val plain: String, val tint: MetaTint = MetaTint.DIM)

/**
 * The widget's meta line drawn in the pixel font, with an icon in front of each
 * readout — "[sun] 9   [wind] 71 km/h   [flower] brzoza" — instead of a row of
 * words in a monospace TextView.
 *
 * Why a bitmap: that line is the cramped one. It was a TextView whose size the
 * solver had to *predict* from a character count and which shrank to fit, with
 * the words themselves ("porywy", "wilgotno") eating the width. Icons say the
 * quantity in 7 cells instead of 6 letters, and drawing it ourselves makes the
 * fit exact (like the rain row): the largest whole-pixel scale at which every
 * readout fits, and if even the smallest doesn't hold them all, the last ones are
 * dropped rather than the type shrunk past legibility.
 *
 * Pure layout in [layout] (covered by MetaLineArtTest); drawing in [render].
 */
object MetaLineArt {

    /** Blank cells between an icon and its value. */
    private const val ICON_GAP = 2

    /** Blank cells between two readouts. */
    private const val READOUT_GAP = 5

    class Layout(val readouts: List<Readout>, val scale: Int, val widthUnits: Int)

    /** Width of one readout in font units: icon, gap, value. */
    fun unitsOf(r: Readout): Int =
        (r.glyph?.let { it.width + ICON_GAP } ?: 0) + PixelFont.widthUnits(PixelFont.sanitize(r.value))

    /**
     * The readouts that fit [maxWidthPx] x [maxHeightPx], at the largest whole
     * scale that fits. Readouts are kept in order and cut from the end; null when
     * not even the first fits at scale 1.
     */
    fun layout(readouts: List<Readout>, maxWidthPx: Int, maxHeightPx: Int): Layout? {
        var kept = readouts
        while (kept.isNotEmpty()) {
            val units = kept.sumOf(::unitsOf) + READOUT_GAP * (kept.size - 1)
            val scale = minOf(maxHeightPx / PixelFont.ROWS, maxWidthPx / units)
            if (scale >= 1) return Layout(kept, scale, units)
            kept = kept.dropLast(1)
        }
        return null
    }

    /** The picture for [readouts], exactly [heightPx] tall; [color] maps a tint to its colour. Null when there is nothing to show. */
    fun render(readouts: List<Readout>, maxWidthPx: Int, heightPx: Int, color: (MetaTint) -> Int): Bitmap? {
        val l = layout(readouts, maxWidthPx, heightPx) ?: return null
        val bmp = Bitmap.createBitmap(l.widthUnits * l.scale, maxOf(1, heightPx), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val top = (heightPx - PixelFont.ROWS * l.scale) / 2
        var x = 0
        l.readouts.forEachIndexed { i, r ->
            val c = color(r.tint)
            r.glyph?.let { g ->
                val paint = Paint().apply { this.color = c; isAntiAlias = false }
                for (row in 0 until 7) for (col in 0 until g.width) {
                    if (g.rows[row][col] == '#') {
                        val left = (x + col * l.scale).toFloat()
                        val t = (top + row * l.scale).toFloat()
                        canvas.drawRect(left, t, left + l.scale, t + l.scale, paint)
                    }
                }
                x += (g.width + ICON_GAP) * l.scale
            }
            // draw() leaves the cursor one gap past the last glyph; the layout doesn't count that one.
            x = PixelFont.draw(canvas, PixelFont.sanitize(r.value), x, top, c, l.scale) - PixelFont.GAP * l.scale
            if (i < l.readouts.lastIndex) x += READOUT_GAP * l.scale
        }
        return bmp
    }
}
