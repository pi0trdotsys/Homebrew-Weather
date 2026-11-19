package dev.pi0trdotsys.homebrewweather.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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
            ACTION_NEXT_PLACE -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    // Switching places is the user looking at a place, not the
                    // forecast changing: render it, but don't fire alerts for it.
                    WidgetPrefs.cyclePlace(context, id)
                    refreshWidget(context, AppWidgetManager.getInstance(context), id, announce = false)
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
        const val ACTION_NEXT_PLACE = "dev.pi0trdotsys.homebrewweather.ACTION_NEXT_PLACE"

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

        // Sizes are no longer constants here. Every dp/sp the widget draws is
        // derived from the footprint the host granted this instance — see
        // WidgetMetrics (the ladder) and WidgetSize (reading the footprint
        // correctly), and applyMetrics() below for where they land.

        fun componentName(context: Context) = ComponentName(context, WeatherWidgetProvider::class.java)

        /** All currently-placed widget ids, e.g. for the periodic worker. */
        fun allWidgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(componentName(context))

        /** Kicks off a background fetch + RemoteViews push for a single widget instance. */
        fun refreshWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            announce: Boolean = true,
        ) {
            // Quick "refreshing…" feedback (cheap partial update, no network) before
            // the background fetch completes — matches the web mockup's refreshing state.
            try {
                val spinner = RemoteViews(context.packageName, R.layout.weather_widget)
                spinner.setViewVisibility(R.id.widget_refresh_spinner, View.VISIBLE)
                spinner.setViewVisibility(R.id.widget_status_banner, View.VISIBLE)
                spinner.setTextViewText(R.id.widget_status_banner, CapacitorStorage.lang(context).texts.refreshing)
                val cachedDay = WidgetPrefs.getCachedWeather(context, appWidgetId)?.isDay
                val pal = paletteFor(context, appWidgetId, cachedDay)
                spinner.setTextColor(R.id.widget_status_banner, pal.color(R.color.widget_cyan))
                spinner.setInt(R.id.widget_status_banner, "setBackgroundResource", bannerBackground(pal))
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, spinner)
            } catch (e: Exception) {
                // best-effort; the full render below still runs regardless
            }

            bgExecutor.execute {
                try {
                    val rv = buildRemoteViews(context, appWidgetId, announce)
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
                // every blink if this used a different ladder. The ladder now
                // depends on which rows the content shows, so rebuild that
                // content from the same cached forecast the last render wrote.
                // Read against the real clock, like the full render does.
                val weather = WidgetPrefs.getCachedWeather(context, id)?.rebasedTo(System.currentTimeMillis())
                val content = weather?.let {
                    WidgetContent.build(it, WidgetPrefs.getDensity(context, id), offline = false, texts = CapacitorStorage.lang(context).texts, pollen = CapacitorStorage.pollenEnabled(context))
                }
                val pal = paletteFor(context, id, weather?.isDay)
                val rows = content?.rows() ?: WidgetMetrics.Rows.ALL
                val m = WidgetSize.resolve(context, id).metrics(rows)
                val iconPx = dpToPx(context, m.dayIconDp)
                val nowIconPx = dpToPx(context, m.heroIconDp)
                // The meta line may carry a countdown ("zachód za 40 min"): keep it
                // honest between refreshes. Text only — if that line appears or
                // disappears the rows change, and that waits for the next full render.
                content?.metaReadouts?.takeIf { it.isNotEmpty() }?.let { r ->
                    metaBitmap(context, r, m, pal)?.let { rv.setImageViewBitmap(R.id.widget_meta_line, it) }
                }
                // The weather scene moves only while it rains, snows or storms; on any
                // other sky there is nothing to re-draw (see WidgetScene).
                if (weather != null && WidgetPrefs.getBackdrop(context, id) == WidgetBackdrop.SCENE) {
                    val kind = WidgetScene.kindOf(Wmo.wmoToKind(weather.currentWeatherCode), weather.isDay)
                    if (WidgetScene.animated(kind)) {
                        val name = WidgetPrefs.getCity(context, id)?.name ?: ""
                        sceneBitmap(context, weather, name, m.widthDp, m.heightDp, frame, pal)
                            ?.let {
                                rv.setImageViewBitmap(R.id.widget_backdrop, it)
                                rv.setViewVisibility(R.id.widget_backdrop, View.VISIBLE)
                            }
                    }
                }
                // ...and so can the hero's rain sentence ("deszcz za ok. 10 min").
                content?.heroLine?.let { rv.setTextViewText(R.id.widget_now_line, it) }
                // Re-render each day's icon at the new frame from cached weather codes
                // only — no network call, matching the "no new battery cost" tradeoff.
                if (weather != null) {
                    val offset = content?.dayOffset ?: 0
                    for (i in 0 until 4) {
                        val entry = weather.daily.getOrNull(offset + i) ?: continue
                        val kind = Wmo.wmoToKind(entry.weatherCode)
                        rv.setImageViewBitmap(ICON_IDS[i], WidgetGraphics.icon(context, kind, iconPx, frame, pal.paper))
                    }
                    // Keep the "now" line's icon animating in lockstep with the
                    // day-grid icons on every blink tick too (same kind-resolution
                    // rule as the full buildRemoteViews() path below).
                    val nowKind = Wmo.wmoToKind(weather.currentWeatherCode)
                    val nowIconKind = if (!weather.isDay && (nowKind == "sun" || nowKind == "partly")) "moon" else nowKind
                    rv.setImageViewBitmap(R.id.widget_now_icon, WidgetGraphics.icon(context, nowIconKind, nowIconPx, frame, pal.paper))
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
        private fun aqiShortLabel(aqi: Int, texts: Texts): String = texts.aqiShort(aqi)

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

        private fun dowAbbrev(isoDate: String, texts: Texts): String {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val date = sdf.parse(isoDate) ?: return "?"
                val cal = Calendar.getInstance()
                cal.time = date
                // Calendar.DAY_OF_WEEK: Sunday=1 .. Saturday=7; Texts.dow counts from Sunday = 0.
                texts.dow(cal.get(Calendar.DAY_OF_WEEK) - 1)
            } catch (e: Exception) {
                "?"
            }
        }


        /** 4-day POP trend as Unicode block chars (Terminal 2.0 sparkline),
         * rendered into [R.id.widget_sparkline] — mirrors
         * WidgetMock4x2.tsx's popToSparkline()/spark.bars: proportional to
         * `pop / max(1, maxPop)` (never sorted — stays in day order so it
         * reads as a trend), one char per day shown in the grid. */
        private fun popSparkline(pops: List<Int>): String {
            if (pops.isEmpty()) return ""
            val max = maxOf(1, pops.maxOrNull() ?: 0)
            val chars = charArrayOf('▁', '▂', '▃', '▄', '▅', '▆', '▇', '█')
            return pops.joinToString("") { p ->
                val idx = ((p.toFloat() / max) * (chars.size - 1)).roundToInt().coerceIn(0, chars.size - 1)
                chars[idx].toString()
            }
        }

        /** HH:mm at render time — [R.id.widget_sync_line]'s "sync ..." text (full
         * density only). Seconds were dropped: a widget that refreshes every
         * 15-30 minutes has no use for them, and they were the widest thing in
         * the stat column. */
        private fun currentTimeHm(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

        /** "24°/14°" — no spaces around the slash (matches WidgetMock4x2.tsx's
         * `{d.tempDay}°` / `/` / `{d.tempNight}°` spans exactly, and is what
         * actually fits the 13sp column of a true-4x2 40dp-tall grid row
         * without horizontal ellipsis-clipping). Day = amber, "/" = dim cyan,
         * night = cyan — mirrors the mockup's amber/hudCyanDim/hudCyan split
         * (previously night was widget_green here, a pre-existing deviation
         * from the token file fixed as part of this pass). */
        private fun tempSpannable(pal: Palette, dayMax: Double, nightMin: Double): SpannableString {
            val text = "${Math.round(dayMax)}°/${Math.round(nightMin)}°"
            val span = SpannableString(text)
            val amber = pal.color(R.color.widget_amber)
            val cyan = pal.color(R.color.widget_cyan)
            val cyanDim = pal.dim(R.color.widget_cyan_dim)
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
        private fun compactTempSpannable(pal: Palette, dayMax: Double): SpannableString {
            val text = "${Math.round(dayMax)}°"
            val span = SpannableString(text)
            span.setSpan(
                ForegroundColorSpan(pal.color(R.color.widget_amber)),
                0,
                text.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            return span
        }


        private val DAY_LABEL_IDS = intArrayOf(R.id.widget_day0_label, R.id.widget_day1_label, R.id.widget_day2_label, R.id.widget_day3_label)
        private val ICON_IDS = intArrayOf(R.id.widget_icon0, R.id.widget_icon1, R.id.widget_icon2, R.id.widget_icon3)
        private val TEMP_IDS = intArrayOf(R.id.widget_temp0, R.id.widget_temp1, R.id.widget_temp2, R.id.widget_temp3)
        private val POP_IDS = intArrayOf(R.id.widget_pop0, R.id.widget_pop1, R.id.widget_pop2, R.id.widget_pop3)
        private val DIVIDER_IDS = intArrayOf(R.id.widget_div0, R.id.widget_div1, R.id.widget_div2)
        private val COL_IDS = intArrayOf(R.id.widget_col0, R.id.widget_col1, R.id.widget_col2, R.id.widget_col3)

        /**
         * The palette this widget renders with right now: dark or paper per its
         * appearance setting (AUTO follows [isDay], dark while that is unknown),
         * and the high-contrast preference.
         */
        internal fun paletteFor(context: Context, appWidgetId: Int, isDay: Boolean?): Palette =
            Palette(
                context,
                WidgetPrefs.getAppearance(context, appWidgetId).isPaper(isDay),
                CapacitorStorage.highContrast(context),
            )

        /** The weather scene for [weather] at [frame], or null when the sky has none (see [WidgetScene]). */
        private fun sceneBitmap(
            context: Context,
            weather: WeatherApi.WeatherData,
            cityName: String,
            widthDp: Int,
            heightDp: Int,
            frame: Int,
            pal: Palette,
        ): Bitmap? {
            val kind = WidgetScene.kindOf(Wmo.wmoToKind(weather.currentWeatherCode), weather.isDay)
            return WidgetScene.render(
                context,
                dpToPx(context, widthDp),
                dpToPx(context, heightDp),
                kind,
                frame,
                cityName.lowercase().hashCode(),
                pal,
            )
        }

        /** The live scene's loop for [weather], or no frames when the sky has none. */
        private fun sceneFrames(
            context: Context,
            weather: WeatherApi.WeatherData,
            cityName: String,
            widthDp: Int,
            heightDp: Int,
            pal: Palette,
        ): List<Bitmap> {
            val kind = WidgetScene.kindOf(Wmo.wmoToKind(weather.currentWeatherCode), weather.isDay)
            return WidgetScene.renderLoop(
                context,
                dpToPx(context, widthDp),
                dpToPx(context, heightDp),
                kind,
                cityName.lowercase().hashCode(),
                pal,
            )
        }

        private fun bannerBackground(pal: Palette): Int =
            if (pal.paper) R.drawable.widget_status_banner_bg_paper else R.drawable.widget_status_banner_bg

        /** The meta line as a bitmap that fills the widget's inner width and the row the solver reserved. */
        private fun metaBitmap(context: Context, readouts: List<Readout>, m: WidgetMetrics, pal: Palette): Bitmap? {
            val density = context.resources.displayMetrics.density
            val innerWidthPx = Math.round((m.widthDp - 2f * m.padDp) * density)
            val heightPx = Math.round(m.metaRowDp * density)
            return MetaLineArt.render(readouts, innerWidthPx, heightPx) { pal.meta(it) }
                ?.let { WidgetGraphics.sized(context, it) }
        }

        /**
         * The big number's colour: cold to hot by [TempColor], the same scale as the
         * curve and the dashboard. A stale or offline forecast gets the same hue, dimmed.
         */
        private fun heroTempColor(pal: Palette, celsius: Double, dimmed: Boolean): Int {
            val base = pal.large(TempColor.argb(celsius))
            return if (dimmed) HighContrast.dim(base, pal.background) else base
        }

        /** A storm has to stand out from plain rain at a glance; a chance
         * (no likely window) stays quiet. */
        private fun popColor(pal: Palette, style: WidgetContent.PopStyle): Int = when (style) {
            WidgetContent.PopStyle.THUNDER -> pal.color(R.color.widget_amber)
            WidgetContent.PopStyle.SNOW -> pal.color(R.color.widget_snow)
            WidgetContent.PopStyle.RAIN -> pal.color(R.color.widget_cyan)
            WidgetContent.PopStyle.CHANCE, WidgetContent.PopStyle.NONE -> pal.dim(R.color.widget_cyan_dim)
        }

        /** 0 = Sunday .. 6 = Saturday, for [Texts.dowLong]; null if the date doesn't parse. */
        private fun dowIndex(isoDate: String): Int? = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance().apply { time = sdf.parse(isoDate)!! }
            cal.get(Calendar.DAY_OF_WEEK) - 1
        } catch (e: Exception) {
            null
        }

        /** Rows the "no city yet" / "offline, nothing cached" placeholder states
         * draw: the header and a one-line message in the footer slot. */
        private val PLACEHOLDER_ROWS = WidgetMetrics.Rows(
            meta = false,
            footer = true,
            pop = false,
            curve = false,
            stats = false,
        )

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
            sp(rv, R.id.widget_cfg_btn, m.headerSp)
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
            }

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
            }
        }

        /** Builds the full RemoteViews for one widget instance. Performs a blocking network
         * call — must be invoked off the main thread (see [refreshWidget] / WeatherWorker).
         *
         * [announce] false renders without sending notifications — used when the user has just
         * switched the widget to another place (see ACTION_NEXT_PLACE). */
        fun buildRemoteViews(context: Context, appWidgetId: Int, announce: Boolean = true): RemoteViews {
            // One layout for every size. What changes per instance is the size
            // ladder applied to it (WidgetMetrics) and which rows that ladder's
            // tier keeps — see applyMetrics(). The footprint it's derived from
            // comes from WidgetSize, which reads the host's options correctly;
            // reading them the obvious way is what previously made this widget
            // render its small layout at full size.
            //
            // Sized twice. This first pass covers the early-return states below
            // (no city yet, offline with nothing cached), which only ever show a
            // header and a one-line message. Once real weather is in hand, the
            // content decides which rows exist (WidgetContent) and the ladder is
            // re-solved for exactly those rows and applied on top — RemoteViews
            // replays its actions in order, so the later sizes and visibilities
            // are the ones that stick.
            val size = WidgetSize.resolve(context, appWidgetId)
            val texts = CapacitorStorage.lang(context).texts
            val rv = RemoteViews(context.packageName, R.layout.weather_widget)
            applyMetrics(context, rv, size.metrics(PLACEHOLDER_ROWS))
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
            // Only shown once the widget has several places (see below), when the
            // city name switches places and this is the way into the setup screen.
            rv.setViewVisibility(R.id.widget_cfg_btn, View.GONE)
            rv.setOnClickPendingIntent(R.id.widget_cfg_btn, configurePending)
            // What TalkBack says for the controls; the city one is refined below
            // once the city (and whether there are several places) is known.
            rv.setContentDescription(R.id.widget_refresh_btn, texts.a11yRefresh)
            rv.setContentDescription(R.id.widget_city_btn, texts.a11ySetup)
            rv.setContentDescription(R.id.widget_cfg_btn, texts.a11ySetup)

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

            // Light or dark: for AUTO, by whether it is day at the place — known from the
            // cached forecast until the real one is in hand (see below).
            var pal = paletteFor(context, appWidgetId, WidgetPrefs.getCachedWeather(context, appWidgetId)?.isDay)

            /** Everything the appearance changes that is not drawn per render: fills, hairlines, the
             * fixed-colour views of the layout, and the theme's own colours on top. */
            fun applyLook(primaryColorRes: Int, dimColorRes: Int) {
                rv.setInt(
                    R.id.widget_root,
                    "setBackgroundResource",
                    if (pal.paper) transparency.paperDrawableRes else transparency.drawableRes,
                )
                rv.setInt(R.id.widget_rule, "setBackgroundColor", if (pal.paper) 0x552B3B2B else context.getColor(R.color.widget_hud_line))
                DIVIDER_IDS.forEach {
                    if (pal.paper) rv.setInt(it, "setBackgroundColor", 0x332B3B2B)
                    else rv.setInt(it, "setBackgroundResource", R.drawable.widget_grid_divider)
                }
                rv.setInt(
                    R.id.widget_col0,
                    "setBackgroundResource",
                    if (pal.paper) R.drawable.widget_today_col_paper else R.drawable.widget_today_col,
                )
                rv.setInt(R.id.widget_status_banner, "setBackgroundResource", bannerBackground(pal))
                // The refresh glyph is white in the file; ink on paper.
                rv.setInt(R.id.widget_refresh_btn, "setColorFilter", if (pal.paper) pal.adapt(Color.WHITE) else Color.WHITE)
                rv.setInt(R.id.widget_online_dot, "setColorFilter", pal.color(R.color.widget_online))

                rv.setTextColor(R.id.widget_header_label, pal.dim(dimColorRes))
                rv.setTextColor(R.id.widget_cfg_btn, pal.dim(dimColorRes))
                rv.setTextColor(R.id.widget_hero_unit, pal.dim(R.color.widget_cyan_dim))
                rv.setTextColor(R.id.widget_sync_line, pal.dim(R.color.widget_green_dim))
                rv.setTextColor(R.id.widget_footer_joke, pal.color(R.color.widget_amber))
                rv.setTextColor(R.id.widget_cursor, pal.color(primaryColorRes))
                rv.setTextColor(R.id.widget_now_line, pal.color(primaryColorRes))
                DAY_LABEL_IDS.forEach { rv.setTextColor(it, pal.color(primaryColorRes)) }
            }

            // WidgetTheme.AUTO_HEALTH's real color depends on live weather data
            // fetched further below — seed with its "healthy" (green) fallback here
            // so early-return states (no city set / offline with no cache) still
            // render with a theme color instead of looking unstyled, then
            // recompute for real once weather data is available (see the
            // `theme == WidgetTheme.AUTO_HEALTH` block after the weather fetch).
            if (theme == WidgetTheme.AUTO_HEALTH) {
                applyLook(WidgetTheme.PHOSPHOR_GREEN.primaryColorRes, WidgetTheme.PHOSPHOR_GREEN.dimColorRes)
            } else {
                applyLook(theme.primaryColorRes, theme.dimColorRes)
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

            // A widget with no city yet takes the one the app has open. Some
            // launchers never show the setup screen for a widget pinned from the app
            // ("add widget" in ./settings), and the alternative is a widget stuck on
            // "set city" until someone taps it. The setup screen, when it does
            // come, simply replaces this.
            val storedCity = WidgetPrefs.getCity(context, appWidgetId)
                ?: CapacitorStorage.lastAppCity(context)?.also { WidgetPrefs.setCity(context, appWidgetId, it) }
            if (storedCity == null) {
                rv.setTextViewText(R.id.widget_header_label, texts.setCityHeader)
                rv.setTextViewText(R.id.widget_footer_joke, texts.tapToConfigure)
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
            // With several places the name is the "next place" button, and the
            // header says which of how many this is. A single place keeps its
            // old behaviour: the name opens the setup screen.
            val placeState = WidgetPrefs.getPlaceState(context, appWidgetId)
            val badge = Places.badge(placeState)
            rv.setTextViewText(
                R.id.widget_header_label,
                "┌─ $liveMarker${city.name}${badge?.let { " · $it" }.orEmpty()} ─┐",
            )
            rv.setContentDescription(
                R.id.widget_city_btn,
                if (placeState.canCycle) texts.a11yNextPlace(city.name, placeState.index + 1, placeState.size)
                else texts.a11yCityButton(city.name),
            )
            if (placeState.canCycle) {
                val nextIntent = Intent(context, WeatherWidgetProvider::class.java).apply {
                    action = ACTION_NEXT_PLACE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("nextplace://widget/$appWidgetId")
                }
                rv.setOnClickPendingIntent(
                    R.id.widget_city_btn,
                    PendingIntent.getBroadcast(context, appWidgetId * 10 + 7, nextIntent, pendingFlags),
                )
                rv.setViewVisibility(R.id.widget_cfg_btn, View.VISIBLE)
            }

            // Tapping the widget opens the app *on this widget's city*. It used to
            // open the app on whatever location the app had saved for itself —
            // tap a widget showing Tolox and land on Málaga, with nothing linking
            // the two. Same request code as the city-less intent set above, so
            // FLAG_UPDATE_CURRENT replaces that one's extras with these.
            val openCityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_CITY_LAT, city.lat)
                putExtra(MainActivity.EXTRA_CITY_LON, city.lon)
                putExtra(MainActivity.EXTRA_CITY_NAME, city.name)
                putExtra(MainActivity.EXTRA_CITY_LIVE, city.isLive)
            }
            rv.setOnClickPendingIntent(
                R.id.widget_content,
                PendingIntent.getActivity(context, appWidgetId * 10 + 0, openCityIntent, pendingFlags),
            )

            var isFresh = false
            var weather = if (debugForceOfflineCache) {
                WidgetPrefs.getCachedWeather(context, appWidgetId, city)
            } else {
                val cached = WidgetPrefs.getCachedWeather(context, appWidgetId, city)
                try {
                    // The forecast, then air quality, pollen and official warnings, all
                    // before anything evaluates notifications (see WeatherRepository).
                    val fresh = WeatherRepository.fetch(context, city, cached)
                    isFresh = true
                    fresh
                } catch (e: Exception) {
                    cached
                }
            }

            if (weather == null) {
                rv.setTextViewText(R.id.widget_footer_joke, texts.offlineNoCacheFooter)
                rv.setViewVisibility(R.id.widget_aqi_line, View.GONE)
                rv.setViewVisibility(R.id.widget_refresh_spinner, View.GONE)
                rv.setTextViewText(R.id.widget_status_banner, texts.bannerOfflineNoCache)
                rv.setTextColor(R.id.widget_status_banner, pal.color(R.color.widget_offline))
                rv.setViewVisibility(R.id.widget_status_banner, View.VISIBLE)
                return rv
            }

            // Terminal 2.0 status banner + dimming: stale vs offline-cache vs fresh.
            val dimmed = !online || !isFresh
            when {
                !online -> {
                    rv.setTextViewText(R.id.widget_status_banner, texts.bannerOfflineCached)
                    rv.setTextColor(R.id.widget_status_banner, pal.color(R.color.widget_offline))
                    rv.setViewVisibility(R.id.widget_status_banner, View.VISIBLE)
                }
                !isFresh -> {
                    rv.setTextViewText(R.id.widget_status_banner, texts.bannerStale)
                    rv.setTextColor(R.id.widget_status_banner, pal.color(R.color.widget_amber))
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
                    if (announce) {
                        WeatherNotifier.evaluate(context, appWidgetId, city, weather)
                    } else {
                        WeatherNotifier.primeState(context, appWidgetId, city, weather)
                    }
                } catch (e: Exception) {
                    // best-effort, never break the widget refresh
                }

                WidgetPrefs.setCachedWeather(context, appWidgetId, weather, city)
            }

            // Everything below reads "now" and "today" off the forecast, so a
            // cached one (offline, or a failed refresh) is first moved to the
            // real current hour — see WeatherData.rebasedTo. A fresh fetch
            // comes back unchanged. The cache above keeps the raw fetch.
            weather = weather.rebasedTo(System.currentTimeMillis())

            // The real day/night is known now: an AUTO widget may flip here.
            pal = paletteFor(context, appWidgetId, weather.isDay)
            val lookTheme = if (theme == WidgetTheme.AUTO_HEALTH) computeHealthTheme(weather) else theme
            applyLook(lookTheme.primaryColorRes, lookTheme.dimColorRes)

            // Decide what to say first, then size the widget for exactly that —
            // see WidgetContent for the "show the exceptions" rules and
            // WidgetMetrics.Rows for why sizing has to follow content now.
            val density = WidgetPrefs.getDensity(context, appWidgetId)
            val content = WidgetContent.build(weather, density, offline = !online, texts = texts, pollen = CapacitorStorage.pollenEnabled(context))
            val metrics = size.metrics(content.rows())
            applyMetrics(context, rv, metrics)

            vis(rv, R.id.widget_online_dot, content.showOnlineDot)
            vis(rv, R.id.widget_sync_line, metrics.showSync && content.showSync)
            vis(rv, R.id.widget_sparkline, metrics.showStats && content.showSparkline)
            vis(rv, R.id.widget_pop_max, metrics.showStats && content.showPopMax)

            val iconPx = dpToPx(context, metrics.dayIconDp)
            // Use whatever frame the blink tick last persisted so a manual
            // refresh / periodic re-fetch stays visually in sync with it
            // rather than resetting the animation.
            val frame = BlinkPrefs.frame(context)

            // The grid's four days: from today, or from tomorrow in the evening.
            val days = weather.daily.drop(content.dayOffset).take(4)

            // The rain row's bitmaps: the row's exact height, and the column's
            // width less a couple of dp so a full-width glyph run never touches
            // the dividers.
            val popHeightPx = Math.round(metrics.popRowDp * context.resources.displayMetrics.density)
            val popWidthPx = dpToPx(context, (metrics.colWidthDp - 4f).toInt().coerceAtLeast(8))
            // One scale for all four columns (see PixelFont.fitRow).
            val (popRow, popScale) = PixelFont.fitRow(content.dayPop, popWidthPx, popHeightPx)

            for (i in 0 until 4) {
                val entry = days.getOrNull(i) ?: continue
                val dayLabel = if (i == 0) content.firstDayLabel else dowAbbrev(entry.date, texts)
                val kind = Wmo.wmoToKind(entry.weatherCode)
                rv.setTextViewText(DAY_LABEL_IDS[i], dayLabel)
                rv.setImageViewBitmap(ICON_IDS[i], WidgetGraphics.icon(context, kind, iconPx, frame, pal.paper))
                rv.setTextViewText(
                    TEMP_IDS[i],
                    if (metrics.showTempPair) tempSpannable(pal, entry.tempMax, entry.tempMin)
                    else compactTempSpannable(pal, entry.tempMax),
                )
                // Blank rather than hidden for a day below the threshold: the
                // row exists because *some* day merits a figure, and the four
                // columns have to stay aligned with each other — PixelFont
                // renders a blank as a spacer of the row's full height.
                rv.setImageViewBitmap(
                    POP_IDS[i],
                    WidgetGraphics.sized(
                        context,
                        PixelFont.render(
                            popRow[i],
                            popColor(pal, content.dayPopStyle[i]),
                            scale = popScale,
                            heightPx = popHeightPx,
                        ),
                    ),
                )

                // Tapping a day opens the app on that day's hours (see
                // MainActivity.EXTRA_DAY and the day panel in the web app).
                val dayIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_CITY_LAT, city.lat)
                    putExtra(MainActivity.EXTRA_CITY_LON, city.lon)
                    putExtra(MainActivity.EXTRA_CITY_NAME, city.name)
                    putExtra(MainActivity.EXTRA_CITY_LIVE, city.isLive)
                    putExtra(MainActivity.EXTRA_DAY, entry.date)
                }
                val spokenDay = if (i == 0) content.firstDayLabel
                    else dowIndex(entry.date)?.let { texts.dowLong(it) } ?: dayLabel
                rv.setContentDescription(
                    COL_IDS[i],
                    texts.a11yDay(
                        spokenDay,
                        Math.round(entry.tempMax).toInt(),
                        Math.round(entry.tempMin).toInt(),
                        content.dayPop[i].removePrefix("▽ ").takeIf { it.isNotBlank() },
                    ),
                )
                rv.setOnClickPendingIntent(
                    COL_IDS[i],
                    PendingIntent.getActivity(context, appWidgetId * 10 + 3 + i, dayIntent, pendingFlags),
                )

            }

            // widget_footer_comment stays permanently GONE/0dp (WidgetMock4x2.tsx
            // never had it; id kept only for RemoteViews-action compatibility).
            // The meta line is a picture (icons and values in the pixel font), so a
            // widget too narrow for even one readout simply has none.
            if (content.metaReadouts.isNotEmpty()) {
                val meta = metaBitmap(context, content.metaReadouts, metrics, pal)
                if (meta != null) rv.setImageViewBitmap(R.id.widget_meta_line, meta)
                else rv.setViewVisibility(R.id.widget_meta_line, View.GONE)
            }

            // Hero: icon, big current temperature, and one line under it — the
            // rain sentence when there's rain coming, the condition otherwise.
            val kind0 = Wmo.wmoToKind(weather.currentWeatherCode)
            val isNight = !weather.isDay
            val nowIconKind = if (isNight && (kind0 == "sun" || kind0 == "partly")) "moon" else kind0
            val nowIconPx = dpToPx(context, metrics.heroIconDp)
            rv.setImageViewBitmap(R.id.widget_now_icon, WidgetGraphics.icon(context, nowIconKind, nowIconPx, frame, pal.paper))

            rv.setTextViewText(
                R.id.widget_hero_temp,
                if (weather.currentTemperature.isNaN()) "--°" else "${Math.round(weather.currentTemperature)}°",
            )
            rv.setTextColor(
                R.id.widget_hero_temp,
                heroTempColor(pal, weather.currentTemperature, dimmed),
            )
            rv.setTextViewText(R.id.widget_now_line, content.heroLine)

            // Full density: the next 24 hours as a temperature curve with the rain
            // against it (replaced the per-day range bars).
            val curveBitmap = if (metrics.showCurve && content.showCurve) {
                val innerWidthPx = dpToPx(context, (metrics.widthDp - 2 * metrics.padDp))
                WidgetGraphics.tempCurve(context, innerWidthPx, dpToPx(context, metrics.curveHeightDp), weather.hourly, weather.daily, pal)
            } else {
                null
            }
            if (curveBitmap != null) {
                rv.setImageViewBitmap(R.id.widget_curve, curveBitmap)
                rv.setViewVisibility(R.id.widget_curve, View.VISIBLE)
                val temps = weather.hourly.take(WidgetGraphics.CURVE_HOURS).map { it.temperature }.filter { !it.isNaN() }
                rv.setContentDescription(
                    R.id.widget_curve,
                    texts.a11yCurve(Math.round(temps.min()).toInt(), Math.round(temps.max()).toInt()),
                )
            } else {
                rv.setViewVisibility(R.id.widget_curve, View.GONE)
            }
            rv.setContentDescription(R.id.widget_now_icon, texts.kindLabel(nowIconKind))
            rv.setContentDescription(
                R.id.widget_content,
                texts.a11ySummary(
                    city.name,
                    weather.currentTemperature.takeIf { !it.isNaN() }?.let { Math.round(it).toInt() },
                    content.heroLine,
                    listOfNotNull(content.metaLine, content.aqi?.let { "AQI $it · ${aqiShortLabel(it, texts)}" })
                        .joinToString(", ").takeIf { it.isNotBlank() },
                ),
            )

            // Full density only: the 4-day rain sparkline and its maximum. Color
            // (amber if any day's PoP >= 50%, else cyan) matches WidgetMock4x2.tsx's
            // spark.hasRain rule and is shared by the bars and the "▽ max%" figure.
            val next4Pops = days.map { it.precipitationProbabilityMax }
            val maxPop = next4Pops.maxOrNull() ?: 0
            val hasRain = next4Pops.any { it >= 50 }
            val sparkColor = pal.color(if (hasRain) R.color.widget_amber else R.color.widget_cyan)
            rv.setTextViewText(R.id.widget_sparkline, popSparkline(next4Pops))
            rv.setTextColor(R.id.widget_sparkline, sparkColor)
            rv.setTextViewText(R.id.widget_pop_max, "▽ $maxPop%")
            rv.setTextColor(R.id.widget_pop_max, sparkColor)
            rv.setTextViewText(R.id.widget_sync_line, "sync ${currentTimeHm()}")

            val aqi = content.aqi
            if (aqi != null && metrics.showAqi) {
                val colorRes = aqiLabelAndColor(aqi).second
                val text = "AQI $aqi · ${aqiShortLabel(aqi, texts)}"
                val span = SpannableString(text)
                span.setSpan(ForegroundColorSpan(pal.adapt(context.getColor(colorRes))), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                rv.setTextViewText(R.id.widget_aqi_line, span)
                rv.setViewVisibility(R.id.widget_aqi_line, View.VISIBLE)
            } else {
                rv.setTextViewText(R.id.widget_aqi_line, "")
                rv.setViewVisibility(R.id.widget_aqi_line, View.GONE)
            }

            // The "city map" background, if this widget has it on: terrain contours
            // where the grid for this place is known and hilly, a skyline otherwise —
            // and while the grid is still being fetched (off this render path), which
            // then triggers a redraw. Drawn in the theme's own colour.
            if (WidgetPrefs.getBackdrop(context, appWidgetId) == WidgetBackdrop.CITY) {
                val grid = Terrain.cached(context, city.lat, city.lon)
                if (grid == null) {
                    Terrain.request(context, city.lat, city.lon) {
                        refreshWidget(context, AppWidgetManager.getInstance(context), appWidgetId, announce = false)
                    }
                }
                val primaryRes = if (theme == WidgetTheme.AUTO_HEALTH) {
                    computeHealthTheme(weather).primaryColorRes
                } else {
                    theme.primaryColorRes
                }
                rv.setImageViewBitmap(
                    R.id.widget_backdrop,
                    WidgetBackdropArt.render(
                        context,
                        dpToPx(context, metrics.widthDp),
                        dpToPx(context, metrics.heightDp),
                        pal.color(primaryRes),
                        city.name,
                        grid,
                        quiet = CapacitorStorage.highContrast(context),
                    ),
                )
                rv.setViewVisibility(R.id.widget_backdrop, View.VISIBLE)
            } else if (WidgetPrefs.getBackdrop(context, appWidgetId) == WidgetBackdrop.LIVE) {
                // The live scene: frames the launcher flips through by itself (see WidgetScene.loop).
                rv.setViewVisibility(R.id.widget_backdrop, View.GONE)
                val frames = sceneFrames(context, weather, city.name, metrics.widthDp, metrics.heightDp, pal)
                if (frames.isNotEmpty()) {
                    frames.forEach { bmp ->
                        val frameView = RemoteViews(context.packageName, R.layout.widget_scene_frame)
                        frameView.setImageViewBitmap(R.id.widget_scene_frame_img, bmp)
                        rv.addView(R.id.widget_scene_flipper, frameView)
                    }
                    rv.setViewVisibility(R.id.widget_scene_flipper, View.VISIBLE)
                }
            } else if (WidgetPrefs.getBackdrop(context, appWidgetId) == WidgetBackdrop.SCENE) {
                // The weather scene: rain, snow, a storm, or stars. Nothing for a calm day.
                val scene = sceneBitmap(context, weather, city.name, metrics.widthDp, metrics.heightDp, frame, pal)
                if (scene != null) {
                    rv.setImageViewBitmap(R.id.widget_backdrop, scene)
                    rv.setViewVisibility(R.id.widget_backdrop, View.VISIBLE)
                } else {
                    rv.setViewVisibility(R.id.widget_backdrop, View.GONE)
                }
            } else {
                rv.setViewVisibility(R.id.widget_backdrop, View.GONE)
            }

            // Seed by current hour so the joke is stable within a refresh cycle but
            // varies across refreshes/hours, matching pickSigma()'s intent.
            val seed = (System.currentTimeMillis() / (60 * 60 * 1000L)).toInt()
            // Footer voice follows the app-wide tone (see Tone) and language.
            val footer = FooterContent.line(
                context,
                CapacitorStorage.footerSource(context),
                CapacitorStorage.tone(context),
                texts.lang,
                weather,
                kind0,
                isNight,
                seed,
            )
            rv.setTextViewText(R.id.widget_footer_joke, "> $footer")

            return rv
        }
    }
}
