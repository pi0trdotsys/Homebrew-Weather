package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {

    private val home = WidgetCity(51.10, 17.03, "Wrocław")
    private val work = WidgetCity(50.26, 19.02, "Katowice")
    private val away = WidgetCity(52.23, 21.01, "Warszawa")
    private val live = WidgetCity(51.0, 17.0, "Piekoszów", isLive = true)

    private fun state(vararg c: WidgetCity, at: Int = 0) = Places.State(c.toList(), at)

    @Test
    fun `an empty state has no current place and cannot cycle`() {
        assertNull(Places.State.EMPTY.current)
        assertFalse(Places.State.EMPTY.canCycle)
        assertNull(Places.badge(Places.State.EMPTY))
    }

    @Test
    fun `replacing on an empty state creates the first place`() {
        val s = Places.replaceCurrent(Places.State.EMPTY, home)
        assertEquals(listOf(home), s.list)
        assertEquals(home, s.current)
    }

    @Test
    fun `replacing swaps only the current place`() {
        val s = Places.replaceCurrent(state(home, work, at = 1), away)
        assertEquals(listOf(home, away), s.list)
        assertEquals(away, s.current)
    }

    @Test
    fun `replacing with a place that is already listed does not show it twice`() {
        val s = Places.replaceCurrent(state(home, work, at = 1), home)
        assertEquals(listOf(home), s.list)
        assertEquals(home, s.current)
    }

    @Test
    fun `replacing keeps the index on the right place after dropping an earlier duplicate`() {
        val s = Places.replaceCurrent(state(home, work, away, at = 2), home)
        assertEquals(listOf(work, home), s.list)
        assertEquals(1, s.index)
        assertEquals(home, s.current)
    }

    @Test
    fun `adding appends and selects the new place`() {
        val s = Places.add(state(home), work)
        assertEquals(listOf(home, work), s.list)
        assertEquals(work, s.current)
    }

    @Test
    fun `adding a place that is already listed selects it instead`() {
        val s = Places.add(state(home, work, at = 1), home.copy(name = "Wroclaw"))
        assertEquals(2, s.size)
        assertEquals(0, s.index)
        assertEquals("a repeat refreshes the stored name", "Wroclaw", s.current?.name)
    }

    @Test
    fun `adding to a full list replaces the current place rather than doing nothing`() {
        val full = Places.State((1..Places.MAX).map { WidgetCity(40.0 + it, 10.0, "P$it") }, 2)
        val s = Places.add(full, away)
        assertEquals(Places.MAX, s.size)
        assertEquals(away, s.current)
        assertEquals(2, s.index)
    }

    @Test
    fun `there is only ever one live place`() {
        val moved = live.copy(lat = 50.0, lon = 16.0, name = "Kielce")
        val s = Places.add(state(home, live, at = 1), moved)
        assertEquals(2, s.size)
        assertEquals("Kielce", s.current?.name)
    }

    @Test
    fun `a live place is never the same as a fixed one at the same spot`() {
        assertFalse(Places.same(live, WidgetCity(live.lat, live.lon, "x")))
        assertTrue(Places.same(live, live.copy(lat = 1.0, lon = 2.0)))
    }

    @Test
    fun `places within about a kilometre are the same place`() {
        assertTrue(Places.same(home, home.copy(lat = home.lat + 0.005)))
        assertFalse(Places.same(home, home.copy(lat = home.lat + 0.05)))
    }

    @Test
    fun `next wraps around and a single place stays put`() {
        val s = state(home, work, away, at = 2)
        assertEquals(0, Places.next(s).index)
        assertEquals(1, Places.next(Places.next(s)).index)
        assertEquals(state(home), Places.next(state(home)))
    }

    @Test
    fun `removing a place before the current one keeps the same place current`() {
        val s = Places.remove(state(home, work, away, at = 2), 0)
        assertEquals(listOf(work, away), s.list)
        assertEquals(away, s.current)
    }

    @Test
    fun `removing the current last place moves to the new last one`() {
        val s = Places.remove(state(home, work, at = 1), 1)
        assertEquals(listOf(home), s.list)
        assertEquals(home, s.current)
    }

    @Test
    fun `removing the only place empties the state`() {
        assertEquals(Places.State.EMPTY, Places.remove(state(home), 0))
    }

    @Test
    fun `removing out of range changes nothing`() {
        val s = state(home, work)
        assertEquals(s, Places.remove(s, 5))
    }

    @Test
    fun `the badge says which of how many`() {
        assertEquals("2/3", Places.badge(state(home, work, away, at = 1)))
        assertNull(Places.badge(state(home)))
    }

    @Test
    fun `a live place keeps one cache key however far it moves`() {
        assertEquals("live", Places.cacheKey(live))
        assertEquals("live", Places.cacheKey(live.copy(lat = 10.0, lon = 10.0)))
        assertEquals("51.10_17.03", Places.cacheKey(home))
        assertTrue(Places.cacheKey(home) != Places.cacheKey(work))
    }
}
