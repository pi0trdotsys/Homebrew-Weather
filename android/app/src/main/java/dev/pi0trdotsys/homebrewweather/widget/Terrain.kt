package dev.pi0trdotsys.homebrewweather.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * The lay of the land around a place — a grid of elevations — for the widget's
 * "city map" background (see [WidgetBackdropArt]).
 *
 * ## Where it comes from
 *
 * Open-Meteo's elevation endpoint: no key, same host family as the forecast, up
 * to 100 points a call. A [COLS] x [ROWS] grid is 288 points, so three calls, made
 * once per place and kept for good — a hill doesn't move. The cache is keyed by a
 * 0.1° cell ([cellKey], about 7-11 km), so a widget that follows your location
 * re-uses the grid while you stay in the same area instead of fetching one per
 * fix.
 *
 * ## Never in the way
 *
 * Fetching happens off the render path ([request]): the widget draws a skyline
 * until a grid arrives, then redraws. The free tier answers 429 when asked too
 * fast, so a failure is not retried for [FAILURE_BACKOFF_MS] and the three calls
 * are spaced out. Pure parts (sample points, parsing, relief) are covered by
 * TerrainTest.
 */
object Terrain {

    const val COLS = 24
    const val ROWS = 12

    /** Width of the area the grid covers, km; the height follows the grid's 2:1 shape. */
    const val SPAN_KM_X = 14.0

    /** The elevation endpoint takes at most this many points per call. */
    private const val CHUNK = 100

    const val FAILURE_BACKOFF_MS = 10 * 60 * 1000L

    /** Elevations in metres, row-major from the north-west corner: [rows] rows of [cols]. */
    class Grid(val cols: Int, val rows: Int, val elevation: IntArray) {
        init {
            require(elevation.size == cols * rows) { "grid is ${elevation.size} values, not $cols x $rows" }
        }

        operator fun get(row: Int, col: Int): Int = elevation[row * cols + col]

        /** Highest minus lowest point, metres. */
        val relief: Int get() = (elevation.maxOrNull() ?: 0) - (elevation.minOrNull() ?: 0)
    }

    /** "51.1_17.0": the 0.1° cell a place falls in. */
    fun cellKey(lat: Double, lon: Double): String = String.format(Locale.US, "%.1f_%.1f", lat, lon)

    /** The points to ask about, in the order [Grid] expects: north to south, west to east. */
    fun samplePoints(lat: Double, lon: Double): List<Pair<Double, Double>> {
        val spanY = SPAN_KM_X / (COLS.toDouble() / ROWS)
        val dLat = spanY / 111.0
        val dLon = SPAN_KM_X / (111.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.05))
        return (0 until ROWS).flatMap { r ->
            val la = lat + dLat / 2 - dLat * r / (ROWS - 1)
            (0 until COLS).map { c -> la to (lon - dLon / 2 + dLon * c / (COLS - 1)) }
        }
    }

    /** `{"elevation":[…]}` as metres, or null if it isn't that. */
    fun parseElevations(json: String): List<Double>? = try {
        val arr = JSONObject(json).optJSONArray("elevation")
        if (arr == null) null else (0 until arr.length()).map { arr.getDouble(it) }
    } catch (e: Exception) {
        null
    }

    fun toJson(grid: Grid): String = JSONObject()
        .put("c", grid.cols).put("r", grid.rows)
        .put("e", JSONArray().apply { grid.elevation.forEach { put(it) } })
        .toString()

    fun fromJson(raw: String?): Grid? = try {
        if (raw == null) null else {
            val o = JSONObject(raw)
            val arr = o.getJSONArray("e")
            Grid(o.getInt("c"), o.getInt("r"), IntArray(arr.length()) { arr.getInt(it) })
        }
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------------
    // Cache and fetch
    // ---------------------------------------------------------------------

    private const val PREFS_NAME = "terrain_prefs"
    private val executor = Executors.newSingleThreadExecutor()
    private val inFlight = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun cached(context: Context, lat: Double, lon: Double): Grid? =
        fromJson(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("t_${cellKey(lat, lon)}", null))

    /**
     * Fetches the grid around ([lat], [lon]) in the background if it isn't cached
     * and nothing is already under way, then calls [onDone] (on that background
     * thread) once it is stored. A failed attempt calls nothing and is not retried
     * for [FAILURE_BACKOFF_MS].
     */
    fun request(context: Context, lat: Double, lon: Double, onDone: () -> Unit) {
        val key = cellKey(lat, lon)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains("t_$key")) return
        if (System.currentTimeMillis() - prefs.getLong("failed_$key", 0L) < FAILURE_BACKOFF_MS) return
        if (!inFlight.add(key)) return
        executor.execute {
            try {
                val points = samplePoints(lat, lon)
                val elevations = ArrayList<Double>(points.size)
                for (chunk in points.chunked(CHUNK)) {
                    val url = "https://api.open-meteo.com/v1/elevation" +
                        "?latitude=" + chunk.joinToString(",") { String.format(Locale.US, "%.5f", it.first) } +
                        "&longitude=" + chunk.joinToString(",") { String.format(Locale.US, "%.5f", it.second) }
                    elevations += parseElevations(get(url)) ?: throw RuntimeException("no elevations")
                    Thread.sleep(400)
                }
                if (elevations.size != COLS * ROWS) throw RuntimeException("got ${elevations.size} points")
                val grid = Grid(COLS, ROWS, IntArray(elevations.size) { elevations[it].roundToInt() })
                prefs.edit().putString("t_$key", toJson(grid)).remove("failed_$key").apply()
                onDone()
            } catch (e: Exception) {
                prefs.edit().putLong("failed_$key", System.currentTimeMillis()).apply()
            } finally {
                inFlight.remove(key)
            }
        }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            if (conn.responseCode !in 200..299) throw RuntimeException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
