package dev.pi0trdotsys.homebrewweather.widget

/**
 * Notification copy for the [Tone.CLEAN] and [Tone.SIGMA] voices: the same
 * alerts as [RudeNotifications], saying what's happening and what to do about
 * it, without the insults. Used for the separate threshold alerts (when the
 * morning brief is off). The brief and the rain-soon alert are factual in every
 * tone and only *add* a rude closing line in [Tone.RUDE].
 */
object PlainNotifications {

    fun highTemp(city: String, tempC: Int): String =
        "$city: $tempC°C — upał. Pij wodę i szukaj cienia."

    fun lowTemp(city: String, tempC: Int): String =
        "$city: $tempC°C — zimno. Ubierz się cieplej."

    fun swing(city: String, todayMax: Int, tomorrowMax: Int, warming: Boolean): String =
        "$city: jutro $tomorrowMax° zamiast $todayMax° — wyraźnie ${if (warming) "cieplej" else "chłodniej"}."

    fun aqi(city: String, aqiValue: Int, label: String): String =
        "$city: AQI $aqiValue ($label) — ogranicz wysiłek na zewnątrz."
}
