package dev.pi0trdotsys.homebrewweather.widget

import java.util.Locale

/**
 * The language the whole app speaks — widget, notifications, widget setup and
 * (via src/lib/i18n.ts, same storage key) the web app.
 *
 * Before this the widget and notifications spoke Polish and the app English,
 * with no way to change either. Stored as `settings:lang` in the shared
 * Capacitor prefs. Unset, it follows the device: Polish on a Polish phone,
 * English anywhere else.
 */
enum class Lang(val storageValue: String) {
    PL("pl"),
    EN("en");

    val texts: Texts get() = if (this == PL) PlTexts else EnTexts

    companion object {
        fun fromStorage(raw: String?): Lang = entries.firstOrNull { it.storageValue == raw } ?: deviceDefault()

        fun deviceDefault(): Lang = if (Locale.getDefault().language == "pl") PL else EN
    }
}

/**
 * Every user-facing string the native side produces, per language. Pure
 * Kotlin, so the phrasing is unit-testable alongside the rules that pick it.
 */
interface Texts {
    val lang: Lang

    // ---- widget: hero, grid, meta ------------------------------------------
    fun kindLabel(kind: String): String
    val today: String
    val tomorrow: String

    /** Day-of-week abbreviation, [dow] 0 = Sunday .. 6 = Saturday. */
    fun dow(dow: Int): String
    fun heroPop(pop: Int): String
    fun feels(t: Int): String
    fun wind(kmh: Int): String
    fun humid(pct: Int): String
    fun dry(pct: Int): String
    fun fullMeta(feels: Int?, humidity: Int?, windKmh: Int?): String?

    // ---- rain phrasing ------------------------------------------------------
    fun rainNoun(kind: RainWindow.Kind): String
    fun rainOngoing(kind: RainWindow.Kind): String

    /** Widget hero form: "deszcz 15–19". Hours are whole numbers. */
    fun rainShort(kind: RainWindow.Kind, ongoing: Boolean, soon: Boolean, start: Int, end: Int?): String

    /** Full sentence for notifications: "deszcz od 15:00 do ok. 19:00". */
    fun rainLong(kind: RainWindow.Kind, ongoing: Boolean, soon: Boolean, start: String, end: String?): String

    /** Grid form of a window that runs past the end of the data: "od 20". */
    fun dayOpen(start: Int): String

    /** Rain about to stop, for the rain-stop notification. */
    fun rainStop(kind: RainWindow.Kind, end: String): String

    // ---- widget states -------------------------------------------------------
    val setCityHeader: String
    val tapToConfigure: String
    val offlineNoCacheFooter: String
    val bannerOfflineNoCache: String
    val bannerOfflineCached: String
    val bannerStale: String
    val refreshing: String

    // ---- air quality -----------------------------------------------------------
    /** Full US AQI category name, for notifications. */
    fun aqiLabel(aqi: Int): String

    /** Short form that fits the widget's stat column ("AQI 142 · sensitive"). */
    fun aqiShort(aqi: Int): String

    // ---- morning brief / evening preview --------------------------------------
    fun briefTitle(city: String): String
    fun possibleRain(pop: Int): String
    fun hot(max: Int): String
    fun cold(min: Int): String
    fun air(aqi: Int, label: String): String
    fun tomorrowSwing(delta: Int): String
    fun eveningTitle(city: String): String

    // ---- separate threshold alerts (plain voice) -------------------------------
    fun highTemp(city: String, t: Int): String
    fun lowTemp(city: String, t: Int): String
    fun swing(city: String, todayMax: Int, tomorrowMax: Int, warming: Boolean): String
    fun aqiAlert(city: String, aqi: Int, label: String): String

    // ---- notification channels ---------------------------------------------------
    val channelRain: Pair<String, String>
    val channelBrief: Pair<String, String>
    val channelEvening: Pair<String, String>
    val channelTempExtreme: Pair<String, String>
    val channelTempSwing: Pair<String, String>
    val channelAqi: Pair<String, String>
    val testPrefix: String
    val testNoRain: String

