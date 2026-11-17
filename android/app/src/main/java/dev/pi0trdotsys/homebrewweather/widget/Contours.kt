package dev.pi0trdotsys.homebrewweather.widget

/**
 * Contour lines (isolines) of a scalar field by marching squares — the strokes
 * of the widget's terrain background.
 *
 * Pure geometry, no Android: a field is a row-major FloatArray of [w] x [h]
 * samples, and a line is a list of short segments in sample coordinates, which
 * the caller scales to pixels. Covered by ContoursTest.
 */
object Contours {

    data class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    /**
     * [field] enlarged [factor] times in each direction by bilinear interpolation,
     * so a coarse 24 x 12 grid contours into flowing curves instead of facets.
     * Returns the new field and its width and height.
     */
    fun upsample(field: FloatArray, w: Int, h: Int, factor: Int): Triple<FloatArray, Int, Int> {
        val nw = (w - 1) * factor + 1
        val nh = (h - 1) * factor + 1
        val out = FloatArray(nw * nh)
        for (y in 0 until nh) {
            val fy = y.toFloat() / factor
            val y0 = fy.toInt().coerceAtMost(h - 2)
            val ty = fy - y0
            for (x in 0 until nw) {
                val fx = x.toFloat() / factor
                val x0 = fx.toInt().coerceAtMost(w - 2)
                val tx = fx - x0
                val a = field[y0 * w + x0]
                val b = field[y0 * w + x0 + 1]
                val c = field[(y0 + 1) * w + x0]
                val d = field[(y0 + 1) * w + x0 + 1]
                out[y * nw + x] = (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty
            }
        }
        return Triple(out, nw, nh)
    }

    /** A 3x3 box blur, to take the edge off a noisy surface model before contouring. */
    fun smooth(field: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(field.size)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0f
            var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until w && yy in 0 until h) {
                    sum += field[yy * w + xx]
                    n++
                }
            }
            out[y * w + x] = sum / n
        }
        return out
    }

    /**
     * Segments of the line where the field crosses [level]. Per cell, the case is
     * which of the four corners are at or above it; edges are numbered 0 top,
     * 1 right, 2 bottom, 3 left, and each case joins the edges it crosses. The two
     * ambiguous "saddle" cases are decided by the cell's average.
     */
    fun segments(field: FloatArray, w: Int, h: Int, level: Float): List<Segment> {
        val out = ArrayList<Segment>()
        for (y in 0 until h - 1) for (x in 0 until w - 1) {
            val tl = field[y * w + x]
            val tr = field[y * w + x + 1]
            val br = field[(y + 1) * w + x + 1]
            val bl = field[(y + 1) * w + x]
            val case = (if (tl >= level) 8 else 0) or (if (tr >= level) 4 else 0) or
                (if (br >= level) 2 else 0) or (if (bl >= level) 1 else 0)
            if (case == 0 || case == 15) continue

            fun edge(e: Int): Pair<Float, Float> = when (e) {
                0 -> (x + frac(tl, tr, level)) to y.toFloat()
                1 -> (x + 1f) to (y + frac(tr, br, level))
                2 -> (x + frac(bl, br, level)) to (y + 1f)
                else -> x.toFloat() to (y + frac(tl, bl, level))
            }
            val pairs: List<Pair<Int, Int>> = when (case) {
                1, 14 -> listOf(3 to 2)
                2, 13 -> listOf(2 to 1)
                3, 12 -> listOf(3 to 1)
                4, 11 -> listOf(0 to 1)
                6, 9 -> listOf(0 to 2)
                7, 8 -> listOf(3 to 0)
                5 -> if ((tl + tr + br + bl) / 4 >= level) listOf(3 to 0, 2 to 1) else listOf(3 to 2, 0 to 1)
                else /* 10 */ -> if ((tl + tr + br + bl) / 4 >= level) listOf(3 to 2, 0 to 1) else listOf(3 to 0, 2 to 1)
            }
            for ((a, b) in pairs) {
                val p = edge(a)
                val q = edge(b)
                out += Segment(p.first, p.second, q.first, q.second)
            }
        }
        return out
    }

    /** Where between two corner values the [level] sits, 0..1. */
    private fun frac(a: Float, b: Float, level: Float): Float =
        if (a == b) 0.5f else ((level - a) / (b - a)).coerceIn(0f, 1f)

    /**
     * About ten lines across [relief] metres, at a step a map would use: the
     * first of 5, 10, 20, 25, 50, 100, 200 m that gives no more than ten.
     */
    fun niceStep(relief: Float): Float =
        listOf(5f, 10f, 20f, 25f, 50f, 100f, 200f).firstOrNull { relief / it <= 10f } ?: 500f
}
