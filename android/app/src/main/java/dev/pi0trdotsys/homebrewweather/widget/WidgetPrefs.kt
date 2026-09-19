package dev.pi0trdotsys.homebrewweather.widget

import android.content.Context
import org.json.JSONObject

/**
 * A saved city for one particular widget instance.
 *
 * [isLive] marks "follow my location" (a.k.a. live/follow) mode: when true,
 * [lat]/[lon]/[name] are just the *last-resolved* snapshot (kept up to date
 * so the offline-fallback cache and header label have something sane to show
 * immediately/offline), but every refresh re-acquires the device's current
 * location and re-resolves against that instead of trusting this frozen
 * point — see [WeatherWidgetProvider]'s refresh path. Cities picked by name
 * search always have isLive = false (a plain fixed point, unchanged
 * behavior).
 */
data class WidgetCity(val lat: Double, val lon: Double, val name: String, val isLive: Boolean = false) {
    fun toJson(): String = JSONObject().apply {
        put("lat", lat)
        put("lon", lon)
        put("name", name)
        put("isLive", isLive)
    }.toString()

    companion object {
        fun fromJson(raw: String?): WidgetCity? {
            if (raw.isNullOrBlank()) return null
            return try {
                val o = JSONObject(raw)
                WidgetCity(
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    name = o.optString("name", "?"),
                    // Missing key = older entries written before "follow my
                    // location" mode existed -> default false (fixed point).
                    isLive = o.optBoolean("isLive", false),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Dedicated SharedPreferences file for per-widget-instance city selection,
 * keyed by "city_" + appWidgetId — separate from the main app's storage so
 * the widget keeps working independent of the WebView/app ever being opened.
 */
object WidgetPrefs {
    private const val PREFS_NAME = "widget_city_prefs"
    private fun cityKey(appWidgetId: Int) = "city_$appWidgetId"
    private fun weatherCacheKey(appWidgetId: Int) = "weather_cache_$appWidgetId"
    private fun themeKey(appWidgetId: Int) = "theme_$appWidgetId"
    private fun transparencyKey(appWidgetId: Int) = "transparency_$appWidgetId"
    private fun minWidthKey(appWidgetId: Int) = "min_width_$appWidgetId"
    private fun minHeightKey(appWidgetId: Int) = "min_height_$appWidgetId"

    // Match res/xml/weather_widget_info.xml's minWidth/minHeight — the sizes a
    // widget instance renders at until the host first reports real granted
    // options via onAppWidgetOptionsChanged (see WeatherWidgetProvider).
    const val DEFAULT_MIN_WIDTH_DP = 250
    const val DEFAULT_MIN_HEIGHT_DP = 165

    fun getCity(context: Context, appWidgetId: Int): WidgetCity? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetCity.fromJson(prefs.getString(cityKey(appWidgetId), null))
    }

    fun setCity(context: Context, appWidgetId: Int, city: WidgetCity) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(cityKey(appWidgetId), city.toJson()).apply()
    }

    fun removeCity(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(cityKey(appWidgetId))
            .remove(weatherCacheKey(appWidgetId))
            .remove(themeKey(appWidgetId))
            .remove(transparencyKey(appWidgetId))
            .remove(minWidthKey(appWidgetId))
            .remove(minHeightKey(appWidgetId))
            .apply()
    }

    /** Per-widget-instance color theme (see [WidgetTheme]). Defaults to
     * [WidgetTheme.DEFAULT] (Phosphor Green) for any widget that's never had
     * this explicitly set, so existing/never-configured widgets render
     * exactly as they did before this feature existed. */
    fun getTheme(context: Context, appWidgetId: Int): WidgetTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetTheme.fromStorageId(prefs.getInt(themeKey(appWidgetId), WidgetTheme.DEFAULT.storageId))
    }

    fun setTheme(context: Context, appWidgetId: Int, theme: WidgetTheme) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(themeKey(appWidgetId), theme.storageId).apply()
    }

    /** Per-widget-instance background fill transparency (see [WidgetTransparency]).
     * Defaults to [WidgetTransparency.DEFAULT] (Opaque) for the same
     * never-configured-widgets-look-unchanged reason as [getTheme]. */
    fun getTransparency(context: Context, appWidgetId: Int): WidgetTransparency {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetTransparency.fromStorageId(prefs.getInt(transparencyKey(appWidgetId), WidgetTransparency.DEFAULT.storageId))
    }

    fun setTransparency(context: Context, appWidgetId: Int, transparency: WidgetTransparency) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(transparencyKey(appWidgetId), transparency.storageId).apply()
    }

