package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            WeatherApi.HourlyEntry(
                time = "2026-09-24T%02d:00".format(h),
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
        assertEquals("deszcz 15:00-19:00", RainWindow.short(w))
        assertEquals("deszcz od 15:00 do ok. 19:00", RainWindow.long(w))
    }

    @Test
    fun `window running past the data is open-ended`() {
        val w = RainWindow.find(hours(11, 0, 0, 80, 80, 80), clear)!!
        assertNull(w.end)
        assertEquals("deszcz od 13:00", RainWindow.short(w))
    }

    @Test
    fun `raining now counts even when the hourly probability is low`() {
        val w = RainWindow.find(hours(11, 20, 60, 10, 0), rain)!!
        assertTrue(w.ongoing)
        assertEquals(0, w.startsInHours)
        assertEquals("13:00", w.end)
        assertEquals("pada do ~13:00", RainWindow.short(w))
    }

    @Test
    fun `likely this hour but not observed yet is soon, not ongoing`() {
        val w = RainWindow.find(hours(11, 80, 80, 0), clear)!!
        assertFalse(w.ongoing)
        assertEquals("deszcz wkrótce, do ~13:00", RainWindow.short(w))
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
        assertEquals("burza 15:00-18:00", RainWindow.short(w))
    }

    @Test
    fun `snow is named as snow`() {
        val w = RainWindow.find(hours(6, 0, 70, 70, 0, codes = listOf(clear, snow, snow, clear)), clear)!!
        assertEquals("śnieg 07:00-09:00", RainWindow.short(w))
    }

    @Test
    fun `window crossing midnight keeps wall-clock times`() {
        val w = RainWindow.find(hours(22, 0, 80, 80, 80, 0), clear)!!
        assertEquals("deszcz 23:00-02:00", RainWindow.short(w))
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
    }
}
