package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCitiesTest {

    private fun fixed(n: Int) = WidgetCity(40.0 + n, 10.0 + n, "City $n")
    private val live = WidgetCity(51.0, 17.0, "Piekoszów", isLive = true)

    @Test
    fun `a new city goes to the front`() {
        val list = AppCities.with(listOf(fixed(1), fixed(2)), fixed(3))
        assertEquals(listOf("City 3", "City 1", "City 2"), list.map { it.name })
    }

    @Test
    fun `a city already saved keeps its place and refreshes its name`() {
        val list = AppCities.with(listOf(fixed(1), fixed(2), fixed(3)), fixed(2).copy(name = "Renamed"))
        assertEquals(listOf("City 1", "Renamed", "City 3"), list.map { it.name })
    }

    @Test
    fun `the live entry takes one slot and moves in place`() {
        val start = listOf(fixed(1), live, fixed(2))
        val list = AppCities.with(start, live.copy(lat = 50.0, lon = 16.0, name = "Kielce"))
        assertEquals(listOf("City 1", "Kielce", "City 2"), list.map { it.name })
        assertTrue(list[1].isLive)
    }

    @Test
    fun `a first live entry is added at the front`() {
        assertEquals("Piekoszów", AppCities.with(listOf(fixed(1)), live).first().name)
    }

    @Test
    fun `the list is capped and drops from the end, never the entry just added`() {
        val full = (1..AppCities.MAX).map(::fixed)
        val list = AppCities.with(full, fixed(99))
        assertEquals(AppCities.MAX, list.size)
        assertEquals("City 99", list.first().name)
        assertFalse(list.any { it.name == "City ${AppCities.MAX}" })
    }

    @Test
    fun `it reads and writes the shape the web app uses`() {
        val json = AppCities.serialize(listOf(fixed(1), live))
        assertFalse("a fixed city carries no flag", json.substringBefore("City 2").contains("live\":false"))
        val back = AppCities.parse(json)
        assertEquals(2, back.size)
        assertFalse(back[0].isLive)
        assertTrue(back[1].isLive)
        assertEquals(
            listOf(WidgetCity(36.43, -5.15, "Estepona, Spain")),
            AppCities.parse("""[{"lat":36.43,"lon":-5.15,"name":"Estepona, Spain"}]"""),
        )
    }

    @Test
    fun `garbage is an empty list`() {
        assertTrue(AppCities.parse(null).isEmpty())
        assertTrue(AppCities.parse("not json").isEmpty())
        assertTrue(AppCities.parse("""[{"name":"no coordinates"}]""").isEmpty())
    }
}
