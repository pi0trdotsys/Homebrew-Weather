package dev.pi0trdotsys.homebrewweather.widget

import kotlin.math.roundToInt

/**
 * Storm, hail, violent wind and extreme heat, read off the forecast.
 *
 * Open-Meteo has no official warnings, so these are derived. Until now a storm
 * was only a "rain window" — it rode the rain toggle and shared its tone, so
 * with rain alerts off, or in the rude voice, the one forecast that is actually
 * about safety was optional and could be a joke. This is its own, always
 * serious, channel. Mirrored in src/lib/severe-weather.ts for the dashboard.
 *
 * ## The rule
 *
 * Index 0 is the current hour (read from the live readings), the rest the
 * hourly forecast.
 *
 * - **Hail** — WMO code 96 or 99 within [LOOKAHEAD_HOURS]. Supersedes a plain storm.
 * - **Storm** — WMO code 95..99 within [LOOKAHEAD_HOURS] and no hail.
 * - **Wind** — a gust of at least [SEVERE_GUST_KMH] within [LOOKAHEAD_HOURS]
 *   (roughly Beaufort 9, the strong-gale line).
 * - **Heat** — a temperature of at least [EXTREME_HEAT_C] within
 *   [HEAT_LOOKAHEAD_HOURS]; heat builds slowly, so it looks further ahead.
 *
 * Pure logic, no Android — covered by SevereWeatherTest.
 */
object SevereWeather {

    /** In order of severity — the order warnings are returned in. */
    enum class Kind { HAIL, STORM, WIND, HEAT }

    data class Warning(
        val kind: Kind,
        /** Hours until the first qualifying hour; 0 = now. */
        val startsInHours: Int,
        /** Peak gust (km/h) for wind, peak temperature (°C) for heat; 0 otherwise. */
        val peak: Int = 0,
    )

    const val LOOKAHEAD_HOURS = 6
    const val HEAT_LOOKAHEAD_HOURS = 12
    const val SEVERE_GUST_KMH = 75
    const val EXTREME_HEAT_C = 35

    fun find(weather: WeatherApi.WeatherData): List<Warning> {
        val hours = weather.hourly

        fun code(i: Int): Int? = when {
            i == 0 -> weather.currentWeatherCode
            else -> hours.getOrNull(i)?.weatherCode
        }
        fun gust(i: Int): Double = when {
            i == 0 -> maxOfKnown(weather.windGustKmh, hours.getOrNull(0)?.windGustKmh ?: Double.NaN)
            else -> hours.getOrNull(i)?.windGustKmh ?: Double.NaN
        }
        fun temp(i: Int): Double = when {
            i == 0 -> maxOfKnown(weather.currentTemperature, hours.getOrNull(0)?.temperature ?: Double.NaN)
            else -> hours.getOrNull(i)?.temperature ?: Double.NaN
        }

        val out = mutableListOf<Warning>()
        val near = 0..LOOKAHEAD_HOURS

        near.firstOrNull { (code(it) ?: 0) in HAIL_CODES }?.let { out += Warning(Kind.HAIL, it) }
        if (out.isEmpty()) {
            near.firstOrNull { (code(it) ?: 0) in 95..99 }?.let { out += Warning(Kind.STORM, it) }
        }

        near.firstOrNull { gust(it) >= SEVERE_GUST_KMH }?.let { start ->
            val peak = near.map { gust(it) }.filter { !it.isNaN() }.max()
            out += Warning(Kind.WIND, start, peak.roundToInt())
        }

        val far = 0..HEAT_LOOKAHEAD_HOURS
        far.firstOrNull { temp(it) >= EXTREME_HEAT_C }?.let { start ->
            val peak = far.map { temp(it) }.filter { !it.isNaN() }.max()
            out += Warning(Kind.HEAT, start, peak.roundToInt())
        }
        return out
    }

    private val HAIL_CODES = setOf(96, 99)

    /** The larger of two readings, ignoring an unknown (NaN) one; NaN if both are. */
    private fun maxOfKnown(a: Double, b: Double): Double = when {
        a.isNaN() -> b
        b.isNaN() -> a
        else -> maxOf(a, b)
    }
}
