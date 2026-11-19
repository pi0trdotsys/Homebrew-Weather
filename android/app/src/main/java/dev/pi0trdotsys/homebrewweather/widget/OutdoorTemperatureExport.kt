package dev.pi0trdotsys.homebrewweather.widget

/**
 * The pure part of [OutdoorExportProvider]: which cached forecast to export and
 * what the single exported row holds.
 *
 * The provider is exported with no permission (the sibling app that reads it is
 * signed with another key, so a signature permission is not possible), which is
 * why a [Row] is four numbers and nothing else: no place name, no coordinates,
 * no time zone, nothing that says where the phone is.
 */
object OutdoorTemperatureExport {

    const val PATH = "outdoor"

    const val COL_TEMP = "temp_c"
    const val COL_MAX = "max_c"
    const val COL_MIN = "min_c"
    const val COL_UPDATED = "updated_at"

    /** Column order of the cursor; the contract with the reader. */
    val COLUMNS: Array<String> = arrayOf(COL_TEMP, COL_MAX, COL_MIN, COL_UPDATED)

    /**
     * [tempC] is the temperature right now; [maxC] and [minC] are today's,
     * null when the forecast holds no entry for today (they are not invented).
     * [updatedAtMillis] is when the forecast was written to the cache, 0 if unknown.
     */
    data class Row(
        val tempC: Double,
        val maxC: Double?,
        val minC: Double?,
        val updatedAtMillis: Long,
    ) {
        /** In [COLUMNS] order, ready for MatrixCursor.addRow. */
        fun toColumns(): Array<Any?> = arrayOf(tempC, maxC, minC, updatedAtMillis)
    }

    /** One widget's cached forecast for the place it shows, with its write time. */
    class Candidate(val weather: WeatherApi.WeatherData, val updatedAtMillis: Long)

    /**
     * The row for [weather] as it reads at [nowMillis], or null when there is no
     * usable current temperature. The forecast is rebased to the real clock first,
     * so a stale cache does not report last night's value.
     */
    fun row(weather: WeatherApi.WeatherData, updatedAtMillis: Long, nowMillis: Long): Row? {
        val now = weather.rebasedTo(nowMillis)
        val temp = now.currentTemperature
        if (temp.isNaN() || temp.isInfinite()) return null
        // "Today" in the place's own calendar. An old cache with no offset falls
        // back to the device's, which is where it was almost certainly fetched.
        val offset = now.utcOffsetSeconds
            ?: (java.util.TimeZone.getDefault().getOffset(nowMillis) / 1000)
        val today = WeatherApi.localHourIso(nowMillis, offset).substringBefore('T')
        val day = now.daily.firstOrNull { it.date >= today }
        return Row(
            tempC = temp,
            maxC = day?.tempMax?.takeIf { it.isFinite() },
            minC = day?.tempMin?.takeIf { it.isFinite() },
            updatedAtMillis = updatedAtMillis,
        )
    }

    /**
     * The row to export among all widgets' caches: the usable one written most
     * recently. On a tie the earlier candidate wins, so an old cache without a
     * timestamp (0) loses to any dated one but is still used when it is alone.
     */
    fun pick(candidates: List<Candidate>, nowMillis: Long): Row? =
        candidates
            .mapNotNull { row(it.weather, it.updatedAtMillis, nowMillis) }
            .fold(null as Row?) { best, r -> if (best == null || r.updatedAtMillis > best.updatedAtMillis) r else best }
}
