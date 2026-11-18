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
                    // The web app writes the same flag as "live".
                    isLive = o.optBoolean("isLive", o.optBoolean("live", false)),
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
    private fun placesKey(appWidgetId: Int) = "places_$appWidgetId"
    private fun placeIndexKey(appWidgetId: Int) = "place_index_$appWidgetId"

    // One cached forecast per *place*, not per widget: a widget that cycles
    // between places must never fall back to another place's forecast when
    // offline. The prefix (with its trailing underscore) is what removeCity
    // sweeps, so widget 1's cleanup can't touch widget 10's entries.
    private fun weatherCachePrefix(appWidgetId: Int) = "weather_cache_${appWidgetId}_"
    private fun weatherCacheKey(appWidgetId: Int, city: WidgetCity) =
        weatherCachePrefix(appWidgetId) + Places.cacheKey(city)
    private fun themeKey(appWidgetId: Int) = "theme_$appWidgetId"
    private fun transparencyKey(appWidgetId: Int) = "transparency_$appWidgetId"
    private fun densityKey(appWidgetId: Int) = "density_$appWidgetId"
    private fun backdropKey(appWidgetId: Int) = "backdrop_$appWidgetId"
    private fun appearanceKey(appWidgetId: Int) = "appearance_$appWidgetId"
    private fun minWidthKey(appWidgetId: Int) = "min_width_$appWidgetId"
    private fun minHeightKey(appWidgetId: Int) = "min_height_$appWidgetId"

    // Match res/xml/weather_widget_info.xml's minWidth/minHeight — the sizes a
    // widget instance renders at until the host first reports real granted
    // options via onAppWidgetOptionsChanged (see WeatherWidgetProvider).
    const val DEFAULT_MIN_WIDTH_DP = 250
    const val DEFAULT_MIN_HEIGHT_DP = 165

    /** The places this widget cycles through, and which one it shows. A widget
     * from before places existed has only the single `city_` entry, which reads
     * as a one-place list. */
    fun getPlaceState(context: Context, appWidgetId: Int): Places.State {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(placesKey(appWidgetId), null)
        if (raw != null) {
            try {
                val arr = org.json.JSONArray(raw)
                val list = (0 until arr.length()).mapNotNull { WidgetCity.fromJson(arr.optString(it)) }
                if (list.isNotEmpty()) {
                    val idx = prefs.getInt(placeIndexKey(appWidgetId), 0).coerceIn(0, list.lastIndex)
                    return Places.State(list, idx)
                }
            } catch (e: Exception) {
                // fall through to the legacy single city
            }
        }
        val legacy = WidgetCity.fromJson(prefs.getString(cityKey(appWidgetId), null))
        return if (legacy != null) Places.State(listOf(legacy), 0) else Places.State.EMPTY
    }

    fun setPlaceState(context: Context, appWidgetId: Int, state: Places.State) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        if (state.list.isEmpty()) {
            edit.remove(placesKey(appWidgetId)).remove(placeIndexKey(appWidgetId)).remove(cityKey(appWidgetId))
        } else {
            val arr = org.json.JSONArray()
            state.list.forEach { arr.put(it.toJson()) }
            edit.putString(placesKey(appWidgetId), arr.toString())
                .putInt(placeIndexKey(appWidgetId), state.index)
                // Kept in step so anything that still reads the single city sees the current one.
                .putString(cityKey(appWidgetId), state.current?.toJson())
        }
        edit.apply()
    }

    /** The place this widget shows right now. */
    fun getCity(context: Context, appWidgetId: Int): WidgetCity? =
        getPlaceState(context, appWidgetId).current

    /** Replaces the place being shown — "change city", and a live place
     * keeping its coordinates and name current between refreshes. */
    fun setCity(context: Context, appWidgetId: Int, city: WidgetCity) {
        setPlaceState(context, appWidgetId, Places.replaceCurrent(getPlaceState(context, appWidgetId), city))
    }

    /** Adds [city] as another place and shows it. */
    fun addPlace(context: Context, appWidgetId: Int, city: WidgetCity) {
        setPlaceState(context, appWidgetId, Places.add(getPlaceState(context, appWidgetId), city))
    }

    fun removePlace(context: Context, appWidgetId: Int, position: Int) {
        setPlaceState(context, appWidgetId, Places.remove(getPlaceState(context, appWidgetId), position))
    }

    /** Moves to the next place and returns it, or null with fewer than two. */
    fun cyclePlace(context: Context, appWidgetId: Int): WidgetCity? {
        val state = getPlaceState(context, appWidgetId)
        if (!state.canCycle) return null
        val next = Places.next(state)
        setPlaceState(context, appWidgetId, next)
        return next.current
    }

    fun removeCity(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefix = weatherCachePrefix(appWidgetId)
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { edit.remove(it) }
        edit.remove(cityKey(appWidgetId))
            .remove(placesKey(appWidgetId))
            .remove(placeIndexKey(appWidgetId))
            .remove("weather_cache_$appWidgetId")
            .remove(themeKey(appWidgetId))
            .remove(transparencyKey(appWidgetId))
            .remove(densityKey(appWidgetId))
            .remove(backdropKey(appWidgetId))
            .remove(appearanceKey(appWidgetId))
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

    /** Per-widget-instance content density (see [WidgetDensity]). Defaults to
     * [WidgetDensity.STANDARD] — the exception-based view — for widgets placed
     * before this setting existed as well as new ones. */
    fun getDensity(context: Context, appWidgetId: Int): WidgetDensity {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetDensity.fromStorageId(prefs.getInt(densityKey(appWidgetId), WidgetDensity.DEFAULT.storageId))
    }

    fun setDensity(context: Context, appWidgetId: Int, density: WidgetDensity) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(densityKey(appWidgetId), density.storageId).apply()
    }

    /** Per-widget-instance light/dark look (see [WidgetAppearance]); dark unless chosen. */
    fun getAppearance(context: Context, appWidgetId: Int): WidgetAppearance {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetAppearance.fromStorageId(prefs.getInt(appearanceKey(appWidgetId), WidgetAppearance.DEFAULT.storageId))
    }

    fun setAppearance(context: Context, appWidgetId: Int, appearance: WidgetAppearance) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(appearanceKey(appWidgetId), appearance.storageId).apply()
    }

    /** Per-widget-instance background (see [WidgetBackdrop]); off unless chosen, so a widget looks as it always did. */
    fun getBackdrop(context: Context, appWidgetId: Int): WidgetBackdrop {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetBackdrop.fromStorageId(prefs.getInt(backdropKey(appWidgetId), WidgetBackdrop.DEFAULT.storageId))
    }

    fun setBackdrop(context: Context, appWidgetId: Int, backdrop: WidgetBackdrop) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(backdropKey(appWidgetId), backdrop.storageId).apply()
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
    fun getCachedWeather(
        context: Context,
        appWidgetId: Int,
        place: WidgetCity? = getCity(context, appWidgetId),
    ): WeatherApi.WeatherData? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val city = place ?: return null
        val raw = prefs.getString(weatherCacheKey(appWidgetId, city), null) ?: return null
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
                    precipitationSumMm = d.optDouble("mm", Double.NaN),
                    uvIndexMax = d.optDouble("uv", Double.NaN),
                    windGustMaxKmh = d.optDouble("gust", Double.NaN),
                    sunrise = d.optString("sr", "").takeIf { it.isNotBlank() },
                    sunset = d.optString("ss", "").takeIf { it.isNotBlank() },
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
                // Absent in caches written before the rain-window feature;
                // an empty list just means "no rain sentence this render".
                hourly = o.optJSONArray("hourly")?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val h = arr.getJSONObject(i)
                        WeatherApi.HourlyEntry(
                            time = h.getString("t"),
                            precipitationProbability = h.optInt("p", 0),
                            weatherCode = h.optInt("c", 0),
                            temperature = h.optDouble("T", Double.NaN),
                            isDay = if (h.has("d")) h.optInt("d") == 1 else null,
                            precipitationMm = h.optDouble("m", Double.NaN),
                            windGustKmh = h.optDouble("g", Double.NaN),
                        )
                    }
                } ?: emptyList(),
                utcOffsetSeconds = if (o.has("utcOffset")) o.optInt("utcOffset") else null,
                windGustKmh = o.optDouble("windGust", Double.NaN),
                nowcast = o.optJSONArray("nowcast")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val pair = arr.optJSONArray(i) ?: return@mapNotNull null
                        RainNowcast.Step(pair.optString(0), pair.optDouble(1, Double.NaN)).takeIf { !it.mm.isNaN() }
                    }
                } ?: emptyList(),
                pollen = o.optJSONObject("pollen")?.let { p ->
                    WeatherApi.PollenReading(
                        alder = p.optDouble("a", Double.NaN), birch = p.optDouble("b", Double.NaN),
                        grass = p.optDouble("g", Double.NaN), mugwort = p.optDouble("m", Double.NaN),
                        olive = p.optDouble("o", Double.NaN), ragweed = p.optDouble("r", Double.NaN),
                    )
                },
                official = o.optJSONArray("official")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val w = arr.optJSONObject(i) ?: return@mapNotNull null
                        WeatherApi.OfficialWarning(
                            id = w.optString("i"), event = w.optString("e"), degree = w.optInt("d", 1),
                            from = w.optString("f"), to = w.optString("t"), text = w.optString("x"),
                        )
                    }
                } ?: emptyList(),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** [place] is the place [weather] was fetched for — passed explicitly by a
     * render, because the widget may have moved to another place while the
     * fetch was in flight, and its forecast must not land under that one. */
    fun setCachedWeather(
        context: Context,
        appWidgetId: Int,
        weather: WeatherApi.WeatherData,
        place: WidgetCity? = getCity(context, appWidgetId),
    ) {
        val dailyArr = org.json.JSONArray()
        weather.daily.forEach { entry ->
            dailyArr.put(JSONObject().apply {
                put("date", entry.date)
                put("weatherCode", entry.weatherCode)
                put("tempMax", entry.tempMax)
                put("tempMin", entry.tempMin)
                put("pop", entry.precipitationProbabilityMax)
                if (!entry.precipitationSumMm.isNaN()) put("mm", entry.precipitationSumMm)
                if (!entry.uvIndexMax.isNaN()) put("uv", entry.uvIndexMax)
                if (!entry.windGustMaxKmh.isNaN()) put("gust", entry.windGustMaxKmh)
                entry.sunrise?.let { put("sr", it) }
                entry.sunset?.let { put("ss", it) }
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
            if (!weather.windGustKmh.isNaN()) put("windGust", weather.windGustKmh)
            if (weather.nowcast.isNotEmpty()) {
                put("nowcast", org.json.JSONArray().apply {
                    weather.nowcast.forEach { put(org.json.JSONArray().put(it.time).put(it.mm)) }
                })
            }
            weather.pollen?.let { r ->
                put("pollen", JSONObject().apply {
                    fun put(key: String, v: Double) { if (!v.isNaN()) this.put(key, v) }
                    put("a", r.alder); put("b", r.birch); put("g", r.grass)
                    put("m", r.mugwort); put("o", r.olive); put("r", r.ragweed)
                })
            }
            if (weather.official.isNotEmpty()) {
                put("official", org.json.JSONArray().apply {
                    weather.official.forEach { w ->
                        put(JSONObject().put("i", w.id).put("e", w.event).put("d", w.degree)
                            .put("f", w.from).put("t", w.to).put("x", w.text))
                    }
                })
            }
            if (weather.currentPrecipitationProbability >= 0) put("currentPop", weather.currentPrecipitationProbability)
            if (weather.maxNext6hPop >= 0) put("next6hPop", weather.maxNext6hPop)
            if (weather.usAqi >= 0) put("aqi", weather.usAqi)
            weather.utcOffsetSeconds?.let { put("utcOffset", it) }
            put("daily", dailyArr)
            // Short keys: this blob is rewritten on every refresh, per widget,
            // and 120 hourly entries would otherwise dwarf everything else in it.
            put("hourly", org.json.JSONArray().apply {
                weather.hourly.forEach { h ->
                    put(JSONObject().apply {
                        put("t", h.time)
                        put("p", h.precipitationProbability)
                        put("c", h.weatherCode)
                        if (!h.temperature.isNaN()) put("T", h.temperature)
                        h.isDay?.let { put("d", if (it) 1 else 0) }
                        if (!h.precipitationMm.isNaN()) put("m", h.precipitationMm)
                        if (!h.windGustKmh.isNaN()) put("g", h.windGustKmh)
                    })
                }
            })
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val city = place ?: return
        prefs.edit().putString(weatherCacheKey(appWidgetId, city), o.toString()).apply()
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
    private const val NOTIF_UV_ENABLED_KEY = "settings:notif-uv-enabled"
    private const val NOTIF_UV_THRESHOLD_KEY = "settings:notif-uv-threshold"
    private const val NOTIF_GUST_ENABLED_KEY = "settings:notif-gust-enabled"
    private const val NOTIF_GUST_THRESHOLD_KEY = "settings:notif-gust-threshold"
    private const val NOTIF_SEVERE_ENABLED_KEY = "settings:notif-severe-enabled"
    private const val NOTIF_POLLEN_ENABLED_KEY = "settings:notif-pollen-enabled"
    private const val FOOTER_SOURCE_KEY = "settings:footer-source"
    private const val HIGH_CONTRAST_KEY = "settings:high-contrast"
    private const val CITIES_KEY = "brew-wx:cities"
    private const val BRIEF_ENABLED_KEY = "settings:brief-enabled"
    private const val BRIEF_HOUR_KEY = "settings:brief-hour"
    private const val TONE_KEY = "settings:tone"
    private const val LANG_KEY = "settings:lang"
    private const val EVENING_ENABLED_KEY = "settings:evening-enabled"
    private const val EVENING_HOUR_KEY = "settings:evening-hour"

    /** The app's saved-cities list as stored (see [AppCities]), or null. */
    fun rawCities(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(CITIES_KEY, null)

    fun saveRawCities(context: Context, json: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(CITIES_KEY, json).apply()
    }

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

    /** UV index the day's peak has to reach to be mentioned; "very high" starts at 8. */
    fun uvEnabled(context: Context): Boolean = getBool(context, NOTIF_UV_ENABLED_KEY, true)
    fun uvThreshold(context: Context): Double = getDouble(context, NOTIF_UV_THRESHOLD_KEY, 8.0)

    /** Wind gust (km/h) the day's strongest gust has to reach to be mentioned. */
    fun gustEnabled(context: Context): Boolean = getBool(context, NOTIF_GUST_ENABLED_KEY, true)
    fun gustThreshold(context: Context): Double = getDouble(context, NOTIF_GUST_THRESHOLD_KEY, 60.0)

    /** Pollen high enough to mention in the brief and on the widget (see [Pollen]). */
    fun pollenEnabled(context: Context): Boolean = getBool(context, NOTIF_POLLEN_ENABLED_KEY, true)

    /** Immediate storm / hail / violent-wind / extreme-heat warnings (see
     * [SevereWeather]). On by default: they're the one alert that is about safety. */
    fun severeEnabled(context: Context): Boolean = getBool(context, NOTIF_SEVERE_ENABLED_KEY, true)

    /** One morning summary instead of separate threshold alerts (see
     * MorningBrief). On by default — that is the point of the feature. */
    fun briefEnabled(context: Context): Boolean = getBool(context, BRIEF_ENABLED_KEY, true)

    /** Local hour (0-23) the brief goes out from, default 7. */
    fun briefHour(context: Context): Int = getDouble(context, BRIEF_HOUR_KEY, 7.0).toInt().coerceIn(0, 23)

    /** What the widget footer shows: a joke, a well-being tip or an event from this day in history. */
    fun footerSource(context: Context): FooterSource {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return FooterSource.fromStorage(prefs.getString(FOOTER_SOURCE_KEY, null))
    }

    /** Brighter secondary text on the widget; off by default (see [HighContrast]). */
    fun highContrast(context: Context): Boolean = getBool(context, HIGH_CONTRAST_KEY, false)

    fun tone(context: Context): Tone {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Tone.fromStorage(prefs.getString(TONE_KEY, null))
    }

    /** App-wide language (see [Lang]); unset follows the device. */
    fun lang(context: Context): Lang {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Lang.fromStorage(prefs.getString(LANG_KEY, null))
    }

    /** Evening preview of tomorrow (see [EveningPreview]). Off by default:
     * a new notification nobody asked for yet. */
    fun eveningEnabled(context: Context): Boolean = getBool(context, EVENING_ENABLED_KEY, false)

    /** Local hour (0-23) the evening preview goes out from, default 20. */
    fun eveningHour(context: Context): Int = getDouble(context, EVENING_HOUR_KEY, 20.0).toInt().coerceIn(0, 23)
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
