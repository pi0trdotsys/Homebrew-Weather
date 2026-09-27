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

    /** Days dated 2026-09-24 onwards, in order: the per-day rain rule matches
     * hourly entries to a column by date, so each day needs its own. */
    private fun dated(vararg days: WeatherApi.DailyEntry) =
        days.mapIndexed { i, d -> d.copy(date = "2026-09-%02d".format(24 + i)) }

    /** Hour [h] counted from midnight on the 24th; 24 and up roll into the next days. */
    private fun hour(h: Int, pop: Int, code: Int = if (pop >= 50) 61 else 1) =
        WeatherApi.HourlyEntry("2026-09-%02dT%02d:00".format(24 + h / 24, h % 24), pop, code)

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
        daily = dated(day(31.0, 18.0, 0), day(30.0, 18.0, 0), day(33.0, 18.0, 0), day(32.0, 21.0, 0)),
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
        daily = dated(day(22.0, 14.0, 70), day(19.0, 11.0, 12), day(17.0, 9.0, 100, 95), day(20.0, 12.0, 45)),
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
        assertEquals("deszcz 15–18", c.heroLine)
        assertEquals(142, c.aqi)
        assertTrue(c.showPopRow)
        // Today's 70% is the hero's sentence now, not a second readout.
        assertEquals(listOf("", "", "▽ 100%", "▽ 45%"), c.dayPop)
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
        assertEquals("deszcz 15–18", c.heroLine)
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

    // --- rain pinned to its day; the evening grid ---------------------------

    /** 23:00 on the 24th, dry tonight, rain 08-19 on the 25th — the device case. */
    private val lateEvening = calm.copy(
        isDay = false,
        currentWeatherCode = 3,
        daily = dated(
            day(26.0, 22.0, 0), day(23.0, 20.0, 90, 61), day(25.0, 20.0, 10),
            day(25.0, 21.0, 0), day(24.0, 19.0, 0),
        ),
        hourly = (23..60).map { h -> hour(h, if (h in 32..42) 80 else 0) },
    )

    @Test
    fun `in the evening tomorrow's rain goes under tomorrow, not under the temperature`() {
        val c = WidgetContent.build(lateEvening, WidgetDensity.STANDARD, false)
        assertEquals("pochmurno", c.heroLine)
        assertEquals(1, c.dayOffset)
        assertEquals("jutro", c.firstDayLabel)
        assertEquals(listOf("▽ 8–19", "", "", ""), c.dayPop)
        assertEquals(5, c.rows().dayLabelCells)
    }

    @Test
    fun `before the evening the grid starts today and tomorrow's rain still sits under it`() {
        val afternoon = lateEvening.copy(hourly = (14..60).map { h -> hour(h, if (h in 32..42) 80 else 0) })
        val c = WidgetContent.build(afternoon, WidgetDensity.STANDARD, false)
        assertEquals(0, c.dayOffset)
        assertEquals("dziś", c.firstDayLabel)
        assertEquals(listOf("", "▽ 8–19", "", ""), c.dayPop)
    }

    @Test
    fun `minimal keeps a day's rain window but none of the chances`() {
        val c = WidgetContent.build(lateEvening, WidgetDensity.MINIMAL, false)
        assertEquals(listOf("▽ 8–19", "", "", ""), c.dayPop)
        assertTrue(c.showPopRow)
    }

    @Test
    fun `no fifth day, no evening shift`() {
        val short = lateEvening.copy(daily = lateEvening.daily.take(4))
        assertEquals(0, WidgetContent.build(short, WidgetDensity.STANDARD, false).dayOffset)
    }

    @Test
    fun `today's column counts only the hours still ahead`() {
        // The daily max is 70% from a shower that's already over; what's left
        // of the day tops out at 20%, which isn't worth a figure.
        val past = calm.copy(
            daily = dated(day(22.0, 14.0, 70), day(19.0, 11.0, 0), day(17.0, 9.0, 0), day(20.0, 12.0, 0)),
            hourly = (15..30).map { hour(it, 20) },
        )
        assertEquals("", WidgetContent.build(past, WidgetDensity.STANDARD, false).dayPop[0])
    }


    @Test
    fun `night shows a moon label, not the daytime condition`() {
        assertEquals("pogodna noc", WidgetContent.build(calm.copy(isDay = false), WidgetDensity.STANDARD, false).heroLine)
    }

    // --- colour of a day's rain (a storm must stand out) --------------------

    @Test
    fun `a storm window is styled apart from rain and from a mere chance`() {
        val storm = lateEvening.copy(hourly = (23..60).map { h -> hour(h, if (h in 32..42) 80 else 0, if (h in 32..42) 95 else 3) })
        val c = WidgetContent.build(storm, WidgetDensity.FULL, false)
        assertEquals(WidgetContent.PopStyle.THUNDER, c.dayPopStyle[0])
        assertEquals(WidgetContent.PopStyle.CHANCE, c.dayPopStyle[1])
        assertEquals(WidgetContent.PopStyle.RAIN, WidgetContent.build(lateEvening, WidgetDensity.STANDARD, false).dayPopStyle[0])
    }

    // --- English ------------------------------------------------------------

    @Test
    fun `english speaks english end to end`() {
        val c = WidgetContent.build(eventful, WidgetDensity.STANDARD, false, EnTexts)
        assertEquals("rain 15–18", c.heroLine)
        assertEquals("today", c.firstDayLabel)
        assertEquals("feels 13° · wind 42 km/h · humid 94%", c.metaLine)
        val evening = WidgetContent.build(lateEvening, WidgetDensity.STANDARD, false, EnTexts)
        assertEquals("overcast", evening.heroLine)
        assertEquals("tmrw", evening.firstDayLabel)
        assertEquals("sunny · rain 0%", WidgetContent.build(calm, WidgetDensity.FULL, false, EnTexts).heroLine)
    }

    @Test
    fun `both languages have every day-of-week label, at most 3 cells`() {
        listOf(PlTexts, EnTexts).forEach { t ->
            (0..6).forEach { d -> assertTrue("${t.lang} $d", t.dow(d).length in 2..3) }
            assertTrue(t.today.length <= 5 && t.tomorrow.length <= 5)
        }
    }

    // --- PixelFont: the rain row's own font -----------------------------------

    @Test
    fun `every string the rain row can produce is in the pixel font`() {
        val samples = mutableListOf("▽ 100%", "▽ 0%", "▽ 13–20+", "▽ 0–24")
        listOf(PlTexts, EnTexts).forEach { t -> samples += "▽ ${t.dayOpen(20)}" }
        val missing = samples.flatMap { s -> s.filterNot(PixelFont::covers).toList() }.distinct()
        assertTrue("missing glyphs: $missing", missing.isEmpty())
    }

    @Test
    fun `pixel font fits the narrowest column at a whole-pixel scale`() {
        // 170dp widget at 3x density: a column is ~117px wide, the row ~36px tall.
        val text = "▽ 13–20+"
        val scale = PixelFont.fitScale(text, maxWidthPx = 117, maxHeightPx = 36)
        assertTrue("scale $scale", scale >= 2)
        assertTrue(PixelFont.widthUnits(text) * scale <= 117)
        // ...and the row's height caps it on a wide widget.
        assertEquals(36 / PixelFont.ROWS, PixelFont.fitScale(text, maxWidthPx = 10_000, maxHeightPx = 36))
    }

    @Test
    fun `the rain row shares one scale, and sheds the marker before it gets tiny`() {
        val row = listOf("▽ 13–20+", "▽ 10–12", "", "")
        // Wide column: one scale for both, marker kept.
        val (wide, wideScale) = PixelFont.fitRow(row, maxWidthPx = 264, maxHeightPx = 36)
        assertEquals(row, wide)
        assertEquals(minOf(264 / PixelFont.widthUnits("▽ 13–20+"), 36 / PixelFont.ROWS), wideScale)
        // 170dp widget (~105px columns): with the marker the row would drop to
        // scale 2; without it, 3 — so the marker goes, for every column.
        val (narrow, narrowScale) = PixelFont.fitRow(row, maxWidthPx = 105, maxHeightPx = 36)
        assertEquals(listOf("13–20+", "10–12", "", ""), narrow)
        assertEquals(3, narrowScale)
    }

    // --- rebasedTo: a cached forecast read against the real clock -------------

    /** Fetched at 23:00 on the 24th (UTC+2), rain from 08:00 on the 25th. */
    private val fetchedLate = lateEvening.copy(
        utcOffsetSeconds = 7200,
        currentTemperature = 22.0,
        apparentTemperature = 25.0,
        hourly = (23..60).map { h ->
            WeatherApi.HourlyEntry(
                "2026-09-%02dT%02d:00".format(24 + h / 24, h % 24),
                if (h in 32..42) 80 else 0,
                if (h in 32..42) 61 else 3,
                temperature = 10.0 + h % 24,
                isDay = h % 24 in 7..19,
            )
        },
    )

    /** 2026-09-25 09:30 local (UTC+2) as epoch ms. */
    private val nextMorning = java.time.OffsetDateTime.parse("2026-09-25T09:30:00+02:00").toInstant().toEpochMilli()

    @Test
    fun `a forecast from last night reads as this morning's`() {
        val r = fetchedLate.rebasedTo(nextMorning)
        assertEquals("2026-09-25T09:00", r.hourly.first().time)
        assertEquals(19.0, r.currentTemperature, 0.0) // the 09:00 hourly temperature
        assertTrue(r.isDay)
        assertTrue("stale feels-like is dropped", r.apparentTemperature.isNaN())
        assertEquals("2026-09-25", r.daily.first().date)

        val c = WidgetContent.build(r, WidgetDensity.STANDARD, offline = true)
        assertEquals("dziś", c.firstDayLabel)
        assertEquals(0, c.dayOffset)
        // The 09:00 forecast hour is rain, so "now" is raining, until 19.
        assertEquals("pada do ~19", c.heroLine)
    }

    @Test
    fun `a forecast fetched this hour is left alone`() {
        val justNow = java.time.OffsetDateTime.parse("2026-09-24T23:40:00+02:00").toInstant().toEpochMilli()
        assertEquals(fetchedLate, fetchedLate.rebasedTo(justNow))
    }

    @Test
    fun `old caches without an offset are left alone`() {
        assertEquals(lateEvening, lateEvening.rebasedTo(nextMorning))
    }
}
