package dev.pi0trdotsys.homebrewweather.widget

/**
 * The sun about to set or rise — "zachód za 40 min" — as a widget exception.
 *
 * Sunrise and sunset are not news for 23 hours of the day, and the widget's
 * rule is to say only what is different from an ordinary day. In the hour
 * before the sun goes down or comes up, it is exactly the thing you'd want
 * to know (the last of the light for a walk, the first for a run), so that is
 * when the meta line says it, and not before.
 *
 * Pure — covered by SunEventsTest.
 */
object SunEvents {

    /** How close an event has to be to be worth a line. */
    const val WITHIN_MINUTES = 60

    enum class Kind { SUNRISE, SUNSET }

    data class Next(val kind: Kind, val minutes: Int)

    /**
     * The nearest sunrise or sunset still ahead of [nowLocalIso]
     * ("2026-11-17T16:05", the location's own clock) if it is at most
     * [WITHIN_MINUTES] away; null otherwise, and when the forecast has no sun
     * times (an old cache, polar day or night).
     */
    fun next(weather: WeatherApi.WeatherData, nowLocalIso: String): Next? {
        val now = LocalIso.minutes(nowLocalIso) ?: return null
        return weather.daily.take(2)
            .flatMap { d ->
                listOfNotNull(
                    d.sunrise?.let { it to Kind.SUNRISE },
                    d.sunset?.let { it to Kind.SUNSET },
                )
            }
            .mapNotNull { (iso, kind) -> LocalIso.minutes(iso)?.let { Next(kind, (it - now).toInt()) } }
            .filter { it.minutes in 0..WITHIN_MINUTES }
            .minByOrNull { it.minutes }
    }

    fun text(next: Next, texts: Texts): String = when (next.kind) {
        Kind.SUNSET -> texts.sunsetIn(next.minutes)
        Kind.SUNRISE -> texts.sunriseIn(next.minutes)
    }
}
