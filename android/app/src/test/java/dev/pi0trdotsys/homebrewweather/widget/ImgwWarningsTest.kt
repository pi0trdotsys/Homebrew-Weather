package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImgwWarningsTest {

    /** Shaped like the real feed: `teryt` is an array, times are Warsaw's own. */
    private val feed = """
    [
      {"id":"Sk20261118104223044","nazwa_zdarzenia":"Silny wiatr","stopien":"1","prawdopodobienstwo":"70",
       "obowiazuje_do":"2026-11-19 01:00:00","obowiazuje_od":"2026-11-18 19:00:00","opublikowano":"2026-11-18 11:42:00",
       "tresc":"Prognozuje się wystąpienie silnego wiatru o średniej prędkości do 45 km/h i porywach do 80 km/h. Wiatr słabnie nad ranem.",
       "komentarz":"Brak.","biuro":"CBPM","teryt":["0201","0264","3001"]},
      {"id":"Up1","nazwa_zdarzenia":"Upał","stopien":"2","obowiazuje_od":"2026-11-18 10:00:00","obowiazuje_do":"2026-11-18 20:00:00",
       "tresc":"Upał.","teryt":["0264"]},
      {"id":"Old","nazwa_zdarzenia":"Mgła","stopien":"1","obowiazuje_od":"2026-11-17 01:00:00","obowiazuje_do":"2026-11-18 06:00:00",
       "tresc":"Mgła.","teryt":["0264"]},
      {"id":"Far","nazwa_zdarzenia":"Burze","stopien":"3","obowiazuje_od":"2026-11-20 10:00:00","obowiazuje_do":"2026-11-20 20:00:00",
       "tresc":"Burze.","teryt":["0264"]},
      {"id":"Elsewhere","nazwa_zdarzenia":"Silny mróz","stopien":"2","obowiazuje_od":"2026-11-18 10:00:00","obowiazuje_do":"2026-11-19 20:00:00",
       "tresc":"Mróz.","teryt":["1465"]}
    ]
    """.trimIndent()

    private val now = "2026-11-18T12:30"

    @Test
    fun `only warnings for the county that are valid now or soon come through`() {
        val ids = ImgwWarnings.parse(feed, "0264", now).map { it.id }
        assertEquals(listOf("Up1", "Sk20261118104223044"), ids) // by degree, highest first
    }

    @Test
    fun `a warning for another county is not ours`() {
        assertEquals(listOf("Elsewhere"), ImgwWarnings.parse(feed, "1465", now).map { it.id })
        assertTrue(ImgwWarnings.parse(feed, "9999", now).isEmpty())
    }

    @Test
    fun `one that has ended or starts beyond the look-ahead is left out`() {
        val ids = ImgwWarnings.parse(feed, "0264", now).map { it.id }
        assertFalse("Old" in ids)
        assertFalse("Far" in ids)
    }

    @Test
    fun `a warning starting right at the look-ahead edge counts, one minute later does not`() {
        val atEdge = feed.replace("2026-11-18 19:00:00", "2026-11-19 00:30:00") // exactly 12 h after 12:30
        assertTrue(ImgwWarnings.parse(atEdge, "0264", now).any { it.id.startsWith("Sk") })
        val past = feed.replace("2026-11-18 19:00:00", "2026-11-19 00:31:00")
        assertFalse(ImgwWarnings.parse(past, "0264", now).any { it.id.startsWith("Sk") })
    }

    @Test
    fun `fields are read as the feed gives them`() {
        val w = ImgwWarnings.parse(feed, "0264", now).first { it.id.startsWith("Sk") }
        assertEquals("Silny wiatr", w.event)
        assertEquals(1, w.degree)
        assertEquals("2026-11-18T19:00", w.from)
        assertEquals("2026-11-19T01:00", w.to)
        assertTrue(w.text.startsWith("Prognozuje się"))
    }

    @Test
    fun `a malformed feed or clock is an empty list, never a crash`() {
        assertTrue(ImgwWarnings.parse("not json", "0264", now).isEmpty())
        assertTrue(ImgwWarnings.parse("{}", "0264", now).isEmpty())
        assertTrue(ImgwWarnings.parse(feed, "0264", "garbage").isEmpty())
    }

    @Test
    fun `official events map onto the hazards the app derives itself`() {
        assertEquals(SevereWeather.Kind.WIND, ImgwWarnings.kindOf("Silny wiatr"))
        assertEquals(SevereWeather.Kind.HEAT, ImgwWarnings.kindOf("Upał"))
        assertEquals(SevereWeather.Kind.STORM, ImgwWarnings.kindOf("Burze"))
        assertEquals(SevereWeather.Kind.HAIL, ImgwWarnings.kindOf("Burze z gradem"))
        assertNull(ImgwWarnings.kindOf("Silny mróz"))
        assertNull(ImgwWarnings.kindOf("Mgła"))
    }

    @Test
    fun `only Poland is asked about`() {
        assertTrue(ImgwWarnings.inPoland(51.1, 17.0))
        assertTrue(ImgwWarnings.inPoland(54.35, 18.65))
        assertFalse(ImgwWarnings.inPoland(36.4, -5.1)) // Estepona
        assertFalse(ImgwWarnings.inPoland(52.5, 13.4)) // Berlin
    }

    @Test
    fun `the county code is read from the Nominatim tags`() {
        assertEquals("0264", PolandCounty.parseCode("""{"extratags":{"teryt:terc":"0264","name:prefix":"miasto na prawach powiatu"}}"""))
        assertEquals("2604", PolandCounty.parseCode("""{"extratags":{"teryt:terc":"2604"}}"""))
        assertEquals("2604", PolandCounty.parseCode("""{"extratags":{"teryt:terc":"2604011"}}"""))
        assertNull(PolandCounty.parseCode("""{"extratags":{}}"""))
        assertNull(PolandCounty.parseCode("""{"extratags":{"teryt:terc":"ab"}}"""))
        assertNull(PolandCounty.parseCode("nope"))
    }

    @Test
    fun `neighbouring places share a cell`() {
        assertEquals(PolandCounty.cellKey(51.1079, 17.0385), PolandCounty.cellKey(51.1101, 17.0412))
        assertTrue(PolandCounty.cellKey(51.1, 17.0) != PolandCounty.cellKey(51.3, 17.0))
    }

    @Test
    fun `the notification shows the first sentence and when it applies`() {
        val w = ImgwWarnings.parse(feed, "0264", now).first { it.id.startsWith("Sk") }
        val s = WeatherNotifier.officialSummary(w, PlTexts)
        assertTrue(s, s.startsWith("Prognozuje się wystąpienie silnego wiatru"))
        assertFalse("only the first sentence", s.contains("słabnie"))
        assertTrue(s, s.endsWith("(od 18.11 19:00 do 19.11 01:00)"))
        assertTrue(WeatherNotifier.officialSummary(w, EnTexts).endsWith("(from 18.11 19:00 to 19.11 01:00)"))
    }

    @Test
    fun `a very long first sentence is cut`() {
        val long = w("A".repeat(300))
        assertTrue(WeatherNotifier.officialSummary(long, PlTexts).length < 190)
    }

    private fun w(text: String) = WeatherApi.OfficialWarning("id", "Silny wiatr", 1, "2026-11-18T19:00", "2026-11-19T01:00", text)

    @Test
    fun `the widget and the brief lead with the official warning`() {
        val warning = w("Prognozuje się silny wiatr.")
        val forecast = WeatherApi.WeatherData(
            isDay = true, currentWeatherCode = 1, currentTemperature = 12.0, official = listOf(warning),
            daily = listOf(WeatherApi.DailyEntry("2026-11-18", 1, 14.0, 8.0, 0)),
            hourly = listOf(WeatherApi.HourlyEntry("2026-11-18T12:00", 0, 1)),
        )
        assertEquals("IMGW: silny wiatr 1°", WidgetContent.notableMeta(forecast, PlTexts))
        val t = MorningBrief.Thresholds(true, 30.0, true, 0.0, true, 8.0, true, 100.0)
        val brief = MorningBrief.compose("Wrocław", forecast, t, { "x" }, null, PlTexts)!!
        assertTrue(brief.full, brief.full.lines()[1].startsWith("IMGW: silny wiatr 1°"))
    }

    @Test
    fun `a notification scope is stable per place and differs between places and widgets`() {
        val a = WidgetCity(51.1, 17.0, "A")
        val b = WidgetCity(50.0, 19.9, "B")
        assertEquals(WeatherNotifier.scopeOf(7, a), WeatherNotifier.scopeOf(7, a.copy(name = "Renamed")))
        assertTrue(WeatherNotifier.scopeOf(7, a) != WeatherNotifier.scopeOf(7, b))
        assertTrue(WeatherNotifier.scopeOf(7, a) != WeatherNotifier.scopeOf(8, a))
        assertTrue(WeatherNotifier.scopeOf(Int.MAX_VALUE / 2, a) in 0 until 5_000_000)
    }
}
