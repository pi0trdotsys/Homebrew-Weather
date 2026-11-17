package dev.pi0trdotsys.homebrewweather.widget

import kotlin.math.abs

/**
 * A short well-being tip for the widget footer (and the dashboard), as an
 * alternative to a joke — the "footer source" setting picks one of
 * joke / well-being tip / on this day.
 *
 * The tip follows the weather: heat, frost, rain, a strong sun, bad air, wind or
 * night each have their own pool, with a general one for an ordinary day (see
 * [situation] for the rule, mirrored in src/lib/wellbeing.ts). Same pick rule
 * as the jokes (index = |seed| % pool size, the seed being the hour) and the
 * same one-line ceiling, for the same reason — see [SigmaJokes.MAX_LINE_LENGTH].
 *
 * Deliberately everyday advice, not medical advice: water, shade, a stretch,
 * light, sleep. Nothing here claims to treat or prevent anything.
 *
 * This file is the source of truth for the wording; src/lib/wellbeing.generated.ts
 * is generated from it (bun scripts/gen-jokes.ts), and CI fails if it drifts.
 * Covered by WellbeingTipsTest.
 */
object WellbeingTips {

    /** The footer is one line; same ceiling as the jokes. */
    const val MAX_LINE_LENGTH = SigmaJokes.MAX_LINE_LENGTH

    /** What the tip is about, most pressing first (see [situation]). */
    enum class Situation(val key: String) {
        AIR("air"),
        SUN("sun"),
        HOT("hot"),
        COLD("cold"),
        RAIN("rain"),
        WIND("wind"),
        NIGHT("night"),
        CALM("calm"),
    }

    /** Air quality from here is worth a tip about staying in. US AQI "unhealthy for sensitive groups". */
    const val AIR_FROM_AQI = 101

    /** The sun's UV index from which a tip about protection beats the general ones. */
    const val SUN_FROM_UV = 8

    const val HOT_FROM_C = 30
    const val COLD_UP_TO_C = 0
    const val WIND_FROM_GUST_KMH = 60

    /**
     * The situation the tip should speak to, first match wins:
     * bad air, strong sun (daytime only), heat, frost, rain or a storm falling
     * now, strong gusts, night, otherwise calm.
     *
     * Every reading may be unknown (NaN / -1, a cache from before it was kept),
     * and an unknown never triggers a situation.
     */
    fun situation(weather: WeatherApi.WeatherData): Situation {
        val hour = weather.hourly.firstOrNull()?.time?.substringAfter('T', "")?.take(2)?.toIntOrNull()
        val daytime = weather.isDay && (hour == null || hour < WidgetContent.EVENING_HOUR)
        val uv = weather.daily.firstOrNull()?.uvIndexMax ?: Double.NaN
        val temp = weather.currentTemperature
        val kind = Wmo.wmoToKind(weather.currentWeatherCode)
        val gust = weather.windGustKmh

        return when {
            weather.usAqi >= AIR_FROM_AQI -> Situation.AIR
            daytime && !uv.isNaN() && uv >= SUN_FROM_UV -> Situation.SUN
            !temp.isNaN() && temp >= HOT_FROM_C -> Situation.HOT
            !temp.isNaN() && temp <= COLD_UP_TO_C -> Situation.COLD
            kind == "rain" || kind == "thunder" -> Situation.RAIN
            !gust.isNaN() && gust >= WIND_FROM_GUST_KMH -> Situation.WIND
            !weather.isDay -> Situation.NIGHT
            else -> Situation.CALM
        }
    }

    fun pick(lang: Lang, situation: Situation, seed: Int): String {
        val pools = if (lang == Lang.EN) EN else PL
        val pool = pools[situation.key] ?: pools.getValue(Situation.CALM.key)
        return pool[abs(seed) % pool.size]
    }

    fun pick(lang: Lang, weather: WeatherApi.WeatherData, seed: Int): String = pick(lang, situation(weather), seed)

    /** Every line in every pool, for the length test. */
    internal fun allLines(): List<String> = (PL.values + EN.values).flatten()

    internal fun poolKeys(): Set<String> = PL.keys + EN.keys

