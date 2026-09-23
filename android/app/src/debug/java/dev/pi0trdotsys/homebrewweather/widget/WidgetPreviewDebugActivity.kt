package dev.pi0trdotsys.homebrewweather.widget

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.pi0trdotsys.homebrewweather.R

/**
 * DEV-ONLY verification harness. Lives entirely under src/debug/ (this file and
 * its manifest registration), so it is only ever compiled into debug builds —
 * absent from release, and never reachable from the launcher (no LAUNCHER
 * intent-filter is declared for it anywhere).
 *
 * Exists because dragging a widget onto a home screen to eyeball a layout change
 * is fragile to automate and only ever proves one size. This seeds fake
 * (deliberately worst-case) weather data into [WidgetPrefs]' cache for a
 * throwaway appWidgetId, flips [WeatherWidgetProvider.debugForceOfflineCache] so
 * [WeatherWidgetProvider.buildRemoteViews] skips the live network fetch and reads
 * that cache instead, then inflates the *actual* returned RemoteViews tree into
 * a container per entry in [SIZES] — the same real code path production uses,
 * not a hand-copied approximation.
 *
 * [SIZES] is the point: the widget is now sized continuously from its granted
 * footprint (see [WidgetMetrics]), so the thing worth checking is the *range*,
 * not one blessed size. The list deliberately spans from the declared
 * minResize up past what real launchers grant, and includes the exact
 * 368x176dp a real device was measured at.
 *
 * Launch with:
 *   adb shell am start -n dev.pi0trdotsys.homebrewweather/.widget.WidgetPreviewDebugActivity
 * then inspect with:
 *   adb exec-out screencap -p > preview.png
 */
class WidgetPreviewDebugActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        seedFakeData(FAKE_WIDGET_ID)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        root.addView(caption("DEBUG: widget size matrix (dev-only, not shipped)", Color.parseColor("#ff5555")))
        addIconSheet(root)

        WeatherWidgetProvider.debugForceOfflineCache = true
        try {
            SIZES.forEach { (label, w, h, hideBanner) ->
                root.addView(caption("${w}x${h}dp — $label", Color.parseColor("#33ff66")))

                // Stand in for the host's onAppWidgetOptionsChanged: a fake id
                // that was never really placed can't receive one, and
                // WidgetSize.resolve falls back to exactly these prefs when the
                // host has no live options for an id.
                WidgetPrefs.setLastKnownMinWidthDp(this, FAKE_WIDGET_ID, w)
                WidgetPrefs.setLastKnownMinHeightDp(this, FAKE_WIDGET_ID, h)

                val rv = WeatherWidgetProvider.buildRemoteViews(applicationContext, FAKE_WIDGET_ID)
                // The status banner is a real content overlay by design, so one
                // render per size with it hidden proves the rows underneath have
                // no independent clipping.
                if (hideBanner) rv.setViewVisibility(R.id.widget_status_banner, View.GONE)

                val container = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(w), dp(h)).apply {
                        topMargin = dp(6)
                        bottomMargin = dp(10)
                    }
                    setBackgroundColor(Color.parseColor("#181818"))
                }
                container.addView(rv.apply(applicationContext, container))
                root.addView(container)
            }
        } finally {
            // Don't leave the process-wide debug seam flipped on beyond this screen.
            WeatherWidgetProvider.debugForceOfflineCache = false
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Color.BLACK)
                isFillViewport = true
                addView(
                    root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
        )
    }

    /**
     * Every pixel-icon kind, at the two sizes the widget actually draws them
     * and across all four animation frames.
     *
     * The size matrix below only ever exercises whichever kinds the fake
     * forecast happens to contain, at whatever frame the blink tick left
     * persisted — so before this existed there was no way to look at, say,
     * the fog icon, or to see a sun's twinkle frame next to its full frame.
     * That is exactly how the previous grids shipped with a storm cloud
     * almost the same colour as the widget background.
     *
     * Left column is the 4-day grid size, right column the hero size.
     */
    private fun addIconSheet(root: LinearLayout) {
        root.addView(caption("pixel icons — grid size (20dp) / hero size (30dp), frames 0-3", Color.parseColor("#55ffff")))
        PixelIcons.ICONS.keys.forEach { kind ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4) }
                setBackgroundColor(Color.parseColor("#0a0f0a"))
                setPadding(dp(4), dp(3), dp(4), dp(3))
            }
            row.addView(
                TextView(this).apply {
                    text = kind.padEnd(8)
                    setTextColor(Color.parseColor("#4a6a4a"))
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                },
            )
            listOf(20, 30).forEach { sizeDp ->
                for (frame in 0 until 4) {
                    row.addView(
                        android.widget.ImageView(this).apply {
                            setImageBitmap(WidgetGraphics.icon(context, kind, dp(sizeDp), frame))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply { marginEnd = dp(5) }
                        },
                    )
                }
            }
            root.addView(row)
        }
    }

    private fun caption(text: String, color: Int) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        typeface = android.graphics.Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        gravity = Gravity.START
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(10) }
    }

    private fun dp(value: Int): Int =
        Math.round(value * resources.displayMetrics.density)

    /**
     * Deliberately worst-case-ish sample data (long-ish city name, a mix of icon
     * kinds across the 4 days including a below-zero day, a max-length
     * "unhealthy (sensitive)" AQI label, 100% PoP) so the layout gets
     * stress-tested against real content widths, not best-case short strings.
     */
    private fun seedFakeData(fakeWidgetId: Int) {
        WidgetPrefs.setCity(
            this,
            fakeWidgetId,
            WidgetCity(lat = 52.2297, lon = 21.0122, name = "Warszawa-Śródmieście", isLive = false),
        )
        val fakeWeather = WeatherApi.WeatherData(
            isDay = true,
            currentWeatherCode = 61, // rain
            currentTemperature = 18.0,
            apparentTemperature = 16.0,
            humidityPercent = 82,
            windSpeedKmh = 23.0,
            currentPrecipitationProbability = 74,
            usAqi = 142, // -> "unhealthy (sensitive)", the longest AQI category label
            daily = listOf(
                WeatherApi.DailyEntry(date = "2026-07-31", weatherCode = 0, tempMax = 29.0, tempMin = 14.0, precipitationProbabilityMax = 12),
                WeatherApi.DailyEntry(date = "2026-08-01", weatherCode = 61, tempMax = 22.0, tempMin = 11.0, precipitationProbabilityMax = 88),
                WeatherApi.DailyEntry(date = "2026-08-02", weatherCode = 95, tempMax = 19.0, tempMin = 9.0, precipitationProbabilityMax = 100),
                WeatherApi.DailyEntry(date = "2026-08-03", weatherCode = 71, tempMax = -2.0, tempMin = -8.0, precipitationProbabilityMax = 45),
            ),
        )
        WidgetPrefs.setCachedWeather(this, fakeWidgetId, fakeWeather)
    }

    private data class Preview(
        val label: String,
        val widthDp: Int,
        val heightDp: Int,
        val hideBanner: Boolean = true,
    )

    companion object {
        private const val FAKE_WIDGET_ID = -777

        private val SIZES = listOf(
            Preview("real device: Lawnchair 4x2 on a 400dp-wide screen", 368, 176),
            Preview("same, with the status-banner overlay showing", 368, 176, hideBanner = false),
            Preview("declared default (minWidth x minHeight)", 250, 165),
            Preview("wide and short — a squashed resize", 368, 110),
            Preview("declared minResize", 180, 90),
            Preview("narrow square — a 2x2-ish placement", 170, 170),
            Preview("generous — taller than anything declared", 300, 260),
        )
    }
}
