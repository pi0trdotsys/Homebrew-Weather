package dev.pi0trdotsys.homebrewweather.widget

import dev.pi0trdotsys.homebrewweather.R

/**
 * Per-widget-instance color theme (a beta "personalization" feature — see
 * WidgetConfigureActivity's theme swatches). Each theme supplies just the
 * two color roles [WeatherWidgetProvider.buildRemoteViews] actually retints
 * via plain `RemoteViews.setTextColor()` calls (no API 31+ ColorStateList
 * calls, so this stays minSdk 24-safe):
 *  - [primaryColorRes]: the widget's main "terminal text" hue (cursor, the
 *    "right now" line, the day-grid day labels).
 *  - [dimColorRes]: the softer "comment"-style hue used for the header label.
 *
 * Deliberately NOT touched by any theme (kept as fixed, semantic/accent
 * colors regardless of theme — see WeatherWidgetProvider for where each
 * lives): the AQI category colors, the online/offline dot, the cyan
 * meta/PoP lines, the amber max/min split in tempSpannable(), and the amber
 * footer joke line — all of those stay exactly as they were before this
 * feature existed, so [PHOSPHOR_GREEN] (the default) renders pixel-identical
 * to the pre-theme widget.
 *
 * [AUTO_HEALTH] is the one exception to "primaryColorRes/dimColorRes are
 * fixed": its color is computed live each refresh from current conditions
 * (see WeatherWidgetProvider.computeHealthTheme()/computeComfortScore()) —
 * its own primaryColorRes/dimColorRes here are just a placeholder ("healthy"
 * green) never actually rendered, since buildRemoteViews() special-cases
 * this theme and re-resolves the real color from one of the *other* three
 * entries' color resources once weather data is available.
 */
enum class WidgetTheme(val storageId: Int, val label: String, val primaryColorRes: Int, val dimColorRes: Int) {
    PHOSPHOR_GREEN(0, "Phosphor Green", R.color.widget_green, R.color.widget_green_dim),
    AMBER_TERMINAL(1, "Amber Terminal", R.color.widget_amber, R.color.widget_amber_dim),
    CYAN(2, "Cyan", R.color.widget_cyan, R.color.widget_cyan_dim),
    CRIMSON(3, "Crimson", R.color.widget_crimson, R.color.widget_crimson_dim),
    AUTO_HEALTH(4, "Auto (health)", R.color.widget_green, R.color.widget_green_dim),

    /** The device's own accent colour (Android 12+); offered only there. */
    MATERIAL_YOU(5, "Material You", R.color.widget_monet, R.color.widget_monet_dim);

    companion object {
        val DEFAULT = PHOSPHOR_GREEN

        fun fromStorageId(id: Int): WidgetTheme = values().firstOrNull { it.storageId == id } ?: DEFAULT
    }
}

/**
 * Per-widget-instance background fill transparency (independent axis from
 * [WidgetTheme] — every theme is available at every transparency level, no
 * theme x transparency drawable matrix). Only the fill alpha of
 * widget_background differs between these four drawables; border/stroke and
 * corner geometry are identical across all of them — see the
 * widget_background_*.xml comments.
 */
enum class WidgetTransparency(
    val storageId: Int,
    val label: String,
    val drawableRes: Int,
    /** The same fill for the light "paper" appearance (see [WidgetAppearance]). */
    val paperDrawableRes: Int,
) {
    OPAQUE(0, "Opaque (100%)", R.drawable.widget_background_100, R.drawable.widget_background_paper_100),
    HIGH(1, "High (85%)", R.drawable.widget_background_85, R.drawable.widget_background_paper_85),
    MEDIUM(2, "Medium (60%)", R.drawable.widget_background_60, R.drawable.widget_background_paper_60),
    LOW(3, "Low (35%)", R.drawable.widget_background_35, R.drawable.widget_background_paper_35);

    companion object {
        val DEFAULT = OPAQUE

        fun fromStorageId(id: Int): WidgetTransparency = values().firstOrNull { it.storageId == id } ?: DEFAULT
    }
}

/**
 * Per-widget-instance background picture, independent of [WidgetTheme] and
 * [WidgetTransparency]. [CITY] is the "city map": contour lines of the terrain
 * around the place, or a skyline where the land is flat (see [WidgetBackdropArt]);
 * [SCENE] is the weather picture — rain, snow, a storm, a starry night (see [WidgetScene]).
 */
enum class WidgetBackdrop(val storageId: Int) {
    NONE(0),
    CITY(1),
    SCENE(2);

    companion object {
        val DEFAULT = NONE

        fun fromStorageId(id: Int): WidgetBackdrop = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}

/**
 * Per-widget-instance light or dark look, independent of the accent [WidgetTheme]
 * and the fill [WidgetTransparency].
 *
 * The widget was built dark: cyan, amber and a phosphor green on near-black. On a
 * bright wallpaper, a translucent dark widget looks like a smudge. [PAPER] is the
 * other look — ink on warm paper, every colour kept in its hue but darkened until it
 * reads — and [AUTO] switches between the two with the sun: paper by day, dark by night.
 * Dark is the default, so no existing widget changes.
 */
enum class WidgetAppearance(val storageId: Int) {
    DARK(0),
    PAPER(1),
    AUTO(2);

    /** Whether to draw paper, given whether it is daytime at the place ([isDay] null = not known yet, so dark). */
    fun isPaper(isDay: Boolean?): Boolean = when (this) {
        DARK -> false
        PAPER -> true
        AUTO -> isDay == true
    }

    companion object {
        val DEFAULT = DARK

        fun fromStorageId(id: Int): WidgetAppearance = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}
