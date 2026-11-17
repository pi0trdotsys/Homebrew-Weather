package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RainAmountTest {

    private fun hour(h: Int, pop: Int, mm: Double = Double.NaN, code: Int = if (pop >= 50) 61 else 1) =
        WeatherApi.HourlyEntry("2026-09-%02dT%02d:00".format(24 + h / 24, h % 24), pop, code, precipitationMm = mm)

    private fun forecast(hours: List<WeatherApi.HourlyEntry>, daily: List<WeatherApi.DailyEntry> = emptyList()) =
        WeatherApi.WeatherData(
            isDay = true, currentWeatherCode = 1, currentTemperature = 20.0,
            daily = daily, hourly = hours,
        )

    private fun windowOf(vararg mm: Double): RainWindow.Window {
        val hours = listOf(hour(10, 0)) + mm.mapIndexed { i, v -> hour(11 + i, 80, v) } + hour(11 + mm.size, 0)
        return RainWindow.find(hours, 1)!!
    }

    @Test
    fun `intensity follows the wettest hour`() {
        assertEquals(RainWindow.Intensity.DRIZZLE, RainWindow.intensity(RainWindow.Kind.RAIN, 0.2))
        assertEquals(RainWindow.Intensity.RAIN, RainWindow.intensity(RainWindow.Kind.RAIN, 1.5))
        assertEquals(RainWindow.Intensity.HEAVY, RainWindow.intensity(RainWindow.Kind.RAIN, 4.0))
        assertEquals(RainWindow.Intensity.HEAVY, RainWindow.intensity(RainWindow.Kind.RAIN, 12.0))
    }

    @Test
    fun `the boundaries sit where documented`() {
        assertEquals(RainWindow.Intensity.RAIN, RainWindow.intensity(RainWindow.Kind.RAIN, RainWindow.DRIZZLE_BELOW_MM))
        assertEquals(RainWindow.Intensity.DRIZZLE, RainWindow.intensity(RainWindow.Kind.RAIN, RainWindow.DRIZZLE_BELOW_MM - 0.01))
        assertEquals(RainWindow.Intensity.RAIN, RainWindow.intensity(RainWindow.Kind.RAIN, RainWindow.HEAVY_FROM_MM - 0.01))
    }

    @Test
    fun `snow and storms keep their own noun whatever the amount`() {
        assertEquals(RainWindow.Intensity.RAIN, RainWindow.intensity(RainWindow.Kind.SNOW, 9.0))
        assertEquals(RainWindow.Intensity.RAIN, RainWindow.intensity(RainWindow.Kind.THUNDER, 0.1))
    }

    @Test
    fun `no amount data reads as plain rain, never a guess`() {
        assertEquals(RainWindow.Intensity.RAIN, RainWindow.intensity(RainWindow.Kind.RAIN, Double.NaN))
        val w = windowOf(Double.NaN, Double.NaN)
        assertTrue(w.totalMm.isNaN())
        assertEquals("deszcz 11–13", RainWindow.short(w))
    }

    @Test
    fun `a window sums and peaks its own hours only`() {
        val w = windowOf(0.4, 3.2, 1.0)
        assertEquals(4.6, w.totalMm, 1e-9)
        assertEquals(3.2, w.peakMm, 1e-9)
    }

    @Test
    fun `the hero line names a downpour and a drizzle by what they are`() {
        assertEquals("ulewa 11–12", RainWindow.short(windowOf(7.0)))
        assertEquals("mżawka 11–12", RainWindow.short(windowOf(0.2)))
        assertEquals("deszcz 11–12", RainWindow.short(windowOf(1.2)))
        assertEquals("downpour 11–12", RainWindow.short(windowOf(7.0), EnTexts))
        assertEquals("drizzle 11–12", RainWindow.short(windowOf(0.2), EnTexts))
    }

    @Test
    fun `a notification sentence carries the total`() {
        assertEquals("ulewa od 11:00 do ok. 12:00, 7,0 mm łącznie", RainWindow.long(windowOf(7.0)))
        assertEquals("rain from 11:00 until about 13:00, 2.4 mm in total", RainWindow.long(windowOf(1.0, 1.4), EnTexts))
    }

    @Test
    fun `a trace of rain is not announced as an amount`() {
        assertFalse(RainWindow.long(windowOf(0.04)).contains("mm"))
    }

    @Test
    fun `a day column carries its run's amounts`() {
        val hours = (0..23).map { hour(it, if (it in 8..9) 90 else 0, if (it in 8..9) 2.5 else 0.0) }
        val d = RainWindow.forDay(hours.map { it }, "2026-09-24")!!
        assertEquals(5.0, d.totalMm, 1e-9)
        assertEquals(2.5, d.peakMm, 1e-9)
    }

    @Test
    fun `amounts are formatted by language`() {
        assertEquals("0,2 mm", PlTexts.mm(0.2))
        assertEquals("0.2 mm", EnTexts.mm(0.2))
        assertEquals("9,9 mm", PlTexts.mm(9.9))
        assertEquals("10 mm", PlTexts.mm(9.96))
        assertEquals("12 mm", EnTexts.mm(12.3))
    }

    @Test
    fun `remaining rain today sums only the hours that are left`() {
        val w = forecast((12..27).map { hour(it, 0, if (it in 14..15) 3.0 else 0.0) })
        // 12:00-23:00 is today; 24:00 onwards is tomorrow and must not count.
        assertEquals(6.0, WidgetContent.remainingTodayMm(w)!!, 1e-9)
        assertNull(WidgetContent.remainingTodayMm(forecast((12..14).map { hour(it, 0) })))
    }

    @Test
    fun `the standard widget shows rain amount only when it is a lot`() {
        val wet = forecast((12..20).map { hour(it, 80, 1.0) })
        assertEquals("opady 9,0 mm", WidgetContent.notableMeta(wet))
        val drizzle = forecast((12..20).map { hour(it, 80, 0.1) })
        assertNull(WidgetContent.notableMeta(drizzle))
    }

    @Test
    fun `the brief mentions a possible-rain amount only when it is worth a jacket`() {
        val t = MorningBrief.Thresholds(true, 30.0, true, 0.0, true, 8.0, true, 100.0)
        fun brief(mm: Double): String {
            val w = forecast(
                hours = (7..30).map { hour(it, 40, 0.0, 1) },
                daily = listOf(WeatherApi.DailyEntry("2026-09-24", 3, 20.0, 12.0, 40, precipitationSumMm = mm)),
            )
            return MorningBrief.compose("Tolox", w, t, { "x" }, null)!!.full
        }
        assertTrue(brief(3.0).contains("ok. 3,0 mm"))
        assertFalse(brief(0.4).contains("mm"))
    }

    @Test
    fun `the evening preview names tomorrow's downpour`() {
        val hours = (0..47).map { hour(it, if (it in 32..34) 90 else 0, if (it in 32..34) 6.0 else 0.0) }
        val w = forecast(
            hours,
            daily = listOf(
                WeatherApi.DailyEntry("2026-09-24", 1, 20.0, 12.0, 0),
                WeatherApi.DailyEntry("2026-09-25", 61, 19.0, 11.0, 90),
            ),
        )
        assertTrue(EveningPreview.compose("Tolox", w)!!.summary.contains("ulewa 8–11"))
    }
}
