package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SunEventsTest {

    private fun day(date: String, sunrise: String?, sunset: String?) =
        WeatherApi.DailyEntry(date, 1, 20.0, 10.0, 0, sunrise = sunrise, sunset = sunset)

    private fun forecast(vararg days: WeatherApi.DailyEntry, offset: Int? = 3600) = WeatherApi.WeatherData(
        isDay = true, currentWeatherCode = 1, currentTemperature = 15.0,
        daily = days.toList(), utcOffsetSeconds = offset,
    )

    private val nov17 = day("2026-11-17", "2026-11-17T07:12", "2026-11-17T16:05")
    private val nov18 = day("2026-11-18", "2026-11-18T07:14", "2026-11-18T16:03")

    @Test
    fun `within the hour before sunset it says so`() {
        val next = SunEvents.next(forecast(nov17, nov18), "2026-11-17T15:25")!!
        assertEquals(SunEvents.Kind.SUNSET, next.kind)
        assertEquals(40, next.minutes)
    }

    @Test
    fun `earlier than an hour it stays quiet`() {
        assertNull(SunEvents.next(forecast(nov17, nov18), "2026-11-17T14:00"))
    }

    @Test
    fun `just after sunset the next thing is tomorrow's sunrise, which is hours away`() {
        assertNull(SunEvents.next(forecast(nov17, nov18), "2026-11-17T16:30"))
    }

    @Test
    fun `before dawn it announces the sunrise, tomorrow's included`() {
        val next = SunEvents.next(forecast(nov17, nov18), "2026-11-18T06:30")!!
        assertEquals(SunEvents.Kind.SUNRISE, next.kind)
        assertEquals(44, next.minutes)
    }

    @Test
    fun `the nearest of two events wins`() {
        val odd = day("2026-06-21", "2026-06-21T05:00", "2026-06-21T05:30")
        val next = SunEvents.next(forecast(odd), "2026-06-21T04:50")!!
        assertEquals(SunEvents.Kind.SUNRISE, next.kind)
    }

    @Test
    fun `no sun times means no line, never a crash`() {
        assertNull(SunEvents.next(forecast(day("2026-11-17", null, null)), "2026-11-17T15:30"))
        assertNull(SunEvents.next(forecast(), "2026-11-17T15:30"))
        assertNull(SunEvents.next(forecast(nov17), "garbage"))
    }

    @Test
    fun `it is worded in both languages`() {
        val next = SunEvents.Next(SunEvents.Kind.SUNSET, 40)
        assertEquals("zachód za 40 min", SunEvents.text(next, PlTexts))
        assertEquals("sunset in 40 min", SunEvents.text(next, EnTexts))
    }

    @Test
    fun `the widget's meta line leads with it, in standard and full`() {
        val w = forecast(nov17, nov18)
        // 15:25 local at UTC+1 is 14:25 UTC.
        val nowUtc = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse("2026-11-17T14:25")!!.time
        assertEquals("zachód za 40 min", WidgetContent.notableMeta(w, PlTexts, nowUtc))
        assertTrue(WidgetContent.fullMeta(w, PlTexts, nowUtc)!!.startsWith("zachód za 40 min"))
    }
}
