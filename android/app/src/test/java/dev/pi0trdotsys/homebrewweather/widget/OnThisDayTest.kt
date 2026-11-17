package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnThisDayTest {

    private val wikitext = """
== Święta ==
* Międzynarodowy Dzień Studenta
== Wydarzenia w Polsce ==
* [[1370]] – [[Ludwik Węgierski]] został królem Polski.
* [[1892]] – Założono [[Polska Partia Socjalistyczna|Polską Partię Socjalistyczną]].
* 1923 – Rozpoczęła działalność ''Transatlantycka Centrala''.<ref>Przypis</ref>
== Wydarzenia na świecie ==
=== XVI wiek ===
* [[1558]] – [[Elżbieta I Tudor]] została królową Anglii.
* [[1789]] – Wydarzenie z {{Cytuj|x=y}} szablonem i długim opisem, który na pewno nie zmieści się w pięćdziesięciu znakach.
== Urodzili się ==
* [[1790]] – [[August Möbius]], niemiecki matematyk
== Zmarli ==
* [[1998]] – ktoś, kto zmarł
""".trimIndent()

    @Test
    fun `only the two events sections are read, births and deaths are left out`() {
        val events = OnThisDay.parsePolishWikitext(wikitext)
        assertEquals(listOf("1370", "1892", "1923", "1558", "1789"), events.map { it.year })
    }

    @Test
    fun `wiki markup is reduced to plain text`() {
        val events = OnThisDay.parsePolishWikitext(wikitext)
        assertEquals("Ludwik Węgierski został królem Polski.", events[0].text)
        assertEquals("Założono Polską Partię Socjalistyczną.", events[1].text)
        assertEquals("Rozpoczęła działalność Transatlantycka Centrala.", events[2].text)
        assertTrue(events[4].text.startsWith("Wydarzenie z  szablonem".replace("  ", " ")))
    }

    @Test
    fun `templates that carry text keep it`() {
        assertEquals(
            "Sándor Wekerle został premierem Królestwa Węgier.",
            OnThisDay.cleanWikitext("{{link-interwiki |Sándor Wekerle |Q=Q340174}} został [[Premierzy Węgier|premierem Królestwa Węgier]]."),
        )
        assertEquals("rekord: 19°C", OnThisDay.cleanWikitext("rekord: {{C|19}}<ref>{{cytuj|x=y}}</ref>"))
        assertEquals("a b", OnThisDay.cleanWikitext("a {{cytuj stronę|x}} b"))
    }

    @Test
    fun `the english feed is read as year and text`() {
        val json = """{"selected":[{"text":"Apollo 11 landed on the Moon.","year":1969},{"text":"","year":1}]}"""
        assertEquals(listOf(OnThisDay.Event("1969", "Apollo 11 landed on the Moon.")), OnThisDay.parseEnglishFeed(json))
    }

    @Test
    fun `short events are preferred and the day of the year picks among them`() {
        val short1 = OnThisDay.Event("1892", "Założono PPS.")
        val short2 = OnThisDay.Event("1920", "Weszła w życie konstytucja.")
        val long = OnThisDay.Event("1735", "W dobrach Schaffgotschów w Karkonoszach została utworzona specjalna gmina górska.")
        val events = listOf(long, short1, long, short2)
        assertEquals(short1, OnThisDay.pick(events, 2))
        assertEquals(short2, OnThisDay.pick(events, 3))
        assertEquals("wraps around", short1, OnThisDay.pick(events, 4))
    }

    @Test
    fun `with nothing short the whole list is used`() {
        val a = OnThisDay.Event("1735", "x".repeat(60))
        val b = OnThisDay.Event("1736", "y".repeat(60))
        assertEquals(b, OnThisDay.pick(listOf(a, b), 1))
    }

    @Test
    fun `an empty list picks nothing`() {
        assertNull(OnThisDay.pick(emptyList(), 100))
    }

    @Test
    fun `the footer line never exceeds the limit and is cut at a word`() {
        val event = OnThisDay.Event("1969", "Astronauci Apollo 11 wylądowali na Księżycu po raz pierwszy w historii")
        val line = OnThisDay.fit(event)
        assertTrue(line, line.length <= WellbeingTips.MAX_LINE_LENGTH)
        assertTrue(line.endsWith("…"))
        assertTrue(line.startsWith("1969 · Astronauci"))
        assertTrue("cut at a word boundary: $line", !line.dropLast(1).endsWith("wylądowal"))
    }

    @Test
    fun `a line that fits is left alone`() {
        assertEquals("1892 · Założono PPS.", OnThisDay.fit(OnThisDay.Event("1892", "Założono PPS.")))
    }
}