    // ---- widget setup screen -------------------------------------------------------
    val cfgSearchHint: String
    val cfgSearch: String
    val cfgUseLocation: String
    fun cfgUseLast(name: String): String
    val cfgTypeFirst: String
    val cfgSearching: String
    val cfgSearchFailed: String
    fun cfgNoResults(query: String): String
    fun cfgResults(n: Int): String
    val cfgPermissionDenied: String
    val cfgLocating: String
    val cfgNoProvider: String
    val cfgNoFix: String
    val cfgResolving: String
    fun cfgSaving(name: String): String
    fun densityHint(d: WidgetDensity): String
}

object PlTexts : Texts {
    override val lang = Lang.PL

    /**
     * Two of these used to be "częściowo" and "noc", which only read as
     * conditions behind the old "now · " prefix ("now · częściowo"). Standing
     * alone under the temperature they said "partially" and "night", so they
     * are the words a forecast would actually use.
     */
    override fun kindLabel(kind: String) = when (kind) {
        "sun" -> "słonecznie"
        "partly" -> "przejaśnienia"
        "cloud" -> "pochmurno"
        "fog" -> "mgła"
        "rain" -> "deszcz"
        "snow" -> "śnieg"
        "thunder" -> "burza"
        "moon" -> "pogodna noc"
        else -> kind
    }

    override val today = "dziś"
    override val tomorrow = "jutro"
    private val DOW = arrayOf("nd", "pn", "wt", "śr", "cz", "pt", "sb")
    override fun dow(dow: Int) = DOW[Math.floorMod(dow, 7)]
    override fun heroPop(pop: Int) = "opady $pop%"
    override fun feels(t: Int) = "odczuwalna $t°"
    override fun wind(kmh: Int) = "wiatr $kmh km/h"
    override fun humid(pct: Int) = "wilgotno $pct%"
    override fun dry(pct: Int) = "sucho $pct%"
    override fun fullMeta(feels: Int?, humidity: Int?, windKmh: Int?): String? =
        listOfNotNull(feels?.let { "odcz. $it°" }, humidity?.let { "wilg. $it%" }, windKmh?.let { "wiatr ${it}km/h" })
            .takeIf { it.isNotEmpty() }?.joinToString("  ·  ")

    override fun rainNoun(kind: RainWindow.Kind) = when (kind) {
        RainWindow.Kind.RAIN -> "deszcz"
        RainWindow.Kind.SNOW -> "śnieg"
        RainWindow.Kind.THUNDER -> "burza"
    }

    override fun rainOngoing(kind: RainWindow.Kind) = when (kind) {
        RainWindow.Kind.RAIN -> "pada"
        RainWindow.Kind.SNOW -> "sypie"
        RainWindow.Kind.THUNDER -> "grzmi"
    }

    override fun rainShort(kind: RainWindow.Kind, ongoing: Boolean, soon: Boolean, start: Int, end: Int?): String {
        val noun = rainNoun(kind)
        return when {
            ongoing && end != null -> "${rainOngoing(kind)} do ~$end"
            ongoing -> "${rainOngoing(kind)} na dłużej"
            soon && end != null -> "$noun wkrótce, do ~$end"
            soon -> "$noun wkrótce"
            end != null -> "$noun $start–$end"
            else -> "$noun od $start"
        }
    }

    override fun rainLong(kind: RainWindow.Kind, ongoing: Boolean, soon: Boolean, start: String, end: String?): String {
        val noun = rainNoun(kind)
        return when {
            ongoing && end != null -> "${rainOngoing(kind)}, przestanie ok. $end"
            ongoing -> "${rainOngoing(kind)} i nie zanosi się na koniec"
            soon && end != null -> "$noun w ciągu godziny, do ok. $end"
            soon -> "$noun w ciągu godziny"
            end != null -> "$noun od $start do ok. $end"
            else -> "$noun od $start"
        }
    }

    override fun dayOpen(start: Int) = "od $start"

