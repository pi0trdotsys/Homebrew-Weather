package dev.pi0trdotsys.homebrewweather.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * A 5x7 pixel font, drawn into bitmaps — the same pixel language as the
 * [PixelIcons] next to it, for the per-day rain row ("▽ 7–19").
 *
 * ## Why a bitmap font
 *
 * That row was a TextView, and its two symbols are exactly what `monospace`
 * doesn't have: `▽` and `–` come from a fallback font with their own, wider
 * metrics. The size solver can only *predict* a TextView's width, and for this
 * row the prediction was wrong — at 170dp the widest window measured right at
 * the column's limit and ellipsized to "▽ 13–2…", and the fix was a guessed
 * safety margin. Here the width is known exactly before drawing (see
 * [widthUnits]), so the renderer picks the largest whole pixel size that fits
 * the column and nothing can be cut off. Whole pixels also keep the glyphs
 * crisp, matching the icons instead of antialiased text beside them.
 *
 * Covers what the row can say in either language: digits, `▽ – + %`, and the
 * lowercase letters (for "od 20" / "from 20"). Anything else renders as a
 * box rather than silently disappearing; WidgetContentRulesTest checks every
 * string the row can produce is covered.
 */
object PixelFont {

    const val ROWS = 7

    /** Blank columns between glyphs, in font units. */
    private const val GAP = 1