    /** Last-known min width/height (dp) the host has granted this widget instance,
     * as reported by AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH/HEIGHT via
     * WeatherWidgetProvider.onAppWidgetOptionsChanged() — lets buildRemoteViews()
     * render a layout that actually fits instead of always assuming the declared
     * default (250x180dp). Defaults to that declared default for any widget
     * instance the host hasn't reported real options for yet. */
    fun getLastKnownMinWidthDp(context: Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(minWidthKey(appWidgetId), DEFAULT_MIN_WIDTH_DP)
    }

    fun setLastKnownMinWidthDp(context: Context, appWidgetId: Int, dp: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(minWidthKey(appWidgetId), dp).apply()
    }

    fun getLastKnownMinHeightDp(context: Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(minHeightKey(appWidgetId), DEFAULT_MIN_HEIGHT_DP)
    }

    fun setLastKnownMinHeightDp(context: Context, appWidgetId: Int, dp: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(minHeightKey(appWidgetId), dp).apply()
    }

    /** Last-known-good weather, used as an offline fallback when a refresh fails. */
    fun getCachedWeather(context: Context, appWidgetId: Int): WeatherApi.WeatherData? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(weatherCacheKey(appWidgetId), null) ?: return null
        return try {
            val o = JSONObject(raw)
            val dailyArr = o.getJSONArray("daily")
            val daily = (0 until dailyArr.length()).map { i ->
                val d = dailyArr.getJSONObject(i)
                WeatherApi.DailyEntry(
                    date = d.getString("date"),
                    weatherCode = d.getInt("weatherCode"),
                    tempMax = d.getDouble("tempMax"),
                    tempMin = d.getDouble("tempMin"),
                    precipitationProbabilityMax = d.getInt("pop"),
                )
            }
            WeatherApi.WeatherData(
                isDay = o.optBoolean("isDay", true),
                currentWeatherCode = o.optInt("currentWeatherCode", 0),
                // optDouble default NaN: older cached entries (written before this field
                // existed) won't have it — NaN is treated as "unknown" by callers that
                // care (see WeatherNotifier), never as a real reading of 0°C.
                currentTemperature = o.optDouble("currentTemp", Double.NaN),
                // Same "unknown" convention for the newer feels-like/humidity/wind
                // fields — older cached blobs simply won't have these keys.
                apparentTemperature = o.optDouble("apparentTemp", Double.NaN),
                humidityPercent = o.optInt("humidity", -1),
                windSpeedKmh = o.optDouble("windSpeed", Double.NaN),
                currentPrecipitationProbability = o.optInt("currentPop", -1),
                maxNext6hPop = o.optInt("next6hPop", -1),
                usAqi = o.optInt("aqi", -1),
                daily = daily,
            )
        } catch (e: Exception) {
            null
        }
    }

    fun setCachedWeather(context: Context, appWidgetId: Int, weather: WeatherApi.WeatherData) {
        val dailyArr = org.json.JSONArray()
        weather.daily.forEach { entry ->
            dailyArr.put(JSONObject().apply {
                put("date", entry.date)
                put("weatherCode", entry.weatherCode)
                put("tempMax", entry.tempMax)
                put("tempMin", entry.tempMin)
                put("pop", entry.precipitationProbabilityMax)
            })
        }
        val o = JSONObject().apply {
            put("isDay", weather.isDay)
            put("currentWeatherCode", weather.currentWeatherCode)
            // org.json's put(String, Double) throws for NaN/Infinite, so only write
            // it when it's an actual reading — optDouble() on read already defaults
            // a missing key back to NaN ("unknown").
            if (!weather.currentTemperature.isNaN()) put("currentTemp", weather.currentTemperature)
            if (!weather.apparentTemperature.isNaN()) put("apparentTemp", weather.apparentTemperature)
            if (weather.humidityPercent >= 0) put("humidity", weather.humidityPercent)
            if (!weather.windSpeedKmh.isNaN()) put("windSpeed", weather.windSpeedKmh)
            if (weather.currentPrecipitationProbability >= 0) put("currentPop", weather.currentPrecipitationProbability)
            if (weather.maxNext6hPop >= 0) put("next6hPop", weather.maxNext6hPop)
            if (weather.usAqi >= 0) put("aqi", weather.usAqi)
            put("daily", dailyArr)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(weatherCacheKey(appWidgetId), o.toString()).apply()
    }
}

/**
 * Read-only access to the existing Capacitor Preferences SharedPreferences
 * file (group "CapacitorStorage", the @capacitor/preferences default),
 * shared with the main web app. Keys are stored as raw strings, no prefix.
 * Used only as a convenience default / for the refresh interval — the
 * widget's own city storage lives in [WidgetPrefs].
 */
object CapacitorStorage {
    private const val PREFS_NAME = "CapacitorStorage"
    private const val COORDS_KEY = "brew-wx:coords"
    private const val INTERVAL_KEY = "brew-wx:interval"

