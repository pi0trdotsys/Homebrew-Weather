package dev.pi0trdotsys.homebrewweather.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The cities the app has saved (`brew-wx:cities` in the shared Capacitor
 * preferences — src/lib/settings.ts), as the widget's setup screen sees them.
 *
 * Before this the setup screen could offer exactly one of them, the last city the
 * app had open, and a city picked on the widget never reached the app's list, so
 * the two never agreed on what "my cities" are. Now setup lists them for one-tap
 * picking, and a city picked there is saved back.
 *
 * The rules are the web app's, mirrored (`withCity` in settings.ts) — change
 * both together:
 *  - a city already in the list keeps its position and gets its name and
 *    coordinates refreshed; a new one goes to the front;
 *  - a *live* ("follow my location") entry takes one slot — a new one replaces
 *    the old in place — so wandering about never pushes favourites out;
 *  - the list holds [MAX] and drops from the end, never the entry just added.
 *
 * Pure list logic in [with]; storage in [load] / [add]. Covered by AppCitiesTest.
 */
object AppCities {

    const val MAX = 8

    /** Stored shape: `{ lat, lon, name, live? }` — `live` only when true, as the web app writes it. */
    fun parse(raw: String?): List<WidgetCity> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val lat = o.optDouble("lat", Double.NaN)
                val lon = o.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) return@mapNotNull null
                WidgetCity(lat, lon, o.optString("name", "?"), isLive = o.optBoolean("live", false))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serialize(list: List<WidgetCity>): String =
        JSONArray().apply {
            list.forEach {
                put(
                    JSONObject().put("lat", it.lat).put("lon", it.lon).put("name", it.name)
                        .apply { if (it.isLive) put("live", true) },
                )
            }
        }.toString()

    /** [city] added to [list] by the rules above. */
    fun with(list: List<WidgetCity>, city: WidgetCity): List<WidgetCity> {
        val existing = list.indexOfFirst { Places.same(it, city) }
        val next = if (existing >= 0) {
            // Replaced in place; a stored duplicate of the same place goes (as on the web side).
            list.mapIndexedNotNull { i, c ->
                when {
                    i == existing -> city
                    Places.same(c, city) -> null
                    else -> c
                }
            }
        } else {
            listOf(city) + list
        }
        if (next.size <= MAX) return next
        // Over the cap: drop from the end, but never the entry that was just added.
        val keep = next.indexOfFirst { it === city }.coerceAtLeast(0)
        val drop = next.indices.last { it != keep }
        return next.filterIndexed { i, _ -> i != drop }
    }

    fun load(context: Context): List<WidgetCity> = parse(CapacitorStorage.rawCities(context))

    fun add(context: Context, city: WidgetCity) {
        CapacitorStorage.saveRawCities(context, serialize(with(load(context), city)))
    }
}
