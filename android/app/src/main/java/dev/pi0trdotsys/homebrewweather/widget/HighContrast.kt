package dev.pi0trdotsys.homebrewweather.widget

import kotlin.math.pow

/**
 * The widget's secondary ("dim") text colours, made readable on request.
 *
 * The dim shades — header, meta line, the min temperature's separator — were
 * picked as "quiet", and on the dark widget background the green one measures
 * about 3:1, under the 4.5:1 that WCAG AA asks of text this small. Rather than
 * hand-pick a second palette per theme, [dim] derives the readable shade from
 * the theme's own primary colour: it blends the background toward that colour
 * only as far as it takes to reach the ratio, so a theme keeps its hue and the
 * secondary text stays visibly quieter than the primary.
 *
 * Pure (no android.graphics.Color, which a JVM test can't use) — covered by
 * HighContrastTest.
 */
object HighContrast {

    /** WCAG AA for normal-size text. */
    const val AA_RATIO = 4.5

    /** WCAG relative luminance of an opaque 0xRRGGBB / 0xAARRGGBB colour. */
    fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((color shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun blend(from: Int, to: Int, t: Double): Int {
        fun mix(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /**
     * The least-bright blend of [background] toward [primary] that reaches
     * [minRatio] against [background]; [primary] itself if even that falls short
     * (a primary that can't reach the ratio can't be helped by dimming).
     */
    fun dim(primary: Int, background: Int, minRatio: Double = AA_RATIO): Int {
        var t = 0.4
        while (t < 1.0) {
            val candidate = blend(background, primary, t)
            if (contrast(candidate, background) >= minRatio) return candidate
            t += 0.02
        }
        return primary or (0xFF shl 24)
    }

    /**
     * [color] itself if it already reaches [minRatio] against [background]; otherwise
     * the nearest darker shade of the same hue that does (RGB scaled toward black in
     * 5% steps), or black if none does. The way a colour built for a dark widget
     * becomes legible ink on a light one: a cyan that is 17:1 on near-black is under
     * 2:1 on paper, and this keeps its hue while it makes it readable.
     */
    fun darkenToContrast(color: Int, background: Int, minRatio: Double): Int {
        if (contrast(color, background) >= minRatio) return color or (0xFF shl 24)
        var f = 0.95
        while (f > 0.0) {
            val c = scale(color, f)
            if (contrast(c, background) >= minRatio) return c
            f -= 0.05
        }
        return 0xFF000000.toInt()
    }

    private fun scale(color: Int, f: Double): Int {
        fun ch(shift: Int) = (((color shr shift) and 0xFF) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
