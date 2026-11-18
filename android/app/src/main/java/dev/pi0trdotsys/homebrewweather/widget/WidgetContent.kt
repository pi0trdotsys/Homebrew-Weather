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
    /** The 24-hour temperature curve (full density), which replaced the per-day range bars. */
    val showCurve: Boolean,
    /** Secondary conditions line, or null to hide the row entirely. */
    val metaLine: String?,
    /** The same readouts as [metaLine], structured for the pixel-icon meta line (empty when there is none). */
    val metaReadouts: List<Readout>,
    val showFooter: Boolean,
) {
    val showStats: Boolean get() = aqi != null || showSync || showSparkline || showPopMax

    /** The optional rows this content wants, for the size solver. */
    fun rows() = WidgetMetrics.Rows(
        meta = metaLine != null,
        footer = showFooter,
        pop = showPopRow,
        curve = showCurve,
        stats = showStats,
        heroLineCells = heroLine.length,
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

        /** Gusts from here are worth a mention on their own: this is where
         * umbrellas turn inside out and loose things start to move. */
        const val GUST_NOTABLE_KMH = 60

        /** "Very high" on the UV scale starts at 8 — below that the sun is
         * ordinary summer sun, and the widget stays quiet about it. */
        const val UV_NOTABLE = 8

        /** Rain still to come today that is worth a number. Under this it's a
         * drizzle or a shower, and the hero's own word ("mżawka") says enough. */
        const val RAIN_AMOUNT_NOTABLE_MM = 2.0
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
            nowMillis: Long = System.currentTimeMillis(),
            pollen: Boolean = true,
        ): WidgetContent {
            val nowKind = Wmo.wmoToKind(weather.currentWeatherCode).let {
                if (!weather.isDay && (it == "sun" || it == "partly")) "moon" else it
            }
            // Today's rain only; any other day's goes under its own column.
            val window = RainWindow.today(weather.hourly, weather.currentWeatherCode)
            // Rain by the minute beats the hourly window when it has something to say.
            val base = nowcastLine(weather, window?.kind ?: RainWindow.Kind.RAIN, texts, nowMillis)
                ?: window?.let { RainWindow.short(it, texts) }
                ?: texts.kindLabel(nowKind)

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
                // The full density's temperature row: the next 24 hours as a curve
                // with the rain against it, in place of four range bars that
                // restated the two numbers printed above them (on a settled spell
                // all four came out the same bar). Only when there are enough hours.
                showCurve = full && weather.hourly.count { !it.temperature.isNaN() } >= WidgetGraphics.CURVE_MIN_POINTS,
                metaReadouts = when (density) {
                    WidgetDensity.FULL -> fullMetaReadouts(weather, texts, nowMillis, pollen)
                    WidgetDensity.STANDARD -> notableReadouts(weather, texts, nowMillis, pollen).take(NOTABLE_MAX)
                    WidgetDensity.MINIMAL -> emptyList()
                },
                metaLine = when (density) {
                    WidgetDensity.FULL -> fullMeta(weather, texts, nowMillis, pollen)
                    WidgetDensity.STANDARD -> notableMeta(weather, texts, nowMillis, pollen)
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

        /** Rain starts within this many minutes: say it by the minute in the hero. */
        const val NOWCAST_START_WITHIN_MIN = 60

        /** Rain that is falling and stops within this many minutes: say when. */
        const val NOWCAST_END_WITHIN_MIN = 90

        /**
         * "deszcz za ok. 10 min" / "pada, koniec za ok. 20 min" from the 15-minute
         * forecast, or null when it has nothing worth the hero line: no 15-minute
         * data, no rain near, or a few drops.
         */
        fun nowcastLine(weather: WeatherApi.WeatherData, kind: RainWindow.Kind, texts: Texts, nowMillis: Long): String? {
            val r = RainNowcast.forWeather(weather, nowMillis) ?: return null
            if (r.totalMm < RainNowcast.NOTIFY_MIN_MM) return null
            val starts = r.startsInMinutes
            val ends = r.endsInMinutes
            return when {
                starts != null && starts <= NOWCAST_START_WITHIN_MIN ->
                    texts.nowcastStart(kind, RainNowcast.roundForSpeech(starts))
                r.ongoing && ends != null && ends <= NOWCAST_END_WITHIN_MIN ->
                    texts.nowcastOngoing(kind, RainNowcast.roundForSpeech(ends))
                else -> null
            }
        }

        /** The meta line holds at most this many ordinary readouts: it is one row wide. */
        const val NOTABLE_MAX = 3

        /**
         * The secondary conditions that are out of the ordinary, most useful first
         * (official warning, sun, pollen, rain amount, gusts, UV, feels-like, wind,
         * humidity) — uncapped; [notableMeta] and the widget keep the first
         * [NOTABLE_MAX].
         */
        fun notableReadouts(
            weather: WeatherApi.WeatherData,
            texts: Texts = PlTexts,
            nowMillis: Long = System.currentTimeMillis(),
            pollen: Boolean = true,
        ): List<Readout> {
            val out = mutableListOf<Readout>()
            officialReadout(weather, texts)?.let { out += it }
            sunReadout(weather, texts, nowMillis)?.let { out += it }
            if (pollen) pollenReadout(weather, texts)?.let { out += it }
            remainingTodayMm(weather)?.takeIf { it >= RAIN_AMOUNT_NOTABLE_MM }?.let { out += rainReadout(it, texts) }
            val gust = weather.windGustKmh
            if (!gust.isNaN() && gust >= GUST_NOTABLE_KMH) {
                out += Readout(MetaGlyph.WIND, "${gust.roundToInt()} km/h", texts.gusts(gust.roundToInt()))
            }
            todayUv(weather)?.takeIf { it >= UV_NOTABLE }?.let { out += Readout(MetaGlyph.SUN, "$it", texts.uv(it), MetaTint.AMBER) }
            val t = weather.currentTemperature
            val feels = weather.apparentTemperature
            if (!t.isNaN() && !feels.isNaN() && abs(feels - t) >= FEELS_DELTA) {
                out += Readout(MetaGlyph.THERMOMETER, "${feels.roundToInt()}°", texts.feels(feels.roundToInt()))
            }
            val wind = weather.windSpeedKmh
            if (!wind.isNaN() && wind >= WINDY_KMH) {
                out += Readout(MetaGlyph.WIND, "${wind.roundToInt()} km/h", texts.wind(wind.roundToInt()))
            }
            val hum = weather.humidityPercent
            when {
                hum >= HUMID_PCT -> out += Readout(MetaGlyph.DROP_HOLLOW, "$hum%", texts.humid(hum))
                hum in 0..DRY_PCT -> out += Readout(MetaGlyph.DROP_HOLLOW, "$hum%", texts.dry(hum))
            }
            return out
        }

        /** Only the secondary conditions that are out of the ordinary, or null. Most useful first, at most [NOTABLE_MAX]. */
        fun notableMeta(
            weather: WeatherApi.WeatherData,
            texts: Texts = PlTexts,
            nowMillis: Long = System.currentTimeMillis(),
            pollen: Boolean = true,
        ): String? = notableReadouts(weather, texts, nowMillis, pollen)
            .take(NOTABLE_MAX).takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.plain }

        private fun officialReadout(weather: WeatherApi.WeatherData, texts: Texts): Readout? =
            weather.official.firstOrNull()?.let {
                Readout(
                    MetaGlyph.WARNING, "${it.event.lowercase()} ${it.degree}°", texts.officialShort(it.event, it.degree),
                    if (it.degree >= 3) MetaTint.CRIMSON else MetaTint.AMBER,
                )
            }

        private fun pollenReadout(weather: WeatherApi.WeatherData, texts: Texts): Readout? =
            Pollen.top(weather.pollen)?.let {
                Readout(
                    MetaGlyph.POLLEN, texts.pollenName(it.species) + if (it.veryHigh) "!" else "",
                    texts.pollenShort(it.species, it.veryHigh), MetaTint.AMBER,
                )
            }

        private fun sunReadout(weather: WeatherApi.WeatherData, texts: Texts, nowMillis: Long): Readout? {
            val offset = weather.utcOffsetSeconds ?: return null
            val next = SunEvents.next(weather, WeatherApi.localMinuteIso(nowMillis, offset)) ?: return null
            val glyph = if (next.kind == SunEvents.Kind.SUNRISE) MetaGlyph.SUNRISE else MetaGlyph.SUNSET
            return Readout(glyph, texts.inMinutes(next.minutes), SunEvents.text(next, texts), MetaTint.AMBER)
        }

        private fun rainReadout(mm: Double, texts: Texts) = Readout(MetaGlyph.DROP, texts.mm(mm), texts.rainAmount(mm), MetaTint.CYAN)

        /** The local hour of the forecast's first entry, in the location's own frame. */
        private fun localHour(weather: WeatherApi.WeatherData): Int? =
            weather.hourly.firstOrNull()?.time?.substringAfter('T', "")?.take(2)?.toIntOrNull()

        /** Today's UV peak, or null when unknown — and null from the evening
         * on, when "UV 9" would be about a sun that has already set. */
        private fun todayUv(weather: WeatherApi.WeatherData): Int? {
            if ((localHour(weather) ?: 0) >= EVENING_HOUR) return null
            return weather.daily.firstOrNull()?.uvIndexMax?.takeIf { !it.isNaN() }?.roundToInt()
        }

        /** Rain still to fall today, mm — summed over the hours that are left,
         * not the whole day's total (which includes a morning that's over).
         * Null without any amounts (a cache from before they were kept). */
        fun remainingTodayMm(weather: WeatherApi.WeatherData): Double? {
            val date = weather.hourly.firstOrNull()?.time?.substringBefore('T') ?: return null
            val known = weather.hourly.takeWhile { it.time.startsWith(date) }
                .map { it.precipitationMm }.filter { !it.isNaN() }
            return if (known.isEmpty()) null else known.sum()
        }

        /** Everything, always — the pre-standard-mode meta line. */
        fun fullMeta(
            weather: WeatherApi.WeatherData,
            texts: Texts = PlTexts,
            nowMillis: Long = System.currentTimeMillis(),
            pollen: Boolean = true,
        ): String? = listOfNotNull(
            officialReadout(weather, texts)?.plain,
            sunReadout(weather, texts, nowMillis)?.plain,
            pollenReadout(weather, texts)?.plain.takeIf { pollen },
            fullReadouts(weather, texts),
        )
            .takeIf { it.isNotEmpty() }?.joinToString("  ·  ")

        /**
         * Every readout the full density shows, as icons: the exceptions first
         * (official warning, sun, pollen), then feels-like, humidity, wind with its
         * gusts folded in ("42/71 km/h"), UV and the rain still to come. The widget
         * keeps as many as fit ([MetaLineArt.layout]).
         */
        fun fullMetaReadouts(
            weather: WeatherApi.WeatherData,
            texts: Texts = PlTexts,
            nowMillis: Long = System.currentTimeMillis(),
            pollen: Boolean = true,
        ): List<Readout> {
            val out = mutableListOf<Readout>()
            officialReadout(weather, texts)?.let { out += it }
            sunReadout(weather, texts, nowMillis)?.let { out += it }
            if (pollen) pollenReadout(weather, texts)?.let { out += it }
            weather.apparentTemperature.takeIf { !it.isNaN() }?.roundToInt()?.let {
                out += Readout(MetaGlyph.THERMOMETER, "$it°", "$it°")
            }
            weather.humidityPercent.takeIf { it >= 0 }?.let { out += Readout(MetaGlyph.DROP_HOLLOW, "$it%", "$it%") }
            val wind = weather.windSpeedKmh.takeIf { !it.isNaN() }?.roundToInt()
            val gust = weather.windGustKmh.takeIf { !it.isNaN() }?.roundToInt()
            val windValue = when {
                wind != null && gust != null && gust > wind -> "$wind/$gust km/h"
                wind != null -> "$wind km/h"
                gust != null -> "$gust km/h"
                else -> null
            }
            windValue?.let { out += Readout(MetaGlyph.WIND, it, it) }
            todayUv(weather)?.let { out += Readout(MetaGlyph.SUN, "$it", "UV $it", MetaTint.AMBER) }
            remainingTodayMm(weather)?.takeIf { it >= RainWindow.MIN_SHOWN_MM }?.let { out += rainReadout(it, texts) }
            return out
        }

        private fun fullReadouts(weather: WeatherApi.WeatherData, texts: Texts): String? = texts.fullMeta(
            feels = weather.apparentTemperature.takeIf { !it.isNaN() }?.roundToInt(),
            humidity = weather.humidityPercent.takeIf { it >= 0 },
            windKmh = weather.windSpeedKmh.takeIf { !it.isNaN() }?.roundToInt(),
            gustKmh = weather.windGustKmh.takeIf { !it.isNaN() }?.roundToInt(),
            uv = todayUv(weather),
            rainMm = remainingTodayMm(weather),
        )
    }
}
