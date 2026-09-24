package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContentRulesTest {

    private fun day(max: Double, min: Double, pop: Int, code: Int = 1) =
        WeatherApi.DailyEntry("2026-09-24", code, max, min, pop)

    private fun hour(h: Int, pop: Int, code: Int = if (pop >= 50) 61 else 1) =
        WeatherApi.HourlyEntry("2026-09-24T%02d:00".format(h % 24), pop, code)

    /** A settled, dry, unremarkable day — the case the widget used to shout on. */
    private val calm = WeatherApi.WeatherData(
        isDay = true,
        currentWeatherCode = 0,
        currentTemperature = 27.0,
        apparentTemperature = 25.5,
        humidityPercent = 40,
        windSpeedKmh = 5.0,
        currentPrecipitationProbability = 0,
        usAqi = 36,
        daily = listOf(day(31.0, 18.0, 0), day(30.0, 18.0, 0), day(33.0, 18.0, 0), day(32.0, 21.0, 0)),
        hourly = (11..34).map { hour(it, 0, 0) },
    )

    /** Rain this afternoon, a storm tomorrow, bad air and wind. */
    private val eventful = calm.copy(
        currentWeatherCode = 3,
        currentTemperature = 18.0,
        apparentTemperature = 13.0,
        humidityPercent = 94,
        windSpeedKmh = 42.0,
        currentPrecipitationProbability = 10,
        usAqi = 142,
        daily = listOf(day(22.0, 14.0, 70), day(19.0, 11.0, 12), day(17.0, 9.0, 100, 95), day(20.0, 12.0, 45)),
        hourly = listOf(10, 10, 10, 10, 80, 90, 70, 20).mapIndexed { i, p -> hour(11 + i, p) },
    )

    @Test
    fun `a calm day in standard density says almost nothing beyond the forecast`() {
        val c = WidgetContent.build(calm, WidgetDensity.STANDARD, offline = false)
        assertEquals("słonecznie", c.heroLine)
        assertNull("AQI 36 is good; not news", c.aqi)
        assertFalse("no day reaches 30% rain", c.showPopRow)
        assertNull("feels-like within 3°, light wind, normal humidity", c.metaLine)
        assertFalse(c.showBars)
        assertFalse(c.showSparkline)
        assertFalse(c.showSync)
        assertFalse("online is the normal case", c.showOnlineDot)
        assertFalse("nothing left for the stat column", c.showStats)
        assertTrue(c.showFooter)
    }

    @Test
    fun `an eventful day surfaces each unusual thing and only those`() {
        val c = WidgetContent.build(eventful, WidgetDensity.STANDARD, offline = false)
        assertEquals("deszcz 15:00-18:00", c.heroLine)
        assertEquals(142, c.aqi)
        assertTrue(c.showPopRow)
        assertEquals(listOf("▽ 70%", "", "▽ 100%", "▽ 45%"), c.dayPop)
        assertEquals("odczuwalna 13° · wiatr 42 km/h · wilgotno 94%", c.metaLine)
        assertTrue(c.showStats)
    }

    @Test
    fun `moderate air shows in standard but not in minimal`() {
        val moderate = calm.copy(usAqi = 75)
        assertEquals(75, WidgetContent.build(moderate, WidgetDensity.STANDARD, false).aqi)
        assertNull(WidgetContent.build(moderate, WidgetDensity.MINIMAL, false).aqi)
        assertEquals(142, WidgetContent.build(eventful, WidgetDensity.MINIMAL, false).aqi)
    }

    @Test
    fun `offline brings the status dot back`() {
        assertTrue(WidgetContent.build(calm, WidgetDensity.STANDARD, offline = true).showOnlineDot)
    }

    @Test
    fun `full density keeps every readout, as before`() {
        val c = WidgetContent.build(calm, WidgetDensity.FULL, offline = false)
        assertEquals("słonecznie · opady 0%", c.heroLine)
        assertEquals(36, c.aqi)
        assertTrue(c.showPopRow)
        assertEquals(listOf("▽ 0%", "▽ 0%", "▽ 0%", "▽ 0%"), c.dayPop)
        assertTrue(c.showBars)
        assertTrue(c.showSync)
        assertTrue(c.showPopMax)
        assertNotNull(c.metaLine)
        // A forecast with no rain still hides the sparkline, which would
        // otherwise render as a stray "▁▁▁▁" rule.
        assertFalse(c.showSparkline)
        assertTrue(WidgetContent.build(eventful, WidgetDensity.FULL, false).showSparkline)
    }

    @Test
    fun `minimal density is temperature and four days`() {
        val c = WidgetContent.build(eventful, WidgetDensity.MINIMAL, offline = false)
        assertFalse(c.showPopRow)
        assertNull(c.metaLine)
        assertFalse(c.showFooter)
        // ...but a rain sentence is still the most useful thing it can say.
        assertEquals("deszcz 15:00-18:00", c.heroLine)
    }

    @Test
    fun `rows handed to the solver match what will be drawn`() {
        val rows = WidgetContent.build(calm, WidgetDensity.STANDARD, false).rows()
        assertFalse(rows.meta)
        assertFalse(rows.pop)
        assertFalse(rows.bars)
        assertFalse(rows.stats)
        assertTrue(rows.footer)
        assertEquals("słonecznie".length, rows.heroLineCells)
    }

    @Test
    fun `a calmer day gets bigger type rather than empty space`() {
        // Same 368x176dp footprint the real device grants. With fewer rows to
        // budget for, the solver must scale *up* — hiding content is supposed
        // to make the rest more legible, not leave a hole.
        val calmScale = WidgetMetrics.forSize(368, 176, WidgetContent.build(calm, WidgetDensity.STANDARD, false).rows()).scale
        val fullScale = WidgetMetrics.forSize(368, 176, WidgetContent.build(calm, WidgetDensity.FULL, false).rows()).scale
        assertTrue("calm $calmScale should exceed full $fullScale", calmScale > fullScale * 1.15f)
    }

    @Test
    fun `old caches without hourly data fall back to the condition label`() {
        val legacy = eventful.copy(hourly = emptyList(), currentWeatherCode = 61)
        assertEquals("deszcz", WidgetContent.build(legacy, WidgetDensity.STANDARD, false).heroLine)
    }

    @Test
    fun `night shows a moon label, not the daytime condition`() {
        assertEquals("pogodna noc", WidgetContent.build(calm.copy(isDay = false), WidgetDensity.STANDARD, false).heroLine)
    }
}