    override fun rainStop(kind: RainWindow.Kind, end: String) = when (kind) {
        RainWindow.Kind.RAIN -> "przestanie padać ok. $end"
        RainWindow.Kind.SNOW -> "przestanie sypać ok. $end"
        RainWindow.Kind.THUNDER -> "burza ucichnie ok. $end"
    }

    override val setCityHeader = "┌─ wybierz miasto ─┐"
    override val tapToConfigure = "> dotknij nazwy miasta, by ustawić"
    override val offlineNoCacheFooter = "> offline — brak zapisanej prognozy"
    override val bannerOfflineNoCache = "offline · brak zapisanej prognozy"
    override val bannerOfflineCached = "offline · zapisana prognoza"
    override val bannerStale = "nieaktualne · ponawiam"
    override val refreshing = "⟳ odświeżam…"

    override fun aqiLabel(aqi: Int) = when {
        aqi <= 50 -> "dobre"
        aqi <= 100 -> "umiarkowane"
        aqi <= 150 -> "złe dla wrażliwych"
        aqi <= 200 -> "niezdrowe"
        aqi <= 300 -> "bardzo niezdrowe"
        else -> "groźne"
    }

    override fun aqiShort(aqi: Int) = when {
        aqi <= 50 -> "dobre"
        aqi <= 100 -> "umiarkow."
        aqi <= 150 -> "wrażliwi"
        aqi <= 200 -> "niezdrowe"
        aqi <= 300 -> "b. złe"
        else -> "groźne"
    }

    override fun briefTitle(city: String) = "$city · dziś"
    override fun possibleRain(pop: Int) = "możliwe opady ($pop%)"
    override fun hot(max: Int) = "upał, do $max°"
    override fun cold(min: Int) = "zimno, do $min°"
    override fun air(aqi: Int, label: String) = "powietrze: AQI $aqi ($label)"
    override fun tomorrowSwing(delta: Int) =
        if (delta > 0) "jutro o $delta° cieplej" else "jutro o ${-delta}° chłodniej"
    override fun eveningTitle(city: String) = "$city · jutro"

    override fun highTemp(city: String, t: Int) = "$city: $t°C — upał. Pij wodę i szukaj cienia."
    override fun lowTemp(city: String, t: Int) = "$city: $t°C — zimno. Ubierz się cieplej."
    override fun swing(city: String, todayMax: Int, tomorrowMax: Int, warming: Boolean) =
        "$city: jutro $tomorrowMax° zamiast $todayMax° — wyraźnie ${if (warming) "cieplej" else "chłodniej"}."
    override fun aqiAlert(city: String, aqi: Int, label: String) =
        "$city: AQI $aqi ($label) — ogranicz wysiłek na zewnątrz."

    override val channelRain = "Deszcz" to "Deszcz lub burza tuż-tuż, a także kiedy przestanie"
    override val channelBrief = "Poranny brief" to "Jedno podsumowanie dnia rano"
    override val channelEvening = "Zapowiedź jutra" to "Wieczorem: jak będzie jutro"
    override val channelTempExtreme = "Upał i mróz" to "Przekroczone progi temperatury (gdy brief jest wyłączony)"
    override val channelTempSwing = "Skoki temperatury" to "Duża zmiana temperatury z dnia na dzień (gdy brief jest wyłączony)"
    override val channelAqi = "Jakość powietrza" to "Przekroczony próg AQI (gdy brief jest wyłączony)"
    override val testPrefix = "[test] "
    override val testNoRain = "w najbliższych godzinach nie pada — tak wyglądałoby ostrzeżenie"

