package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SevereWeatherTest {

    private fun hour(h: Int, code: Int = 1, temp: Double = 20.0, gust: Double = 15.0) =
        WeatherApi.HourlyEntry("2026-09-24T%02d:00".format(h % 24), 0, code, temperature = temp, windGustKmh = gust)

    private fun forecast(
        current: Int = 1,
        temp: Double = 20.0,
        gust: Double = Double.NaN,
        hours: List<WeatherApi.HourlyEntry> = (10..30).map { hour(it) },
    ) = WeatherApi.WeatherData(
        isDay = true,
        currentWeatherCode = current,
        currentTemperature = temp,
        windGustKmh = gust,
        daily = emptyList(),
        hourly = hours,
    )

    private fun kinds(w: WeatherApi.WeatherData) = SevereWeather.find(w).map { it.kind }

    @Test
    fun `an ordinary day has no warnings`() {
        assertTrue(SevereWeather.find(forecast()).isEmpty())
    }

    @Test
    fun `a storm in the next hours is a storm warning with its lead time`() {
        val hours = (10..30).map { hour(it, code = if (it == 13) 95 else 1) }
        val w = SevereWeather.find(forecast(hours = hours)).single()
        assertEquals(SevereWeather.Kind.STORM, w.kind)
        assertEquals(3, w.startsInHours)
    }

    @Test
    fun `a storm happening now is a warning for now`() {
        val w = SevereWeather.find(forecast(current = 95)).single()
        assertEquals(SevereWeather.Kind.STORM, w.kind)
        assertEquals(0, w.startsInHours)
    }

    @Test
    fun `hail supersedes a plain storm`() {
        val hours = (10..30).map { hour(it, code = when (it) { 12 -> 95; 14 -> 96; else -> 1 }) }
        assertEquals(listOf(SevereWeather.Kind.HAIL), kinds(forecast(hours = hours)))
    }

    @Test
    fun `a storm beyond the lookahead is not warned about yet`() {
        val hours = (10..30).map { hour(it, code = if (it == 10 + SevereWeather.LOOKAHEAD_HOURS + 1) 95 else 1) }
        assertTrue(SevereWeather.find(forecast(hours = hours)).isEmpty())
    }

    @Test
    fun `strong gusts warn with the peak`() {
        val hours = (10..30).map { hour(it, gust = when (it) { 12 -> 80.0; 13 -> 96.0; else -> 20.0 }) }
        val w = SevereWeather.find(forecast(hours = hours)).single()
        assertEquals(SevereWeather.Kind.WIND, w.kind)
        assertEquals(2, w.startsInHours)
        assertEquals(96, w.peak)
    }

    @Test
    fun `gusts just under the line do not warn`() {
        val hours = (10..30).map { hour(it, gust = SevereWeather.SEVERE_GUST_KMH - 1.0) }
        assertTrue(SevereWeather.find(forecast(hours = hours)).isEmpty())
    }

    @Test
    fun `the current gust counts for the current hour`() {
        val w = SevereWeather.find(forecast(gust = 82.0)).single()
        assertEquals(SevereWeather.Kind.WIND, w.kind)
        assertEquals(0, w.startsInHours)
    }

    @Test
    fun `heat looks further ahead than a storm does`() {
        val hours = (10..30).map { hour(it, temp = if (it == 10 + 10) 37.0 else 30.0) }
        val w = SevereWeather.find(forecast(temp = 30.0, hours = hours)).single()
        assertEquals(SevereWeather.Kind.HEAT, w.kind)
        assertEquals(10, w.startsInHours)
        assertEquals(37, w.peak)
    }

    @Test
    fun `warnings come back most severe first`() {
        val hours = (10..30).map { hour(it, code = if (it == 11) 99 else 1, temp = 36.0, gust = 90.0) }
        assertEquals(
            listOf(SevereWeather.Kind.HAIL, SevereWeather.Kind.WIND, SevereWeather.Kind.HEAT),
            kinds(forecast(hours = hours)),
        )
    }

    @Test
    fun `missing gust and temperature data never warns`() {
        val hours = (10..30).map { WeatherApi.HourlyEntry("2026-09-24T%02d:00".format(it % 24), 0, 1) }
        assertTrue(SevereWeather.find(forecast(temp = Double.NaN, hours = hours)).isEmpty())
    }

    @Test
    fun `a warning is worded plainly in both languages`() {
        val w = SevereWeather.Warning(SevereWeather.Kind.WIND, 2, 90)
        assertTrue(PlTexts.severeText("Tolox", w).contains("90 km/h"))
        assertTrue(EnTexts.severeText("Tolox", w).contains("90 km/h"))
        assertTrue(EnTexts.severeText("Tolox", SevereWeather.Warning(SevereWeather.Kind.STORM, 0)).contains("now"))
    }
}