    private val PL: Map<String, List<String>> = mapOf(
        "air" to listOf(
            "powietrze słabe, bieganie zrób pod dachem",
            "krótko przewietrz, dłużej gdy się poprawi",
            "trening dziś lepiej w domu niż przy ulicy",
            "jeśli drapie w gardle, odpuść spacer",
            "woda i nawilżone powietrze pomagają gardłu",
            "w tłoku maska dziś ma sens",
        ),
        "sun" to listOf(
            "krem z filtrem na twarz i szyję",
            "okulary z filtrem UV chronią oczy",
            "cień w południe, krem co dwie godziny",
            "czapka z daszkiem to tani filtr",
            "słońce odwadnia, pij więcej niż zwykle",
            "najmocniejsze słońce jest między 11 a 15",
        ),
        "hot" to listOf(
            "pij wodę, zanim poczujesz pragnienie",
            "szukaj cienia między 12 a 15",
            "lekki posiłek łatwiej znosi upał",
            "zwolnij tempo, upał męczy bardziej niż się wydaje",
            "chłodny prysznic obniża temperaturę ciała",
            "jasne, luźne ubranie robi różnicę",
            "czapka i woda to dziś podstawa",
        ),
        "cold" to listOf(
            "ubierz się na cebulkę i zdejmuj warstwy",
            "ciepła herbata, ciepłe dłonie, spokojny dzień",
            "czapka i szalik oszczędzają dużo ciepła",
            "po mrozie rozgrzewaj się stopniowo",
            "krótki spacer w dzień dobrze robi na humor",
            "nawilż dłonie i usta, zimno je wysusza",
            "uważaj na śliskie chodniki",
        ),
        "rain" to listOf(
            "deszcz to dobry moment na przerwę od ekranu",
            "suche skarpetki w torbie ratują dzień",
            "po deszczu powietrze jest czystsze, wyjdź",
            "parasol w torbie, spokój w głowie",
            "ciepła herbata i książka też są planem",
            "uważaj na kałuże i śliskie liście",
            "rozciągnij się, skoro siedzisz w domu",
        ),
        "wind" to listOf(
            "silny wiatr: uważaj na gałęzie i rusztowania",
            "schowaj z balkonu to, co może polecieć",
            "wiatr wychładza, ubierz się o warstwę cieplej",
            "na rowerze ostrożnie, boczny wiatr zwodzi",
            "wiatr wysusza oczy, krople mogą pomóc",
        ),
        "night" to listOf(
            "przyciemnij ekran, godzina przed snem",
            "szklanka wody i spokojny oddech przed snem",
            "chłodna sypialnia sprzyja zasypianiu",
            "jutro też jest dzień, idź spać o ludzkiej porze",
            "krótkie rozciąganie karku przed snem",
            "zgaś światła godzinę przed snem",
        ),
        "calm" to listOf(
            "wstań i rozciągnij kark, minuta wystarczy",
            "wypij szklankę wody",
            "wyjdź na 10 minut na światło dzienne",
            "zrób trzy głębokie oddechy",
            "co godzinę popatrz w dal przez 20 sekund",
            "krótki spacer lepszy niż kolejna kawa",
            "wyprostuj plecy i rozluźnij barki",
            "daj oczom odpocząć od ekranu",
        ),
    )

    private val EN: Map<String, List<String>> = mapOf(
        "air" to listOf(
            "air is poor, run indoors today",
            "air the room briefly, longer once it clears",
            "do today's workout indoors, not by the road",
            "scratchy throat? skip the walk",
            "water and humid air are kind to the throat",
            "in a crowd, a mask makes sense today",
        ),
        "sun" to listOf(
            "sunscreen on your face and neck",
            "sunglasses with UV protection save your eyes",
            "shade at noon, sunscreen every two hours",
            "a cap is the cheapest sun filter",
            "sun dries you out, drink more than usual",
            "the strongest sun is between 11 and 3",
        ),
        "hot" to listOf(
            "drink before you feel thirsty",
            "look for shade between noon and 3",
            "a light meal handles the heat better",
            "slow down, heat tires you more than it seems",
            "a cool shower lowers your body temperature",
            "light, loose clothes make a difference",
            "a hat and water are the basics today",
        ),
        "cold" to listOf(
            "dress in layers and peel them off",
            "warm tea, warm hands, calm day",
            "a hat and scarf save a lot of warmth",
            "warm up gradually after the frost",
            "a short daytime walk is good for your mood",
            "moisturise hands and lips, cold dries them",
            "watch out for slippery pavements",
        ),
        "rain" to listOf(
            "rain is a good time for a screen break",
            "dry socks in your bag can save the day",
            "the air is cleaner after rain, step out",
            "umbrella in the bag, peace in the head",
            "warm tea and a book are a plan too",
            "mind the puddles and slippery leaves",
            "stretch, since you're stuck indoors anyway",
        ),
        "wind" to listOf(
            "strong wind: mind branches and scaffolding",
            "bring in whatever could fly off the balcony",
            "wind chills, wear one more layer",
            "careful on a bike, crosswinds are tricky",
            "wind dries the eyes, drops may help",
        ),
        "night" to listOf(
            "dim the screen an hour before sleep",
            "a glass of water and slow breaths before bed",
            "a cool bedroom helps you fall asleep",
            "tomorrow is a day too, sleep at a sane hour",
            "stretch your neck briefly before bed",
            "switch the lights down an hour before sleep",
        ),
        "calm" to listOf(
            "stand up and stretch your neck for a minute",
            "drink a glass of water",
            "get 10 minutes of daylight",
            "take three deep breaths",
            "look into the distance for 20 seconds each hour",
            "a short walk beats another coffee",
            "straighten your back and drop your shoulders",
            "rest your eyes from the screen",
        ),
    )
}
