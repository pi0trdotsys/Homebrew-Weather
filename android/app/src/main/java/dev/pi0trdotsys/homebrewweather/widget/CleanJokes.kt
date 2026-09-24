package dev.pi0trdotsys.homebrewweather.widget

import kotlin.math.abs

/**
 * Widget footer copy for [Tone.CLEAN]: the same one-line, weather-aware slot
 * as [SigmaJokes], in a voice you'd be fine with on a home screen someone
 * else is looking at — mildly wry, sometimes practical, never at anyone's
 * expense.
 *
 * Same structure and pick rule as [SigmaJokes] (night pool mixed in after
 * dark, index = |seed| % pool.size) and the same 50-character ceiling, for the
 * same reason — see [SigmaJokes.MAX_LINE_LENGTH]. Covered by WidgetContentTest.
 */
object CleanJokes {

    private val CLEAN: Map<String, List<String>> = mapOf(
        "sun" to listOf(
            "słońce w pełni. dobry dzień na spacer",
            "pogodnie. idealnie na kawę na zewnątrz",
            "słońce świeci, cień zaprasza",
            "jasno i ciepło, prognoza bez niespodzianek",
            "dzień, w którym okulary mają sens",
            "słonecznie. krem z filtrem nie zaszkodzi",
            "niebo bez chmur, plan dnia też może być",
            "świetna pogoda na odrobinę ruchu",
            "pełne słońce. woda w torbie się przyda",
            "pogoda jak z pocztówki",
            "słońce robi swoje, ty rób swoje",
            "jasno, ciepło, po prostu dobrze",
        ),
        "partly" to listOf(
            "trochę słońca, trochę chmur. w sam raz",
            "przejaśnienia. niebo jeszcze się waha",
            "słońce wychodzi i się chowa. zmiennie",
            "chmury przechodzą, dzień zostaje",
            "pogoda na kurtkę, którą można zdjąć",
            "niebo w kratkę, ale bez dramatu",
            "raz jasno, raz szaro. nic nadzwyczajnego",
            "chmury z przerwami na słońce",
            "zmienne zachmurzenie, stały plan",
            "pół na pół. da się z tym żyć",
            "dobry dzień, choć niebo niezdecydowane",
            "słońce na zmianę z cieniem",
        ),
        "cloud" to listOf(
            "pochmurno, ale sucho. da się wyjść",
            "szare niebo, spokojny dzień",
            "chmury nad głową, bez opadów w planie",
            "pochmurno. dobry dzień na skupienie",
            "światło miękkie jak w studiu",
            "szaro, ale nie mokro",
            "niebo zasłonięte, dzień i tak trwa",
            "pochmurno i spokojnie",
            "idealna pogoda na herbatę",
            "chmury bez deszczu to też dobra wiadomość",
            "szary dzień, jasne myśli",
            "zachmurzenie pełne, humor opcjonalny",
        ),
        "fog" to listOf(
            "mgła. jedź wolniej, zobaczysz więcej",
            "widoczność słaba, światła włączone",
            "mglisto. świat na chwilę zwolnił",
            "mgła zwykle opada przed południem",
            "gęsto za oknem, spokojnie w środku",
            "mgła jak z filmu, tylko zimniejsza",
            "widać niewiele, ale dzień się rozkręci",
            "ostrożnie na drodze, jest mglisto",
            "mgła. dobra pora na odblaski",
            "świat w trybie rozmytym",
            "powoli i uważnie, mgła nie pogania",
        ),
        "rain" to listOf(
            "pada. parasol to dziś dobry pomysł",
            "deszczowo. kurtka z kapturem się przyda",
            "krople na szybie, herbata w kubku",
            "mokro na zewnątrz, sucho pod parasolem",
            "deszcz. buty nieprzemakalne na start",
            "pada, ale deszcz też kiedyś mija",
            "dobry dzień na rzeczy do zrobienia w domu",
            "deszcz podlewa ogród za ciebie",
            "kałuże w programie dnia",
            "mokro. zostaw zapas czasu na dojazd",
            "pada równo i spokojnie",
            "deszcz pachnie jak świeży start",
        ),
        "snow" to listOf(
            "śnieg. ciepłe buty obowiązkowo",
            "biało za oknem, ślisko na chodniku",
            "sypie. zostaw zapas czasu na drogę",
            "zima w pełnej formie",
            "śnieg skrzypi, ubierz się na cebulkę",
            "biało i cicho, jak zimą bywa",
            "czapka, szalik, rękawiczki. komplet",
            "śnieg pada, świat zwalnia",
            "ślisko. krok po kroku",
            "dobra pogoda na gorącą czekoladę",
            "zimno i biało. przyda się szalik",
            "sypie równo, spokojnie",
        ),
        "thunder" to listOf(
            "burza. lepiej przeczekać w środku",
            "grzmi. okna zamknięte, sprzęt odłączony",
            "burzowo. spacer może poczekać",
            "błyska się. unikaj otwartych przestrzeni",
            "burza przejdzie, zwykle szybko",
            "grzmoty w tle, spokój w środku",
            "pogoda na przeczekanie z książką",
            "burza. parasol nie wystarczy, dach tak",
            "niebo robi hałas, ty zostań w cieple",
            "burza w drodze, schowaj rower",
            "grzmi i leje. do przeczekania",
            "burza. naładuj telefon na wszelki wypadek",
        ),
        "night" to listOf(
            "noc. dobra pora, żeby odpocząć",
            "ciemno za oknem, jutro nowa prognoza",
            "późno. sen to też plan",
            "noc spokojna, niebo w swoim tempie",
            "gwiazdy świecą, ekran może zgasnąć",
            "cisza nocna, także w prognozie",
            "jutro będzie dzień, dziś jest noc",
            "pora na odpoczynek",
            "noc chłodniejsza niż dzień, koc się przyda",
            "ciemno i cicho. idealnie na sen",
            "reszta prognozy rano",
        ),
    )

    /** All pools, exposed for the line-length test. */
    internal val pools: Map<String, List<String>> get() = CLEAN

    fun pick(kind: String, isNight: Boolean = false, seed: Int = 0): String {
        val kindPool = CLEAN[kind] ?: CLEAN.getValue("cloud")
        val pool = if (isNight) (CLEAN.getValue("night") + kindPool) else kindPool
        return pool[abs(seed) % pool.size]
    }
}
