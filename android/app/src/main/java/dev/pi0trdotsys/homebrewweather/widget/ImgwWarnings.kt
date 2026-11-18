package dev.pi0trdotsys.homebrewweather.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Official weather warnings from IMGW-PIB, Poland's met office, for the county a
 * place is in — to sit beside the warnings [SevereWeather] derives from the
 * forecast, not replace them. A derived warning is an inference; this is a
 * forecaster's decision, with a degree (1-3) and a validity period.
 *
 * ## Where from
 *
 * IMGW's open-data feed (`/api/data/warningsmeteo`, no key) lists every active
 * warning with the TERYT codes of the counties (powiaty) it covers. Matching a
 * place to a county is the awkward half: [PolandCounty] asks OpenStreetMap's
 * Nominatim once per ~5 km cell and keeps the answer for good. Only places inside
 * Poland ever cause a request to either service.
 *
 * ## What counts
 *
 * Warnings valid now, or starting within [LOOKAHEAD_HOURS], in order of degree.
 * The pure parsing is covered by ImgwWarningsTest.
 */
object ImgwWarnings {

    private const val FEED_URL = "https://danepubliczne.imgw.pl/api/data/warningsmeteo"

    /** A warning that starts further ahead than this is not yet worth a notification. */
    const val LOOKAHEAD_HOURS = 12

    private const val USER_AGENT = "HomebrewWeather/0.6 (https://github.com/pi0trdotsys/Homebrew-Weather)"

    /** Poland's bounding box, with a margin: outside it nothing is asked of either service. */
    fun inPoland(lat: Double, lon: Double): Boolean = lat in 48.9..55.0 && lon in 14.0..24.3