    override val cfgSearchHint = "szukaj miasta..."
    override val cfgSearch = "[ szukaj ]"
    override val cfgUseLocation = "[ moja lokalizacja ]"
    override fun cfgUseLast(name: String) = "> miasto z aplikacji: $name"
    override val cfgTypeFirst = "// najpierw wpisz nazwę miasta"
    override val cfgSearching = "// szukam..."
    override val cfgSearchFailed = "// wyszukiwanie nie powiodło się, sprawdź połączenie"
    override fun cfgNoResults(query: String) = "// brak wyników dla \"$query\""
    override fun cfgResults(n: Int) = "// wyniki: $n"
    override val cfgPermissionDenied = "// brak zgody na lokalizację — wyszukaj miasto"
    override val cfgLocating = "// ustalam lokalizację..."
    override val cfgNoProvider = "// lokalizacja wyłączona — wyszukaj miasto"
    override val cfgNoFix = "// brak pozycji na czas — spróbuj na zewnątrz albo wyszukaj"
    override val cfgResolving = "// ustalam nazwę miejsca..."
    override fun cfgSaving(name: String) = "// zapisuję $name..."
    override fun densityHint(d: WidgetDensity) = when (d) {
        WidgetDensity.MINIMAL -> "// temperatura + 4 dni, deszcz tylko z godzinami"
        WidgetDensity.STANDARD -> "// tylko to, co dziś odbiega od normy"
        WidgetDensity.FULL -> "// wszystkie odczyty, zawsze"
    }
}

object EnTexts : Texts {
    override val lang = Lang.EN

    override fun kindLabel(kind: String) = when (kind) {
        "sun" -> "sunny"
        "partly" -> "partly cloudy"
        "cloud" -> "overcast"
        "fog" -> "fog"
        "rain" -> "rain"
        "snow" -> "snow"
        "thunder" -> "storm"
        "moon" -> "clear night"
        else -> kind
    }

    // "tomorrow" is too wide for a grid column; "tmrw" is the common short form.
    override val today = "today"
    override val tomorrow = "tmrw"
    private val DOW = arrayOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
    override fun dow(dow: Int) = DOW[Math.floorMod(dow, 7)]
    override fun heroPop(pop: Int) = "rain $pop%"
    override fun feels(t: Int) = "feels $t°"
    override fun wind(kmh: Int) = "wind $kmh km/h"
    override fun humid(pct: Int) = "humid $pct%"
    override fun dry(pct: Int) = "dry $pct%"
    override fun fullMeta(feels: Int?, humidity: Int?, windKmh: Int?): String? =
        listOfNotNull(feels?.let { "feels $it°" }, humidity?.let { "hum $it%" }, windKmh?.let { "wind ${it}km/h" })
            .takeIf { it.isNotEmpty() }?.joinToString("  ·  ")

    override fun rainNoun(kind: RainWindow.Kind) = when (kind) {
        RainWindow.Kind.RAIN -> "rain"
        RainWindow.Kind.SNOW -> "snow"
        RainWindow.Kind.THUNDER -> "storm"
    }

    override fun rainOngoing(kind: RainWindow.Kind) = when (kind) {
        RainWindow.Kind.RAIN -> "raining"
        RainWindow.Kind.SNOW -> "snowing"
        RainWindow.Kind.THUNDER -> "storming"
    }

    override fun rainShort(kind: RainWindow.Kind, ongoing: Boolean, soon: Boolean, start: Int, end: Int?): String {
        val noun = rainNoun(kind)
        return when {
            ongoing && end != null -> "${rainOngoing(kind)} until ~$end"
            ongoing -> "${rainOngoing(kind)} for a while"
            soon && end != null -> "$noun soon, until ~$end"
            soon -> "$noun soon"
            end != null -> "$noun $start–$end"
            else -> "$noun from $start"
        }
    }

    override fun rainLong(kind: RainWindow.Kind, ongoing: Boolean, soon: Boolean, start: String, end: String?): String {
        val noun = rainNoun(kind)
        return when {
            ongoing && end != null -> "${rainOngoing(kind)}, easing off around $end"
            ongoing -> "${rainOngoing(kind)}, no end in sight"
            soon && end != null -> "$noun within the hour, until about $end"
            soon -> "$noun within the hour"
            end != null -> "$noun from $start until about $end"
            else -> "$noun from $start"
        }
    }

    override fun dayOpen(start: Int) = "from $start"

    override fun rainStop(kind: RainWindow.Kind, end: String) = when (kind) {
        RainWindow.Kind.RAIN -> "rain stopping around $end"
        RainWindow.Kind.SNOW -> "snow stopping around $end"
        RainWindow.Kind.THUNDER -> "storm passing around $end"
    }

