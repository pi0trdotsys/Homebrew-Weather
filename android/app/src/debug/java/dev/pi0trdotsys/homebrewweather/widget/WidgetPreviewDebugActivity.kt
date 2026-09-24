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
 *   adb shell am start -n dev.pi0trdotsys.homebrewweather.debug/dev.pi0trdotsys.homebrewweather.widget.WidgetPreviewDebugActivity
 * (debug builds carry the ".debug" applicationId suffix and install next to
 * the release app, so this never disturbs a real home-screen widget)
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
            SIZES.forEach { (label, w, h, hideBanner, density, data) ->
                root.addView(
                    caption(
                        "${w}x${h}dp · ${density.label} · ${if (data == Data.CALM) "calm day" else "eventful day"} — $label",
                        Color.parseColor("#33ff66"),
                    ),
                )

                // Stand in for the host's onAppWidgetOptionsChanged: a fake id
                // that was never really placed can't receive one, and
                // WidgetSize.resolve falls back to exactly these prefs when the
                // host has no live options for an id.
                WidgetPrefs.setLastKnownMinWidthDp(this, FAKE_WIDGET_ID, w)
                WidgetPrefs.setLastKnownMinHeightDp(this, FAKE_WIDGET_ID, h)
                WidgetPrefs.setDensity(this, FAKE_WIDGET_ID, density)
                WidgetPrefs.setCachedWeather(this, FAKE_WIDGET_ID, if (data == Data.CALM) CALM_DAY else EVENTFUL_DAY)

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

    private fun seedFakeData(fakeWidgetId: Int) {
        WidgetPrefs.setCity(
            this,
            fakeWidgetId,
            WidgetCity(lat = 52.2297, lon = 21.0122, name = "Warszawa-Śródmieście", isLive = false),
        )
    }

    private enum class Data { CALM, EVENTFUL }

    private data class Preview(
        val label: String,
        val widthDp: Int,
        val heightDp: Int,
        val hideBanner: Boolean = true,
        val density: WidgetDensity = WidgetDensity.STANDARD,
        val data: Data = Data.EVENTFUL,
    )

    companion object {
        private const val FAKE_WIDGET_ID = -777

        private fun hours(vararg pops: Int, codes: List<Int> = emptyList()) =
            pops.mapIndexed { i, p ->
                WeatherApi.HourlyEntry(
                    time = "2026-07-31T%02d:00".format((11 + i) % 24),
                    precipitationProbability = p,
                    weatherCode = codes.getOrNull(i) ?: if (p >= 50) 61 else 2,
                )
            }

        /**
         * A settled, dry, ordinary day — the case the widget used to shout on
         * and now should be quiet about. Modelled on the real forecast the
         * device showed for Tolox (30-33° highs, 18-21° lows, AQI 36, no rain).
         */
        private val CALM_DAY = WeatherApi.WeatherData(
            isDay = true,
            currentWeatherCode = 1,
            currentTemperature = 27.0,
            apparentTemperature = 25.0,
            humidityPercent = 40,
            windSpeedKmh = 4.0,
            currentPrecipitationProbability = 0,
            usAqi = 36,
            daily = listOf(
                WeatherApi.DailyEntry("2026-07-31", 1, 31.0, 18.0, 0),
                WeatherApi.DailyEntry("2026-08-01", 1, 30.0, 18.0, 0),
                WeatherApi.DailyEntry("2026-08-02", 2, 33.0, 18.0, 0),
                WeatherApi.DailyEntry("2026-08-03", 3, 32.0, 21.0, 0),
            ),
            hourly = hours(*IntArray(24)),
        )

        /**
         * Deliberately worst-case sample data: long city name, every icon kind
         * including a sub-zero day, the longest AQI category, 100% PoP, rain
         * arriving this afternoon, strong wind, feels-like well below the
         * thermometer — everything the standard density has a rule for, all at
         * once, so the layout is stress-tested against real content widths.
         */
        private val EVENTFUL_DAY = WeatherApi.WeatherData(
            isDay = true,
            currentWeatherCode = 3,
            currentTemperature = 18.0,
            apparentTemperature = 13.0,
            humidityPercent = 94,
            windSpeedKmh = 42.0,
            currentPrecipitationProbability = 20,
            usAqi = 142, // -> "sensitive", the longest short AQI label
            daily = listOf(
                WeatherApi.DailyEntry("2026-07-31", 61, 22.0, 14.0, 70),
                WeatherApi.DailyEntry("2026-08-01", 2, 22.0, 11.0, 12),
                WeatherApi.DailyEntry("2026-08-02", 95, 19.0, 9.0, 100),
                WeatherApi.DailyEntry("2026-08-03", 71, -2.0, -8.0, 45),
            ),
            hourly = hours(20, 20, 30, 40, 80, 90, 70, 60, 20, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        )

        private val SIZES = listOf(
            Preview("real device, ordinary day", 368, 176, data = Data.CALM),
            Preview("real device, everything happening at once", 368, 176),
            Preview("real device, every readout", 368, 176, density = WidgetDensity.FULL),
            Preview("real device, temperature and 4 days", 368, 176, density = WidgetDensity.MINIMAL),
            Preview("status-banner overlay showing", 368, 176, hideBanner = false),
            Preview("declared default (minWidth x minHeight)", 250, 165),
            Preview("wide and short — a squashed resize", 368, 110),
            Preview("declared minResize", 180, 90),
            Preview("narrow square — a 2x2-ish placement", 170, 170),
            Preview("generous — taller than anything declared", 300, 260),
            Preview("generous, full density", 300, 260, density = WidgetDensity.FULL),
        )
    }
}
