package dev.pi0trdotsys.homebrewweather.widget

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One morning notification that summarises the day, instead of up to four
 * separate threshold alerts (high, low, day-to-day swing, air quality) arriving
 * whenever each one happened to trip.
 *
 * The thresholds the user set in ./settings are unchanged and still matter —
 * they now decide what the brief *mentions*. The one thing that stays a
 * separate, immediate notification is rain or a storm about to start (see
 * WeatherNotifier.checkRainSoon), because that's the only alert worth
 * interrupting for; everything else is better read once, with coffee.
 *
 * Turning the brief off in settings restores the separate alerts exactly as
 * they were. Pure logic, no Android — covered by MorningBriefTest.
 */
object MorningBrief {

    /** A brief only makes sense in the morning. If the phone was off and the
     * first refresh after [CapacitorStorage.briefHour] comes in the evening,
     * the day's brief is skipped rather than delivered as a "morning" summary
     * at 9pm. */
    const val WINDOW_HOURS = 3

    /** Days with at least this chance of rain get a mention even without a
     * likely rain window — the same bar the widget uses for per-day rain. */
    const val POSSIBLE_RAIN_POP = WidgetContent.DAY_POP_MIN

    data class Thresholds(
        val highEnabled: Boolean,
        val highThreshold: Double,
        val lowEnabled: Boolean,
        val lowThreshold: Double,
        val swingEnabled: Boolean,
        val swingThreshold: Double,
        val aqiEnabled: Boolean,
        val aqiThreshold: Double,
    )

    data class Brief(
        val title: String,
        /** Collapsed notification text: the day line and the most important item. */
        val summary: String,
        /** Expanded text: every line, plus the tone's closing line if any. */
        val full: String,
    )

    /**
     * The brief is evaluated on refreshes, not on a clock, so the window has
     * to be at least one refresh interval wide — with a 6-hour interval, a
     * fixed 3-hour window could fall entirely between two refreshes and the
     * brief would silently never arrive.
     */
    fun isDue(hourNow: Int, briefHour: Int, alreadySentToday: Boolean, refreshMinutes: Long = 30): Boolean {
        val window = maxOf(WINDOW_HOURS, (refreshMinutes / 60).toInt() + 1)
        return !alreadySentToday && hourNow >= briefHour && hourNow < briefHour + window
    }

    fun compose(
        cityName: String,
        weather: WeatherApi.WeatherData,
        thresholds: Thresholds,
        aqiLabel: (Int) -> String,
        tail: String?,
    ): Brief? {
        val today = weather.daily.getOrNull(0) ?: return null
        val tomorrow = weather.daily.getOrNull(1)
        val max = today.tempMax.roundToInt()
        val min = today.tempMin.roundToInt()

        val window = RainWindow.find(weather.hourly, weather.currentWeatherCode)
        // With a rain sentence coming, the day's condition word would only
        // repeat it ("deszcz · deszcz od 09:00...").
        val dayLine = if (window != null) "$max°/$min°"
        else "$max°/$min° · ${WidgetContent.kindLabel(Wmo.wmoToKind(today.weatherCode))}"

        // Ordered by how much each one should change what you do today.
        val items = mutableListOf<String>()
        when {
            window != null -> items += RainWindow.long(window)
            today.precipitationProbabilityMax >= POSSIBLE_RAIN_POP ->
                items += "możliwe opady (${today.precipitationProbabilityMax}%)"
        }
        if (thresholds.highEnabled && today.tempMax >= thresholds.highThreshold) items += "upał, do $max°"
        if (thresholds.lowEnabled && today.tempMin <= thresholds.lowThreshold) items += "zimno, do $min°"
        if (thresholds.aqiEnabled && weather.usAqi >= 0 && weather.usAqi >= thresholds.aqiThreshold) {
            items += "powietrze: AQI ${weather.usAqi} (${aqiLabel(weather.usAqi)})"
        }
        if (thresholds.swingEnabled && tomorrow != null) {
            val delta = (tomorrow.tempMax - today.tempMax).roundToInt()
            if (abs(delta) >= thresholds.swingThreshold) {
                items += if (delta > 0) "jutro o ${abs(delta)}° cieplej" else "jutro o ${abs(delta)}° chłodniej"
            }
        }

        val summary = listOfNotNull(dayLine, items.firstOrNull()).joinToString(" · ")
        val full = (listOf(dayLine) + items).joinToString("\n") + (tail?.let { "\n\n$it" } ?: "")
        return Brief(title = "$cityName · dziś", summary = summary, full = full)
    }
}
