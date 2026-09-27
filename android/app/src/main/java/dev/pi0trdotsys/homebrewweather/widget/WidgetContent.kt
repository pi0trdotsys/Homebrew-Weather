package dev.pi0trdotsys.homebrewweather.widget

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the widget says this render, decided before anything is sized or drawn.
 *
 * ## Show the exceptions, not the state
 *
 * At 368x176dp the widget used to show roughly thirty readouts, several of them
 * the same fact more than once: rain probability three times (a current-hour
 * figure, a 4-day sparkline with its maximum, and a figure under every day),
 * each day's min/max twice (as text and as a range bar), data freshness twice
 * (an always-green dot and a clock with seconds). On an ordinary dry day almost
 * all of that is saying "nothing to see", loudly.
 *
 * In [WidgetDensity.STANDARD] every optional readout now has a condition for
 * appearing, and the conditions are all the same question — *is this
 * different from an unremarkable day?* Rain gets a sentence when it's coming
 * and per-day figures only where they're likely; air quality only once it's
 * worse than "good"; wind and humidity only at extremes; feels-like only when
 * it genuinely differs from the thermometer. An ordinary day comes out calm,
 * and on a day that isn't ordinary the unusual thing is the only thing
 * competing with the temperature, so it's the thing you notice.
 *
 * Nothing is lost: [WidgetDensity.FULL] keeps every readout on permanently.
 *
 * Pure logic, no Android — covered by WidgetContentRulesTest.
 */
