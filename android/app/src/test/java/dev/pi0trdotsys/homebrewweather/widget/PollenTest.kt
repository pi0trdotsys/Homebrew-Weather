package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PollenTest {

    private fun reading(birch: Double = 0.0, grass: Double = 0.0, alder: Double = 0.0) =
        WeatherApi.PollenReading(alder = alder, birch = birch, grass = grass)

    @Test
    fun `below every line nothing is said`() {
        assertNull(Pollen.top(reading(birch = 89.0, grass = 29.0, alder = 49.0)))
    }

    @Test
    fun `a kind at its own line is high`() {
        val hit = Pollen.top(reading(birch = 90.0))!!
        assertEquals(Pollen.Species.BIRCH, hit.species)
        assertEquals(90, hit.grains)
        assertFalse(hit.veryHigh)
    }

    @Test
    fun `lines are per kind`() {
        assertEquals(Pollen.Species.GRASS, Pollen.top(reading(grass = 30.0))!!.species)
        assertNull(Pollen.top(reading(alder = 30.0))) // alder's line is 50
    }

    @Test
    fun `three times the line is very high`() {
        assertTrue(Pollen.top(reading(grass = 90.0))!!.veryHigh)
        assertFalse(Pollen.top(reading(grass = 89.0))!!.veryHigh)
    }

    @Test
    fun `the kind furthest over its line wins, not the biggest count`() {
        val hit = Pollen.top(reading(birch = 120.0, grass = 60.0))!! // 1.3x vs 2x
        assertEquals(Pollen.Species.GRASS, hit.species)
    }

    @Test
    fun `unknown is never a hit`() {
        assertNull(Pollen.top(null))
        assertNull(Pollen.top(WeatherApi.PollenReading()))
    }

    private fun forecast(pollen: WeatherApi.PollenReading?) = WeatherApi.WeatherData(
        isDay = true, currentWeatherCode = 1, currentTemperature = 18.0, pollen = pollen,
        daily = listOf(WeatherApi.DailyEntry("2026-05-10", 1, 20.0, 10.0, 0)),
        hourly = listOf(WeatherApi.HourlyEntry("2026-05-10T12:00", 0, 1)),
    )

    @Test
    fun `the widget says it, in both languages, and only when the setting allows`() {
        val w = forecast(reading(birch = 200.0))
        assertEquals("pylenie: brzoza", WidgetContent.notableMeta(w, PlTexts))
        assertEquals("pollen: birch", WidgetContent.notableMeta(w, EnTexts))
        assertNull(WidgetContent.notableMeta(w, PlTexts, pollen = false))
    }

    @Test
    fun `very high pollen is worded stronger`() {
        assertEquals("silne pylenie: trawy", PlTexts.pollenShort(Pollen.Species.GRASS, true))
        assertEquals("heavy pollen: grass", EnTexts.pollenShort(Pollen.Species.GRASS, true))
    }

    @Test
    fun `the brief mentions it when enabled`() {
        val t = MorningBrief.Thresholds(true, 30.0, true, 0.0, true, 8.0, true, 100.0, pollenEnabled = true)
        val b = MorningBrief.compose("Kielce", forecast(reading(birch = 150.0)), t, { "x" }, null, PlTexts)!!
        assertTrue(b.full, b.full.contains("wysokie pylenie: brzoza"))
        val off = MorningBrief.compose("Kielce", forecast(reading(birch = 150.0)), t.copy(pollenEnabled = false), { "x" }, null, PlTexts)!!
        assertFalse(off.full.contains("pylenie"))
    }

    @Test
    fun `it is a well-being situation, after bad air and before strong sun`() {
        assertEquals(WellbeingTips.Situation.POLLEN, WellbeingTips.situation(forecast(reading(grass = 80.0))))
        assertEquals(WellbeingTips.Situation.AIR, WellbeingTips.situation(forecast(reading(grass = 80.0)).copy(usAqi = 150)))
        assertEquals(WellbeingTips.Situation.CALM, WellbeingTips.situation(forecast(reading(grass = 5.0))))
    }

    @Test
    fun `a reading survives the widget's cache shape`() {
        // The JSON keys are a, b, g, m, o, r; NaN fields are simply absent.
        val r = WeatherApi.PollenReading(birch = 120.0)
        assertTrue(r.alder.isNaN() && !r.birch.isNaN())
    }
}
