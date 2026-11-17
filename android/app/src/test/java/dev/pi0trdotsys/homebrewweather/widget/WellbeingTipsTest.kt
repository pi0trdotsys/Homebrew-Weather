package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WellbeingTipsTest {

    private fun forecast(
        temp: Double = 18.0,
        code: Int = 1,
        isDay: Boolean = true,
        aqi: Int = -1,
        gust: Double = Double.NaN,
        uv: Double = Double.NaN,
        hour: Int = 12,
    ) = WeatherApi.WeatherData(
        isDay = isDay, currentWeatherCode = code, currentTemperature = temp, usAqi = aqi, windGustKmh = gust,
        daily = listOf(WeatherApi.DailyEntry("2026-11-17", code, 20.0, 10.0, 0, uvIndexMax = uv)),
        hourly = listOf(WeatherApi.HourlyEntry("2026-11-17T%02d:00".format(hour), 0, code)),
    )

    private fun situation(w: WeatherApi.WeatherData) = WellbeingTips.situation(w)

    @Test
    fun `an ordinary day is calm`() {
        assertEquals(WellbeingTips.Situation.CALM, situation(forecast()))
    }

    @Test
    fun `each situation is picked by its own reading`() {
        assertEquals(WellbeingTips.Situation.HOT, situation(forecast(temp = 31.0)))
        assertEquals(WellbeingTips.Situation.COLD, situation(forecast(temp = -2.0)))
        assertEquals(WellbeingTips.Situation.RAIN, situation(forecast(code = 61)))
        assertEquals(WellbeingTips.Situation.RAIN, situation(forecast(code = 95)))
        assertEquals(WellbeingTips.Situation.WIND, situation(forecast(gust = 70.0)))
        assertEquals(WellbeingTips.Situation.NIGHT, situation(forecast(isDay = false, hour = 23)))
        assertEquals(WellbeingTips.Situation.AIR, situation(forecast(aqi = 140)))
        assertEquals(WellbeingTips.Situation.SUN, situation(forecast(uv = 9.0)))
    }

    @Test
    fun `the most pressing situation wins`() {
        assertEquals(WellbeingTips.Situation.AIR, situation(forecast(aqi = 150, uv = 10.0, temp = 33.0)))
        assertEquals(WellbeingTips.Situation.SUN, situation(forecast(uv = 10.0, temp = 33.0)))
        assertEquals(WellbeingTips.Situation.HOT, situation(forecast(temp = 33.0, code = 61)))
    }

    @Test
    fun `strong sun is not a tip for the evening`() {
        assertEquals(WellbeingTips.Situation.CALM, situation(forecast(uv = 10.0, hour = WidgetContent.EVENING_HOUR)))
    }

    @Test
    fun `an unknown reading never triggers a situation`() {
        assertEquals(WellbeingTips.Situation.CALM, situation(forecast(temp = Double.NaN)))
    }

    @Test
    fun `every tip fits the footer in both languages`() {
        val tooLong = WellbeingTips.allLines().filter { it.length > WellbeingTips.MAX_LINE_LENGTH }
        assertTrue("too long: $tooLong", tooLong.isEmpty())
    }

    @Test
    fun `every situation has a pool in both languages`() {
        WellbeingTips.Situation.entries.forEach {
            assertTrue(it.key, it.key in WellbeingTips.poolKeys())
            assertTrue(WellbeingTips.pick(Lang.PL, it, 0).isNotBlank())
            assertTrue(WellbeingTips.pick(Lang.EN, it, 0).isNotBlank())
        }
    }

    @Test
    fun `the pick is stable for an hour and varies across hours`() {
        val a = WellbeingTips.pick(Lang.EN, WellbeingTips.Situation.CALM, 100)
        assertEquals(a, WellbeingTips.pick(Lang.EN, WellbeingTips.Situation.CALM, 100))
        val seen = (0 until 20).map { WellbeingTips.pick(Lang.EN, WellbeingTips.Situation.CALM, it) }.toSet()
        assertTrue(seen.size > 3)
    }

    @Test
    fun `the footer falls back to a joke when history is not available`() {
        // OnThisDay needs a Context; here only the pure parts of the choice are checked.
        assertEquals(FooterSource.JOKE, FooterSource.fromStorage(null))
        assertEquals(FooterSource.JOKE, FooterSource.fromStorage("nonsense"))
        assertEquals(FooterSource.HISTORY, FooterSource.fromStorage("history"))
        assertEquals(FooterSource.WELLBEING, FooterSource.fromStorage("wellbeing"))
    }
}
