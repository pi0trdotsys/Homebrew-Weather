package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UvAndGustTest {

    private fun day(uv: Double = Double.NaN, gust: Double = Double.NaN) =
        WeatherApi.DailyEntry("2026-09-24", 1, 26.0, 16.0, 0, uvIndexMax = uv, windGustMaxKmh = gust)

    private fun at(hour: Int) = listOf(WeatherApi.HourlyEntry("2026-09-24T%02d:00".format(hour), 0, 1))

    private fun forecast(
        daily: WeatherApi.DailyEntry,
        hour: Int = 10,
        gust: Double = Double.NaN,
    ) = WeatherApi.WeatherData(
        isDay = true, currentWeatherCode = 1, currentTemperature = 24.0, windGustKmh = gust,
        daily = listOf(daily), hourly = at(hour),
    )

    private val thresholds = MorningBrief.Thresholds(
        highEnabled = true, highThreshold = 30.0,
        lowEnabled = true, lowThreshold = 0.0,
        swingEnabled = true, swingThreshold = 8.0,
        aqiEnabled = true, aqiThreshold = 100.0,
        uvEnabled = true, uvThreshold = 8.0,
        gustEnabled = true, gustThreshold = 60.0,
    )

    private fun brief(w: WeatherApi.WeatherData, t: MorningBrief.Thresholds = thresholds) =
        MorningBrief.compose("Tolox", w, t, { "x" }, null)!!

    @Test
    fun `strong sun and a gusty day make it into the brief`() {
        val b = brief(forecast(day(uv = 9.4, gust = 72.0)))
        assertTrue(b.full.contains("porywy do 72 km/h"))
        assertTrue(b.full.contains("UV 9"))
        assertTrue("gusts come before UV", b.full.indexOf("porywy") < b.full.indexOf("UV"))
    }

    @Test
    fun `ordinary sun and breeze stay out of the brief`() {
        val b = brief(forecast(day(uv = 6.0, gust = 40.0)))
        assertFalse(b.full.contains("UV"))
        assertFalse(b.full.contains("porywy"))
    }

    @Test
    fun `switching the setting off silences the brief line`() {
        val off = thresholds.copy(uvEnabled = false, gustEnabled = false)
        val b = brief(forecast(day(uv = 11.0, gust = 90.0)), off)
        assertFalse(b.full.contains("UV"))
        assertFalse(b.full.contains("porywy"))
    }

    @Test
    fun `unknown UV and gusts are never reported as zero or as a hit`() {
        val b = brief(forecast(day()))
        assertFalse(b.full.contains("UV"))
    }

    @Test
    fun `a standard widget shows UV only at the very-high line`() {
        assertEquals("UV 8", WidgetContent.notableMeta(forecast(day(uv = 8.2))))
        assertNull(WidgetContent.notableMeta(forecast(day(uv = 7.4))))
    }

    @Test
    fun `UV is not shown once the sun has set`() {
        assertNull(WidgetContent.notableMeta(forecast(day(uv = 9.0), hour = WidgetContent.EVENING_HOUR)))
    }

    @Test
    fun `a standard widget shows gusts at the notable line`() {
        assertEquals("porywy 64 km/h", WidgetContent.notableMeta(forecast(day(), gust = 64.0)))
        assertNull(WidgetContent.notableMeta(forecast(day(), gust = 50.0)))
    }

    @Test
    fun `the notable line never runs past three readouts`() {
        val w = forecast(day(uv = 10.0), gust = 80.0).copy(
            apparentTemperature = 30.0, currentTemperature = 24.0, windSpeedKmh = 45.0, humidityPercent = 95,
        )
        assertEquals(3, WidgetContent.notableMeta(w)!!.split(" · ").size)
    }

    @Test
    fun `full density folds gusts into the wind readout and adds UV`() {
        val w = forecast(day(uv = 6.0), gust = 45.0).copy(windSpeedKmh = 20.0, humidityPercent = 50, apparentTemperature = 24.0)
        val meta = WidgetContent.fullMeta(w)!!
        assertTrue(meta, meta.contains("wiatr 20/45km/h"))
        assertTrue(meta, meta.contains("UV 6"))
    }

    @Test
    fun `an old cache without any of the new fields still renders`() {
        val w = WeatherApi.WeatherData(
            isDay = true, currentWeatherCode = 1, currentTemperature = 22.0,
            daily = listOf(WeatherApi.DailyEntry("2026-09-24", 1, 26.0, 16.0, 0)),
            hourly = at(10),
        )
        assertNull(WidgetContent.notableMeta(w))
        assertTrue(SevereWeather.find(w).isEmpty())
        assertNotNull(WidgetContent.build(w, WidgetDensity.FULL, offline = false))
    }

    private fun assertNotNull(x: Any?) = org.junit.Assert.assertNotNull(x)
}
