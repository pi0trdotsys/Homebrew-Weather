package dev.pi0trdotsys.homebrewweather.widget

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.round

/**
 * The rules that exist twice, in Kotlin (here) and in TypeScript (src/lib), run
 * against the language-neutral cases in the JSON files in `testdata/parity` — the web side
 * runs the very same files from src/lib/parity.test.ts. Change one side's rule
 * and the other's tests fail, which is the point.
 *
 * Each adapter only maps a case's JSON input to this side's types, calls the
 * real production function and maps the result back; the rules live in the
 * fixtures. A case marked `"only": "ts"` has no Kotlin counterpart and is
 * skipped; `"only": "kotlin"` ones (air, pollen, the notification horizon, the
 * footer cut) are run here only.
 *
 * Gradle runs the tests in `android/app`, so the fixtures are `../../testdata/parity`.
 * Every case runs and the failures are reported together, by case name.
 */
class ParityTest {

    private val dir: File = File("../../testdata/parity").also {
        assertTrue("fixtures not found at ${it.absolutePath}", it.isDirectory)
    }

    private fun root(rule: String): JSONObject {
        val root = JSONObject(File(dir, "$rule.json").readText())
        assertTrue("rule name in $rule.json", root.getString("rule") == rule)
        return root
    }

    private fun fixture(rule: String): List<JSONObject> {
        val cases = root(rule).getJSONArray("cases")
        return (0 until cases.length()).map { cases.getJSONObject(it) }.filter { it.optString("only") != "ts" }
    }

