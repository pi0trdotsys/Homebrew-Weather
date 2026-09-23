package dev.pi0trdotsys.homebrewweather.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import dev.pi0trdotsys.homebrewweather.MainActivity
import dev.pi0trdotsys.homebrewweather.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Native home-screen widget (AppWidgetProvider + RemoteViews) that mirrors
 * the (now-removed) src/components/WeatherWidget.tsx web tile, but runs
 * entirely independent of the WebView — its own network calls, its own
 * per-instance city, its own refresh loop.
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { refreshWidget(context, appWidgetManager, it) }
        WeatherWorker.enqueuePeriodic(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    refreshWidget(context, AppWidgetManager.getInstance(context), id)
                }
            }
            ACTION_BLINK_TICK -> handleBlinkTick(context)
        }
    }

    override fun onEnabled(context: Context) {
        WeatherWorker.enqueuePeriodic(context)
        BlinkAlarm.start(context)
    }

    override fun onDisabled(context: Context) {
        WeatherWorker.cancelPeriodic(context)
        BlinkAlarm.stop(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.removeCity(context, it) }
    }

    /**
     * Called by the host when this instance is first placed and on every resize.
     *
     * The footprint is resolved by [WidgetSize.fromOptions] rather than read
     * straight out of [newOptions] here — see that class for why the four
     * `OPTION_APPWIDGET_*` values are a portrait/landscape pair rather than the
     * (min, max) pair their names suggest, and what reading them the obvious way
     * did to this widget. The result is cached in [WidgetPrefs] only as a
     * fallback; renders read the host's live options first.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        WidgetSize.fromOptions(context, newOptions)?.let { size ->
            WidgetPrefs.setLastKnownMinWidthDp(context, appWidgetId, size.widthDp)
            WidgetPrefs.setLastKnownMinHeightDp(context, appWidgetId, size.heightDp)
        }
        refreshWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        const val ACTION_REFRESH = "dev.pi0trdotsys.homebrewweather.ACTION_REFRESH"
        const val ACTION_BLINK_TICK = "dev.pi0trdotsys.homebrewweather.ACTION_BLINK_TICK"

        /**
         * Debug-only seam: when true, [buildRemoteViews] skips the live network
         * fetch entirely and renders straight from whatever's in
         * [WidgetPrefs.getCachedWeather] for the given appWidgetId. Only ever
         * flipped true by the debug-build-only WidgetPreviewDebugActivity (see
         * src/debug/), so a real-device layout check can render deterministic,
         * offline-safe sample data through the exact same RemoteViews-building
         * code path production uses — no hand-copied approximation. Always
         * false in any build that doesn't explicitly set it.
         */
        @Volatile
        var debugForceOfflineCache: Boolean = false

        private val bgExecutor = Executors.newCachedThreadPool()
        private val DOW = arrayOf("nd", "pn", "wt", "śr", "cz", "pt", "sb")

        // Sizes are no longer constants here. Every dp/sp the widget draws is
        // derived from the footprint the host granted this instance — see
        // WidgetMetrics (the ladder) and WidgetSize (reading the footprint
        // correctly), and applyMetrics() below for where they land.

        fun componentName(context: Context) = ComponentName(context, WeatherWidgetProvider::class.java)

        /** All currently-placed widget ids, e.g. for the periodic worker. */
        fun allWidgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(componentName(context))

        /** Kicks off a background fetch + RemoteViews push for a single widget instance. */
        fun refreshWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            // Quick "refreshing…" feedback (cheap partial update, no network) before
            // the background fetch completes — matches the web mockup's refreshing state.
            try {
                val spinner = RemoteViews(context.packageName, R.layout.weather_widget)
                spinner.setViewVisibility(R.id.widget_refresh_spinner, View.VISIBLE)
                spinner.setViewVisibility(R.id.widget_status_banner, View.VISIBLE)
                spinner.setTextViewText(R.id.widget_status_banner, "⟳ refreshing…")
                spinner.setTextColor(R.id.widget_status_banner, context.getColor(R.color.widget_cyan))
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, spinner)
            } catch (e: Exception) {
                // best-effort; the full render below still runs regardless
            }

            bgExecutor.execute {
                try {
                    val rv = buildRemoteViews(context, appWidgetId)
                    appWidgetManager.updateAppWidget(appWidgetId, rv)
                } catch (e: Exception) {
                    // Best-effort widget; swallow so a single bad refresh never crashes the host process.
                }
            }
        }

        /** Handles a ~60s BlinkAlarm tick: flips the shared cursor state, advances the
         * shared icon-animation frame counter, and pushes a cheap partial update (no
         * network re-fetch — icon bitmaps are just re-rendered from each widget's
         * already-cached weather codes) to every currently-placed widget instance. */
        private fun handleBlinkTick(context: Context) {
            val cursorOn = BlinkPrefs.toggle(context)
            val frame = BlinkPrefs.advanceFrame(context)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            allWidgetIds(context).forEach { id ->
                val rv = RemoteViews(context.packageName, R.layout.weather_widget)
                rv.setViewVisibility(R.id.widget_cursor, if (cursorOn) View.VISIBLE else View.GONE)

                // Same metrics the last full render used, so a tick's icons come
                // back at the size they were — an ImageView sized by its bitmap
                // (see WidgetGraphics.sized) would otherwise resize the row on
                // every blink if this used a different ladder.
                val m = WidgetSize.resolve(context, id).metrics()
                val iconPx = dpToPx(context, m.dayIconDp)
                val nowIconPx = dpToPx(context, m.heroIconDp)
                // Re-render each day's icon at the new frame from cached weather codes
                // only — no network call, matching the "no new battery cost" tradeoff.
                val weather = WidgetPrefs.getCachedWeather(context, id)
                if (weather != null) {
                    for (i in 0 until 4) {
                        val entry = weather.daily.getOrNull(i) ?: continue
                        val kind = Wmo.wmoToKind(entry.weatherCode)
                        rv.setImageViewBitmap(ICON_IDS[i], WidgetGraphics.icon(context, kind, iconPx, frame))
                    }
                    // Keep the "now" line's icon animating in lockstep with the
                    // day-grid icons on every blink tick too (same kind-resolution
                    // rule as the full buildRemoteViews() path below).
                    val nowKind = Wmo.wmoToKind(weather.currentWeatherCode)
                    val nowIconKind = if (!weather.isDay && (nowKind == "sun" || nowKind == "partly")) "moon" else nowKind
                    rv.setImageViewBitmap(R.id.widget_now_icon, WidgetGraphics.icon(context, nowIconKind, nowIconPx, frame))
                }
                try {
                    appWidgetManager.partiallyUpdateAppWidget(id, rv)
                } catch (e: Exception) {
                    // best-effort, continue with the next widget
                }
            }
        }

        private fun dpToPx(context: Context, dp: Int): Int =
            Math.round(dp * context.resources.displayMetrics.density)

        /**
         * "Follow my location" (WidgetCity.isLive) resolution for a background
         * refresh. A background context can't easily run a full
         * requestLocationUpdates()-with-timeout loop the way the foreground
         * WidgetConfigureActivity does (see requestLocationFix()/safeLastKnown()
         * there) — so, same pragmatic beta-appropriate tradeoff as BlinkAlarm's
         * doc comment elsewhere in this file: a best-effort
         * getLastKnownLocation() across providers is the right, simpler choice
         * here, not a fresh GPS fix.
         *
         * Returns [storedCity] unchanged when it isn't in live mode. When it is
         * live but no last-known fix is available from any provider (e.g. right
         * after boot, before any provider has a fix), also gracefully returns
         * [storedCity] as-is rather than crashing or blanking the widget.
         * Otherwise re-resolves lat/lon (+ a best-effort reverse-geocoded name)
         * and persists the refreshed WidgetCity so the offline-fallback cache
         * and header label stay current too.
         */
        private fun resolveEffectiveCity(context: Context, appWidgetId: Int, storedCity: WidgetCity): WidgetCity {
            if (!storedCity.isLive) return storedCity
            val location = bestEffortLastKnownLocation(context) ?: return storedCity

            // DeviceGeocoder (on-device, no network) first — see its doc comment:
            // WeatherApi.reverseGeocode (Open-Meteo's `/v1/reverse`) is a dead
            // endpoint, kept only as a last-ditch fallback. If both fail this
            // refresh, keep whatever name was already known (storedCity.name)
            // rather than regressing a real, previously-resolved name back to a
            // coordinate label over one transient miss — [DeviceGeocoder] only
            // gets used as the *initial* name in WidgetConfigureActivity.
            val name = DeviceGeocoder.cityName(context, location.latitude, location.longitude)
                ?: try { WeatherApi.reverseGeocode(location.latitude, location.longitude)?.name } catch (e: Exception) { null }
                ?: storedCity.name
            val updated = WidgetCity(
                lat = location.latitude,
                lon = location.longitude,
                name = name,
                isLive = true,
            )
            WidgetPrefs.setCity(context, appWidgetId, updated)
            return updated
        }

        /** Best-effort getLastKnownLocation() across GPS/network/passive providers —
         * never requests a fresh fix, never throws (permission or provider errors are
         * swallowed), just returns the first non-null cached fix or null. */
        private fun bestEffortLastKnownLocation(context: Context): Location? {
            val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (!fineGranted && !coarseGranted) return null

            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            for (provider in providers) {
                try {
                    val loc = lm.getLastKnownLocation(provider)
                    if (loc != null) return loc
                } catch (e: SecurityException) {
                    // no permission for this provider — try the next one
                } catch (e: IllegalArgumentException) {
                    // provider not present on this device — try the next one
                }
            }
            return null
        }

        /** Maps a US AQI (0..500+) reading to a short compact label + color resource,
         * per the standard US EPA AQI category breakpoints. Internal (not private):
         * reused as-is by [WeatherNotifier]'s AQI alert text and by
         * [computeComfortScore]'s AQI penalty, per those callers' doc comments — the
         * category breakpoints live in exactly one place. */
        internal fun aqiLabelAndColor(aqi: Int): Pair<String, Int> = when {
            aqi <= 50 -> "good" to R.color.widget_aqi_good
            aqi <= 100 -> "moderate" to R.color.widget_aqi_moderate
            aqi <= 150 -> "unhealthy (sensitive)" to R.color.widget_aqi_sensitive
            aqi <= 200 -> "unhealthy" to R.color.widget_aqi_unhealthy
            aqi <= 300 -> "very unhealthy" to R.color.widget_aqi_very_unhealthy
            else -> "hazardous" to R.color.widget_aqi_hazardous
        }

        /**
         * Widget-only short form of [aqiLabelAndColor]'s category name.
         *
         * Two of the six US AQI category names are long enough that
         * "AQI 142 · unhealthy (sensitive)" cannot fit the hero's stat column at
         * any size the widget is likely to be given, and an AQI reading whose
         * category has been ellipsized away is just a number. These two
         * replacements bring the worst case to 19 characters, which is the width
         * WidgetMetrics sizes that column against.
         *
         * Deliberately a separate function rather than a change to
         * [aqiLabelAndColor]: that function's exact strings are matched on by
         * [aqiComfortPenalty] and reused verbatim by [WeatherNotifier], neither
         * of which has a width problem.
         */
        private fun aqiShortLabel(aqi: Int): String = when (val label = aqiLabelAndColor(aqi).first) {
            "unhealthy (sensitive)" -> "sensitive"
            "very unhealthy" -> "very bad"
            else -> label
        }

        /** AQI penalty term of [computeComfortScore], keyed off the exact same
         * category labels [aqiLabelAndColor] already returns for the AQI line —
         * never redefines the US AQI breakpoints. */
        private fun aqiComfortPenalty(aqi: Int): Int = when (aqiLabelAndColor(aqi).first) {
            "good" -> 0
            "moderate" -> 15
            "unhealthy (sensitive)" -> 35
            "unhealthy" -> 55
            "very unhealthy" -> 80
            else -> 100 // "hazardous"
        }

        /**
         * "Is it pleasant/healthy to go outside right now" comfort score (0..100,
         * higher = better), used by [WidgetTheme.AUTO_HEALTH] to pick a color.
         * Returns null only when there isn't even a current temperature reading
         * to work with (e.g. a very old/partial offline cache) — every other
         * factor here is optional and simply skipped when unavailable.
         *
         * Deviation from the literal spec pseudocode: the temperature factor's
         * penalty is `min(40, 3 * distance)` (cap the *penalty*, i.e. after the
         * 3x multiplier) rather than `3 * min(40, distance)` (which would cap
         * the penalty at 120, not 40) — the spec's own clarifying comment
         * ("clamp the per-factor penalty to at most 40 points") only matches the
         * former.
         */
        private fun computeComfortScore(weather: WeatherApi.WeatherData): Int? {
            val temp = weather.currentTemperature
            if (temp.isNaN()) return null

            var score = 100.0

            if (temp < 18.0 || temp > 24.0) {
                val distanceFromBand = if (temp < 18.0) 18.0 - temp else temp - 24.0
                score -= minOf(40.0, 3.0 * distanceFromBand)
            }

            if (weather.currentPrecipitationProbability >= 0) {
                score -= weather.currentPrecipitationProbability * 0.4
            }

            if (weather.maxNext6hPop >= 0) {
                score -= weather.maxNext6hPop * 0.2
            }

            if (weather.usAqi >= 0) {
                score -= aqiComfortPenalty(weather.usAqi)
            }

            return score.coerceIn(0.0, 100.0).roundToInt()
        }

        /** Picks which of the 3 fixed-hue themes [WidgetTheme.AUTO_HEALTH] should
         * render as for this refresh, reusing their existing color resources
         * directly rather than adding new ones (per WidgetTheme.kt's doc
         * comment). Falls back to [WidgetTheme.PHOSPHOR_GREEN] (green/"healthy")
         * whenever [computeComfortScore] can't compute a score at all. */
        private fun computeHealthTheme(weather: WeatherApi.WeatherData): WidgetTheme {
            val score = computeComfortScore(weather) ?: return WidgetTheme.PHOSPHOR_GREEN
            return when {
                score >= 75 -> WidgetTheme.PHOSPHOR_GREEN
                score >= 45 -> WidgetTheme.AMBER_TERMINAL
                else -> WidgetTheme.CRIMSON
            }
        }

        private fun isOnline(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        private fun dowAbbrev(isoDate: String): String {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = sdf.parse(isoDate) ?: return "?"
                val cal = Calendar.getInstance()
                cal.time = date
                // Calendar.DAY_OF_WEEK: Sunday=1 .. Saturday=7, matches DOW[0..6] order (nd..sb)
                DOW[cal.get(Calendar.DAY_OF_WEEK) - 1]
            } catch (e: Exception) {
                "?"
            }
        }

        /** Short Polish condition label for the Terminal 2.0 hero's "now" line. */
        private fun kindLabel(kind: String): String = when (kind) {
            "sun" -> "słonecznie"
            "partly" -> "częściowo"
            "cloud" -> "pochmurno"
            "fog" -> "mgła"
            "rain" -> "deszcz"
            "snow" -> "śnieg"
            "thunder" -> "burza"
            "moon" -> "noc"
            else -> kind
        }

        /** 4-day POP trend as Unicode block chars (Terminal 2.0 sparkline),
         * rendered into [R.id.widget_sparkline] — mirrors
         * WidgetMock4x2.tsx's popToSparkline()/spark.bars: proportional to
         * `pop / max(1, maxPop)` (never sorted — stays in day order so it
         * reads as a trend), one char per of the first 4 forecast days. */
        private fun popSparkline(weather: WeatherApi.WeatherData): String {
            val pops = weather.daily.take(4).map { it.precipitationProbabilityMax }
            if (pops.isEmpty()) return ""
            val max = maxOf(1, pops.maxOrNull() ?: 0)
            val chars = charArrayOf('▁', '▂', '▃', '▄', '▅', '▆', '▇', '█')
            return pops.joinToString("") { p ->
                val idx = ((p.toFloat() / max) * (chars.size - 1)).roundToInt().coerceIn(0, chars.size - 1)
                chars[idx].toString()
            }
        }

        /** HH:mm:ss at render time — [R.id.widget_sync_line]'s "sync ..." text. */
        private fun currentTimeHms(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

        /** "24°/14°" — no spaces around the slash (matches WidgetMock4x2.tsx's
         * `{d.tempDay}°` / `/` / `{d.tempNight}°` spans exactly, and is what
         * actually fits the 13sp column of a true-4x2 40dp-tall grid row
         * without horizontal ellipsis-clipping). Day = amber, "/" = dim cyan,
         * night = cyan — mirrors the mockup's amber/hudCyanDim/hudCyan split
         * (previously night was widget_green here, a pre-existing deviation
         * from the token file fixed as part of this pass). */
        private fun tempSpannable(context: Context, dayMax: Double, nightMin: Double): SpannableString {
            val text = "${Math.round(dayMax)}°/${Math.round(nightMin)}°"
            val span = SpannableString(text)
            val amber = context.getColor(R.color.widget_amber)
            val cyan = context.getColor(R.color.widget_cyan)
            val cyanDim = context.getColor(R.color.widget_cyan_dim)
            val slashIdx = text.indexOf('/')
            span.setSpan(ForegroundColorSpan(amber), 0, slashIdx, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(ForegroundColorSpan(cyanDim), slashIdx, slashIdx + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(ForegroundColorSpan(cyan), slashIdx + 1, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            return span
        }

        /** Compact-width variant of [tempSpannable]: today's daily max only, no
         * "/ min°" pair — the full pair reliably wraps onto a 2nd line in each
         * ~42dp-wide grid column at minResizeWidth (180dp), and a wrapped temp
         * pushes the PoP row below it off the bottom of the grid. Dropping the
         * night-min figure here (still shown in the default 250dp-wide layout)
         * is the deliberate trade-off. */
        private fun compactTempSpannable(context: Context, dayMax: Double): SpannableString {
            val text = "${Math.round(dayMax)}°"
            val span = SpannableString(text)
            span.setSpan(
                ForegroundColorSpan(context.getColor(R.color.widget_amber)),
                0,
                text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            return span
        }

        /** "feels like / humidity / wind" — real data for the reclaimed space in
         * the default (non-compact) layout's widget_meta_line row. Only surfaces
         * fields WeatherApi actually parsed; missing/unknown readings (NaN / -1
         * sentinels, e.g. from an older offline cache) are simply omitted rather
         * than shown as garbage. */
        private fun metaLine(weather: WeatherApi.WeatherData): String {
            val parts = mutableListOf<String>()
            if (!weather.apparentTemperature.isNaN()) {
                parts += "feels ${Math.round(weather.apparentTemperature)}°"
            }
            if (weather.humidityPercent >= 0) {
                parts += "hum ${weather.humidityPercent}%"
            }
            if (!weather.windSpeedKmh.isNaN()) {
                parts += "wind ${Math.round(weather.windSpeedKmh)}km/h"
            }
            return if (parts.isEmpty()) "" else parts.joinToString("  ·  ")
        }

        private val DAY_LABEL_IDS = intArrayOf(R.id.widget_day0_label, R.id.widget_day1_label, R.id.widget_day2_label, R.id.widget_day3_label)
        private val ICON_IDS = intArrayOf(R.id.widget_icon0, R.id.widget_icon1, R.id.widget_icon2, R.id.widget_icon3)
        private val TEMP_IDS = intArrayOf(R.id.widget_temp0, R.id.widget_temp1, R.id.widget_temp2, R.id.widget_temp3)
        private val POP_IDS = intArrayOf(R.id.widget_pop0, R.id.widget_pop1, R.id.widget_pop2, R.id.widget_pop3)
        private val BAR_IDS = intArrayOf(R.id.widget_bar0, R.id.widget_bar1, R.id.widget_bar2, R.id.widget_bar3)

        private fun sp(rv: RemoteViews, id: Int, size: Float) =
            rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, size)

        private fun vis(rv: RemoteViews, id: Int, show: Boolean) =
            rv.setViewVisibility(id, if (show) View.VISIBLE else View.GONE)

        /**
         * Sizes a view whose extent can't be inferred from its content — the
         * online dot and the refresh hit-box, both of which draw a fixed
         * drawable rather than a runtime bitmap.
         *
         * `setViewLayoutWidth`/`Height` are API 31+, so below that these two keep
         * the modest dp declared in the layout. That's a deliberate floor, not an
         * oversight: they're the two smallest chrome elements on the widget, and
         * everything that carries actual information (text, weather icons, range
         * bars) sizes itself on every API level via setTextViewTextSize and
         * bitmap-intrinsic sizing.
         */
        private fun sizeDp(rv: RemoteViews, id: Int, dp: Float) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                rv.setViewLayoutWidth(id, dp, TypedValue.COMPLEX_UNIT_DIP)
                rv.setViewLayoutHeight(id, dp, TypedValue.COMPLEX_UNIT_DIP)
            }
        }

        /**
         * Pushes a whole [WidgetMetrics] ladder onto a freshly-inflated tree:
         * every text size, the content padding, and which rows this tier shows.
         *
         * This is the single place layout sizing happens now. res/layout/
         * weather_widget.xml deliberately declares no row heights and only
         * placeholder text sizes — see its header comment.
         *
         * Called before any content is set, so later per-state code (the "set
         * city" placeholder, the offline states) can still hide individual views
         * on top of these decisions without having to know about tiers.
         */
        private fun applyMetrics(context: Context, rv: RemoteViews, m: WidgetMetrics) {
            val pad = dpToPx(context, m.padDp)
            rv.setViewPadding(R.id.widget_content, pad, pad, pad, pad)

            sp(rv, R.id.widget_header_label, m.headerSp)
            sp(rv, R.id.widget_cursor, m.headerSp)
            sizeDp(rv, R.id.widget_online_dot, m.dotDp.toFloat())
            sizeDp(rv, R.id.widget_refresh_box, m.refreshDp.toFloat())

            sp(rv, R.id.widget_hero_temp, m.heroTempSp)
            sp(rv, R.id.widget_hero_unit, m.heroUnitSp)
            sp(rv, R.id.widget_now_line, m.nowLineSp)
            sp(rv, R.id.widget_sparkline, m.sparkSp)
            sp(rv, R.id.widget_pop_max, m.statSp)
            sp(rv, R.id.widget_aqi_line, m.statSp)
            sp(rv, R.id.widget_sync_line, m.statSp)

            for (i in 0 until 4) {
                sp(rv, DAY_LABEL_IDS[i], m.dayLabelSp)
                sp(rv, TEMP_IDS[i], m.dayTempSp)
                sp(rv, POP_IDS[i], m.dayPopSp)
            }

            sp(rv, R.id.widget_meta_line, m.metaSp)
            sp(rv, R.id.widget_footer_joke, m.footerSp)
            sp(rv, R.id.widget_status_banner, m.bannerSp)

            vis(rv, R.id.widget_now_line, m.showNowLine)
            vis(rv, R.id.widget_hero_stats, m.showStats)
            vis(rv, R.id.widget_sync_line, m.showSync)
            vis(rv, R.id.widget_meta_line, m.showMeta)
            vis(rv, R.id.widget_footer_joke, m.showFooter)
            for (i in 0 until 4) {
                vis(rv, DAY_LABEL_IDS[i], m.showDayLabel)
                vis(rv, POP_IDS[i], m.showPop)
                vis(rv, BAR_IDS[i], m.showBars)
            }
        }

        /** Builds the full RemoteViews for one widget instance. Performs a blocking network
         * call — must be invoked off the main thread (see [refreshWidget] / WeatherWorker). */
        fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
            // One layout for every size. What changes per instance is the size
            // ladder applied to it (WidgetMetrics) and which rows that ladder's
            // tier keeps — see applyMetrics(). The footprint it's derived from
            // comes from WidgetSize, which reads the host's options correctly;
            // reading them the obvious way is what previously made this widget
            // render its small layout at full size.
            val metrics = WidgetSize.resolve(context, appWidgetId).metrics()
            val rv = RemoteViews(context.packageName, R.layout.weather_widget)
            applyMetrics(context, rv, metrics)
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            // Tapping the body opens the app.
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = PendingIntent.getActivity(context, appWidgetId * 10 + 0, openAppIntent, pendingFlags)
            rv.setOnClickPendingIntent(R.id.widget_content, openAppPending)

            // "change city" button -> configure activity, passed the appWidgetId directly
            // (not through the host's ACTION_APPWIDGET_CONFIGURE flow, since the widget
            // already exists at this point).
            val configureIntent = Intent(context, WidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("widgetconfig://widget/$appWidgetId")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val configurePending = PendingIntent.getActivity(context, appWidgetId * 10 + 1, configureIntent, pendingFlags)
            rv.setOnClickPendingIntent(R.id.widget_city_btn, configurePending)

            // refresh button -> broadcast back to this provider
            val refreshIntent = Intent(context, WeatherWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("refresh://widget/$appWidgetId")
            }
            val refreshPending = PendingIntent.getBroadcast(context, appWidgetId * 10 + 2, refreshIntent, pendingFlags)
            rv.setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPending)

            // Per-widget-instance personalization (theme + background transparency) —
            // see WidgetConfigureActivity's theme/transparency pickers and
            // WidgetTheme.kt's doc comments for exactly which views these touch and
            // which stay fixed regardless of theme. Applied unconditionally, before
            // any of the early-return states below, so even the "set city" /
            // "offline, no cache" placeholder states respect the chosen look.
            val theme = WidgetPrefs.getTheme(context, appWidgetId)
            val transparency = WidgetPrefs.getTransparency(context, appWidgetId)
            rv.setInt(R.id.widget_root, "setBackgroundResource", transparency.drawableRes)

            fun applyThemeColors(primaryColorRes: Int, dimColorRes: Int) {
                rv.setTextColor(R.id.widget_header_label, context.getColor(dimColorRes))
                rv.setTextColor(R.id.widget_cursor, context.getColor(primaryColorRes))
                rv.setTextColor(R.id.widget_now_line, context.getColor(primaryColorRes))
                DAY_LABEL_IDS.forEach { rv.setTextColor(it, context.getColor(primaryColorRes)) }
            }

            // WidgetTheme.AUTO_HEALTH's real color depends on live weather data
            // fetched further below — seed with its "healthy" (green) fallback here
            // so early-return states (no city set / offline with no cache) still
            // render with a theme color instead of looking unstyled, then
            // recompute for real once weather data is available (see the
            // `theme == WidgetTheme.AUTO_HEALTH` block after the weather fetch).
            if (theme == WidgetTheme.AUTO_HEALTH) {
                applyThemeColors(WidgetTheme.PHOSPHOR_GREEN.primaryColorRes, WidgetTheme.PHOSPHOR_GREEN.dimColorRes)
            } else {
                applyThemeColors(theme.primaryColorRes, theme.dimColorRes)
            }

            val online = isOnline(context)
            rv.setImageViewResource(
                R.id.widget_online_dot,
                if (online) R.drawable.widget_dot_online else R.drawable.widget_dot_offline,
            )

            // Blinking cursor: reflect the currently-persisted on/off tick state
            // (BlinkAlarm flips it roughly once a minute — see handleBlinkTick /
            // BlinkAlarm.kt for why it's a discrete toggle, not a smooth blink).
            rv.setViewVisibility(R.id.widget_cursor, if (BlinkPrefs.isOn(context)) View.VISIBLE else View.GONE)

            val storedCity = WidgetPrefs.getCity(context, appWidgetId)
            if (storedCity == null) {
                rv.setTextViewText(R.id.widget_header_label, "┌─ set city ─┐")
                rv.setTextViewText(R.id.widget_footer_joke, "> tap [city] to configure")
                rv.setViewVisibility(R.id.widget_aqi_line, View.GONE)
                rv.setViewVisibility(R.id.widget_refresh_spinner, View.GONE)
                rv.setViewVisibility(R.id.widget_status_banner, View.GONE)
                return rv
            }

            // "Follow my location" (isLive) widgets re-acquire the device's current
            // location on every refresh instead of trusting a frozen snapshot — see
            // resolveEffectiveCity() for the (best-effort, background-safe) approach.
            val city = resolveEffectiveCity(context, appWidgetId, storedCity)

            val liveMarker = if (city.isLive) "◎ " else ""
            rv.setTextViewText(R.id.widget_header_label, "┌─ $liveMarker${city.name} ─┐")

            var isFresh = false
            var weather = if (debugForceOfflineCache) {
                WidgetPrefs.getCachedWeather(context, appWidgetId)
            } else {
                try {
                    val fresh = WeatherApi.fetchWeather(city.lat, city.lon)
                    isFresh = true
                    fresh
                } catch (e: Exception) {
                    WidgetPrefs.getCachedWeather(context, appWidgetId)
                }
            }

            if (weather == null) {
                rv.setTextViewText(R.id.widget_footer_joke, "> offline — no cached data yet")
                rv.setViewVisibility(R.id.widget_aqi_line, View.GONE)
                rv.setViewVisibility(R.id.widget_refresh_spinner, View.GONE)
                rv.setTextViewText(R.id.widget_status_banner, "offline · no cached data yet")
                rv.setTextColor(R.id.widget_status_banner, context.getColor(R.color.widget_offline))
                rv.setViewVisibility(R.id.widget_status_banner, View.VISIBLE)
                return rv
            }

            // Terminal 2.0 status banner + dimming: stale vs offline-cache vs fresh.
            val dimmed = !online || !isFresh
            when {
                !online -> {
                    rv.setTextViewText(R.id.widget_status_banner, "offline · serving cached snapshot")
                    rv.setTextColor(R.id.widget_status_banner, context.getColor(R.color.widget_offline))
                    rv.setViewVisibility(R.id.widget_status_banner, View.VISIBLE)
                }
                !isFresh -> {
                    rv.setTextViewText(R.id.widget_status_banner, "stale · retrying")
                    rv.setTextColor(R.id.widget_status_banner, context.getColor(R.color.widget_amber))
                    rv.setViewVisibility(R.id.widget_status_banner, View.VISIBLE)
                }
                else -> rv.setViewVisibility(R.id.widget_status_banner, View.GONE)
            }
            rv.setViewVisibility(R.id.widget_refresh_spinner, View.GONE)

            // Notifications are evaluated only against a genuinely fresh fetch (never a
            // stale offline-fallback cache) and are entirely best-effort — a notification
            // failure must never break the widget's own render.
            if (isFresh) {
                try {
                    WeatherNotifier.evaluate(context, appWidgetId, city, weather)
                } catch (e: Exception) {
                    // best-effort, never break the widget refresh
                }

                // Separate Air Quality API call (different host) — fully best-effort:
                // a failure here must never blank/break the main weather render. On
                // failure, fall back to whatever AQI value was last cached rather than
                // just dropping the line.
                val aqi = try {
                    WeatherApi.fetchAirQuality(city.lat, city.lon)
                } catch (e: Exception) {
                    null
                }
                weather = if (aqi != null) {
                    weather.copy(usAqi = aqi)
                } else {
                    val cachedAqi = WidgetPrefs.getCachedWeather(context, appWidgetId)?.usAqi ?: -1
                    if (cachedAqi >= 0) weather.copy(usAqi = cachedAqi) else weather
                }

                WidgetPrefs.setCachedWeather(context, appWidgetId, weather)
            }

            // Now that real weather data (including this refresh's best-effort AQI)
            // is available, resolve WidgetTheme.AUTO_HEALTH's actual color — this
            // overwrites the green fallback [applyThemeColors] call applied above,
            // same "last RemoteViews call before .apply() wins" pattern used
            // elsewhere in this function (e.g. widget_aqi_line's text/visibility).
            if (theme == WidgetTheme.AUTO_HEALTH) {
                val healthTheme = computeHealthTheme(weather)
                applyThemeColors(healthTheme.primaryColorRes, healthTheme.dimColorRes)
            }

            val iconPx = dpToPx(context, metrics.dayIconDp)
            // Use whatever frame the blink tick last persisted so a manual
            // refresh / periodic re-fetch stays visually in sync with it
            // rather than resetting the animation.
            val frame = BlinkPrefs.frame(context)

            // One shared temperature scale for all four range bars, so their
            // offsets are comparable to each other rather than each bar being
            // normalized to itself (which would make every day look identical).
            val days = weather.daily.take(4)
            val globalMin = days.mapNotNull { it.tempMin.takeIf { t -> !t.isNaN() } }.minOrNull() ?: 0.0
            val globalMax = days.mapNotNull { it.tempMax.takeIf { t -> !t.isNaN() } }.maxOrNull() ?: 0.0
            val barWidthPx = dpToPx(context, metrics.colWidthDp.toInt() - 8)
            val barHeightPx = dpToPx(context, metrics.barHeightDp)

            for (i in 0 until 4) {
                val entry = weather.daily.getOrNull(i) ?: continue
                val dayLabel = if (i == 0) "dziś" else dowAbbrev(entry.date)
                val kind = Wmo.wmoToKind(entry.weatherCode)
                rv.setTextViewText(DAY_LABEL_IDS[i], dayLabel)
                rv.setImageViewBitmap(ICON_IDS[i], WidgetGraphics.icon(context, kind, iconPx, frame))
                rv.setTextViewText(
                    TEMP_IDS[i],
                    if (metrics.showTempPair) tempSpannable(context, entry.tempMax, entry.tempMin)
                    else compactTempSpannable(context, entry.tempMax),
                )
                rv.setTextViewText(POP_IDS[i], "▽ ${entry.precipitationProbabilityMax}%")

                if (metrics.showBars) {
                    rv.setImageViewBitmap(
                        BAR_IDS[i],
                        WidgetGraphics.rangeBar(
                            context,
                            barWidthPx,
                            barHeightPx,
                            entry.tempMin,
                            entry.tempMax,
                            globalMin,
                            globalMax,
                        ),
                    )
                }
            }

            // widget_footer_comment stays permanently GONE/0dp in both layouts
            // (WidgetMock4x2.tsx never had it; id kept only for RemoteViews-
            // action compatibility). widget_meta_line is back, though — real
            // data (feels-like/humidity/wind) filling real reclaimed space in
            // the default (non-compact) layout only; the compact layout stays
            // deliberately minimal and never shows it.
            if (metrics.showMeta) {
                val meta = metaLine(weather)
                rv.setTextViewText(R.id.widget_meta_line, meta)
                vis(rv, R.id.widget_meta_line, meta.isNotEmpty())
            }

            // Terminal 2.0 hero: big current temp + condition + rain + AQI.
            // kind0/isNight computed here (rather than further down where the footer
            // joke also needs kind/isNight) so the pixel icon can reuse the same
            // kind/frame the joke and day-grid icons use.
            val kind0 = Wmo.wmoToKind(weather.currentWeatherCode)
            val isNight = !weather.isDay
            val nowIconKind = if (isNight && (kind0 == "sun" || kind0 == "partly")) "moon" else kind0
            val nowIconPx = dpToPx(context, metrics.heroIconDp)
            rv.setImageViewBitmap(R.id.widget_now_icon, WidgetGraphics.icon(context, nowIconKind, nowIconPx, frame))

            rv.setTextViewText(
                R.id.widget_hero_temp,
                if (weather.currentTemperature.isNaN()) "--°" else "${Math.round(weather.currentTemperature)}°",
            )
            rv.setTextColor(
                R.id.widget_hero_temp,
                context.getColor(if (dimmed) R.color.widget_cyan_dim else R.color.widget_cyan),
            )

            val nowParts = mutableListOf("now · ${kindLabel(nowIconKind)}")
            if (weather.currentPrecipitationProbability >= 0) nowParts += "rain ${weather.currentPrecipitationProbability}%"
            rv.setTextViewText(R.id.widget_now_line, nowParts.joinToString(" · "))

            // Terminal 2.0 sparkline + PoP-max: the 4-day PoP trend, relocated
            // out of the footer comment (see popSparkline()'s doc comment) into
            // its own hero-row view. Color (amber if any day's PoP >= 50%, else
            // cyan) matches WidgetMock4x2.tsx's spark.hasRain rule and is shared
            // by the sparkline bars and the "▽ max%" figure beside them.
            val next4Pops = weather.daily.take(4).map { it.precipitationProbabilityMax }
            val maxPop = next4Pops.maxOrNull() ?: 0
            val hasRain = next4Pops.any { it >= 50 }
            val sparkColor = context.getColor(if (hasRain) R.color.widget_amber else R.color.widget_cyan)
            // A forecast with no rain at all in it maps every bar to the
            // shortest block, so the "chart" becomes four ▁ glyphs in a row —
            // which reads as a stray horizontal rule floating above the AQI
            // line, not as a flat trend. Nothing to plot, so plot nothing;
            // "▽ 0%" beside it already says it isn't going to rain. (Spotted
            // on the real widget, where a dry forecast is the common case.)
            vis(rv, R.id.widget_sparkline, metrics.showStats && maxPop > 0)
            rv.setTextViewText(R.id.widget_sparkline, popSparkline(weather))
            rv.setTextColor(R.id.widget_sparkline, sparkColor)
            rv.setTextViewText(R.id.widget_pop_max, "▽ $maxPop%")
            rv.setTextColor(R.id.widget_pop_max, sparkColor)
            rv.setTextViewText(R.id.widget_sync_line, "sync ${currentTimeHms()}")

            // Compact width: the AQI text is the widest, least essential thing sharing
            // the "now" row — at minResizeWidth (180dp) it would otherwise squeeze the
            // actual now-temp/now-rain text down to nothing (0-width ellipsis). Hiding
            // it here is the same "best-effort, drop it before you break something
            // people actually rely on" idea as the AQI fetch's own error handling above.
            if (weather.usAqi >= 0 && metrics.showAqi) {
                val colorRes = aqiLabelAndColor(weather.usAqi).second
                val text = "AQI ${weather.usAqi} · ${aqiShortLabel(weather.usAqi)}"
                val span = SpannableString(text)
                span.setSpan(ForegroundColorSpan(context.getColor(colorRes)), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                rv.setTextViewText(R.id.widget_aqi_line, span)
                rv.setViewVisibility(R.id.widget_aqi_line, View.VISIBLE)
            } else {
                rv.setTextViewText(R.id.widget_aqi_line, "")
                rv.setViewVisibility(R.id.widget_aqi_line, View.GONE)
            }

            // kind0/isNight computed above (right before the "now" icon render).
            // Seed by current hour so the joke is stable within a refresh cycle but
            // varies across refreshes/hours, matching pickSigma()'s intent.
            val seed = (System.currentTimeMillis() / (60 * 60 * 1000L)).toInt()
            val joke = SigmaJokes.pick(kind0, isNight, seed)
            rv.setTextViewText(R.id.widget_footer_joke, "> $joke")

            return rv
        }
    }
}
