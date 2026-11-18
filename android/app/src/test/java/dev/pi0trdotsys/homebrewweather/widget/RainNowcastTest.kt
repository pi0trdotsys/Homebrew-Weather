package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RainNowcastTest {

    /** Steps from 12:00, one per 15 minutes, with the given amounts. */
    private fun steps(vararg mm: Double) = mm.mapIndexed { i, v ->
        RainNowcast.Step("2026-11-18T%02d:%02d".format(12 + i / 4, (i % 4) * 15), v)
    }

    private fun at(hhmm: String) = "2026-11-18T$hhmm"

    @Test
    fun `dry now with rain coming says how many minutes`() {
        val r = RainNowcast.analyse(steps(0.0, 0.0, 0.0, 0.4, 0.6, 0.0), at("12:05"))!!
        assertFalse(r.ongoing)
        assertEquals(40, r.startsInMinutes) // the fourth step starts at 12:45
        assertNull(r.endsInMinutes)
        assertEquals(1.0, r.totalMm, 1e-9)
    }

    @Test
    fun `it is raining now and stops at the first dry step`() {
        val r = RainNowcast.analyse(steps(0.5, 0.7, 0.2, 0.0, 0.0), at("12:20"))!!
        assertTrue(r.ongoing)
        assertNull(r.startsInMinutes)
        assertEquals(25, r.endsInMinutes) // 12:45 is the first dry step
        assertEquals(0.9, r.totalMm, 1e-9) // from the step the clock is in onwards: 0.7 + 0.2
    }

    @Test
    fun `still wet when the data ends has no end`() {
        val r = RainNowcast.analyse(steps(0.0, 0.3, 0.3), at("12:20"))!!
        assertTrue(r.ongoing)
        assertNull(r.endsInMinutes)
    }

    @Test
    fun `a dry spell with no rain in the data is no rain`() {
        val r = RainNowcast.analyse(steps(0.0, 0.0, 0.05, 0.0), at("12:00"))!!
        assertFalse(r.anyRain)
        assertEquals(0.0, r.totalMm, 0.0)
    }

    @Test
    fun `a wet step is 0·1 mm and not a hair less`() {
        assertTrue(RainNowcast.analyse(steps(RainNowcast.WET_MM, 0.0), at("12:00"))!!.ongoing)
        assertFalse(RainNowcast.analyse(steps(RainNowcast.WET_MM - 0.01, 0.0), at("12:00"))!!.ongoing)
    }

    @Test
    fun `rain further than the horizon is left to the hourly window`() {
        val dry = DoubleArray(10) { 0.0 } + doubleArrayOf(1.0) // wet step starts at 14:30, 150 min on
        val r = RainNowcast.analyse(steps(*dry), at("12:00"))!!
        assertNull(r.startsInMinutes)
        assertFalse(r.anyRain)
    }

    @Test
    fun `a step just inside the horizon is announced`() {
        val dry = DoubleArray(8) { 0.0 } + doubleArrayOf(1.0) // starts at 14:00, exactly 120 min on
        assertEquals(120, RainNowcast.analyse(steps(*dry), at("12:00"))!!.startsInMinutes)
    }

    @Test
    fun `a clock outside the data is unknown, never a guess`() {
        assertNull(RainNowcast.analyse(steps(0.0, 0.5), at("11:00")))
        assertNull(RainNowcast.analyse(steps(0.0, 0.5), at("14:00")))
        assertNull(RainNowcast.analyse(emptyList(), at("12:00")))
        assertNull(RainNowcast.analyse(steps(0.5), "garbage"))
    }

    @Test
    fun `minutes are spoken to the nearest five, and zero means any moment`() {
        assertEquals(0, RainNowcast.roundForSpeech(0))
        assertEquals(0, RainNowcast.roundForSpeech(2))
        assertEquals(5, RainNowcast.roundForSpeech(3))
        assertEquals(10, RainNowcast.roundForSpeech(12))
        assertEquals(15, RainNowcast.roundForSpeech(13))
        assertEquals(40, RainNowcast.roundForSpeech(41))
    }

    @Test
    fun `it is worded in both languages`() {
        assertEquals("deszcz za ok. 10 min", PlTexts.nowcastStart(RainWindow.Kind.RAIN, 10))
        assertEquals("deszcz za chwilę", PlTexts.nowcastStart(RainWindow.Kind.RAIN, 0))
        assertEquals("rain in about 10 min", EnTexts.nowcastStart(RainWindow.Kind.RAIN, 10))
        assertEquals("pada, koniec za ok. 20 min", PlTexts.nowcastOngoing(RainWindow.Kind.RAIN, 20))
        assertEquals("raining", EnTexts.nowcastOngoing(RainWindow.Kind.RAIN, null))
    }

    private fun forecast(nowcast: List<RainNowcast.Step>) = WeatherApi.WeatherData(
        isDay = true, currentWeatherCode = 3, currentTemperature = 10.0,
        daily = emptyList(), nowcast = nowcast, utcOffsetSeconds = 3600,
        hourly = listOf(WeatherApi.HourlyEntry("2026-11-18T12:00", 0, 3)),
    )

    private fun nowMillis(localHhmm: String): Long {
        // Local 12:05 at UTC+1 is 11:05 UTC.
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        return sdf.parse("2026-11-18T$localHhmm")!!.time - 3600_000L
    }

    @Test
    fun `the hero says it by the minute when rain is near and worth it`() {
        val w = forecast(steps(0.0, 0.0, 0.5, 0.6, 0.0))
        assertEquals("deszcz za ok. 25 min", WidgetContent.nowcastLine(w, RainWindow.Kind.RAIN, PlTexts, nowMillis("12:05")))
    }

    @Test
    fun `the hero stays quiet for a few drops, for far rain, and without 15-minute data`() {
        val drops = forecast(steps(0.0, 0.0, 0.1, 0.0))
        assertNull(WidgetContent.nowcastLine(drops, RainWindow.Kind.RAIN, PlTexts, nowMillis("12:05")))
        val far = forecast(steps(*(DoubleArray(6) { 0.0 } + doubleArrayOf(2.0))))
        assertNull(WidgetContent.nowcastLine(far, RainWindow.Kind.RAIN, PlTexts, nowMillis("12:00")))
        assertNull(WidgetContent.nowcastLine(forecast(emptyList()), RainWindow.Kind.RAIN, PlTexts, nowMillis("12:05")))
    }

    @Test
    fun `while it rains the hero says when it stops`() {
        val w = forecast(steps(0.4, 0.5, 0.0, 0.0))
        assertEquals("pada, koniec za ok. 25 min", WidgetContent.nowcastLine(w, RainWindow.Kind.RAIN, PlTexts, nowMillis("12:05")))
    }

    @Test
    fun `build puts the nowcast in the hero line`() {
        val w = forecast(steps(0.0, 0.0, 0.5, 0.6, 0.0))
        val c = WidgetContent.build(w, WidgetDensity.STANDARD, offline = false, texts = PlTexts, nowMillis = nowMillis("12:05"))
        assertEquals("deszcz za ok. 25 min", c.heroLine)
    }
}
