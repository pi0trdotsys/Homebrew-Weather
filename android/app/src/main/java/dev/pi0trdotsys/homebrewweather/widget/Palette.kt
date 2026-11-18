package dev.pi0trdotsys.homebrewweather.widget

import android.content.Context
import dev.pi0trdotsys.homebrewweather.R

/**
 * The colours one widget render draws with: the original dark set, or the same
 * hues made legible on warm paper (see [WidgetAppearance]).
 *
 * Everything in the widget used to ask Android for a colour resource directly
 * (`context.getColor(R.color.widget_cyan)`). Asking the palette instead —
 * [color], [dim] — keeps one call site per colour and lets a light background
 * change what comes back, without a second colour file per theme: on paper a
 * colour is kept if it already reads against it (4.5:1 for text) and darkened in
 * its own hue until it does. Large type (the hero number) needs only 3:1
 * ([large]), so it can stay closer to the original colour.
 *
 * One per render, passed down — never a global, since widgets render in parallel
 * and may differ.
 */
class Palette(private val context: Context, val paper: Boolean, private val highContrast: Boolean) {

    /** What colours are measured against. */
    val background: Int = if (paper) PAPER_BG else DARK_BG

    /** [res], as it should look on this background. */
    fun color(res: Int): Int = adapt(context.getColor(res), TEXT_RATIO)

    /** An already-resolved colour (e.g. from [TempColor]) for small text or fine graphics. */
    fun adapt(color: Int, ratio: Double = TEXT_RATIO): Int =
        if (paper) HighContrast.darkenToContrast(color, PAPER_BG, ratio) else color

    /** For large type (the hero number): 3:1 is enough. */
    fun large(color: Int): Int = adapt(color, LARGE_RATIO)

    /**
     * The quiet ("comment") shade [dimRes] stands for: as designed, or — with high
     * contrast on — a shade of its own hue that reaches AA (see [HighContrast.dim]).
     * Anything that isn't a known dim shade comes back as [color].
     */
    fun dim(dimRes: Int): Int {
        val primaryRes = when (dimRes) {
            R.color.widget_green_dim -> R.color.widget_green
            R.color.widget_cyan_dim -> R.color.widget_cyan
            R.color.widget_amber_dim -> R.color.widget_amber
            R.color.widget_crimson_dim -> R.color.widget_crimson
            R.color.widget_monet_dim -> R.color.widget_monet
            else -> return color(dimRes)
        }
        if (!highContrast) return color(dimRes)
        return HighContrast.dim(color(primaryRes), background)
    }

    /** Colour of a tint on the meta line. */
    fun meta(tint: MetaTint): Int = when (tint) {
        MetaTint.DIM -> dim(R.color.widget_green_dim)
        MetaTint.CYAN -> color(R.color.widget_cyan)
        MetaTint.AMBER -> color(R.color.widget_amber)
        MetaTint.CRIMSON -> color(R.color.widget_crimson)
        MetaTint.GREEN -> color(R.color.widget_green)
    }

    companion object {
        const val DARK_BG = 0xFF0A0F0A.toInt()
        const val PAPER_BG = 0xFFF3EFE2.toInt()
        private const val TEXT_RATIO = 4.5
        private const val LARGE_RATIO = 3.0
    }
}
