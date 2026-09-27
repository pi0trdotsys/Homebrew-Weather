package dev.pi0trdotsys.homebrewweather.widget

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Kotlin port of src/lib/weather-api.ts using plain HttpURLConnection
 * + org.json (both built into Android) — no Retrofit/OkHttp/Gson needed for
 * this widget's small surface area. All functions here are blocking; callers
 * must invoke them off the main thread (background thread / coroutine).
 */
object WeatherApi {

    data class GeoResult(
        val name: String,
        val country: String,
        val admin1: String?,
        val latitude: Double,
        val longitude: Double,
    )

    data class DailyEntry(
        val date: String,
        val weatherCode: Int,
        val tempMax: Double,
        val tempMin: Double,
        val precipitationProbabilityMax: Int,
    )

    /**
     * One forecast hour, starting from the current hour. [time] is Open-Meteo's
     * local ISO string ("2026-09-24T15:00"), in the location's own timezone —
     * the same frame `current.time` uses, so no timezone math is needed to
     * compare or display it.
     */
    data class HourlyEntry(
        val time: String,
        val precipitationProbability: Int,
        val weatherCode: Int,
        /** NaN for caches written before it was kept. */
        val temperature: Double = Double.NaN,
        /** Null for caches written before it was kept. */
        val isDay: Boolean? = null,
    )

    data class WeatherData(
        val isDay: Boolean,
        val currentWeatherCode: Int,
        val currentTemperature: Double,
        // "Feels like" / humidity / wind — already requested in the
        // `current=` query string below, just not previously surfaced by the
        // widget. NaN/negative sentinels mean "unknown" (e.g. older cached
        // entries written before these fields existed).
        val apparentTemperature: Double = Double.NaN,
        val humidityPercent: Int = -1,
        val windSpeedKmh: Double = Double.NaN,
        // "Right now" chance of rain, sourced from the `hourly` object at the
        // index matching `current.time` (see fetchWeather() below) — distinct
        // from `DailyEntry.precipitationProbabilityMax`, which is today's daily
        // max PoP. -1 sentinel = unknown (matches this file's other optional
        // fields' convention).
        val currentPrecipitationProbability: Int = -1,
        // Max hourly precipitation_probability over roughly the next 6 hours
        // (inclusive of the current hour), from the same `hourly` arrays used
        // to compute [currentPrecipitationProbability] above — a cheap
        // near-term "is rain coming soon" signal for WidgetTheme.AUTO_HEALTH's
        // comfort score. -1 sentinel = unknown (same convention as this
        // file's other optional fields).
        val maxNext6hPop: Int = -1,
        // US AQI (0..500+) from a separate, fully best-effort Open-Meteo Air
        // Quality call (see fetchAirQuality()) — never blocks/breaks the main
        // weather fetch. -1 sentinel = unknown/not fetched this refresh.
        val usAqi: Int = -1,
        val daily: List<DailyEntry>,
        // The next [HOURLY_HORIZON] hours from the current one. This is what
        // RainWindow reads to answer "when does it start raining, and when
        // does it stop" — the question the widget used to leave to the user,
        // spread across a current-hour figure, a 4-day sparkline and four
        // daily maxima. Empty for caches written before this field existed.
        val hourly: List<HourlyEntry> = emptyList(),
        // The location's offset from UTC, so a cached forecast can be read
        // against the real clock later (see [rebasedTo]). Null for caches
        // written before it was kept.
        val utcOffsetSeconds: Int? = null,
    ) {
        /**
         * This forecast as it should read *now*, however long ago it was
         * fetched.
         *
         * Offline, the widget keeps rendering its last cached forecast — and
         * everything relative in it ("now", "today", "dziś"/"jutro", which
         * column comes first) was frozen at fetch time. A forecast fetched at
         * 23:00 and still on screen at 09:00 kept calling today "jutro", kept
         * showing last night's temperature as the current one, and kept
         * today's first hours of rain in a window that had already started.
         *
         * So: past hours are dropped; "now" is taken from the hourly forecast
         * for the real current hour; days that are over are dropped. Readouts
         * with no hourly forecast (feels-like, humidity, wind) become unknown
         * rather than stay stale, and the standard density hides them. A
         * forecast fetched this hour comes back unchanged.
         */
        fun rebasedTo(nowMillis: Long): WeatherData {
            val offset = utcOffsetSeconds ?: return this
            if (hourly.isEmpty()) return this
            val nowLocal = localHourIso(nowMillis, offset)
            val idx = hourly.indexOfLast { it.time <= nowLocal }
            if (idx <= 0) return this
            val now = hourly[idx]
            val today = nowLocal.substringBefore('T')
            return copy(
                isDay = now.isDay ?: isDay,
                currentWeatherCode = now.weatherCode,
                currentTemperature = if (now.temperature.isNaN()) currentTemperature else now.temperature,
                apparentTemperature = Double.NaN,
                humidityPercent = -1,
                windSpeedKmh = Double.NaN,
                currentPrecipitationProbability = now.precipitationProbability,
                maxNext6hPop = hourly.drop(idx).take(7).maxOfOrNull { it.precipitationProbability } ?: -1,
                daily = daily.filter { it.date >= today },
                hourly = hourly.drop(idx),
            )
        }
    }

