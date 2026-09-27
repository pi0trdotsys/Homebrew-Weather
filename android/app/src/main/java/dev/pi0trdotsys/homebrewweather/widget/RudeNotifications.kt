package dev.pi0trdotsys.homebrewweather.widget

import kotlin.random.Random

/**
 * Push-notification copy in the app's already-established "sigma" voice (see
 * SigmaJokes.kt — same tone, same audience: this is
 * the user's own personal app, mocking themselves, not anyone else). The
 * default English notification strings ("heat's no joke today") read as
 * generic weather-app copy; these are deliberately rude/bad-boy/mocking
 * instead, per an explicit request to make alerts "chamskie, bad-boyowe,
 * złośliwe" — Polish, mildly-to-moderately vulgar, matching SigmaJokes.kt's
 * existing register (kurwa/chuj-family words, "sigma vs beta" mockery), not
 * hate speech or slurs and never aimed at a real person.
 *
 * Each function picks one of several variants (by [seed], typically derived
 * from the current time/day so repeated alerts don't always say the exact
 * same line) and formats in the dynamic value(s) (city, temperature, AQI…).
 */
object RudeNotifications {

    private fun pick(pool: List<String>, seed: Int): String =
        pool[Math.floorMod(seed, pool.size)]

    private fun randomSeed(): Int = Random.nextInt(0, 1_000_000)

    // ---------------------------------------------------------------------
    // Rain / thunderstorm incoming
    // ---------------------------------------------------------------------
    private val RAIN = listOf(
        "%s: leje jak skurwysyn, ogarnij kurtkę zamiast się mazać",
        "%s: burza nadchodzi, ruszaj dupsko po parasol",
        "%s: deszcz, kurwa, znowu. weź się w garść i ubierz jak trzeba",
        "%s: pada. kurtka albo zamokniesz jak ostatni frajer",
        "%s: burza łuk, wyłącz to gówniane wifi i się schowaj",
        "%s: leje jak z cebra, ogarnij się zanim wyjdziesz jak idiota",
    )
    private val THUNDER = listOf(
        "%s: burza z piorunami, ruchy, nie stój pod drzewem jak nieogar",
        "%s: grzmi jak twoje ego, ale lepiej się schowaj",
        "%s: piorun i grom, kurwa, zamknij okna i weź się ogarnij",
        "%s: burza szaleje, nie graj bohatera i wracaj do domu",
    )

    fun rain(city: String, isThunder: Boolean, seed: Int = randomSeed(), lang: Lang = Lang.PL): String {
        val template = pick(rainPool(isThunder, lang), seed)
        return String.format(template, city)
    }

    /**
     * The same rain/storm lines without the leading "<city>: ", for appending
     * under the factual rain-soon text ("deszcz w ciągu godziny, do ok. 19:00").
     * The fact goes first in every tone — it's what you need to read at a
     * glance on the lock screen — and the attitude comes after it.
     */
    fun rainTail(isThunder: Boolean, seed: Int = randomSeed(), lang: Lang = Lang.PL): String =
        pick(rainPool(isThunder, lang), seed).removePrefix("%s: ")

    private fun rainPool(isThunder: Boolean, lang: Lang) = when {
        lang == Lang.EN && isThunder -> EN_THUNDER
        lang == Lang.EN -> EN_RAIN
        isThunder -> THUNDER
        else -> RAIN
    }

    // ---------------------------------------------------------------------
    // Morning brief closing line
    // ---------------------------------------------------------------------
    private val BRIEF_TAIL = listOf(
        "masz prognozę, nie masz wymówek",
        "przeczytałeś, to teraz rusz dupę",
        "kawa, spodnie, wychodzisz. w tej kolejności",
        "dzień sam się nie przeżyje, ogarnij się",
        "wstawaj, kurwa, pogoda nie będzie czekać",
        "tyle wiesz, reszta to twoje wymówki",
        "nie marudź, inni mają gorzej i nie dostają briefów",
        "prognoza jest, plan dnia jakoś nie bardzo",
    )

    fun briefTail(seed: Int = randomSeed(), lang: Lang = Lang.PL): String =
        pick(if (lang == Lang.EN) EN_BRIEF_TAIL else BRIEF_TAIL, seed)

    // ---------------------------------------------------------------------
    // High temperature
    // ---------------------------------------------------------------------
    private val HIGH = listOf(
        "%s: %d°C, spierdalaj z tym leniuchowaniem i się nawadniaj",
        "%s: %d°C, mózg ci się gotuje jak twoje pomysły na życie",
        "%s: gorąco jak w piekle, %d°C, nie bądź cieniasem, pij wodę",
        "%s: %d°C, słońce piecze mocniej niż twoje wymówki",
        "%s: upał, %d°C, ogarnij się zanim padniesz jak ofiara",
    )

    fun highTemp(city: String, tempC: Int, seed: Int = randomSeed(), lang: Lang = Lang.PL): String =
        String.format(pick(if (lang == Lang.EN) EN_HIGH else HIGH, seed), city, tempC)