    /**
     * The feed's warnings that cover [countyCode] (the 4-digit TERYT of a county)
     * and are valid at [nowLocalIso] ("2026-11-18T12:30", Poland's own clock) or
     * start within [LOOKAHEAD_HOURS], highest degree first. A malformed feed is an
     * empty list, never a crash.
     */
    fun parse(json: String, countyCode: String, nowLocalIso: String): List<WeatherApi.OfficialWarning> {
        val now = LocalIso.minutes(nowLocalIso) ?: return emptyList()
        val arr = try { JSONArray(json) } catch (e: Exception) { return emptyList() }
        val out = ArrayList<WeatherApi.OfficialWarning>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!covers(o, countyCode)) continue
            val from = localIso(o.optString("obowiazuje_od"))
            val to = localIso(o.optString("obowiazuje_do"))
            val fromMin = LocalIso.minutes(from) ?: continue
            val toMin = LocalIso.minutes(to) ?: continue
            if (toMin <= now || fromMin > now + LOOKAHEAD_HOURS * 60) continue
            out += WeatherApi.OfficialWarning(
                id = o.optString("id"),
                event = o.optString("nazwa_zdarzenia").trim(),
                degree = o.optString("stopien").toIntOrNull()?.coerceIn(1, 3) ?: 1,
                from = from,
                to = to,
                text = o.optString("tresc").trim(),
            )
        }
        return out.sortedWith(compareByDescending<WeatherApi.OfficialWarning> { it.degree }.thenBy { it.from })
    }

    /** `teryt` is a JSON array of codes (a string of them is accepted too). */
    private fun covers(o: JSONObject, countyCode: String): Boolean {
        val t = o.opt("teryt")
        return when (t) {
            is JSONArray -> (0 until t.length()).any { t.optString(it).trim() == countyCode }
            is String -> Regex("""\d{4}""").findAll(t).any { it.value == countyCode }
            else -> false
        }
    }

    /** "2026-11-18 19:00:00" to "2026-11-18T19:00". */
    private fun localIso(feedTime: String): String = feedTime.trim().replace(' ', 'T').take(16)

    /**
     * The kind of derived warning an official one is the same hazard as, or null
     * for the ones with no counterpart (frost, fog, snow...). Used to keep a derived
     * notification from arriving beside the official one about the same thing.
     */
    fun kindOf(event: String): SevereWeather.Kind? {
        val e = event.lowercase()
        return when {
            "burz" in e && "grad" in e -> SevereWeather.Kind.HAIL
            "burz" in e -> SevereWeather.Kind.STORM
            "wiatr" in e -> SevereWeather.Kind.WIND
            "upał" in e -> SevereWeather.Kind.HEAT
            else -> null
        }
    }

    /**
     * The official warnings for the place at ([lat], [lon]): an empty list outside
     * Poland or when none apply; null when the county or the feed couldn't be had
     * (the caller keeps what it had). Blocking.
     */
    fun forPlace(context: Context, lat: Double, lon: Double, utcOffsetSeconds: Int?, nowMillis: Long): List<WeatherApi.OfficialWarning>? {
        if (!inPoland(lat, lon)) return emptyList()
        return try {
            val county = PolandCounty.resolve(context, lat, lon) ?: return null
            val now = WeatherApi.localMinuteIso(nowMillis, utcOffsetSeconds ?: 3600)
            parse(get(FEED_URL), county, now)
        } catch (e: Exception) {
            null
        }
    }

    internal fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode !in 200..299) throw RuntimeException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * The county (powiat) a place in Poland is in, as the 4-digit TERYT code IMGW's
 * warnings use, from OpenStreetMap's Nominatim reverse geocoder: at zoom 9 it
 * answers with the county's boundary, whose tags carry `teryt:terc`.
 *
 * Nominatim's usage policy asks for an identifying User-Agent, no more than one
 * request a second and caching; this asks once per ~5 km cell, keeps the answer
 * for good (a county doesn't move) and backs off for [FAILURE_BACKOFF_MS] after a
 * failure. Covered by ImgwWarningsTest for the parsing.
 */
object PolandCounty {

    private const val PREFS_NAME = "county_prefs"
    const val FAILURE_BACKOFF_MS = 30 * 60 * 1000L

    /** "50.85_20.55": the 0.05° cell a place falls in. */
    fun cellKey(lat: Double, lon: Double): String =
        String.format(java.util.Locale.US, "%.2f_%.2f", Math.round(lat * 20) / 20.0, Math.round(lon * 20) / 20.0)

    /** The 4-digit county code out of Nominatim's answer, or null. */
    fun parseCode(json: String): String? = try {
        val terc = JSONObject(json).optJSONObject("extratags")?.optString("teryt:terc").orEmpty()
        terc.takeIf { it.length >= 4 && it.take(4).all(Char::isDigit) }?.take(4)
    } catch (e: Exception) {
        null
    }

    fun resolve(context: Context, lat: Double, lon: Double): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = cellKey(lat, lon)
        prefs.getString("c_$key", null)?.let { return it }
        if (System.currentTimeMillis() - prefs.getLong("failed_$key", 0L) < FAILURE_BACKOFF_MS) return null
        return try {
            val json = ImgwWarnings.get(
                "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&zoom=9&format=jsonv2&extratags=1&addressdetails=0",
            )
            val code = parseCode(json) ?: throw RuntimeException("no county")
            prefs.edit().putString("c_$key", code).remove("failed_$key").apply()
            code
        } catch (e: Exception) {
            prefs.edit().putLong("failed_$key", System.currentTimeMillis()).apply()
            null
        }
    }
}

/**
 * One fetch of everything the widget and its notifications read about a place:
 * the forecast (which may throw — without it there is nothing to show), then the
 * air quality with pollen, and the official warnings, each best-effort and each
 * falling back to what [cached] already held.
 *
 * This used to live inline in the widget render, *after* notifications were
 * evaluated — so the air-quality figure never reached them, and AQI alerts and
 * the brief's air line could not fire. Doing it once, here, before anyone
 * evaluates, is what fixes that, and what lets the worker evaluate a widget's
 * other places with exactly the same data.
 */
object WeatherRepository {

    fun fetch(context: Context, city: WidgetCity, cached: WeatherApi.WeatherData?): WeatherApi.WeatherData {
        val fresh = WeatherApi.fetchWeather(city.lat, city.lon)
        val air = WeatherApi.fetchAirQuality(city.lat, city.lon)
        val official = ImgwWarnings.forPlace(context, city.lat, city.lon, fresh.utcOffsetSeconds, System.currentTimeMillis())
        return fresh.copy(
            usAqi = air?.aqi ?: cached?.usAqi?.takeIf { it >= 0 } ?: -1,
            pollen = air?.pollen ?: cached?.pollen,
            official = official ?: cached?.official ?: emptyList(),
        )
    }
}
