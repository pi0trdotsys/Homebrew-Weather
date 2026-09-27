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
        texts: Texts = PlTexts,
    ): Brief? {
        val today = weather.daily.getOrNull(0) ?: return null
        val tomorrow = weather.daily.getOrNull(1)
        val max = today.tempMax.roundToInt()
        val min = today.tempMin.roundToInt()

        val window = RainWindow.today(weather.hourly, weather.currentWeatherCode)
        // With a rain sentence coming, the day's condition word would only
        // repeat it ("deszcz · deszcz od 09:00...").
        val dayLine = if (window != null) "$max°/$min°"
        else "$max°/$min° · ${texts.kindLabel(Wmo.wmoToKind(today.weatherCode))}"

        // Ordered by how much each one should change what you do today.
        val items = mutableListOf<String>()
        when {
            window != null -> items += RainWindow.long(window, texts)
            today.precipitationProbabilityMax >= POSSIBLE_RAIN_POP ->
                items += texts.possibleRain(today.precipitationProbabilityMax)
        }
        if (thresholds.highEnabled && today.tempMax >= thresholds.highThreshold) items += texts.hot(max)
        if (thresholds.lowEnabled && today.tempMin <= thresholds.lowThreshold) items += texts.cold(min)
        if (thresholds.aqiEnabled && weather.usAqi >= 0 && weather.usAqi >= thresholds.aqiThreshold) {
            items += texts.air(weather.usAqi, aqiLabel(weather.usAqi))
        }
        if (thresholds.swingEnabled && tomorrow != null) {
            val delta = (tomorrow.tempMax - today.tempMax).roundToInt()
            if (abs(delta) >= thresholds.swingThreshold) items += texts.tomorrowSwing(delta)
        }

        val summary = listOfNotNull(dayLine, items.firstOrNull()).joinToString(" · ")
        val full = (listOf(dayLine) + items).joinToString("\n") + (tail?.let { "\n\n$it" } ?: "")
        return Brief(title = texts.briefTitle(cityName), summary = summary, full = full)
    }
}

/**
 * One evening notification about tomorrow — the question that prompted
 * pinning rain to its day in the first place: at 23:26 the widget said
 * "deszcz 08:00-18:00" and it wasn't clear which day that meant. This says it
 * outright, the evening before: "jutro 23°/20° · burza 7–19".
 *
 * Opt-in (see [CapacitorStorage.eveningEnabled]), same due-window rule as the
 * morning brief. Pure logic — covered by MorningBriefTest.
 */
object EveningPreview {

    /** Same window rule as the brief. It can't spill past midnight — the
     * hour wraps to 0, which is below [eveningHour] — so a preview is never
     * delivered once "tomorrow" has become today. */
    fun isDue(hourNow: Int, eveningHour: Int, alreadySentToday: Boolean, refreshMinutes: Long = 30): Boolean =
        MorningBrief.isDue(hourNow, eveningHour, alreadySentToday, refreshMinutes)

    fun compose(cityName: String, weather: WeatherApi.WeatherData, texts: Texts = PlTexts): MorningBrief.Brief? {
        val today = weather.hourly.firstOrNull()?.time?.substringBefore('T')
            ?: weather.daily.firstOrNull()?.date ?: return null
        val tomorrow = weather.daily.firstOrNull { it.date > today } ?: return null
        val max = tomorrow.tempMax.roundToInt()
        val min = tomorrow.tempMin.roundToInt()
        val window = RainWindow.forDay(weather.hourly, tomorrow.date)
        val weatherPart = when {
            window != null -> "${texts.rainNoun(window.kind)} ${RainWindow.dayShort(window, texts)}"
            tomorrow.precipitationProbabilityMax >= MorningBrief.POSSIBLE_RAIN_POP ->
                "${texts.kindLabel(Wmo.wmoToKind(tomorrow.weatherCode))} · ${texts.possibleRain(tomorrow.precipitationProbabilityMax)}"
            else -> texts.kindLabel(Wmo.wmoToKind(tomorrow.weatherCode))
        }
        val line = "$max°/$min° · $weatherPart"
        return MorningBrief.Brief(title = texts.eveningTitle(cityName), summary = line, full = line)
    }
}

/**
 * "Przestanie padać o 16" — the other half of the rain heads-up. When it has
 * been raining for a while, the useful news is when it stops, not that it's
 * still raining. Pure logic — covered by RainWindowTest.
 */
object RainStop {

    /** A spell shorter than this was a shower; nobody's waiting for it to end. */
    const val MIN_SPELL_HOURS = 2

    /** Warn when the first dry hour is at most this far away. */
    const val STOP_SOON_HOURS = 1

    /**
     * Whether [w] is a rain spell about to end that deserves a notification:
     * raining now, it's been raining for at least [MIN_SPELL_HOURS], and the
     * first dry hour is within [STOP_SOON_HOURS].
     */
    fun isDue(w: RainWindow.Window?, rainingForHours: Int): Boolean {
        if (w == null || !w.ongoing) return false
        val endsIn = w.endsInHours ?: return false
        return endsIn in 1..STOP_SOON_HOURS && rainingForHours >= MIN_SPELL_HOURS
    }
}