    // Notification settings — written by the web app's Settings page
    // (src/lib/settings.ts / src/routes/settings.tsx). Key names + string
    // encodings ("true"/"false", plain number strings) are a contract with
    // that file; don't rename here without updating it too.
    private const val NOTIF_RAIN_ENABLED_KEY = "settings:notif-rain-enabled"
    private const val NOTIF_HIGH_ENABLED_KEY = "settings:notif-high-enabled"
    private const val NOTIF_HIGH_THRESHOLD_KEY = "settings:notif-high-threshold"
    private const val NOTIF_LOW_ENABLED_KEY = "settings:notif-low-enabled"
    private const val NOTIF_LOW_THRESHOLD_KEY = "settings:notif-low-threshold"
    private const val NOTIF_SWING_ENABLED_KEY = "settings:notif-swing-enabled"
    private const val NOTIF_SWING_THRESHOLD_KEY = "settings:notif-swing-threshold"
    private const val NOTIF_AQI_ENABLED_KEY = "settings:notif-aqi-enabled"
    private const val NOTIF_AQI_THRESHOLD_KEY = "settings:notif-aqi-threshold"

    fun lastAppCity(context: Context): WidgetCity? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetCity.fromJson(prefs.getString(COORDS_KEY, null))
    }

    /** Minutes, clamped to WorkManager's real minimum (15), default 30. */
    fun refreshIntervalMinutes(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(INTERVAL_KEY, null)
        val allowed = setOf(15L, 30L, 60L, 180L, 360L)
        val parsed = raw?.toLongOrNull()
        val minutes = if (parsed != null && parsed in allowed) parsed else 30L
        return maxOf(minutes, 15L)
    }

    private fun getBool(context: Context, key: String, default: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(key, null) ?: return default
        return raw == "true"
    }

    private fun getDouble(context: Context, key: String, default: Double): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(key, null) ?: return default
        return raw.toDoubleOrNull() ?: default
    }

    fun rainEnabled(context: Context): Boolean = getBool(context, NOTIF_RAIN_ENABLED_KEY, true)
    fun highEnabled(context: Context): Boolean = getBool(context, NOTIF_HIGH_ENABLED_KEY, true)
    fun highThreshold(context: Context): Double = getDouble(context, NOTIF_HIGH_THRESHOLD_KEY, 30.0)
    fun lowEnabled(context: Context): Boolean = getBool(context, NOTIF_LOW_ENABLED_KEY, true)
    fun lowThreshold(context: Context): Double = getDouble(context, NOTIF_LOW_THRESHOLD_KEY, 0.0)
    fun swingEnabled(context: Context): Boolean = getBool(context, NOTIF_SWING_ENABLED_KEY, true)
    fun swingThreshold(context: Context): Double = getDouble(context, NOTIF_SWING_THRESHOLD_KEY, 8.0)
    fun aqiEnabled(context: Context): Boolean = getBool(context, NOTIF_AQI_ENABLED_KEY, true)
    fun aqiThreshold(context: Context): Double = getDouble(context, NOTIF_AQI_THRESHOLD_KEY, 100.0)
}

/**
 * Tiny dedicated SharedPreferences file holding just the widget cursor's
 * current on/off blink state, so every widget instance toggles in lock-step
 * on each AlarmManager tick (see [BlinkAlarm] / WeatherWidgetProvider). Also
 * holds the shared icon-animation frame counter (0..3), advanced on the same
 * ~60s tick — piggybacking on the existing alarm rather than adding a new one
 * (see PixelIcons.transformGrid for what each frame looks like per icon kind).
 */
object BlinkPrefs {
    private const val PREFS_NAME = "widget_blink_prefs"
    private const val KEY_CURSOR_ON = "cursor_on"
    private const val KEY_FRAME = "frame"
    private const val FRAME_COUNT = 4

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_CURSOR_ON, true)

    /** Flips the stored state and returns the new value. */
    fun toggle(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val next = !prefs.getBoolean(KEY_CURSOR_ON, true)
        prefs.edit().putBoolean(KEY_CURSOR_ON, next).apply()
        return next
    }

    /** Currently-persisted animation frame (0..3), read by a normal
     * buildRemoteViews() fetch so it stays in sync with whatever the last
     * blink tick set — no separate frame clock. */
    fun frame(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_FRAME, 0)

    /** Advances the stored frame counter (wrapping 0..3) and returns the new value. */
    fun advanceFrame(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val next = (prefs.getInt(KEY_FRAME, 0) + 1) % FRAME_COUNT
        prefs.edit().putInt(KEY_FRAME, next).apply()
        return next
    }
}
