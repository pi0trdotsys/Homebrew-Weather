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
 *
 * ## Today in the hero, other days under their own column
 *
 * The first version put *any* window starting within 12h under the hero
 * temperature. In the evening those 12 hours reach into tomorrow morning, so
 * at 23:26 the widget said "deszcz 08:00-18:00" right under "23°" — in the
 * slot that describes now, with nothing saying it meant tomorrow, while the
 * "pn" column carrying tomorrow's rain icon said nothing at all. The fix is to
 * pin every window to the day it belongs to: [today] feeds the hero and only
 * ever returns a window that starts today; [forDay] feeds the grid, so
 * tomorrow's rain appears as "▽ 8–18" under tomorrow. [find] keeps the plain
 * horizon rule for the rain-soon notification, where "in the next hour" must
 * still work across midnight.
 */
object RainWindow {

    const val LIKELY_POP = 50

    /** How far ahead [find] lets a window *start*. Only the rain-soon
     * notification uses it, and it only cares about the next hour or so. */
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
        /** Hours from now until the first dry hour, or null if the data runs out first. */
        val endsInHours: Int? = null,
    )

    /** One day's rain, for that day's grid column: hours of the day, 0..24. */
    data class DayWindow(
        val startHour: Int,
        /** First dry hour, 24 when it rains into midnight, null when the data
         * stops mid-day while it's still wet. */
        val endHour: Int?,
        /** More wet hours later the same day, after this run has ended. */
        val moreLater: Boolean,
        /** Most severe precipitation in the run, so the grid can colour a
         * storm differently from plain rain. */
        val kind: Kind = Kind.RAIN,
    )

    /** First window starting within [START_HORIZON_HOURS] of now, today or not. */
    fun find(
        hourly: List<WeatherApi.HourlyEntry>,
        currentWeatherCode: Int,
    ): Window? = findStartingBefore(hourly, currentWeatherCode, minOf(hourly.size, START_HORIZON_HOURS + 1))

    /**
     * First window that starts *today* — the location's today, i.e. the date
     * of the current hour in the hourly data. Null in the evening when the
     * next rain is tomorrow's; that one belongs to [forDay].
     */
    fun today(
        hourly: List<WeatherApi.HourlyEntry>,
        currentWeatherCode: Int,
    ): Window? {
        if (hourly.isEmpty()) return null
        val date = dateOf(hourly[0].time)
        val todayCount = hourly.indexOfFirst { dateOf(it.time) != date }.let { if (it < 0) hourly.size else it }
        return findStartingBefore(hourly, currentWeatherCode, todayCount)
    }

    /**
     * The first run of likely-wet hours on [date] ("2026-09-28"), for the
     * grid column of that day. Forecast only, so no "raining now" rule.
     */
    fun forDay(hourly: List<WeatherApi.HourlyEntry>, date: String): DayWindow? {
        val day = hourly.filter { dateOf(it.time) == date }
        val first = day.indexOfFirst { it.precipitationProbability >= LIKELY_POP }
        if (first < 0) return null
        var end = first
        while (end < day.size && day[end].precipitationProbability >= LIKELY_POP) end++
        val endHour = when {
            end < day.size -> hourOf(day[end].time)
            hourOf(day.last().time) == 23 -> 24
            else -> null
        }
        val moreLater = (end until day.size).any { day[it].precipitationProbability >= LIKELY_POP }
        return DayWindow(hourOf(day[first].time), endHour, moreLater, severest(day.subList(first, end).map { it.weatherCode }))
    }

    /**
     * Grid form, a few cells wide: "8–18", "8–10+" (it comes back later that
     * day), "od 20" / "from 20" (the data ends while it's still wet).
     */
    fun dayShort(d: DayWindow, texts: Texts = PlTexts): String = when (d.endHour) {
        null -> texts.dayOpen(d.startHour)
        else -> "${d.startHour}–${d.endHour}" + if (d.moreLater) "+" else ""
    }

    private fun findStartingBefore(
        hourly: List<WeatherApi.HourlyEntry>,
        currentWeatherCode: Int,
        startLimit: Int,
    ): Window? {
        if (hourly.isEmpty()) return null
        val nowIsWet = isPrecip(currentWeatherCode)

        fun wet(i: Int): Boolean =
            hourly[i].precipitationProbability >= LIKELY_POP || (i == 0 && nowIsWet)

        val startIdx = (0 until startLimit).firstOrNull(::wet)
            ?: return null
        var endIdx = startIdx
        while (endIdx < hourly.size && wet(endIdx)) endIdx++

        val span = hourly.subList(startIdx, endIdx)
        val codes = span.map { it.weatherCode } + if (startIdx == 0 && nowIsWet) listOf(currentWeatherCode) else emptyList()

        return Window(
            kind = severest(codes),
            startsInHours = startIdx,
            start = clock(hourly[startIdx].time),
            end = hourly.getOrNull(endIdx)?.let { clock(it.time) },
            ongoing = startIdx == 0 && nowIsWet,
            endsInHours = if (endIdx < hourly.size) endIdx else null,
        )
    }

    private fun severest(codes: List<Int>): Kind = when {
        codes.any { Wmo.wmoToKind(it) == "thunder" } -> Kind.THUNDER
        codes.any { Wmo.wmoToKind(it) == "snow" } -> Kind.SNOW
        else -> Kind.RAIN
    }

    /**
     * Widget form — as short as it can be while still standing on its own,
     * because it shares the hero row with the temperature.
     *
     * "deszcz 15–19", "pada do ~19", "burza od 15", "śnieg wkrótce".
     *
     * Whole hours, like the grid: every boundary is on the hour anyway, so
     * ":00" was two cells of nothing, which the solver now spends on type size.
     */
    fun short(w: Window, texts: Texts = PlTexts): String =
        texts.rainShort(w.kind, w.ongoing, w.startsInHours == 0, hourOf(w.start), w.end?.let { endHourLabel(it) })

    /**
     * Full-sentence form for notifications, where there's room to say it the
     * way a person would.
     */
    fun long(w: Window, texts: Texts = PlTexts): String =
        texts.rainLong(w.kind, w.ongoing, w.startsInHours == 0, w.start, w.end)

    private fun isPrecip(code: Int): Boolean =
        Wmo.wmoToKind(code).let { it == "rain" || it == "snow" || it == "thunder" }

    /** "2026-09-24T15:00" -> "15:00". Falls back to the raw string rather
     * than throwing on an unexpected shape. */
    private fun clock(iso: String): String =
        iso.substringAfter('T', iso).take(5)

    /** "2026-09-24T15:00" -> "2026-09-24". */
    private fun dateOf(iso: String): String = iso.substringBefore('T')

    /** "2026-09-24T08:00" or "08:00" -> 8. */
    private fun hourOf(isoOrClock: String): Int =
        isoOrClock.substringAfter('T', isoOrClock).take(2).toIntOrNull() ?: 0

    /** An end at midnight reads as "24" — "deszcz 21–0" says nothing. */
    private fun endHourLabel(clock: String): Int = hourOf(clock).let { if (it == 0) 24 else it }
}
