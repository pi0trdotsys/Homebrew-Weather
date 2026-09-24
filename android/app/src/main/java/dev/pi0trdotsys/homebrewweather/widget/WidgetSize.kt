package dev.pi0trdotsys.homebrewweather.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle

/**
 * The footprint (dp) a host has actually granted one widget instance.
 *
 * ## The bug this exists to fix
 *
 * `AppWidgetManager`'s four size options are *not* a (min, max) pair per axis —
 * they're a portrait/landscape pair, split across the two names in a way that
 * reads backwards:
 *
 * | option                       | meaning                   |
 * |------------------------------|---------------------------|
 * | `OPTION_APPWIDGET_MIN_WIDTH`  | width in **portrait**     |
 * | `OPTION_APPWIDGET_MAX_WIDTH`  | width in **landscape**    |
 * | `OPTION_APPWIDGET_MIN_HEIGHT` | height in **landscape**   |
 * | `OPTION_APPWIDGET_MAX_HEIGHT` | height in **portrait**    |
 *
 * So for a home screen used in portrait, the real size is
 * (`MIN_WIDTH`, `MAX_HEIGHT`) — the two options whose names don't match.
 *
 * The previous code read (`MIN_WIDTH`, `MIN_HEIGHT`) and treated it as the
 * current size. On the reporting user's device that meant a widget genuinely
 * rendering at 368x176dp was measured as 368x**96**dp (96dp being its *landscape*
 * height), which put it below the compact-layout height threshold: it drew the
 * stripped-down small layout — tiny text, no sparkline/AQI/sync/meta — inside a
 * frame with ~70dp of visibly empty grid below the content. Not a styling
 * problem; the widget simply never knew how big it was.
 *
 * ## Resolution order
 *
 * Live options from the host first (they're always current, and unlike the
 * `onAppWidgetOptionsChanged` callback they don't depend on having received a
 * resize event this process lifetime), then the last values persisted to
 * [WidgetPrefs], then the declared defaults. The persisted fallback is what lets
 * the debug preview harness — which renders a fake widget id the host has never
 * heard of — drive this at arbitrary sizes.
 */
data class WidgetSize(val widthDp: Int, val heightDp: Int) {

    fun metrics(rows: WidgetMetrics.Rows = WidgetMetrics.Rows.ALL): WidgetMetrics =
        WidgetMetrics.forSize(widthDp, heightDp, rows)

    companion object {

        /**
         * Reads the current footprint for [appWidgetId], preferring what the host
         * reports right now over anything cached.
         */
        fun resolve(context: Context, appWidgetId: Int): WidgetSize {
            val fromHost = try {
                AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
            } catch (e: Exception) {
                null
            }
            val live = fromHost?.let { fromOptions(context, it) }
            if (live != null) return live

            return WidgetSize(
                widthDp = WidgetPrefs.getLastKnownMinWidthDp(context, appWidgetId),
                heightDp = WidgetPrefs.getLastKnownMinHeightDp(context, appWidgetId),
            )
        }

        /**
         * Picks the width/height pair matching the device's current orientation
         * out of [options]' four values, or null when the host hasn't populated
         * them yet (every value 0 — normal for an instance that has never been
         * measured, e.g. between placement and first layout).
         */
        fun fromOptions(context: Context, options: Bundle): WidgetSize? {
            val portrait =
                context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE

            val width = options.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
                else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
                0,
            )
            val height = options.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
                else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                0,
            )

            // Some hosts populate only one orientation's pair; fall back to the
            // other axis-mate rather than reporting a nonsense 0.
            val w = if (width > 0) width else options.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                0,
            )
            val h = if (height > 0) height else options.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                0,
            )

            if (w <= 0 || h <= 0) return null
            return WidgetSize(w, h)
        }
    }
}