    override val setCityHeader = "┌─ set city ─┐"
    override val tapToConfigure = "> tap the city name to set it up"
    override val offlineNoCacheFooter = "> offline — no saved forecast yet"
    override val bannerOfflineNoCache = "offline · no saved forecast yet"
    override val bannerOfflineCached = "offline · saved forecast"
    override val bannerStale = "stale · retrying"
    override val refreshing = "⟳ refreshing…"

    override fun aqiLabel(aqi: Int) = when {
        aqi <= 50 -> "good"
        aqi <= 100 -> "moderate"
        aqi <= 150 -> "unhealthy (sensitive)"
        aqi <= 200 -> "unhealthy"
        aqi <= 300 -> "very unhealthy"
        else -> "hazardous"
    }

    override fun aqiShort(aqi: Int) = when {
        aqi <= 50 -> "good"
        aqi <= 100 -> "moderate"
        aqi <= 150 -> "sensitive"
        aqi <= 200 -> "unhealthy"
        aqi <= 300 -> "very bad"
        else -> "hazardous"
    }

    override fun briefTitle(city: String) = "$city · today"
    override fun possibleRain(pop: Int) = "possible rain ($pop%)"
    override fun hot(max: Int) = "hot, up to $max°"
    override fun cold(min: Int) = "cold, down to $min°"
    override fun air(aqi: Int, label: String) = "air: AQI $aqi ($label)"
    override fun tomorrowSwing(delta: Int) =
        if (delta > 0) "tomorrow $delta° warmer" else "tomorrow ${-delta}° colder"
    override fun eveningTitle(city: String) = "$city · tomorrow"

    override fun highTemp(city: String, t: Int) = "$city: $t°C — heat. Drink water, find shade."
    override fun lowTemp(city: String, t: Int) = "$city: $t°C — cold. Dress warmly."
    override fun swing(city: String, todayMax: Int, tomorrowMax: Int, warming: Boolean) =
        "$city: $tomorrowMax° tomorrow instead of $todayMax° — noticeably ${if (warming) "warmer" else "colder"}."
    override fun aqiAlert(city: String, aqi: Int, label: String) =
        "$city: AQI $aqi ($label) — go easy outdoors."

    override val channelRain = "Rain" to "Rain or a storm about to start, and when it's about to stop"
    override val channelBrief = "Morning brief" to "One summary of the day, in the morning"
    override val channelEvening = "Tomorrow preview" to "In the evening: what tomorrow looks like"
    override val channelTempExtreme = "Heat and frost" to "Temperature thresholds crossed (when the brief is off)"
    override val channelTempSwing = "Temperature swings" to "Big day-to-day temperature change (when the brief is off)"
    override val channelAqi = "Air quality" to "AQI threshold crossed (when the brief is off)"
    override val testPrefix = "[test] "
    override val testNoRain = "no rain in the next hours — this is what the heads-up looks like"

    override val cfgSearchHint = "search city..."
    override val cfgSearch = "[ search ]"
    override val cfgUseLocation = "[ use my location ]"
    override fun cfgUseLast(name: String) = "> use last app location: $name"
    override val cfgTypeFirst = "// type a city name first"
    override val cfgSearching = "// searching..."
    override val cfgSearchFailed = "// search failed, check connection"
    override fun cfgNoResults(query: String) = "// no results for \"$query\""
    override fun cfgResults(n: Int) = "// $n result(s)"
    override val cfgPermissionDenied = "// location permission denied — search instead"
    override val cfgLocating = "// locating..."
    override val cfgNoProvider = "// no location provider enabled — search instead"
    override val cfgNoFix = "// no fix in time — try again outdoors, or search instead"
    override val cfgResolving = "// resolving location name..."
    override fun cfgSaving(name: String) = "// saving $name..."
    override fun densityHint(d: WidgetDensity) = when (d) {
        WidgetDensity.MINIMAL -> "// temperature + 4 days, rain as hours only"
        WidgetDensity.STANDARD -> "// only what's out of the ordinary today"
        WidgetDensity.FULL -> "// every readout, always"
    }
}