    /** "2026-09-28T09:00" — the current hour in a location [offsetSeconds] from UTC. */
    fun localHourIso(nowMillis: Long, offsetSeconds: Int): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:00", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date(nowMillis + offsetSeconds * 1000L))
    }

    /** How many forecast hours [WeatherData.hourly] keeps: enough to cover
     * every grid day to midnight, so each column can carry its own rain window
     * (RainWindow.forDay) — including the evening grid, which ends a day later. */
    const val HOURLY_HORIZON = 120

    /** How many days [WeatherData.daily] keeps: four for the grid, plus the one
     * it moves on to in the evening (WidgetContent.EVENING_HOUR). */
    const val DAILY_DAYS = 5

    private fun httpGetJson(urlStr: String): JSONObject {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code for $urlStr: $body")
            }
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseGeoResult(o: JSONObject): GeoResult = GeoResult(
        name = o.optString("name", "unknown"),
        country = o.optString("country", ""),
        admin1 = if (o.has("admin1")) o.optString("admin1", "").ifBlank { null } else null,
        latitude = o.optDouble("latitude"),
        longitude = o.optDouble("longitude"),
    )

    fun geocode(query: String): List<GeoResult> {
        if (query.isBlank()) return emptyList()
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=en&format=json"
        val json = httpGetJson(url)
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { parseGeoResult(results.getJSONObject(it)) }
    }

    fun reverseGeocode(lat: Double, lon: Double): GeoResult? {
        val url = "https://geocoding-api.open-meteo.com/v1/reverse?latitude=$lat&longitude=$lon&language=en&format=json"
        return try {
            val json = httpGetJson(url)
            val results = json.optJSONArray("results") ?: return null
            if (results.length() == 0) null else parseGeoResult(results.getJSONObject(0))
        } catch (e: Exception) {
            null
        }
    }

    fun fetchWeather(lat: Double, lon: Double): WeatherData {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day,surface_pressure" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
            "&timezone=auto&forecast_days=7"
        val json = httpGetJson(url)

        val current = json.getJSONObject("current")
        val isDay = current.optInt("is_day", 1) == 1
        val currentCode = current.optInt("weather_code", 0)
        val currentTemp = current.optDouble("temperature_2m", Double.NaN)
        // Already requested in the URL above (per the original web app's query)
        // but previously left unparsed — now surfaced in the new widget meta row.
        val apparentTemp = current.optDouble("apparent_temperature", Double.NaN)
        val humidity = if (current.has("relative_humidity_2m")) current.optInt("relative_humidity_2m", -1) else -1
        val windSpeed = current.optDouble("wind_speed_10m", Double.NaN)

        // `hourly` was already requested in the URL above but never parsed —
        // this is where "current" (this exact hour's) rain probability comes
        // from, as opposed to the daily grid's daily-max PoP. Open-Meteo's
        // `current.time` and every `hourly.time[i]` are ISO-local strings in
        // the same local-time frame for a given request, so plain string
        // matching/comparison (no timezone math needed) finds "now"'s index:
        // ISO8601 date-time strings sort lexicographically = chronologically.
        // Resolve "now"'s index into the hourly arrays once, then reuse it for
        // both currentPop (this exact hour) and maxNext6hPop (a small forward
        // window from that same index) below — same hourly arrays, no extra
        // network call.
        val hourly = json.optJSONObject("hourly")
        val hTimes = hourly?.optJSONArray("time")
        val hPops = hourly?.optJSONArray("precipitation_probability")
        val currentTimeStr = current.optString("time", "")

        var currentIdx = -1
        if (hTimes != null && currentTimeStr.isNotBlank()) {
            for (i in 0 until hTimes.length()) {
                if (hTimes.optString(i) == currentTimeStr) {
                    currentIdx = i
                    break
                }
            }
            if (currentIdx == -1) {
                // No exact match — which is in fact the usual case: Open-Meteo
                // reports `current` in 15-minute steps ("23:30"), so it only
                // lands on an hourly slot at :00. The current hour is the last
                // slot at or before it. This used to take the first slot at or
                // *after* it, i.e. the next hour, which at 23:30 is tomorrow's
                // 00:00: the widget then treated tomorrow as "today" and put
                // tomorrow morning's rain under the current temperature.
                for (i in 0 until hTimes.length()) {
                    if (hTimes.optString(i) <= currentTimeStr) currentIdx = i else break
                }
                if (currentIdx == -1) currentIdx = 0
            }
        }

        val currentPop = if (hPops != null && currentIdx in 0 until hPops.length()) hPops.optInt(currentIdx, -1) else -1

        // Roughly the next 6 hours, inclusive of the current hour — clamps to
        // whatever the hourly array actually has (Open-Meteo's default hourly
        // horizon comfortably covers this within forecast_days=7).
        val maxNext6hPop = if (hPops != null && currentIdx >= 0) {
            val endIdx = minOf(hPops.length() - 1, currentIdx + 6)
            (currentIdx..endIdx).mapNotNull { i -> hPops.optInt(i, -1).takeIf { it >= 0 } }.maxOrNull() ?: -1
        } else -1

        val hCodes = hourly?.optJSONArray("weather_code")
        val hTemps = hourly?.optJSONArray("temperature_2m")
        val hIsDay = hourly?.optJSONArray("is_day")
        val hourlyEntries = if (hTimes != null && hPops != null && currentIdx >= 0) {
            val end = minOf(hTimes.length(), currentIdx + HOURLY_HORIZON)
            (currentIdx until end).map { i ->
                HourlyEntry(
                    time = hTimes.optString(i),
                    precipitationProbability = hPops.optInt(i, 0),
                    weatherCode = hCodes?.optInt(i, 0) ?: 0,
                    temperature = hTemps?.optDouble(i, Double.NaN) ?: Double.NaN,
                    isDay = hIsDay?.let { if (it.isNull(i)) null else it.optInt(i, 1) == 1 },
                )
            }
        } else {
            emptyList()
        }

        val daily = json.getJSONObject("daily")
        val times = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val maxes = daily.getJSONArray("temperature_2m_max")
        val mins = daily.getJSONArray("temperature_2m_min")
        val pops = daily.optJSONArray("precipitation_probability_max")

        val count = minOf(DAILY_DAYS, times.length())
        val entries = (0 until count).map { i ->
            DailyEntry(
                date = times.getString(i),
                weatherCode = codes.getInt(i),
                tempMax = maxes.getDouble(i),
                tempMin = mins.getDouble(i),
                precipitationProbabilityMax = pops?.optInt(i, 0) ?: 0,
            )
        }
        return WeatherData(
            isDay = isDay,
            currentWeatherCode = currentCode,
            currentTemperature = currentTemp,
            apparentTemperature = apparentTemp,
            humidityPercent = humidity,
            windSpeedKmh = windSpeed,
            currentPrecipitationProbability = currentPop,
            maxNext6hPop = maxNext6hPop,
            daily = entries,
            hourly = hourlyEntries,
            utcOffsetSeconds = if (json.has("utc_offset_seconds")) json.optInt("utc_offset_seconds") else null,
        )
    }

    /**
     * Separate Open-Meteo Air Quality API call (different host, no key, same
     * no-backend philosophy as the rest of this file). Fully best-effort by
     * design: returns null on any failure (network hiccup, AQ API briefly
     * down, unexpected shape) rather than throwing, so a caller can simply
     * omit the AQI line for a refresh instead of risking the main weather
     * fetch/render.
     */
    fun fetchAirQuality(lat: Double, lon: Double): Int? {
        return try {
            val url = "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=$lat&longitude=$lon&current=us_aqi&timezone=auto"
            val json = httpGetJson(url)
            val current = json.optJSONObject("current") ?: return null
            if (!current.has("us_aqi")) return null
            val aqi = current.optInt("us_aqi", -1)
            if (aqi >= 0) aqi else null
        } catch (e: Exception) {
            null
        }
    }
}