    private val GLYPHS: Map<Char, Array<String>> = mapOf(
        ' ' to arrayOf("..", "..", "..", "..", "..", "..", ".."),
        '0' to arrayOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to arrayOf(".#.", "##.", ".#.", ".#.", ".#.", ".#.", "###"),
        '2' to arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to arrayOf("#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
        '4' to arrayOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to arrayOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to arrayOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to arrayOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
        '▽' to arrayOf(".....", "#####", "#...#", ".#.#.", ".#.#.", "..#..", "....."),
        '–' to arrayOf("....", "....", "....", "####", "....", "....", "...."),
        '+' to arrayOf(".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."),
        '%' to arrayOf("##...", "##..#", "...#.", "..#..", ".#...", "#..##", "...##"),
        '~' to arrayOf(".....", ".....", ".#..#", "#.##.", ".....", ".....", "....."),
        'a' to arrayOf(".....", ".....", ".###.", "....#", ".####", "#...#", ".####"),
        'b' to arrayOf("#....", "#....", "#.##.", "##..#", "#...#", "#...#", "####."),
        'c' to arrayOf(".....", ".....", ".###.", "#....", "#....", "#...#", ".###."),
        'd' to arrayOf("....#", "....#", ".##.#", "#..##", "#...#", "#...#", ".####"),
        'e' to arrayOf(".....", ".....", ".###.", "#...#", "#####", "#....", ".###."),
        'f' to arrayOf("..##", ".#..", ".#..", "###.", ".#..", ".#..", ".#.."),
        'g' to arrayOf(".....", ".####", "#...#", "#...#", ".####", "....#", ".###."),
        'h' to arrayOf("#....", "#....", "#.##.", "##..#", "#...#", "#...#", "#...#"),
        'i' to arrayOf(".#.", "...", "##.", ".#.", ".#.", ".#.", "###"),
        'j' to arrayOf("...#", "....", "..##", "...#", "...#", "#..#", ".##."),
        'k' to arrayOf("#...", "#...", "#..#", "#.#.", "##..", "#.#.", "#..#"),
        'l' to arrayOf("##.", ".#.", ".#.", ".#.", ".#.", ".#.", "###"),
        'm' to arrayOf(".....", ".....", "##.#.", "#.#.#", "#.#.#", "#...#", "#...#"),
        'n' to arrayOf(".....", ".....", "#.##.", "##..#", "#...#", "#...#", "#...#"),
        'o' to arrayOf(".....", ".....", ".###.", "#...#", "#...#", "#...#", ".###."),
        'p' to arrayOf(".....", "####.", "#...#", "#...#", "####.", "#....", "#...."),
        'q' to arrayOf(".....", ".####", "#...#", "#...#", ".####", "....#", "....#"),
        'r' to arrayOf(".....", ".....", "#.##", "##..", "#...", "#...", "#..."),
        's' to arrayOf(".....", ".....", ".####", "#....", ".###.", "....#", "####."),
        't' to arrayOf(".#..", ".#..", "###.", ".#..", ".#..", ".#.#", "..#."),
        'u' to arrayOf(".....", ".....", "#...#", "#...#", "#...#", "#..##", ".##.#"),
        'v' to arrayOf(".....", ".....", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
        'w' to arrayOf(".....", ".....", "#...#", "#...#", "#.#.#", "#.#.#", ".#.#."),
        'x' to arrayOf(".....", ".....", "#...#", ".#.#.", "..#..", ".#.#.", "#...#"),
        'y' to arrayOf(".....", "#...#", "#...#", "#...#", ".####", "....#", ".###."),
        'z' to arrayOf(".....", ".....", "#####", "...#.", "..#..", ".#...", "#####"),
    )

    /** Drawn for any character the font doesn't have. */
    private val MISSING = arrayOf("###", "#.#", "#.#", "#.#", "#.#", "#.#", "###")

    fun covers(c: Char): Boolean = c in GLYPHS

    private fun glyph(c: Char): Array<String> = GLYPHS[c] ?: MISSING

    /** Width of [text] in font units (1 unit = 1 pixel at scale 1), gaps included. */
    fun widthUnits(text: String): Int =
        if (text.isEmpty()) 0 else text.sumOf { glyph(it)[0].length } + GAP * (text.length - 1)

    /**
     * Largest whole-pixel scale at which [text] fits [maxWidthPx] x
     * [maxHeightPx], never below 1. Pure, so the fit is testable without a
     * device.
     */
    fun fitScale(text: String, maxWidthPx: Int, maxHeightPx: Int): Int {
        val w = widthUnits(text)
        if (w == 0) return 1
        return maxOf(1, minOf(maxWidthPx / w, maxHeightPx / ROWS))
    }

    /** Below this scale the glyphs are too small to read comfortably. */
    const val COMFORT_SCALE = 3

    /**
     * One scale for a whole row of [texts], so the four day columns match —
     * sized independently, a short "▽ 10–12" came out a size larger than a
     * long "▽ 13–20+" beside it. If that shared scale falls below
     * [COMFORT_SCALE] and dropping [marker] from every entry buys a larger
     * one, the marker goes: on a narrow widget the hours matter more than
     * the symbol, and the colour and the position under the day already say
     * it's rain. Returns the texts to draw and the scale to draw them at.
     */
    fun fitRow(texts: List<String>, maxWidthPx: Int, maxHeightPx: Int, marker: String = "▽ "): Pair<List<String>, Int> {
        fun scaleOf(row: List<String>) =
            row.filter { it.isNotBlank() }.minOfOrNull { fitScale(it, maxWidthPx, maxHeightPx) } ?: 1
        val full = scaleOf(texts)
        if (full >= COMFORT_SCALE) return texts to full
        val bare = texts.map { it.removePrefix(marker) }
        val bareScale = scaleOf(bare)
        return if (bareScale > full) bare to bareScale else texts to full
    }

    /**
     * [text] drawn in [color] at [scale], centred vertically in a bitmap
     * exactly [heightPx] tall — the row's full height, so all four columns
     * stay aligned and the size solver's reservation for the row is what gets
     * measured. Blank text gives a 1px-wide spacer.
     */
    fun render(text: String, color: Int, scale: Int, heightPx: Int): Bitmap {
        val h = maxOf(1, heightPx)
        if (text.isBlank()) return Bitmap.createBitmap(1, h, Bitmap.Config.ARGB_8888)
        val bmp = Bitmap.createBitmap(maxOf(1, widthUnits(text) * scale), h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply { this.color = color; isAntiAlias = false }
        val top = (h - ROWS * scale) / 2
        var x = 0
        for (c in text) {
            val g = glyph(c)
            for (row in 0 until ROWS) {
                val line = g[row]
                for (col in line.indices) {
                    if (line[col] == '#') {
                        val l = (x + col * scale).toFloat()
                        val t = (top + row * scale).toFloat()
                        canvas.drawRect(l, t, l + scale, t + scale, paint)
                    }
                }
            }
            x += (g[0].length + GAP) * scale
        }
        return bmp
    }
}
