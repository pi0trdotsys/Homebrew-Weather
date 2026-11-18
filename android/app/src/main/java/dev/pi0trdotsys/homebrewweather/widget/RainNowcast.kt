package dev.pi0trdotsys.homebrewweather.widget

import kotlin.math.roundToInt

/**
 * Rain by the minute: "deszcz za 12 min", from Open-Meteo's 15-minute
 * precipitation forecast for the next hours.
 *
 * ## Why this exists
 *
 * Everything the widget said about rain came from the hourly forecast: a window
 * "15–19", and a heads-up that could only say "within the hour". The question on
 * the way out of the door is "do I have ten minutes?" and an hourly step can't
 * answer it. This reads the 15-minute steps instead, and says it in minutes.
 *
 * ## The rule
 *
 * A step is *wet* when it holds at least [WET_MM] (0.1 mm in 15 minutes, about
 * 0.4 mm/h: drizzle you'd notice). The step the clock is in is the last one that
 * started at or before now.
 *
 * - **Raining now** (that step is wet): how long until the first dry step after
 *   it, or null if it is still wet when the data ends.
 * - **Dry now**: how long until the first wet step, if it is within [HORIZON_MIN].
 *
 * Minutes are exact here and rounded for people by [roundForSpeech]. Mirrored in
 * src/lib/rain-nowcast.ts; keep the two identical (shared cases in
 * testdata/parity). Covered by RainNowcastTest.
 */
object RainNowcast {

    const val WET_MM = 0.1
    const val STEP_MIN = 15

    /** How many 15-minute steps are asked for: three hours, comfortably past [HORIZON_MIN]. */
    const val STEPS_REQUESTED = 12

    /** Only rain this close is a "starts in N minutes"; further out the hourly window says it. */
    const val HORIZON_MIN = 120

    /** [time] is the local start of the 15 minutes ("2026-11-17T16:15"); [mm] what falls in them. */
    data class Step(val time: String, val mm: Double)

    /**
     * [startsInMinutes] is set only when it is dry now and rain is coming within
     * the horizon; [endsInMinutes] only while it is raining and the data shows it
     * stopping. [totalMm] is what falls in that wet run (the whole data's worth
     * if it never stops).
     */
    data class Result(
        val ongoing: Boolean,
        val startsInMinutes: Int?,
        val endsInMinutes: Int?,
        val totalMm: Double,
    ) {
        val anyRain: Boolean get() = ongoing || startsInMinutes != null
    }

    /** What the data says for [nowLocalIso] ("2026-11-17T16:05"), or null with no usable steps around that time. */
    fun analyse(steps: List<Step>, nowLocalIso: String): Result? {
        val now = LocalIso.minutes(nowLocalIso) ?: return null
        val timed = steps.mapNotNull { s -> LocalIso.minutes(s.time)?.let { it to s.mm } }.sortedBy { it.first }
        if (timed.isEmpty()) return null
        // The step the clock is in: the last that started at or before now, and not over yet.
        val cur = timed.indexOfLast { it.first <= now }
        if (cur < 0 || now >= timed[cur].first + STEP_MIN) return null

        fun wet(i: Int) = timed[i].second >= WET_MM
        if (wet(cur)) {
            var end = cur
            while (end < timed.size && wet(end)) end++
            val total = (cur until end).sumOf { timed[it].second }
            val ends = if (end < timed.size) (timed[end].first - now).toInt() else null
            return Result(ongoing = true, startsInMinutes = null, endsInMinutes = ends, totalMm = total)
        }
        val start = (cur + 1 until timed.size).firstOrNull(::wet) ?: return Result(false, null, null, 0.0)
        val startsIn = (timed[start].first - now).toInt()
        if (startsIn > HORIZON_MIN) return Result(false, null, null, 0.0)
        var end = start
        while (end < timed.size && wet(end)) end++
        return Result(false, startsIn, null, (start until end).sumOf { timed[it].second })
    }

    /** Below this much rain in a wet run, a notification isn't worth it (a few drops). */
    const val NOTIFY_MIN_MM = 0.3

    /** The nowcast for [weather] at the real clock [nowMillis], or null without 15-minute data or an offset. */
    fun forWeather(weather: WeatherApi.WeatherData, nowMillis: Long): Result? {
        if (weather.nowcast.isEmpty()) return null
        val offset = weather.utcOffsetSeconds ?: return null
        return analyse(weather.nowcast, WeatherApi.localMinuteIso(nowMillis, offset))
    }

    /**
     * Minutes as a person says them: to the nearest 5, and "now" (0) for anything
     * under 3 — "za 2 min" would claim a precision the 15-minute steps don't have.
     */
    fun roundForSpeech(minutes: Int): Int = if (minutes < 3) 0 else (minutes / 5.0).roundToInt().coerceAtLeast(1) * 5
}