data class WidgetContent(
    /** The line under the hero temperature: a rain sentence, else the condition. */
    val heroLine: String,
    /** AQI value to show in the stat column, or null to hide it. */
    val aqi: Int?,
    val showSync: Boolean,
    val showOnlineDot: Boolean,
    val showSparkline: Boolean,
    val showPopMax: Boolean,
    val showPopRow: Boolean,
    /** Per-day rain text, 4 entries; blank for a day that doesn't merit one.
     * A likely-rain window ("▽ 8–18") where the day has one, else a chance. */
    val dayPop: List<String>,
    /** How each [dayPop] entry is coloured: a storm must stand out from rain. */
    val dayPopStyle: List<PopStyle>,
    /** Index into `weather.daily` of the grid's first column: 0, or 1 in the
     * evening (see [EVENING_HOUR]). */
    val dayOffset: Int,
    /** Label of the grid's first column: "dziś", or "jutro" in the evening. */
    val firstDayLabel: String,
    val showBars: Boolean,
    /** Secondary conditions line, or null to hide the row entirely. */
    val metaLine: String?,
    val showFooter: Boolean,
) {
    val showStats: Boolean get() = aqi != null || showSync || showSparkline || showPopMax

    /** The optional rows this content wants, for the size solver. */
    fun rows() = WidgetMetrics.Rows(
        meta = metaLine != null,
        footer = showFooter,
        pop = showPopRow,
        bars = showBars,
        stats = showStats,
        heroLineCells = heroLine.length,
        metaCells = metaLine?.length ?: 0,
        dayLabelCells = firstDayLabel.length,
    )

    /** Colour of a per-day rain entry. */
    enum class PopStyle { NONE, CHANCE, RAIN, SNOW, THUNDER }

    companion object {
        /** A day's rain figure is worth showing from here up. Below it the
         * per-day "▽ 12%" readouts were noise — in a dry climate, four of
         * them saying "no" on almost every day. */
        const val DAY_POP_MIN = 30

        /** US AQI "moderate" starts at 51; "good" is not news. */
        const val AQI_STANDARD_MIN = 51

        /** "Unhealthy for sensitive groups" starts at 101 — the point at which
         * even the minimal widget should say something. */
        const val AQI_MINIMAL_MIN = 101

        /** Feels-like is only information when it disagrees with the thermometer. */
        const val FEELS_DELTA = 3

        const val WINDY_KMH = 30
        const val HUMID_PCT = 90
        const val DRY_PCT = 20

        /**
         * From this local hour the grid starts at tomorrow.
         *
         * By evening today's column is history: its max was hours ago and its
         * min was this morning, yet it sat highlighted as the current day — at
         * 23:26 it still said 26°/22°. What's left of today (the temperature
         * now, and any rain still to come tonight) is the hero's job, so the
         * grid moves on to the days that are still ahead.
         */
        const val EVENING_HOUR = 18

        fun build(
            weather: WeatherApi.WeatherData,
            density: WidgetDensity,
            offline: Boolean,
            texts: Texts = PlTexts,
        ): WidgetContent {
            val nowKind = Wmo.wmoToKind(weather.currentWeatherCode).let {
                if (!weather.isDay && (it == "sun" || it == "partly")) "moon" else it
            }
            // Today's rain only; any other day's goes under its own column.
            val window = RainWindow.today(weather.hourly, weather.currentWeatherCode)
            val base = window?.let { RainWindow.short(it, texts) } ?: texts.kindLabel(nowKind)

            val dayOffset = dayOffset(weather)
            val days = weather.daily.drop(dayOffset).take(4)
            val full = density == WidgetDensity.FULL
            val minimal = density == WidgetDensity.MINIMAL

            val heroLine = if (full && weather.currentPrecipitationProbability >= 0) {
                "$base · ${texts.heroPop(weather.currentPrecipitationProbability)}"
            } else {
                base
            }

            val aqi = weather.usAqi.takeIf { it >= 0 }?.takeIf {
                when (density) {
                    WidgetDensity.FULL -> true
                    WidgetDensity.STANDARD -> it >= AQI_STANDARD_MIN
                    WidgetDensity.MINIMAL -> it >= AQI_MINIMAL_MIN
                }
            }

            // Minimal keeps the windows and drops only the chances: when rain
            // moved from the hero to its day's column, a minimal widget would
            // otherwise have lost tomorrow's rain altogether — and "when will
            // it rain" is the one thing even minimal is meant to say.
            val none = "" to PopStyle.NONE
            fun chance(p: Int) = if (full || p >= DAY_POP_MIN) "▽ $p%" to PopStyle.CHANCE else none
            fun windowCell(w: RainWindow.DayWindow) = "▽ ${RainWindow.dayShort(w, texts)}" to when (w.kind) {
                RainWindow.Kind.RAIN -> PopStyle.RAIN
                RainWindow.Kind.SNOW -> PopStyle.SNOW
                RainWindow.Kind.THUNDER -> PopStyle.THUNDER
            }
            val cells = (0 until 4).map { i ->
                val day = days.getOrNull(i) ?: return@map none
                val isToday = dayOffset == 0 && i == 0
                if (minimal) {
                    if (isToday) return@map none
                    return@map RainWindow.forDay(weather.hourly, day.date)?.let(::windowCell) ?: none
                }
                if (isToday) {
                    // The hero owns today's rain. Without a window there, the
                    // column may still carry a chance, but only of the hours
                    // still ahead: the daily max also covers a morning shower
                    // that's already over.
                    if (window != null) return@map none
                    return@map chance(remainingTodayPop(weather) ?: day.precipitationProbabilityMax)
                }
                RainWindow.forDay(weather.hourly, day.date)?.let { return@map windowCell(it) }
                chance(day.precipitationProbabilityMax)
            }
            val dayPop = cells.map { it.first }
            val showPopRow = dayPop.any { it.isNotEmpty() }

            return WidgetContent(
                heroLine = heroLine,
                aqi = aqi,
                showSync = full,
                // Freshness is status, not weather: only worth ink when it's
                // bad. The status banner already says "stale" / "offline" in
                // words; the dot stays only as the persistent offline marker.
                showOnlineDot = full || offline,
                showSparkline = full && days.any { it.precipitationProbabilityMax > 0 },
                showPopMax = full,
                showPopRow = showPopRow,
                dayPop = dayPop,
                dayPopStyle = cells.map { it.second },
                dayOffset = dayOffset,
                firstDayLabel = if (dayOffset == 0) texts.today else texts.tomorrow,
                // A range bar restates the two numbers printed above it. On
                // real data from a settled spell (30-33° highs, 18-21° lows)
                // the four bars came out identical — ink that says nothing.
                showBars = full,
                metaLine = when (density) {
                    WidgetDensity.FULL -> fullMeta(weather, texts)
                    WidgetDensity.STANDARD -> notableMeta(weather, texts)
                    WidgetDensity.MINIMAL -> null
                },
                showFooter = !minimal,
            )
        }

        /**
         * 1 from [EVENING_HOUR] on, when there's a fifth day to fill the grid
         * with; otherwise 0. The hour is the location's, read off the hourly
         * data's first entry (the current hour), same frame as everything else.
         */
        fun dayOffset(weather: WeatherApi.WeatherData): Int {
            val hour = weather.hourly.firstOrNull()?.time
                ?.substringAfter('T', "")?.take(2)?.toIntOrNull() ?: return 0
            return if (hour >= EVENING_HOUR && weather.daily.size >= 5) 1 else 0
        }

        /** Highest hourly rain chance over what's left of today, or null
         * without hourly data (caches written before it existed). */
        private fun remainingTodayPop(weather: WeatherApi.WeatherData): Int? {
            val date = weather.hourly.firstOrNull()?.time?.substringBefore('T') ?: return null
            return weather.hourly.takeWhile { it.time.startsWith(date) }
                .maxOfOrNull { it.precipitationProbability }
        }

        /** Only the secondary conditions that are out of the ordinary, or null. */
        fun notableMeta(weather: WeatherApi.WeatherData, texts: Texts = PlTexts): String? {
            val parts = mutableListOf<String>()
            val t = weather.currentTemperature
            val feels = weather.apparentTemperature
            if (!t.isNaN() && !feels.isNaN() && abs(feels - t) >= FEELS_DELTA) {
                parts += texts.feels(feels.roundToInt())
            }
            val wind = weather.windSpeedKmh
            if (!wind.isNaN() && wind >= WINDY_KMH) parts += texts.wind(wind.roundToInt())
            val hum = weather.humidityPercent
            when {
                hum >= HUMID_PCT -> parts += texts.humid(hum)
                hum in 0..DRY_PCT -> parts += texts.dry(hum)
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }

        /** Everything, always — the pre-standard-mode meta line. */
        fun fullMeta(weather: WeatherApi.WeatherData, texts: Texts = PlTexts): String? = texts.fullMeta(
            feels = weather.apparentTemperature.takeIf { !it.isNaN() }?.roundToInt(),
            humidity = weather.humidityPercent.takeIf { it >= 0 },
            windKmh = weather.windSpeedKmh.takeIf { !it.isNaN() }?.roundToInt(),
        )
    }
}