    // ---------------------------------------------------------------------
    // Low temperature
    // ---------------------------------------------------------------------
    private val LOW = listOf(
        "%s: %d°C, zamarzniesz jak twoje szanse na randkę, ubierz się",
        "%s: kurwa zimno, %d°C, kurtka albo przestań marudzić",
        "%s: %d°C, mróz jak twoje serce, ale wyjdź z domu i tak",
        "%s: %d°C na dworze, zjeb się nie ubierając, twoja sprawa",
        "%s: %d°C, zimno jak twój Discord status, weź czapkę",
    )

    fun lowTemp(city: String, tempC: Int, seed: Int = randomSeed(), lang: Lang = Lang.PL): String =
        String.format(pick(if (lang == Lang.EN) EN_LOW else LOW, seed), city, tempC)

    // ---------------------------------------------------------------------
    // Big day-to-day temperature swing
    // ---------------------------------------------------------------------
    private val SWING_UP = listOf(
        "%s: jutro skok z %d° na %d°, ogarnij ciuchy, nie bądź ogarem",
        "%s: %d° -> %d° jutro, pogoda zmienia się szybciej niż twoje relacje",
        "%s: robi się gorąco, %d° -> %d° jutro, zdejmij tę kurtkę na czas",
    )
    private val SWING_DOWN = listOf(
        "%s: jutro spadek z %d° na %d°, ogarnij ciuchy albo zamarzniesz jak frajer",
        "%s: %d° -> %d° jutro, dresiarski styl warstwowy, ruchy",
        "%s: robi się zimno, %d° -> %d° jutro, nie graj twardziela bez kurtki",
    )

    fun swing(
        city: String,
        todayMax: Int,
        tomorrowMax: Int,
        warming: Boolean,
        seed: Int = randomSeed(),
        lang: Lang = Lang.PL,
    ): String {
        val pool = when {
            lang == Lang.EN && warming -> EN_SWING_UP
            lang == Lang.EN -> EN_SWING_DOWN
            warming -> SWING_UP
            else -> SWING_DOWN
        }
        return String.format(pick(pool, seed), city, todayMax, tomorrowMax)
    }

    // ---------------------------------------------------------------------
    // Air quality
    // ---------------------------------------------------------------------
    private val AQI = listOf(
        "%s: AQI %d (%s), powietrze gorsze niż twoje decyzje, zostań w chacie",
        "%s: smog jak chuj, AQI %d, nie biegaj na dworze jak ćwok",
        "%s: AQI %d (%s), oddychasz gównem, odpuść trening na zewnątrz",
        "%s: powietrze do dupy, AQI %d, maska albo zostań w domu jak sigma",
    )

    fun aqi(city: String, aqiValue: Int, label: String, seed: Int = randomSeed(), lang: Lang = Lang.PL): String =
        String.format(pick(if (lang == Lang.EN) EN_AQI else AQI, seed), city, aqiValue, label)

    // ---------------------------------------------------------------------
    // English (Lang.EN): the same register, in English slang.
    // ---------------------------------------------------------------------
    private val EN_RAIN = listOf(
        "%s: pouring like hell, grab a jacket and stop whining",
        "%s: rain again, damn it. dress like an adult",
        "%s: it's raining. jacket or get soaked like a clown",
        "%s: coming down hard, sort yourself out before going out",
    )
    private val EN_THUNDER = listOf(
        "%s: thunderstorm, don't stand under a tree like an idiot",
        "%s: thunder louder than your ego, get inside",
        "%s: lightning, damn it, close the windows and get a grip",
    )
    private val EN_BRIEF_TAIL = listOf(
        "you've got the forecast, no excuses",
        "read it? now move your ass",
        "coffee, pants, out the door. in that order",
        "the day won't live itself, get going",
        "that's the forecast. the rest is your excuses",
    )
    private val EN_HIGH = listOf(
        "%s: %d°C, stop lazing around and drink some water",
        "%s: %d°C, your brain's cooking like your life plans",
        "%s: hot as hell, %d°C, don't be a wimp, hydrate",
    )
    private val EN_LOW = listOf(
        "%s: %d°C, freezing like your dating chances, dress up",
        "%s: damn cold, %d°C, jacket or stop complaining",
        "%s: %d°C, cold as your heart, go out anyway",
    )
    private val EN_SWING_UP = listOf(
        "%s: jumping from %d° to %d° tomorrow, sort your clothes out",
        "%s: %d° -> %d° tomorrow, weather changes faster than your moods",
    )
    private val EN_SWING_DOWN = listOf(
        "%s: dropping from %d° to %d° tomorrow, dress up or freeze",
        "%s: %d° -> %d° tomorrow, don't play tough without a jacket",
    )
    private val EN_AQI = listOf(
        "%s: AQI %d (%s), air worse than your decisions, stay in",
        "%s: smog as hell, AQI %d, don't jog out there like a fool",
        "%s: AQI %d (%s), you're breathing garbage, train indoors",
    )
}