    /**
     * Runs [adapter] over every case of [rule] and fails once with all mismatches.
     * A fixture with a `"tolerance"` compares its numbers within that (the Kotlin
     * geometry computes in 32-bit floats, the fixtures were made with doubles).
     */
    private fun run(rule: String, adapter: (JSONObject) -> Any?) {
        val cases = fixture(rule)
        assertTrue("$rule has cases", cases.isNotEmpty())
        val tolerance = root(rule).optDouble("tolerance", Double.NaN)
        val failures = cases.mapNotNull { c ->
            val expected = norm(c.get("expect"))
            val actual = try {
                norm(adapter(c))
            } catch (e: Exception) {
                return@mapNotNull "  ${c.getString("name")}: threw $e"
            }
            val same = if (tolerance.isNaN()) actual == expected else close(actual, expected, tolerance)
            if (same) null else "  ${c.getString("name")}\n      expected $expected\n      actual   $actual"
        }
        assertTrue("$rule: ${failures.size} of ${cases.size} cases differ\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** JSON and Kotlin values in one comparable shape: doubles to 6 decimals, NaN and JSON null as null. */
    private fun norm(v: Any?): Any? = when (v) {
        null, JSONObject.NULL -> null
        is JSONObject -> v.keys().asSequence().associateWith { norm(v.get(it)) }.toSortedMap()
        is JSONArray -> (0 until v.length()).map { norm(v.get(it)) }
        is Map<*, *> -> v.entries.associate { it.key.toString() to norm(it.value) }.toSortedMap()
        is List<*> -> v.map { norm(it) }
        is Double -> if (v.isNaN()) null else num(v)
        is Float -> num(v.toDouble())
        is Number -> num(v.toDouble())
        else -> v
    }

    /** Normalised values equal, numbers within [tol]. */
    private fun close(a: Any?, e: Any?, tol: Double): Boolean = when {
        a is Number && e is Number -> Math.abs(a.toDouble() - e.toDouble()) <= tol
        a is List<*> && e is List<*> -> a.size == e.size && a.indices.all { close(a[it], e[it], tol) }
        a is Map<*, *> && e is Map<*, *> -> a.keys == e.keys && a.keys.all { close(a[it], e[it], tol) }
        else -> a == e
    }

    private fun num(d: Double): Any {
        val r = round(d * 1e6) / 1e6
        return if (r == Math.floor(r) && Math.abs(r) < 1e15) r.toLong() else r
    }

    private fun JSONArray.nan(i: Int): Double = if (isNull(i)) Double.NaN else getDouble(i)
    private fun JSONObject.nan(key: String): Double = if (isNull(key)) Double.NaN else getDouble(key)
    private fun JSONArray.rows(): List<JSONArray> = (0 until length()).map { getJSONArray(it) }

    // ------------------------------------------------------------ 1. rain windows

    private fun rainHourly(rows: JSONArray) = rows.rows().map { r ->
        WeatherApi.HourlyEntry(
            time = r.getString(0),
            precipitationProbability = if (r.isNull(1)) 0 else r.getInt(1),
            weatherCode = r.getInt(2),
            precipitationMm = r.nan(3),
        )
    }

    private fun window(w: RainWindow.Window?): Any? = w?.let {
        mapOf(
            "kind" to it.kind.name.lowercase(), "startsInHours" to it.startsInHours, "start" to it.start, "end" to it.end,
            "ongoing" to it.ongoing, "totalMm" to it.totalMm, "peakMm" to it.peakMm, "intensity" to it.intensity.name.lowercase(),
        )
    }

    @Test
    fun `rain windows and intensity`() = run("rain-window") { c ->
        val input = c.getJSONObject("input")
        when (c.getString("fn")) {
            "today" -> window(RainWindow.today(rainHourly(input.getJSONArray("hourly")), input.getInt("currentCode")))
            "find" -> window(RainWindow.find(rainHourly(input.getJSONArray("hourly")), input.getInt("currentCode")))
            "forDay" -> RainWindow.forDay(rainHourly(input.getJSONArray("hourly")), input.getString("date"))?.let {
                mapOf(
                    "kind" to it.kind.name.lowercase(), "startHour" to it.startHour, "endHour" to it.endHour,
                    "moreLater" to it.moreLater, "totalMm" to it.totalMm, "peakMm" to it.peakMm,
                )
            }
            "intensity" -> mapOf(
                "intensity" to RainWindow.intensity(RainWindow.Kind.valueOf(input.getString("kind").uppercase()), input.nan("peakMm"))
                    .name.lowercase(),
            )
            else -> error("unknown fn in ${c.getString("name")}")
        }
    }

    // ------------------------------------------------------------ 2. severe weather

    @Test
    fun `severe weather`() = run("severe-weather") { c ->
        val input = c.getJSONObject("input")
        val hourly = input.getJSONArray("hourly").rows().map { r ->
            WeatherApi.HourlyEntry(r.getString(0), 0, r.getInt(1), temperature = r.nan(2), windGustKmh = r.nan(3))
        }
        val weather = WeatherApi.WeatherData(
            isDay = true,
            currentWeatherCode = input.getInt("currentCode"),
            currentTemperature = input.nan("currentTemp"),
            windGustKmh = input.nan("currentGust"),
            daily = emptyList(),
            hourly = hourly,
        )
        SevereWeather.find(weather).map {
            val m = mutableMapOf<String, Any?>("kind" to it.kind.name.lowercase(), "startsInHours" to it.startsInHours)
            if (it.kind == SevereWeather.Kind.WIND || it.kind == SevereWeather.Kind.HEAT) m["peak"] = it.peak
            m
        }
    }

    // ------------------------------------------------------------ 3. on this day

    private fun events(list: List<OnThisDay.Event>) = list.map { mapOf("year" to it.year, "text" to it.text) }

    @Test
    fun `on this day`() = run("on-this-day") { c ->
        val input = c.getJSONObject("input")
        fun event() = OnThisDay.Event(input.getString("year"), input.getString("text"))
        when (c.getString("fn")) {
            "clean" -> mapOf("text" to OnThisDay.cleanWikitext(input.getString("text")))
            "parsePolish" -> mapOf("events" to events(OnThisDay.parsePolishWikitext(input.getString("wikitext"))))
            "parseEnglish" -> mapOf("events" to events(OnThisDay.parseEnglishFeed(input.getJSONObject("json").toString())))
            "pick" -> {
                val list = input.getJSONArray("events")
                val picked = OnThisDay.pick(
                    (0 until list.length()).map { OnThisDay.Event(list.getJSONObject(it).getString("year"), list.getJSONObject(it).getString("text")) },
                    input.getInt("dayOfYear"),
                )
                mapOf("year" to picked?.year)
            }
            "line" -> mapOf("line" to event().line)
            "fit" -> mapOf("line" to OnThisDay.fit(event()))
            else -> error("unknown fn in ${c.getString("name")}")
        }
    }

    // ------------------------------------------------------------ 4. saved cities

    private fun cityOf(o: JSONObject) = WidgetCity(o.getDouble("lat"), o.getDouble("lon"), o.getString("name"), isLive = o.optBoolean("live", false))

    @Test
    fun `saved cities`() = run("saved-cities") { c ->
        val input = c.getJSONObject("input")
        val list = input.getJSONArray("list")
        val out = AppCities.with((0 until list.length()).map { cityOf(list.getJSONObject(it)) }, cityOf(input.getJSONObject("city")))
        mapOf(
            "list" to out.map {
                val m = mutableMapOf<String, Any?>("name" to it.name, "lat" to it.lat, "lon" to it.lon)
                if (it.isLive) m["live"] = true
                m
            },
        )
    }

    // ------------------------------------------------------------ 5. wellbeing situation

    @Test
    fun `wellbeing situation`() = run("wellbeing-situation") { c ->
        val input = c.getJSONObject("input")
        val pollen = input.optJSONObject("pollen")?.let {
            WeatherApi.PollenReading(
                birch = if (it.has("birch")) it.getDouble("birch") else Double.NaN,
                grass = if (it.has("grass")) it.getDouble("grass") else Double.NaN,
            )
        }
        val code = input.getInt("code")
        val weather = WeatherApi.WeatherData(
            isDay = input.getBoolean("isDay"),
            currentWeatherCode = code,
            currentTemperature = input.nan("temp"),
            usAqi = if (input.has("aqi")) input.getInt("aqi") else -1,
            windGustKmh = input.nan("gust"),
            daily = listOf(WeatherApi.DailyEntry("2026-11-17", code, 20.0, 10.0, 0, uvIndexMax = input.nan("uvMax"))),
            hourly = if (input.isNull("hour")) emptyList() else listOf(WeatherApi.HourlyEntry("2026-11-17T%02d:00".format(input.getInt("hour")), 0, code)),
            pollen = pollen,
        )
        mapOf("situation" to WellbeingTips.situation(weather).key)
    }

    // ------------------------------------------------------------ 6. rain nowcast

    @Test
    fun `rain nowcast`() = run("rain-nowcast") { c ->
        val input = c.getJSONObject("input")
        when (c.getString("fn")) {
            "analyse" -> RainNowcast.analyse(
                input.getJSONArray("steps").rows().map { RainNowcast.Step(it.getString(0), it.nan(1)) },
                input.getString("now"),
            )?.let {
                mapOf(
                    "ongoing" to it.ongoing, "startsInMinutes" to it.startsInMinutes,
                    "endsInMinutes" to it.endsInMinutes, "totalMm" to it.totalMm,
                )
            }
            "speech" -> mapOf("minutes" to RainNowcast.roundForSpeech(input.getInt("minutes")))
            "localMinute" -> mapOf("iso" to WeatherApi.localMinuteIso(input.getLong("nowMs"), input.getInt("offsetSeconds")))
            else -> error("unknown fn in ${c.getString("name")}")
        }
    }

    // ------------------------------------------------------------ 7. temperature colour

    @Test
    fun `temperature colour`() = run("temp-color") { c ->
        mapOf("hex" to TempColor.hex(c.getJSONObject("input").nan("celsius")))
    }

    // ------------------------------------------------------------ 8. terrain

    @Test
    fun `terrain grid helpers`() = run("terrain") { c ->
        val input = c.getJSONObject("input")
        when (c.getString("fn")) {
            "cellKey" -> mapOf("key" to Terrain.cellKey(input.getDouble("lat"), input.getDouble("lon")))
            "samplePoints" -> {
                val p = Terrain.samplePoints(input.getDouble("lat"), input.getDouble("lon")).map { listOf(it.first, it.second) }
                mapOf(
                    "count" to p.size, "first" to p[0], "second" to p[1], "endOfFirstRow" to p[23], "startOfSecondRow" to p[24],
                    "middle" to p[150], "last" to p.last(), "sumLat" to p.sumOf { it[0] }, "sumLon" to p.sumOf { it[1] },
                )
            }
            "parseElevations" -> mapOf("elevations" to Terrain.parseElevations(input.getString("json")))
            "relief" -> {
                val e = input.getJSONArray("elevation")
                mapOf("relief" to Terrain.Grid(input.getInt("cols"), input.getInt("rows"), IntArray(e.length()) { e.getInt(it) }).relief)
            }
            else -> error("unknown fn in ${c.getString("name")}")
        }
    }

    // ------------------------------------------------------------ 9. contours

    private fun floats(a: JSONArray) = FloatArray(a.length()) { a.getDouble(it).toFloat() }

    @Test
    fun `contour lines`() = run("contours") { c ->
        val input = c.getJSONObject("input")
        when (c.getString("fn")) {
            "upsample" -> Contours.upsample(floats(input.getJSONArray("field")), input.getInt("w"), input.getInt("h"), input.getInt("factor"))
                .let { (field, w, h) -> mapOf("w" to w, "h" to h, "field" to field.toList()) }
            "smooth" -> mapOf("field" to Contours.smooth(floats(input.getJSONArray("field")), input.getInt("w"), input.getInt("h")).toList())
            "segments" -> mapOf(
                "segments" to Contours.segments(
                    floats(input.getJSONArray("field")), input.getInt("w"), input.getInt("h"), input.getDouble("level").toFloat(),
                ).map { listOf(it.x1, it.y1, it.x2, it.y2) },
            )
            "niceStep" -> mapOf("step" to Contours.niceStep(input.getDouble("relief").toFloat()))
            else -> error("unknown fn in ${c.getString("name")}")
        }
    }

    // ------------------------------------------------------------ 10. skyline

    @Test
    fun `skyline`() = run("skyline") { c ->
        val input = c.getJSONObject("input")
        when (c.getString("fn")) {
            "hash" -> mapOf("hash" to input.getString("text").lowercase().hashCode())
            "prng" -> {
                val r = Mulberry32(input.getString("text").lowercase().hashCode())
                mapOf("doubles" to List(input.getInt("count")) { r.nextDouble() })
            }
            "buildings" -> mapOf(
                "buildings" to Skyline.buildings(input.getString("seed"), input.getInt("widthUnits"), input.getInt("maxHeightUnits")).map {
                    mapOf("x" to it.x, "width" to it.width, "height" to it.height, "windows" to it.windows.map { w -> listOf(w.first, w.second) })
                },
            )
            else -> error("unknown fn in ${c.getString("name")}")
        }
    }
}
