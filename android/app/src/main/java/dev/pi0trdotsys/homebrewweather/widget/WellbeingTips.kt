package dev.pi0trdotsys.homebrewweather.widget

/**
 * A short well-being tip for the widget footer (and the dashboard), as an
 * alternative to a joke — the "footer source" setting picks one of
 * joke / well-being tip / on this day.
 *
 * The tip follows the weather: heat, frost, rain, a strong sun, bad air, wind or
 * night each have their own pool, with a general one for an ordinary day (see
 * [situation] for the rule, mirrored in src/lib/wellbeing.ts). Same pick rule
 * as the jokes (the next line of a fresh shuffle of the pool each round, the seed
 * being the hour: see [LinePicker]) and the same one-line ceiling, for the same reason — see [SigmaJokes.MAX_LINE_LENGTH].
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
        POLLEN("pollen"),
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
     * bad air, high pollen, strong sun (daytime only), heat, frost, rain or a storm
     * falling now, strong gusts, night, otherwise calm.
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
            Pollen.top(weather.pollen) != null -> Situation.POLLEN
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
        return pool[LinePicker.index(seed, pool.size)]
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
            "zamknij okna, gdy powietrze jest ciężkie",
            "krótszy spacer dziś, dłuższy po poprawie",
            "omiń ruchliwe ulice, wybierz park",
            "gotując, włącz okap nad kuchenką",
            "kurz wycieraj na mokro, nie rozwiewaj go",
            "umyj twarz i dłonie zaraz po powrocie",
            "zakupy zrób szybko albo zamów do domu",
            "sprawdź powietrze wieczorem, bywa lepiej",
            "dobry dzień na jogę w salonie",
            "oddychaj spokojnie, nie przyspieszaj kroku",
            "jeśli musisz wyjść, wybierz boczne uliczki",
            "dziś więcej herbaty w domu, mniej ulicy",
            "dzieci i seniorzy niech zostaną pod dachem",
            "wytrzyj parapety, osiada na nich kurz",
        ),
        "pollen" to listOf(
            "pylenie wysokie: po powrocie umyj twarz i włosy",
            "okna zamknij w południe, przewietrz wieczorem",
            "okulary chronią oczy przed pyłkiem",
            "ubranie z dworu zostaw przed sypialnią",
            "pranie dziś lepiej suszyć pod dachem",
            "po spacerze umyj twarz i zmień koszulkę",
            "po deszczu pyłku jest mniej, wtedy wyjdź",
            "pościel zmieniaj częściej w sezonie pylenia",
            "wieczorna kąpiel zmyje pyłek ze skóry",
            "zdejmij buty przy drzwiach, pyłek zostanie",
            "odkurzaj częściej, pyłek też wchodzi do domu",
            "pies po spacerze? przetrzyj mu sierść",
            "nie trzyj oczu, to tylko je podrażnia",
            "chusteczki w kieszeni ratują spacer",
            "w samochodzie jedź przy zamkniętych oknach",
            "wietrznie, więc pyłku w powietrzu więcej",
            "dziś kino albo muzeum zamiast trawnika",
            "włosy na noc umyte, poduszka czystsza",
            "ręcznik z dworu wymień na świeży",
            "kapelusz z rondem zatrzyma część pyłku",
        ),
        "sun" to listOf(
            "krem z filtrem na twarz i szyję",
            "okulary z filtrem UV chronią oczy",
            "cień w południe, krem co dwie godziny",
            "czapka z daszkiem to tani filtr",
            "słońce odwadnia, pij więcej niż zwykle",
            "najmocniejsze słońce jest między 11 a 15",
            "smaruj uszy i kark, o nich łatwo zapomnieć",
            "lekka koszula z długim rękawem też chroni",
            "butelka wody do torby, na każdy wypad",
            "spacer lepiej rano albo pod wieczór",
            "parasol od słońca to świetny pomysł",
            "sprawdź datę ważności kremu z filtrem",
            "posmaruj się ponownie po kąpieli lub potach",
            "rób przerwy w cieniu, nie tylko w południe",
            "dziecku zakryj głowę nawet na krótki spacer",
            "woda i piasek odbijają słońce, uważaj",
            "lody smakują tak samo w cieniu",
            "zasłoń okna od południa, w domu chłodniej",
            "przez chmury też się opalasz, krem zostaje",
            "godzina na słońcu? wypij szklankę wody",
        ),
        "hot" to listOf(
            "pij wodę, zanim poczujesz pragnienie",
            "szukaj cienia między 12 a 15",
            "lekki posiłek łatwiej znosi upał",
            "zwolnij tempo, upał męczy bardziej niż się wydaje",
            "chłodny prysznic obniża temperaturę ciała",
            "jasne, luźne ubranie robi różnicę",
            "czapka i woda to dziś podstawa",
            "zasłoń okna w dzień, otwórz je nocą",
            "mokry ręcznik na kark szybko chłodzi",
            "dzieci i zwierząt nie zostawiaj w aucie",
            "trening przełóż na wczesny ranek",
            "arbuz i ogórek też nawadniają",
            "stopy w misce zimnej wody to mała ulga",
            "biblioteka albo galeria to chłodny plan",
            "lekka pościel i uchylone okno w nocy",
            "kawy dziś mniej, wody więcej",
            "sprawdź, czy zwierzak ma wodę i cień",
            "wentylator i miska z lodem dają powiew",
            "po zmroku wyprowadź psa, asfalt parzy łapy",
            "krótka sjesta po obiedzie to dobry zwyczaj",
            "cytryna i mięta uczynią wodę smaczniejszą",
        ),
        "cold" to listOf(
            "ubierz się na cebulkę i zdejmuj warstwy",
            "ciepła herbata, ciepłe dłonie, spokojny dzień",
            "czapka i szalik oszczędzają dużo ciepła",
            "po mrozie rozgrzewaj się stopniowo",
            "krótki spacer w dzień dobrze robi na humor",
            "nawilż dłonie i usta, zimno je wysusza",
            "uważaj na śliskie chodniki",
            "ciepłe skarpety to mały luksus",
            "rękawiczki w kieszeni, zanim zmarzną dłonie",
            "zupa rozgrzeje lepiej niż kolejna kawa",
            "posyp schody solą lub piaskiem",
            "w zimie słońce jest rzadsze, złap je w południe",
            "na lodzie krok krótszy, ręce poza kieszeniami",
            "mokre buty susz z dala od kaloryfera",
            "wietrz krótko, a porządnie",
            "termos z herbatą na dłuższy spacer",
            "gdy wieje, naciągnij szalik na nos",
            "tłustszy krem na twarz przed wyjściem",
            "mokre ubrania zdejmij zaraz po powrocie",
            "zimowy wieczór: koc, lampka i dobry film",
            "na mrozie daj sobie więcej czasu na dojazd",
        ),
        "rain" to listOf(
            "deszcz to dobry moment na przerwę od ekranu",
            "suche skarpetki w torbie ratują dzień",
            "po deszczu powietrze jest czystsze, wyjdź",
            "parasol w torbie, spokój w głowie",
            "ciepła herbata i książka też są planem",
            "uważaj na kałuże i śliskie liście",
            "rozciągnij się, skoro siedzisz w domu",
            "kurtka z kapturem wygodniejsza niż parasol",
            "mokre buty wypchaj gazetą, szybciej wyschną",
            "za kierownicą zwolnij, droga jest śliska",
            "w burzy omijaj samotne drzewa i otwarte pola",
            "gdy grzmi, poczekaj pod dachem",
            "puść muzykę i zrób to, co odkładasz",
            "zupa i deszcz za oknem to dobry duet",
            "telefon w kieszeni na zamek, woda mu szkodzi",
            "dziś tramwaj zamiast roweru",
            "kałuże cieszą dzieci, buty już mniej",
            "ziemia pachnie po deszczu, wyjdź na chwilę",
            "mokrą kurtkę powieś zaraz po wejściu",
            "odblask na plecaku, w deszczu widać gorzej",
            "spisz plan na weekend, skoro leje",
        ),
        "wind" to listOf(
            "silny wiatr: uważaj na gałęzie i rusztowania",
            "schowaj z balkonu to, co może polecieć",
            "wiatr wychładza, ubierz się o warstwę cieplej",
            "na rowerze ostrożnie, boczny wiatr zwodzi",
            "wiatr wysusza oczy, krople mogą pomóc",
            "zamknij okna, żeby nie trzaskały",
            "parkuj z dala od drzew, spadają gałęzie",
            "czapka i kaptur przy porywach to podstawa",
            "las dziś lepiej omiń, gałęzie lecą",
            "rzeczy z parapetu schowaj do środka",
            "wiatr niesie piach, osłoń oczy okularami",
            "drzwi trzymaj mocno, wiatr potrafi je wyrwać",
            "suszarkę z balkonu schowaj lub przypnij",
            "dzień na latawiec, ale z dala od drzew",
            "na rowerze zaczynaj pod wiatr, wracaj z wiatrem",
            "parasol w takim wietrze lubi się łamać",
            "ciepły napój świetnie kończy wietrzny spacer",
            "namiot i hamak dziś zostaw w domu",
            "wiatr wysusza skórę, nawilż usta",
        ),
        "night" to listOf(
            "przyciemnij ekran, godzina przed snem",
            "szklanka wody i spokojny oddech przed snem",
            "chłodna sypialnia sprzyja zasypianiu",
            "jutro też jest dzień, idź spać o ludzkiej porze",
            "krótkie rozciąganie karku przed snem",
            "zgaś światła godzinę przed snem",
            "odłóż telefon, zanim sen odłoży ciebie",
            "stała pora snu to prezent dla jutra",
            "kolacja lekka, sen spokojniejszy",
            "zapisz jutrzejsze sprawy, a głowa odpocznie",
            "wieczorem raczej herbata niż kawa",
            "ciepły prysznic i książka zamiast scrollowania",
            "zaciągnij zasłony, ciemność pomaga",
            "wywietrz sypialnię przed snem",
            "cicha muzyka albo cisza, bez telewizora",
            "budzik postaw z dala od łóżka",
            "podziękuj sobie za dzień, nawet kiepski",
            "wypisz trzy rzeczy, które się udały",
            "rozluźnij barki i szczękę przed snem",
            "nie możesz zasnąć? wstań i poczytaj chwilę",
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
            "odezwij się do kogoś, za kim tęsknisz",
            "wejdź po schodach, nie wjeżdżaj windą",
            "posiedź pięć minut bez telefonu",
            "otwórz okno, niech wejdzie świeże powietrze",
            "zaplanuj dziś jedną miłą rzecz",
            "stopy płasko na podłodze, plecy oparte",
            "jak siedzisz teraz? rozluźnij szczękę",
            "porusz nadgarstkami i barkami przy biurku",
            "siedzisz od godziny? wstań na chwilę",
            "bułki kup pieszo, zostaw auto",
            "zjedz wolniej, smak wraca",
            "rozejrzyj się: co ładnego dziś widzisz?",
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
            "keep windows shut while the air is heavy",
            "a shorter walk today, a longer one later",
            "skip the busy roads, pick a park path",
            "cooking? switch the extractor fan on",
            "damp-wipe dust instead of stirring it up",
            "wash your face and hands when you get home",
            "quick shop, or order in, whichever is easier",
            "check the air again tonight, it may improve",
            "yoga in the living room suits today",
            "breathe easy and keep your pace gentle",
            "if you must go out, take the side streets",
            "more tea at home, less time by traffic",
            "kids and older folks are better off indoors",
            "wipe the windowsills, dust settles there",
        ),
        "pollen" to listOf(
            "pollen is high: wash face and hair at home",
            "keep windows shut at noon, air out at night",
            "sunglasses keep pollen out of your eyes",
            "leave outdoor clothes outside the bedroom",
            "dry the laundry indoors today",
            "after a walk, wash your face and change shirt",
            "less pollen after rain, a good time to go out",
            "change your bedding more often in the season",
            "an evening shower rinses pollen off your skin",
            "shoes off at the door, pollen stays outside",
            "vacuum more often, pollen comes in with you",
            "dog walk done? wipe its coat down",
            "rubbing itchy eyes only makes it worse",
            "tissues in your pocket can save a walk",
            "drive with the windows up today",
            "windy days stir up more pollen",
            "cinema or a museum instead of the lawn today",
            "wash your hair at night, keep the pillow clean",
            "swap the outdoor towel for a fresh one",
            "brimmed hats keep some pollen off your face",
        ),
        "sun" to listOf(
            "sunscreen on your face and neck",
            "sunglasses with UV protection save your eyes",
            "shade at noon, sunscreen every two hours",
            "a cap is the cheapest sun filter",
            "sun dries you out, drink more than usual",
            "the strongest sun is between 11 and 3",
            "don't forget your ears and the back of your neck",
            "long sleeves in light linen are sun cover too",
            "carry a water bottle on every outing",
            "walks are nicer in the early morning or evening",
            "an umbrella works against sun too",
            "check the date on your sunscreen",
            "reapply after a swim or a sweaty hour",
            "take breaks in the shade, not only at noon",
            "cover a child's head, even on a short walk",
            "water and sand bounce the sun back at you",
            "ice cream tastes the same in the shade",
            "close south-facing curtains to keep rooms cool",
            "clouds let UV through, keep the cream on",
            "one glass of water per hour in full sun",
        ),
        "hot" to listOf(
            "drink before you feel thirsty",
            "look for shade between noon and 3",
            "a light meal handles the heat better",
            "slow down, heat tires you more than it seems",
            "a cool shower lowers your body temperature",
            "light, loose clothes make a difference",
            "a hat and water are the basics today",
            "close the curtains by day, open windows at night",
            "put a wet towel on your neck to cool off",
            "never leave kids or pets in a parked car",
            "move your workout to early morning",
            "watermelon and cucumber count as water too",
            "feet in a bowl of cold water, small relief",
            "library or gallery? a cool plan",
            "light sheets and a cracked window tonight",
            "less coffee, more water today",
            "does your pet have water and shade?",
            "fan plus a bowl of ice makes a breeze",
            "walk the dog after dark, hot asphalt burns paws",
            "try a short siesta after lunch",
            "lemon and mint make water more tempting",
        ),
        "cold" to listOf(
            "dress in layers and peel them off",
            "warm tea, warm hands, calm day",
            "a hat and scarf save a lot of warmth",
            "warm up gradually after the frost",
            "a short daytime walk is good for your mood",
            "moisturise hands and lips, cold dries them",
            "watch out for slippery pavements",
            "warm socks are a small luxury",
            "gloves in your pocket before hands get cold",
            "soup warms you better than another coffee",
            "sprinkle salt or sand on icy steps",
            "winter sun is rare, catch it around noon",
            "short steps on ice, hands out of pockets",
            "dry wet boots away from the radiator",
            "air the room briefly but thoroughly",
            "pack a flask of tea for a longer walk",
            "when it blows, pull your scarf over your nose",
            "richer face cream before you head out",
            "change out of damp clothes when you get in",
            "winter evening: blanket, lamp and a good film",
            "give yourself extra travel time in frost",
        ),
        "rain" to listOf(
            "rain is a good time for a screen break",
            "dry socks in your bag can save the day",
            "the air is cleaner after rain, step out",
            "umbrella in the bag, peace in the head",
            "warm tea and a book are a plan too",
            "mind the puddles and slippery leaves",
            "stretch, since you're stuck indoors anyway",
            "hooded jacket beats an umbrella in a breeze",
            "stuff wet shoes with newspaper to dry faster",
            "slow down behind the wheel, roads are slick",
            "in a storm, avoid lone trees and open fields",
            "thunder close? wait it out indoors",
            "play some music and tackle that chore",
            "soup and rain outside make a good pair",
            "phone in a zipped pocket, water is no friend",
            "leave the bike home, take the dry tram",
            "puddles delight kids, shoes less so",
            "the earth smells fresh after rain, step out",
            "hang the wet jacket up as soon as you are in",
            "reflective strips help you stand out in rain",
            "sketch next weekend's plan while it pours",
        ),
        "wind" to listOf(
            "strong wind: mind branches and scaffolding",
            "bring in whatever could fly off the balcony",
            "wind chills, wear one more layer",
            "careful on a bike, crosswinds are tricky",
            "wind dries the eyes, drops may help",
            "shut the windows so they don't slam",
            "park away from trees today",
            "hat and hood are the basics in gusts",
            "skip the forest walk today",
            "bring windowsill items inside",
            "wind carries grit, shield your eyes",
            "hold doors firmly, wind can rip them away",
            "pin down or bring in the laundry rack",
            "kite weather, just keep clear of the trees",
            "cycling: start into the wind, finish with it",
            "an umbrella in this wind tends to snap",
            "enjoy a warm drink after a blustery walk",
            "tent and hammock stay home today",
            "wind dries skin, balm your lips",
        ),
        "night" to listOf(
            "dim the screen an hour before sleep",
            "a glass of water and slow breaths before bed",
            "a cool bedroom helps you fall asleep",
            "tomorrow is a day too, sleep at a sane hour",
            "stretch your neck briefly before bed",
            "switch the lights down an hour before sleep",
            "put the phone down before sleep does it for you",
            "a steady bedtime is a gift to tomorrow",
            "light dinner, calmer sleep",
            "jot down tomorrow's tasks, let your head rest",
            "tea rather than coffee this evening",
            "warm shower and a book beat scrolling",
            "draw the curtains, darkness helps",
            "air the bedroom before you go to bed",
            "quiet music or silence, no telly",
            "keep the alarm clock away from the bed",
            "thank yourself for today, even a rough one",
            "name three things that went well",
            "unclench your shoulders and jaw before sleep",
            "cannot sleep? get up and read for a bit",
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
            "call someone you have been meaning to",
            "take the stairs instead of the lift",
            "sit for five minutes without your phone",
            "open a window, let some fresh air in",
            "plan one nice thing for today",
            "feet flat on the floor, back supported",
            "how are you sitting now? unclench your jaw",
            "roll your wrists and shoulders at the desk",
            "sitting for an hour? stand up for a moment",
            "walk to the bakery instead of driving",
            "eat a little slower, food tastes better",
            "look around: what is nice today?",
        ),
    )
}
