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

    @Test
    fun `english brief`() {
        val b = MorningBrief.compose("Tolox", quiet, thresholds, { "good" }, null, EnTexts)!!
        assertEquals("Tolox · today", b.title)
        assertEquals("24°/14° · sunny", b.summary)
    }

    // --- evening preview of tomorrow -----------------------------------------

    /** 21:00 on the 24th; tomorrow (25th) a storm 7-19. */
    private val evening = quiet.copy(
        daily = listOf(
            WeatherApi.DailyEntry("2026-09-24", 3, 26.0, 22.0, 0),
            WeatherApi.DailyEntry("2026-09-25", 95, 23.0, 20.0, 90),
        ),
        hourly = (21..48).map { h ->
            val wet = h - 24 in 7..18
            WeatherApi.HourlyEntry("2026-09-%02dT%02d:00".format(24 + h / 24, h % 24), if (wet) 80 else 0, if (wet) 95 else 3)
        },
    )

    @Test
    fun `evening preview names tomorrow and when it rains`() {
        val p = EveningPreview.compose("Estepona", evening)!!
        assertEquals("Estepona · jutro", p.title)
        assertEquals("23°/20° · burza 7–19", p.summary)
        val en = EveningPreview.compose("Estepona", evening, EnTexts)!!
        assertEquals("Estepona · tomorrow", en.title)
        assertEquals("23°/20° · storm 7–19", en.summary)
    }

    @Test
    fun `evening preview of a dry day is its condition`() {
        val dry = evening.copy(hourly = evening.hourly.map { it.copy(precipitationProbability = 0, weatherCode = 0) },
            daily = listOf(evening.daily[0], evening.daily[1].copy(weatherCode = 1, precipitationProbabilityMax = 0)))
        assertEquals("23°/20° · przejaśnienia", EveningPreview.compose("X", dry)!!.summary)
    }

    @Test
    fun `evening preview is due in the evening window, never after midnight`() {
        assertTrue(EveningPreview.isDue(20, 20, alreadySentToday = false))
        assertFalse(EveningPreview.isDue(19, 20, alreadySentToday = false))
        assertFalse("past midnight it would be about today", EveningPreview.isDue(0, 22, alreadySentToday = false))
        assertFalse(EveningPreview.isDue(21, 20, alreadySentToday = true))
    }

    // --- rain stopping ---------------------------------------------------------

    private fun ongoing(endsIn: Int?) = RainWindow.Window(RainWindow.Kind.RAIN, 0, "08:00", "16:00", ongoing = true, endsInHours = endsIn)

    @Test
    fun `rain stop is announced only after a real spell, just before it ends`() {
        assertTrue(RainStop.isDue(ongoing(1), rainingForHours = 3))
        assertFalse("a shower isn't worth it", RainStop.isDue(ongoing(1), rainingForHours = 1))
        assertFalse("still hours to go", RainStop.isDue(ongoing(3), rainingForHours = 5))
        assertFalse("no end in the data", RainStop.isDue(ongoing(null), rainingForHours = 5))
        assertFalse("forecast rain isn't falling yet", RainStop.isDue(ongoing(1).copy(ongoing = false), rainingForHours = 5))
        assertEquals("przestanie padać ok. 16:00", PlTexts.rainStop(RainWindow.Kind.RAIN, "16:00"))
        assertEquals("rain stopping around 16:00", EnTexts.rainStop(RainWindow.Kind.RAIN, "16:00"))
    }
}
