package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaLineArtTest {

    private fun r(value: String, glyph: MetaGlyph? = MetaGlyph.SUN) = Readout(glyph, value, value)

    @Test
    fun `a readout is its icon, a gap and its value`() {
        val one = r("9")
        assertEquals(7 + 2 + PixelFont.widthUnits("9"), MetaLineArt.unitsOf(one))
        assertEquals(PixelFont.widthUnits("km/h"), MetaLineArt.unitsOf(r("km/h", null)))
    }

    @Test
    fun `the scale is the largest whole one that fits both ways`() {
        val readouts = listOf(r("9"), r("71 km/h", MetaGlyph.WIND))
        val units = readouts.sumOf(MetaLineArt::unitsOf) + 5
        val l = MetaLineArt.layout(readouts, maxWidthPx = units * 4 + 3, maxHeightPx = 40)!!
        assertEquals(4, l.scale) // width allows 4; height 40 / 7 = 5
        assertEquals(5, MetaLineArt.layout(readouts, maxWidthPx = 10_000, maxHeightPx = 40)!!.scale)
        assertEquals(2, MetaLineArt.layout(readouts, maxWidthPx = 10_000, maxHeightPx = 15)!!.scale)
    }

    @Test
    fun `when they do not all fit the last ones are dropped, not the type shrunk below one pixel`() {
        val readouts = listOf(r("9"), r("71 km/h", MetaGlyph.WIND), r("brzoza", MetaGlyph.POLLEN))
        val twoUnits = readouts.take(2).sumOf(MetaLineArt::unitsOf) + 5
        val l = MetaLineArt.layout(readouts, maxWidthPx = twoUnits, maxHeightPx = 7)!!
        assertEquals(listOf("9", "71 km/h"), l.readouts.map { it.value })
        assertEquals(1, l.scale)
    }

    @Test
    fun `not even one readout fitting is no line at all`() {
        assertNull(MetaLineArt.layout(listOf(r("71 km/h", MetaGlyph.WIND)), maxWidthPx = 10, maxHeightPx = 40))
        assertNull(MetaLineArt.layout(emptyList(), maxWidthPx = 1000, maxHeightPx = 40))
    }

    @Test
    fun `every icon is seven cells tall and rectangular`() {
        MetaGlyph.entries.forEach { g ->
            assertEquals(g.name, 7, g.rows.size)
            assertTrue(g.name, g.rows.all { it.length == g.width && it.all { c -> c == '#' || c == '.' } })
            assertTrue("${g.name} must draw something", g.rows.any { '#' in it })
        }
    }

    @Test
    fun `the font covers every character the meta line can say in either language`() {
        val texts = listOf(PlTexts, EnTexts)
        val strings = mutableListOf<String>()
        texts.forEach { t ->
            Pollen.Species.entries.forEach { sp -> strings += t.pollenName(sp) + "!" }
            strings += listOf(t.inMinutes(40), t.mm(2.7), t.mm(12.0), "71 km/h", "42/71 km/h", "94%", "13°", "silny wiatr 1°")
        }
        // Polish event names IMGW is likely to send.
        strings += listOf("burze z gradem 2°", "intensywne opady deszczu 1°", "upał 3°", "silny mróz 2°", "mgła 1°", "zawieje i zamiecie śnieżne 2°", "opady marznące 2°", "roztopy 1°")
        strings.forEach { s ->
            val bad = s.filter { !PixelFont.covers(it) }
            assertTrue("'$s' has uncovered characters: $bad", bad.isEmpty())
        }
    }

    @Test
    fun `sanitising swaps what the font lacks for its nearest letter, never a box`() {
        assertEquals("Lodz", PixelFont.sanitize("Łódz").replace("ó", "o"))
        assertEquals("ABC", PixelFont.sanitize("ĄBĆ"))
        assertEquals("a b", PixelFont.sanitize("a b"))
        assertEquals("?", PixelFont.sanitize("☃"))
        assertTrue(PixelFont.sanitize("Žółć świń ąę").all { PixelFont.covers(it) })
    }

    @Test
    fun `the rendered widths agree with the layout`() {
        // Android's Bitmap is a stub under JVM tests; the arithmetic is what matters here.
        val l = MetaLineArt.layout(listOf(r("9"), r("brzoza", MetaGlyph.POLLEN)), 500, 28)!!
        assertNotNull(l)
        assertEquals(l.readouts.sumOf(MetaLineArt::unitsOf) + 5, l.widthUnits)
    }

    @Test
    fun `the standard readouts carry their icons and keep the old plain text`() {
        val w = WeatherApi.WeatherData(
            isDay = true, currentWeatherCode = 1, currentTemperature = 20.0, apparentTemperature = 14.0,
            windSpeedKmh = 45.0, humidityPercent = 95,
            daily = emptyList(), hourly = emptyList(),
        )
        val rs = WidgetContent.notableReadouts(w, PlTexts)
        assertEquals(listOf(MetaGlyph.THERMOMETER, MetaGlyph.WIND, MetaGlyph.DROP_HOLLOW), rs.map { it.glyph })
        assertEquals(listOf("14°", "45 km/h", "95%"), rs.map { it.value })
        assertEquals("odczuwalna 14° · wiatr 45 km/h · wilgotno 95%", WidgetContent.notableMeta(w, PlTexts))
    }

    @Test
    fun `full density folds the gust into the wind readout`() {
        val w = WeatherApi.WeatherData(
            isDay = true, currentWeatherCode = 1, currentTemperature = 20.0, apparentTemperature = 18.0,
            windSpeedKmh = 20.0, windGustKmh = 45.0, humidityPercent = 50,
            daily = emptyList(), hourly = emptyList(),
        )
        val values = WidgetContent.fullMetaReadouts(w, EnTexts).map { it.value }
        assertEquals(listOf("18°", "50%", "20/45 km/h"), values)
    }
}
