package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningBriefTest {

    private val thresholds = MorningBrief.Thresholds(
        highEnabled = true, highThreshold = 30.0,
        lowEnabled = true, lowThreshold = 0.0,
        swingEnabled = true, swingThreshold = 8.0,
        aqiEnabled = true, aqiThreshold = 100.0,
    )

    private fun day(max: Double, min: Double, pop: Int = 0, code: Int = 0) =
        WeatherApi.DailyEntry("2026-09-24", code, max, min, pop)

    private val quiet = WeatherApi.WeatherData(
        isDay = true,
        currentWeatherCode = 0,
        currentTemperature = 22.0,
        usAqi = 30,
        daily = listOf(day(24.0, 14.0), day(25.0, 13.0)),
        hourly = (7..30).map { WeatherApi.HourlyEntry("2026-09-24T%02d:00".format(it % 24), 0, 0) },
    )

    private fun compose(w: WeatherApi.WeatherData, t: MorningBrief.Thresholds = thresholds, tail: String? = null) =
        MorningBrief.compose("Tolox", w, t, { "sensitive" }, tail)!!

    @Test
    fun `a quiet day is one line`() {
        val b = compose(quiet)
        assertEquals("Tolox · dziś", b.title)
        assertEquals("24°/14° · słonecznie", b.summary)
        assertEquals("24°/14° · słonecznie", b.full)
    }

    @Test
    fun `every crossed threshold is mentioned, rain first`() {
        val w = quiet.copy(
            usAqi = 142,
            daily = listOf(day(34.0, -1.0, 80, 61), day(24.0, 10.0)),
            hourly = listOf(0, 0, 80, 90, 0).mapIndexed { i, p ->
                WeatherApi.HourlyEntry("2026-09-24T%02d:00".format(7 + i), p, if (p >= 50) 61 else 0)
            },
        )
        val b = compose(w)
        assertEquals("34°/-1° · deszcz od 09:00 do ok. 11:00", b.summary)
        assertEquals(
            listOf(
                "34°/-1°",
                "deszcz od 09:00 do ok. 11:00",
                "upał, do 34°",
                "zimno, do -1°",
                "powietrze: AQI 142 (sensitive)",
                "jutro o 10° chłodniej",
            ).joinToString("\n"),
            b.full,
        )
    }

    @Test
    fun `disabled thresholds stay out of the brief`() {
        val w = quiet.copy(daily = listOf(day(34.0, 14.0), day(25.0, 13.0)))
        val off = thresholds.copy(highEnabled = false, swingEnabled = false)
        assertEquals("34°/14° · słonecznie", compose(w, off).full)
    }

    @Test
    fun `possible rain without a likely window is still mentioned`() {
        val w = quiet.copy(daily = listOf(day(24.0, 14.0, pop = 40), day(25.0, 13.0)))
        assertTrue(compose(w).full.contains("możliwe opady (40%)"))
    }

    @Test
    fun `tone tail goes last, after a blank line`() {
        assertEquals("24°/14° · słonecznie\n\nkawa, spodnie", compose(quiet, tail = "kawa, spodnie").full)
    }

    @Test
    fun `no forecast, no brief`() {
        assertNull(MorningBrief.compose("Tolox", quiet.copy(daily = emptyList()), thresholds, { "" }, null))
    }

    @Test
    fun `due once, in the morning window only`() {
        assertFalse("before the hour", MorningBrief.isDue(6, 7, alreadySentToday = false))
        assertTrue(MorningBrief.isDue(7, 7, alreadySentToday = false))
        assertTrue(MorningBrief.isDue(9, 7, alreadySentToday = false))
        assertFalse("too late to be a morning brief", MorningBrief.isDue(10, 7, alreadySentToday = false))
        assertFalse("already sent", MorningBrief.isDue(8, 7, alreadySentToday = true))
    }

    @Test
    fun `window widens so a long refresh interval cannot skip it`() {
        // A 6h interval could otherwise land at 06:30 and 12:30, both outside 07-10.
        assertTrue(MorningBrief.isDue(12, 7, alreadySentToday = false, refreshMinutes = 360))
    }
}
