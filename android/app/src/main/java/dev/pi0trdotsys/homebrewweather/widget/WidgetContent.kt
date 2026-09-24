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
    /** Per-day rain text, 4 entries; blank for a day that doesn't merit one. */
    val dayPop: List<String>,
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
    )

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

        fun build(
            weather: WeatherApi.WeatherData,
            density: WidgetDensity,
            offline: Boolean,
        ): WidgetContent {
            val nowKind = Wmo.wmoToKind(weather.currentWeatherCode).let {
                if (!weather.isDay && (it == "sun" || it == "partly")) "moon" else it
            }
            val window = RainWindow.find(weather.hourly, weather.currentWeatherCode)
            val base = window?.let(RainWindow::short) ?: kindLabel(nowKind)

            val pops = weather.daily.take(4).map { it.precipitationProbabilityMax }
            val full = density == WidgetDensity.FULL
            val minimal = density == WidgetDensity.MINIMAL

            val heroLine = if (full && weather.currentPrecipitationProbability >= 0) {
                "$base · opady ${weather.currentPrecipitationProbability}%"
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

            val showPopRow = when (density) {
                WidgetDensity.FULL -> pops.isNotEmpty()
                WidgetDensity.STANDARD -> pops.any { it >= DAY_POP_MIN }
                WidgetDensity.MINIMAL -> false
            }
            val dayPop = (0 until 4).map { i ->
                val p = pops.getOrNull(i) ?: return@map ""
                if (full || p >= DAY_POP_MIN) "▽ $p%" else ""
            }

            return WidgetContent(
                heroLine = heroLine,
                aqi = aqi,
                showSync = full,
                // Freshness is status, not weather: only worth ink when it's
                // bad. The status banner already says "stale" / "offline" in
                // words; the dot stays only as the persistent offline marker.
                showOnlineDot = full || offline,
                showSparkline = full && (pops.maxOrNull() ?: 0) > 0,
                showPopMax = full,
                showPopRow = showPopRow,
                dayPop = dayPop,
                // A range bar restates the two numbers printed above it. On
                // real data from a settled spell (30-33° highs, 18-21° lows)
                // the four bars came out identical — ink that says nothing.
                showBars = full,
                metaLine = when (density) {
                    WidgetDensity.FULL -> fullMeta(weather)
                    WidgetDensity.STANDARD -> notableMeta(weather)
                    WidgetDensity.MINIMAL -> null
                },
                showFooter = !minimal,
            )
        }

        /** Only the secondary conditions that are out of the ordinary, or null. */
        fun notableMeta(weather: WeatherApi.WeatherData): String? {
            val parts = mutableListOf<String>()
            val t = weather.currentTemperature
            val feels = weather.apparentTemperature
            if (!t.isNaN() && !feels.isNaN() && abs(feels - t) >= FEELS_DELTA) {
                parts += "odczuwalna ${feels.roundToInt()}°"
            }
            val wind = weather.windSpeedKmh
            if (!wind.isNaN() && wind >= WINDY_KMH) parts += "wiatr ${wind.roundToInt()} km/h"
            val hum = weather.humidityPercent
            when {
                hum >= HUMID_PCT -> parts += "wilgotno $hum%"
                hum in 0..DRY_PCT -> parts += "sucho $hum%"
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }

        /** Everything, always — the pre-standard-mode meta line, unchanged. */
        fun fullMeta(weather: WeatherApi.WeatherData): String? {
            val parts = mutableListOf<String>()
            if (!weather.apparentTemperature.isNaN()) parts += "feels ${weather.apparentTemperature.roundToInt()}°"
            if (weather.humidityPercent >= 0) parts += "hum ${weather.humidityPercent}%"
            if (!weather.windSpeedKmh.isNaN()) parts += "wind ${weather.windSpeedKmh.roundToInt()}km/h"
            return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
        }

        /**
         * Short Polish condition label for the hero line.
         *
         * Two of these used to be "częściowo" and "noc", which only read as
         * conditions behind the old "now · " prefix ("now · częściowo"). Standing
         * alone under the temperature they said "partially" and "night", so they
         * are now the words a forecast would actually use.
         */
        fun kindLabel(kind: String): String = when (kind) {
            "sun" -> "słonecznie"
            "partly" -> "przejaśnienia"
            "cloud" -> "pochmurno"
            "fog" -> "mgła"
            "rain" -> "deszcz"
            "snow" -> "śnieg"
            "thunder" -> "burza"
            "moon" -> "pogodna noc"
            else -> kind
        }
    }
}
