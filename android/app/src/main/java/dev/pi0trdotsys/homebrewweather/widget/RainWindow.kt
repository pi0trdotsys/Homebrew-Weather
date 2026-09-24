package dev.pi0trdotsys.homebrewweather.widget

/**
 * "When does it start raining, and when does it stop" — derived from the
 * hourly forecast and phrased as one short sentence.
 *
 * ## Why this exists
 *
 * It is the most useful thing a weather widget can say, and the widget never
 * said it. It showed a current-hour rain percentage, a 4-day rain sparkline, a
 * 4-day rain maximum and four per-day rain percentages — four readouts of the
 * same quantity — and still left the user to work out whether to take an
 * umbrella at 3pm. One sentence ("deszcz 15:00-19:00") replaces all of that on
 * a day it matters, and says nothing on a day it doesn't.
 *
 * Mirrored in src/lib/rain-window.ts for the web dashboard; keep the two rules
 * identical. Covered by RainWindowTest.
 *
 * ## The rule
 *
 * - An hour is **wet** when its precipitation probability is at least
 *   [LIKELY_POP]. That is deliberately "more likely than not": a sentence that
 *   names a start time reads as a prediction, and announcing rain for a 30%
 *   hour would be wrong most of the time.
 * - It is also wet, at the current hour only, if the *current* conditions are
 *   precipitation — it is raining now regardless of what the hourly
 *   probability for this slot says.
 * - The window is the first run of wet hours that starts within
 *   [START_HORIZON_HOURS]; it ends at the first dry hour after that (or is
 *   open-ended if the data runs out first).
 * - Its kind is the most severe of the hours in it: thunder over snow over rain.
 */
object RainWindow {

    const val LIKELY_POP = 50

    /** How far ahead a window may *start* and still be worth a sentence. Past
     * this it's a tomorrow problem, and the 4-day grid already covers tomorrow. */
    const val START_HORIZON_HOURS = 12

    enum class Kind { RAIN, SNOW, THUNDER }

    data class Window(
        val kind: Kind,
        /** Hours from now until it starts; 0 = the current hour. */
        val startsInHours: Int,
        /** Wall-clock start, "15:00", in the location's local time. */
        val start: String,
        /** Wall-clock end, or null if still wet at the end of the forecast data. */
        val end: String?,
        /** Precipitation is observed right now, not just forecast. */
        val ongoing: Boolean,
    )

    fun find(
        hourly: List<WeatherApi.HourlyEntry>,
        currentWeatherCode: Int,
    ): Window? {
        if (hourly.isEmpty()) return null
        val nowIsWet = isPrecip(currentWeatherCode)

        fun wet(i: Int): Boolean =
            hourly[i].precipitationProbability >= LIKELY_POP || (i == 0 && nowIsWet)

        val startIdx = (0 until minOf(hourly.size, START_HORIZON_HOURS + 1)).firstOrNull(::wet)
            ?: return null
        var endIdx = startIdx
        while (endIdx < hourly.size && wet(endIdx)) endIdx++

        val span = hourly.subList(startIdx, endIdx)
        val codes = span.map { it.weatherCode } + if (startIdx == 0 && nowIsWet) listOf(currentWeatherCode) else emptyList()
        val kind = when {
            codes.any { Wmo.wmoToKind(it) == "thunder" } -> Kind.THUNDER
            codes.any { Wmo.wmoToKind(it) == "snow" } -> Kind.SNOW
            else -> Kind.RAIN
        }

        return Window(
            kind = kind,
            startsInHours = startIdx,
            start = clock(hourly[startIdx].time),
            end = hourly.getOrNull(endIdx)?.let { clock(it.time) },
            ongoing = startIdx == 0 && nowIsWet,
        )
    }

    /**
     * Widget form — as short as it can be while still standing on its own,
     * because it shares the hero row with the temperature.
     *
     * "deszcz 15:00-19:00", "pada do ~19:00", "burza od 15:00", "śnieg wkrótce".
     */
    fun short(w: Window): String {
        val noun = noun(w.kind)
        return when {
            w.ongoing && w.end != null -> "${ongoingVerb(w.kind)} do ~${w.end}"
            w.ongoing -> "${ongoingVerb(w.kind)} na dłużej"
            w.startsInHours == 0 && w.end != null -> "$noun wkrótce, do ~${w.end}"
            w.startsInHours == 0 -> "$noun wkrótce"
            w.end != null -> "$noun ${w.start}-${w.end}"
            else -> "$noun od ${w.start}"
        }
    }

    /**
     * Full-sentence form for the app's hero and for notifications, where
     * there's room to say it the way a person would.
     */
    fun long(w: Window): String {
        val noun = noun(w.kind)
        return when {
            w.ongoing && w.end != null -> "${ongoingVerb(w.kind)}, przestanie ok. ${w.end}"
            w.ongoing -> "${ongoingVerb(w.kind)} i nie zanosi się na koniec"
            w.startsInHours == 0 && w.end != null -> "$noun w ciągu godziny, do ok. ${w.end}"
            w.startsInHours == 0 -> "$noun w ciągu godziny"
            w.end != null -> "$noun od ${w.start} do ok. ${w.end}"
            else -> "$noun od ${w.start}"
        }
    }

    private fun noun(kind: Kind) = when (kind) {
        Kind.RAIN -> "deszcz"
        Kind.SNOW -> "śnieg"
        Kind.THUNDER -> "burza"
    }

    private fun ongoingVerb(kind: Kind) = when (kind) {
        Kind.RAIN -> "pada"
        Kind.SNOW -> "sypie"
        Kind.THUNDER -> "grzmi"
    }

    private fun isPrecip(code: Int): Boolean =
        Wmo.wmoToKind(code).let { it == "rain" || it == "snow" || it == "thunder" }

    /** "2026-09-24T15:00" -> "15:00". Falls back to the raw string rather
     * than throwing on an unexpected shape. */
    private fun clock(iso: String): String =
        iso.substringAfter('T', iso).take(5)
}
