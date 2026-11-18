package dev.pi0trdotsys.homebrewweather.widget

/**
 * One colour scale for temperature, shared by the widget's big number, the 24-hour
 * curve and the dashboard (src/lib/temp-color.ts — the two must give identical
 * results, which testdata/parity/temp-color.json checks).
 *
 * The hero used to be cyan whatever the weather, so 5° and 32° looked the same. Now
 * it runs from a cold blue below freezing through cyan and amber to crimson, by plain
 * linear blending between four stops, clamped at both ends: the number says "cold" or
 * "hot" before you read it.
 *
 * Pure — covered by TempColorTest.
 */
object TempColor {

    /** (°C, 0xRRGGBB), ascending. */
    private val STOPS = listOf(
        -15.0 to 0x7A9BFF, // bitter cold
        0.0 to 0x55FFFF, //   freezing: the widget's cyan
        22.0 to 0xFFB000, //  warm: the widget's amber
        35.0 to 0xFF5555, //  hot: the widget's crimson
    )

    /** The colour for [celsius] as 0xRRGGBB; unknown (NaN) reads as the freezing cyan. */
    fun rgb(celsius: Double): Int {
        if (celsius.isNaN()) return STOPS[1].second
        if (celsius <= STOPS.first().first) return STOPS.first().second
        if (celsius >= STOPS.last().first) return STOPS.last().second
        val i = STOPS.indexOfLast { it.first <= celsius }
        val (t0, c0) = STOPS[i]
        val (t1, c1) = STOPS[i + 1]
        val f = (celsius - t0) / (t1 - t0)
        fun ch(shift: Int): Int {
            val a = (c0 shr shift) and 0xFF
            val b = (c1 shr shift) and 0xFF
            return Math.floor(a + (b - a) * f + 0.5).toInt()
        }
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** As an opaque ARGB colour for Android's drawing APIs. */
    fun argb(celsius: Double): Int = 0xFF000000.toInt() or rgb(celsius)

    /** "#rrggbb", lowercase — the form the shared fixture uses. */
    fun hex(celsius: Double): String = String.format("#%06x", rgb(celsius))
}
