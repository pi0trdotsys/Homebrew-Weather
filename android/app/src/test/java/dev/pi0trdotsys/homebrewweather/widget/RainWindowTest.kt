package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RainWindowTest {

    private val clear = 0
    private val rain = 61
    private val snow = 71
    private val thunder = 95

    /** Builds an hourly series starting at [startHour], one entry per pop. */
    private fun hours(startHour: Int, vararg pops: Int, codes: List<Int>? = null) =
        pops.mapIndexed { i, p ->
            val h = (startHour + i) % 24
            val day = 24 + (startHour + i) / 24
            WeatherApi.HourlyEntry(
                time = "2026-09-%02dT%02d:00".format(day, h),
                precipitationProbability = p,
                weatherCode = codes?.getOrNull(i) ?: if (p >= 50) rain else clear,
            )
        }

    @Test
    fun `dry forecast has no window`() {
        assertNull(RainWindow.find(hours(11, 0, 10, 20, 30, 40, 10), clear))
    }

    @Test
    fun `below likely threshold is not rain`() {
        // 49% for hours on end is "might", not "will" — no sentence.
        assertNull(RainWindow.find(hours(11, 49, 49, 49, 49), clear))
    }

    @Test
    fun `future window with an end`() {
        val w = RainWindow.find(hours(11, 0, 0, 0, 0, 70, 80, 90, 60, 20, 0), clear)!!
        assertEquals(4, w.startsInHours)
        assertEquals("15:00", w.start)
        assertEquals("19:00", w.end)
        assertFalse(w.ongoing)
        assertEquals("deszcz 15–19", RainWindow.short(w))
        assertEquals("deszcz od 15:00 do ok. 19:00", RainWindow.long(w))
    }

    @Test
    fun `window running past the data is open-ended`() {
        val w = RainWindow.find(hours(11, 0, 0, 80, 80, 80), clear)!!
        assertNull(w.end)
        assertEquals("deszcz od 13", RainWindow.short(w))
    }

    @Test
    fun `raining now counts even when the hourly probability is low`() {
        val w = RainWindow.find(hours(11, 20, 60, 10, 0), rain)!!
        assertTrue(w.ongoing)
        assertEquals(0, w.startsInHours)
        assertEquals("13:00", w.end)
        assertEquals("pada do ~13", RainWindow.short(w))
    }

    @Test
    fun `likely this hour but not observed yet is soon, not ongoing`() {
        val w = RainWindow.find(hours(11, 80, 80, 0), clear)!!
        assertFalse(w.ongoing)
        assertEquals("deszcz wkrótce, do ~13", RainWindow.short(w))
    }

    @Test
    fun `start beyond the horizon is ignored`() {
        val pops = IntArray(RainWindow.START_HORIZON_HOURS + 1) { 0 } + intArrayOf(90, 90)
        assertNull(RainWindow.find(hours(8, *pops), clear))
    }

    @Test
    fun `thunder anywhere in the window wins`() {
        val w = RainWindow.find(
            hours(14, 0, 70, 90, 60, 0, codes = listOf(clear, rain, thunder, rain, clear)),
            clear,
        )!!
        assertEquals(RainWindow.Kind.THUNDER, w.kind)
        assertEquals("burza 15–18", RainWindow.short(w))
    }

    @Test
    fun `snow is named as snow`() {
        val w = RainWindow.find(hours(6, 0, 70, 70, 0, codes = listOf(clear, snow, snow, clear)), clear)!!
        assertEquals("śnieg 7–9", RainWindow.short(w))
    }

    @Test
    fun `window crossing midnight keeps wall-clock times`() {
        val w = RainWindow.find(hours(22, 0, 80, 80, 80, 0), clear)!!
        assertEquals("deszcz 23–2", RainWindow.short(w))
    }

    @Test
    fun `short forms fit the hero line`() {
        // The hero condition line has room for ~20 monospace cells at reference
        // scale (WidgetMetrics caps nowLineSp at heroWidth / 20 cells); the
        // longest short form must not depend on ellipsizing to fit.
        val worst = listOf(
            RainWindow.Window(RainWindow.Kind.SNOW, 0, "11:00", "23:00", ongoing = false),
            RainWindow.Window(RainWindow.Kind.RAIN, 3, "14:00", "23:00", ongoing = false),
            RainWindow.Window(RainWindow.Kind.RAIN, 0, "11:00", null, ongoing = true),
        )
        worst.forEach { w ->
            val s = RainWindow.short(w)
            assertTrue("'$s' is ${s.length} chars", s.length <= 26)
        }
    }

    @Test
    fun `empty hourly data yields nothing rather than guessing`() {
        assertNull(RainWindow.find(emptyList(), rain))
        assertNull(RainWindow.today(emptyList(), rain))
    }

    // --- today() / forDay(): each window belongs to its own day -------------

    /** The real case that prompted this: 23:00, dry tonight, rain 08-18 tomorrow. */
    private val lateEvening = hours(23, 0, 0, 0, 0, 0, 0, 0, 20, 30, 58, 55, 50, 58, 78, 90, 90, 88, 83, 78, 70, 40, 20)

    @Test
    fun `tomorrow's rain is not today's hero sentence`() {
        assertNull(RainWindow.today(lateEvening, clear))
        // The notification's horizon rule still sees it — it just isn't "today".
        assertNotNull(RainWindow.find(lateEvening, clear))
    }

    @Test
    fun `tomorrow's rain lands on tomorrow's column`() {
        val d = RainWindow.forDay(lateEvening, "2026-09-25")!!
        assertEquals(8, d.startHour)
        assertEquals(19, d.endHour)
        assertEquals("8–19", RainWindow.dayShort(d))
        assertNull("nothing wet left today", RainWindow.forDay(lateEvening, "2026-09-24"))
    }

    @Test
    fun `rain later tonight is still today`() {
        val w = RainWindow.today(hours(7, *IntArray(14) { 0 }, 80, 80, 0), clear)!!
        assertEquals("21:00", w.start)
        assertEquals("deszcz 21–23", RainWindow.short(w))
    }

    @Test
    fun `a window ending at midnight reads 24`() {
        val w = RainWindow.today(hours(20, 0, 80, 80, 80, 0), clear)!!
        assertEquals("deszcz 21–24", RainWindow.short(w))
        val d = RainWindow.forDay(hours(20, 0, 80, 80, 80, 0), "2026-09-24")!!
        assertEquals("21–24", RainWindow.dayShort(d))
    }

    @Test
    fun `a second spell the same day is flagged, not merged`() {
        // 8-10 wet, dry, 16-19 wet: "8–10+" rather than a misleading "8–19".
        val day = hours(0, *IntArray(8) { 0 }, 70, 70, 0, 0, 0, 0, 0, 0, 80, 80, 80, 0, 0, 0, 0, 0)
        assertEquals("8–10+", RainWindow.dayShort(RainWindow.forDay(day, "2026-09-24")!!))
    }

    @Test
    fun `data ending mid-day while wet is open-ended`() {
        val d = RainWindow.forDay(hours(10, 0, 0, 80, 80), "2026-09-24")!!
        assertNull(d.endHour)
        assertEquals("od 12", RainWindow.dayShort(d))
    }
}
