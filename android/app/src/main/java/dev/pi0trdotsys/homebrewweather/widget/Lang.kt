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
    fun fullMeta(
        feels: Int?, humidity: Int?, windKmh: Int?,
        gustKmh: Int? = null, uv: Int? = null, rainMm: Double? = null,
    ): String?

    // ---- rain phrasing ------------------------------------------------------
    fun rainNoun(kind: RainWindow.Kind): String
    fun rainOngoing(kind: RainWindow.Kind): String

    /** The word for a spell of precipitation: "mżawka" / "ulewa" for light and
     * heavy rain, otherwise [rainNoun]. */
    fun nounFor(kind: RainWindow.Kind, intensity: RainWindow.Intensity): String

    /** Widget hero form: "deszcz 15–19". Hours are whole numbers. */
    fun rainShort(
        kind: RainWindow.Kind, ongoing: Boolean, soon: Boolean, start: Int, end: Int?,
        intensity: RainWindow.Intensity = RainWindow.Intensity.RAIN,
    ): String

    /** Full sentence for notifications: "deszcz od 15:00 do ok. 19:00". */
    fun rainLong(
        kind: RainWindow.Kind, ongoing: Boolean, soon: Boolean, start: String, end: String?,
        intensity: RainWindow.Intensity = RainWindow.Intensity.RAIN,
    ): String

    /** An amount of rain: "0,2 mm", "12 mm". */
    fun mm(mm: Double): String

    /** A window's total, tacked onto its sentence: "12 mm łącznie". */
    fun rainTotal(mm: Double): String

    /** Today's rain amount for the widget's meta line: "opady 12 mm". */
    fun rainAmount(mm: Double): String

    fun uv(index: Int): String
    fun gusts(kmh: Int): String

    /** Rain by the minute (see [RainNowcast]); [minutes] is already rounded for speech, 0 = "any moment". */
    fun nowcastStart(kind: RainWindow.Kind, minutes: Int): String
    fun nowcastOngoing(kind: RainWindow.Kind, endsInMinutes: Int?): String

    // ---- pollen -------------------------------------------------------------------
    fun pollenName(species: Pollen.Species): String
    fun pollenShort(species: Pollen.Species, veryHigh: Boolean): String
    fun briefPollen(species: Pollen.Species, veryHigh: Boolean): String

    // ---- official IMGW warnings ---------------------------------------------------
    /** Widget meta line: "IMGW: silny wiatr 1°". */
    fun officialShort(event: String, degree: Int): String
    fun officialTitle(event: String, degree: Int): String

    /** "od 18.11 19:00 do 19.11 01:00" — [from] and [to] are local ISO times. */
    fun officialValidity(from: String, to: String): String

    /** "za 40 min" / "in 40 min": the time part of a sun readout, which the icon introduces. */
    fun inMinutes(min: Int): String

    /** "zachód za 40 min" — the sun about to set or rise, on the widget's meta line. */
    fun sunsetIn(min: Int): String
    fun sunriseIn(min: Int): String

    // ---- accessibility: what TalkBack says -------------------------------------
    val a11yRefresh: String
    val a11ySetup: String
    fun a11yCityButton(name: String): String
    fun a11yNextPlace(name: String, index: Int, count: Int): String

    /** Full day name for a spoken label, [dow] 0 = Sunday .. 6 = Saturday. */
    fun dowLong(dow: Int): String
    fun a11ySummary(city: String, tempC: Int?, line: String, meta: String?): String
    fun a11yDay(label: String, max: Int, min: Int, rain: String?): String
    fun a11yCurve(min: Int, max: Int): String

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

    /** Brief lines: a stronger sun and a gusty day. */
    fun briefUv(index: Int): String
    fun briefGusts(kmh: Int): String

    /** Rain amount on a day without a rain window: "możliwe opady (40%), ok. 3 mm". */
    fun possibleRainMm(pop: Int, mm: Double): String

    // ---- severe weather warnings (always plain: a warning is not a punchline) ----
    fun severeTitle(kind: SevereWeather.Kind): String
    fun severeText(city: String, warning: SevereWeather.Warning): String

    // ---- separate threshold alerts (plain voice) -------------------------------
    fun highTemp(city: String, t: Int): String
    fun lowTemp(city: String, t: Int): String
    fun swing(city: String, todayMax: Int, tomorrowMax: Int, warming: Boolean): String
    fun aqiAlert(city: String, aqi: Int, label: String): String
    fun uvAlert(city: String, index: Int): String
    fun gustAlert(city: String, kmh: Int): String

    // ---- notification channels ---------------------------------------------------
    val channelRain: Pair<String, String>
    val channelBrief: Pair<String, String>
    val channelEvening: Pair<String, String>
    val channelTempExtreme: Pair<String, String>
    val channelTempSwing: Pair<String, String>
    val channelAqi: Pair<String, String>
    val channelUv: Pair<String, String>
    val channelGust: Pair<String, String>
    val channelSevere: Pair<String, String>
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

    // ---- places (one widget, several cities) ---------------------------------------
    val cfgPlacesHeader: String
    fun cfgAddPlaceToggle(on: Boolean): String
    val cfgAddPlaceHint: String
    val cfgRemovePlace: String
    fun cfgPlaceLabel(name: String, live: Boolean, current: Boolean): String
    fun cfgPlaceAdded(name: String): String
    val cfgPlacesFull: String
    fun cfgBackdropHint(b: WidgetBackdrop): String
    fun cfgAppearanceHint(a: WidgetAppearance): String

    // ---- saved cities and confirmation toasts on the setup screen ---------------------
    val cfgSavedHeader: String
    val cfgInWidget: String
    fun cfgDone(name: String, live: Boolean, added: Boolean): String
}

/** "0,2 mm" below 10 (one decimal), "12 mm" from 10 (whole number). */
internal fun formatMm(mm: Double, decimalComma: Boolean): String {
    val number = if (mm < 9.95) String.format(Locale.US, "%.1f", mm) else Math.round(mm).toString()
    return (if (decimalComma) number.replace('.', ',') else number) + " mm"
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
    override fun fullMeta(
        feels: Int?, humidity: Int?, windKmh: Int?, gustKmh: Int?, uv: Int?, rainMm: Double?,
    ): String? {
        val wind = when {
            windKmh != null && gustKmh != null && gustKmh > windKmh -> "wiatr $windKmh/${gustKmh}km/h"
            windKmh != null -> "wiatr ${windKmh}km/h"
            gustKmh != null -> "porywy ${gustKmh}km/h"
            else -> null
        }
        val parts = listOfNotNull(
            feels?.let { "odcz. $it°" }, humidity?.let { "wilg. $it%" }, wind,
            uv?.let { "UV $it" }, rainMm?.takeIf { it >= RainWindow.MIN_SHOWN_MM }?.let { rainAmount(it) },
        )
        // More readouts than fit at the usual spacing: tighten the separators
        // rather than let the line's type size collapse.
        return parts.takeIf { it.isNotEmpty() }?.joinToString(if (parts.size > 3) " · " else "  ·  ")
    }

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

    override fun nounFor(kind: RainWindow.Kind, intensity: RainWindow.Intensity) = when (intensity) {
        RainWindow.Intensity.DRIZZLE -> "mżawka"
        RainWindow.Intensity.HEAVY -> "ulewa"
        RainWindow.Intensity.RAIN -> rainNoun(kind)
    }

    override fun mm(mm: Double) = formatMm(mm, decimalComma = true)
    override fun rainTotal(mm: Double) = "${mm(mm)} łącznie"
    override fun rainAmount(mm: Double) = "opady ${mm(mm)}"
    override fun uv(index: Int) = "UV $index"
    override fun gusts(kmh: Int) = "porywy $kmh km/h"
    override fun nowcastStart(kind: RainWindow.Kind, minutes: Int) =
        if (minutes <= 0) "${rainNoun(kind)} za chwilę" else "${rainNoun(kind)} za ok. $minutes min"
    override fun nowcastOngoing(kind: RainWindow.Kind, endsInMinutes: Int?) =
        if (endsInMinutes == null) rainOngoing(kind) else "${rainOngoing(kind)}, koniec za ok. ${maxOf(5, endsInMinutes)} min"

    override fun pollenName(species: Pollen.Species) = when (species) {
        Pollen.Species.ALDER -> "olcha"
        Pollen.Species.BIRCH -> "brzoza"
        Pollen.Species.GRASS -> "trawy"
        Pollen.Species.MUGWORT -> "bylica"
        Pollen.Species.OLIVE -> "oliwka"
        Pollen.Species.RAGWEED -> "ambrozja"
    }
    override fun pollenShort(species: Pollen.Species, veryHigh: Boolean) =
        (if (veryHigh) "silne pylenie: " else "pylenie: ") + pollenName(species)
    override fun briefPollen(species: Pollen.Species, veryHigh: Boolean) =
        (if (veryHigh) "bardzo wysokie pylenie: " else "wysokie pylenie: ") + pollenName(species)

    override fun officialShort(event: String, degree: Int) = "IMGW: ${event.lowercase()} $degree°"
    override fun officialTitle(event: String, degree: Int) = "Ostrzeżenie IMGW, $degree. stopnia: ${event.lowercase()}"
    override fun officialValidity(from: String, to: String) = "od ${shortDateTime(from)} do ${shortDateTime(to)}"

    override fun inMinutes(min: Int) = "za $min min"
    override fun sunsetIn(min: Int) = "zachód za $min min"
    override fun sunriseIn(min: Int) = "wschód za $min min"

    override val a11yRefresh = "Odśwież prognozę"
    override val a11ySetup = "Ustawienia widgetu"
    override fun a11yCityButton(name: String) = "Miasto: $name. Dotknij, aby zmienić"
    override fun a11yNextPlace(name: String, index: Int, count: Int) =
        "Miejsce: $name, $index z $count. Dotknij, aby przejść do następnego"
    private val DOW_LONG = arrayOf("niedziela", "poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota")
    override fun dowLong(dow: Int) = DOW_LONG[Math.floorMod(dow, 7)]
    override fun a11ySummary(city: String, tempC: Int?, line: String, meta: String?) =
        listOfNotNull(city, tempC?.let { "$it°" }, line, meta).joinToString(", ") + ". Dotknij, aby otworzyć pełną prognozę"
    override fun a11yCurve(min: Int, max: Int) = "Temperatura w ciągu najbliższej doby, od $min° do $max°"
    override fun a11yDay(label: String, max: Int, min: Int, rain: String?) =
        "$label: maksymalnie $max°, minimalnie $min°" + (rain?.let { ", $it" } ?: "") + ". Dotknij, aby zobaczyć godziny"

    override fun rainShort(
        kind: RainWindow.Kind, ongoing: Boolean, soon: Boolean, start: Int, end: Int?, intensity: RainWindow.Intensity,
    ): String {
        val noun = nounFor(kind, intensity)
        return when {
            ongoing && end != null -> "${rainOngoing(kind)} do ~$end"
            ongoing -> "${rainOngoing(kind)} na dłużej"
            soon && end != null -> "$noun wkrótce, do ~$end"
            soon -> "$noun wkrótce"
            end != null -> "$noun $start–$end"
            else -> "$noun od $start"
        }
    }

    override fun rainLong(
        kind: RainWindow.Kind, ongoing: Boolean, soon: Boolean, start: String, end: String?, intensity: RainWindow.Intensity,
    ): String {
        val noun = nounFor(kind, intensity)
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
    override fun briefUv(index: Int) = "UV $index — krem z filtrem i okulary"
    override fun briefGusts(kmh: Int) = "porywy do $kmh km/h"
    override fun possibleRainMm(pop: Int, mm: Double) = "możliwe opady ($pop%), ok. ${mm(mm)}"

    override fun severeTitle(kind: SevereWeather.Kind) = when (kind) {
        SevereWeather.Kind.HAIL -> "Ostrzeżenie: burza z gradem"
        SevereWeather.Kind.STORM -> "Ostrzeżenie: burza"
        SevereWeather.Kind.WIND -> "Ostrzeżenie: silny wiatr"
        SevereWeather.Kind.HEAT -> "Ostrzeżenie: upał"
    }

    override fun severeText(city: String, warning: SevereWeather.Warning): String {
        val when_ = when {
            warning.startsInHours <= 0 -> "teraz"
            warning.startsInHours == 1 -> "za godzinę"
            else -> "za ok. ${warning.startsInHours} godz."
        }
        return when (warning.kind) {
            SevereWeather.Kind.HAIL -> "$city: burza z gradem $when_. Schowaj samochód i zostań w środku."
            SevereWeather.Kind.STORM -> "$city: burza $when_. Zostań w środku, trzymaj się z dala od drzew i wody."
            SevereWeather.Kind.WIND -> "$city: porywy do ${warning.peak} km/h $when_. Zabezpiecz luźne rzeczy, uważaj na gałęzie."
            SevereWeather.Kind.HEAT -> "$city: do ${warning.peak}°C $when_. Pij dużo, unikaj słońca w południe."
        }
    }

    override fun highTemp(city: String, t: Int) = "$city: $t°C — upał. Pij wodę i szukaj cienia."
    override fun lowTemp(city: String, t: Int) = "$city: $t°C — zimno. Ubierz się cieplej."
    override fun swing(city: String, todayMax: Int, tomorrowMax: Int, warming: Boolean) =
        "$city: jutro $tomorrowMax° zamiast $todayMax° — wyraźnie ${if (warming) "cieplej" else "chłodniej"}."
    override fun aqiAlert(city: String, aqi: Int, label: String) =
        "$city: AQI $aqi ($label) — ogranicz wysiłek na zewnątrz."
    override fun uvAlert(city: String, index: Int) = "$city: UV do $index — krem z filtrem, okulary, cień w południe."
    override fun gustAlert(city: String, kmh: Int) = "$city: porywy do $kmh km/h — uważaj na zewnątrz."

    override val channelRain = "Deszcz" to "Deszcz lub burza tuż-tuż, a także kiedy przestanie"
    override val channelBrief = "Poranny brief" to "Jedno podsumowanie dnia rano"
    override val channelEvening = "Zapowiedź jutra" to "Wieczorem: jak będzie jutro"
    override val channelTempExtreme = "Upał i mróz" to "Przekroczone progi temperatury (gdy brief jest wyłączony)"
    override val channelTempSwing = "Skoki temperatury" to "Duża zmiana temperatury z dnia na dzień (gdy brief jest wyłączony)"
    override val channelAqi = "Jakość powietrza" to "Przekroczony próg AQI (gdy brief jest wyłączony)"
    override val channelUv = "Promieniowanie UV" to "Wysoki indeks UV (gdy brief jest wyłączony)"
    override val channelGust = "Porywy wiatru" to "Przekroczony próg porywów (gdy brief jest wyłączony)"
    override val channelSevere = "Ostrzeżenia" to "Burza, grad, silny wiatr i upał — od razu, zawsze poważnie"
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

    override val cfgPlacesHeader = "$ configure widget --places"
    override fun cfgAddPlaceToggle(on: Boolean) = if (on) "[x] dodaj jako kolejne miejsce" else "[ ] dodaj jako kolejne miejsce"
    override val cfgAddPlaceHint = "// dotknięcie nazwy miasta na widgecie przełącza miejsca"
    override val cfgRemovePlace = "[ usuń ]"
    override fun cfgPlaceLabel(name: String, live: Boolean, current: Boolean) =
        (if (current) "> " else "  ") + (if (live) "◎ " else "") + name
    override fun cfgPlaceAdded(name: String) = "// dodano $name"
    override val cfgPlacesFull = "// lista pełna — zastąpiono bieżące miejsce"
    override fun cfgBackdropHint(b: WidgetBackdrop) = when (b) {
        WidgetBackdrop.NONE -> "// bez tła"
        WidgetBackdrop.CITY -> "// poziomice terenu wokół miasta, a na równinie pikselowy skyline"
        WidgetBackdrop.SCENE -> "// deszcz, śnieg, burza i gwiazdy w tle, tylko gdy coś się dzieje"
    }

    override fun cfgAppearanceHint(a: WidgetAppearance) = when (a) {
        WidgetAppearance.DARK -> "// ciemny terminal"
        WidgetAppearance.PAPER -> "// jasny papier, dla jasnej tapety"
        WidgetAppearance.AUTO -> "// papier za dnia, ciemny nocą"
    }

    override val cfgSavedHeader = "// miasta z aplikacji — dotknij, aby wybrać"
    override val cfgInWidget = " ✓"
    override fun cfgDone(name: String, live: Boolean, added: Boolean) = when {
        live -> "Widget śledzi Twoją lokalizację ($name)"
        added -> "Dodano miejsce: $name"
        else -> "Miasto widgetu: $name"
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
    override fun fullMeta(
        feels: Int?, humidity: Int?, windKmh: Int?, gustKmh: Int?, uv: Int?, rainMm: Double?,
    ): String? {
        val wind = when {
            windKmh != null && gustKmh != null && gustKmh > windKmh -> "wind $windKmh/${gustKmh}km/h"
            windKmh != null -> "wind ${windKmh}km/h"
            gustKmh != null -> "gusts ${gustKmh}km/h"
            else -> null
        }
        val parts = listOfNotNull(
            feels?.let { "feels $it°" }, humidity?.let { "hum $it%" }, wind,
            uv?.let { "UV $it" }, rainMm?.takeIf { it >= RainWindow.MIN_SHOWN_MM }?.let { rainAmount(it) },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(if (parts.size > 3) " · " else "  ·  ")
    }

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

    override fun nounFor(kind: RainWindow.Kind, intensity: RainWindow.Intensity) = when (intensity) {
        RainWindow.Intensity.DRIZZLE -> "drizzle"
        RainWindow.Intensity.HEAVY -> "downpour"
        RainWindow.Intensity.RAIN -> rainNoun(kind)
    }

    override fun mm(mm: Double) = formatMm(mm, decimalComma = false)
    override fun rainTotal(mm: Double) = "${mm(mm)} in total"
    override fun rainAmount(mm: Double) = "rain ${mm(mm)}"
    override fun uv(index: Int) = "UV $index"
    override fun gusts(kmh: Int) = "gusts $kmh km/h"
    override fun nowcastStart(kind: RainWindow.Kind, minutes: Int) =
        if (minutes <= 0) "${rainNoun(kind)} any moment" else "${rainNoun(kind)} in about $minutes min"
    override fun nowcastOngoing(kind: RainWindow.Kind, endsInMinutes: Int?) =
        if (endsInMinutes == null) rainOngoing(kind) else "${rainOngoing(kind)}, ends in about ${maxOf(5, endsInMinutes)} min"

    override fun pollenName(species: Pollen.Species) = when (species) {
        Pollen.Species.ALDER -> "alder"
        Pollen.Species.BIRCH -> "birch"
        Pollen.Species.GRASS -> "grass"
        Pollen.Species.MUGWORT -> "mugwort"
        Pollen.Species.OLIVE -> "olive"
        Pollen.Species.RAGWEED -> "ragweed"
    }
    override fun pollenShort(species: Pollen.Species, veryHigh: Boolean) =
        (if (veryHigh) "heavy pollen: " else "pollen: ") + pollenName(species)
    override fun briefPollen(species: Pollen.Species, veryHigh: Boolean) =
        (if (veryHigh) "very high pollen: " else "high pollen: ") + pollenName(species)

    // IMGW's own wording is Polish; the event name stays as the met office wrote it.
    override fun officialShort(event: String, degree: Int) = "IMGW: ${event.lowercase()} $degree°"
    override fun officialTitle(event: String, degree: Int) = "IMGW warning, level $degree: ${event.lowercase()}"
    override fun officialValidity(from: String, to: String) = "from ${shortDateTime(from)} to ${shortDateTime(to)}"

    override fun inMinutes(min: Int) = "in $min min"
    override fun sunsetIn(min: Int) = "sunset in $min min"
    override fun sunriseIn(min: Int) = "sunrise in $min min"

    override val a11yRefresh = "Refresh the forecast"
    override val a11ySetup = "Widget setup"
    override fun a11yCityButton(name: String) = "City: $name. Tap to change"
    override fun a11yNextPlace(name: String, index: Int, count: Int) =
        "Place: $name, $index of $count. Tap for the next one"
    private val DOW_LONG = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    override fun dowLong(dow: Int) = DOW_LONG[Math.floorMod(dow, 7)]
    override fun a11ySummary(city: String, tempC: Int?, line: String, meta: String?) =
        listOfNotNull(city, tempC?.let { "$it°" }, line, meta).joinToString(", ") + ". Tap to open the full forecast"
    override fun a11yCurve(min: Int, max: Int) = "Temperature over the next 24 hours, from $min° to $max°"
    override fun a11yDay(label: String, max: Int, min: Int, rain: String?) =
        "$label: high $max°, low $min°" + (rain?.let { ", $it" } ?: "") + ". Tap to see the hours"

    override fun rainShort(
        kind: RainWindow.Kind, ongoing: Boolean, soon: Boolean, start: Int, end: Int?, intensity: RainWindow.Intensity,
    ): String {
        val noun = nounFor(kind, intensity)
        return when {
            ongoing && end != null -> "${rainOngoing(kind)} until ~$end"
            ongoing -> "${rainOngoing(kind)} for a while"
            soon && end != null -> "$noun soon, until ~$end"
            soon -> "$noun soon"
            end != null -> "$noun $start–$end"
            else -> "$noun from $start"
        }
    }

    override fun rainLong(
        kind: RainWindow.Kind, ongoing: Boolean, soon: Boolean, start: String, end: String?, intensity: RainWindow.Intensity,
    ): String {
        val noun = nounFor(kind, intensity)
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
    override fun briefUv(index: Int) = "UV $index — sunscreen and sunglasses"
    override fun briefGusts(kmh: Int) = "gusts up to $kmh km/h"
    override fun possibleRainMm(pop: Int, mm: Double) = "possible rain ($pop%), about ${mm(mm)}"

    override fun severeTitle(kind: SevereWeather.Kind) = when (kind) {
        SevereWeather.Kind.HAIL -> "Warning: thunderstorm with hail"
        SevereWeather.Kind.STORM -> "Warning: thunderstorm"
        SevereWeather.Kind.WIND -> "Warning: strong wind"
        SevereWeather.Kind.HEAT -> "Warning: extreme heat"
    }

    override fun severeText(city: String, warning: SevereWeather.Warning): String {
        val when_ = when {
            warning.startsInHours <= 0 -> "now"
            warning.startsInHours == 1 -> "within the hour"
            else -> "in about ${warning.startsInHours} hours"
        }
        return when (warning.kind) {
            SevereWeather.Kind.HAIL -> "$city: thunderstorm with hail $when_. Put the car under cover and stay inside."
            SevereWeather.Kind.STORM -> "$city: thunderstorm $when_. Stay inside, keep away from trees and water."
            SevereWeather.Kind.WIND -> "$city: gusts up to ${warning.peak} km/h $when_. Secure loose items, watch for branches."
            SevereWeather.Kind.HEAT -> "$city: up to ${warning.peak}°C $when_. Drink plenty of water, avoid the midday sun."
        }
    }

    override fun highTemp(city: String, t: Int) = "$city: $t°C — heat. Drink water, find shade."
    override fun lowTemp(city: String, t: Int) = "$city: $t°C — cold. Dress warmly."
    override fun swing(city: String, todayMax: Int, tomorrowMax: Int, warming: Boolean) =
        "$city: $tomorrowMax° tomorrow instead of $todayMax° — noticeably ${if (warming) "warmer" else "colder"}."
    override fun aqiAlert(city: String, aqi: Int, label: String) =
        "$city: AQI $aqi ($label) — go easy outdoors."
    override fun uvAlert(city: String, index: Int) = "$city: UV up to $index — sunscreen, sunglasses, shade at midday."
    override fun gustAlert(city: String, kmh: Int) = "$city: gusts up to $kmh km/h — take care outdoors."

    override val channelRain = "Rain" to "Rain or a storm about to start, and when it's about to stop"
    override val channelBrief = "Morning brief" to "One summary of the day, in the morning"
    override val channelEvening = "Tomorrow preview" to "In the evening: what tomorrow looks like"
    override val channelTempExtreme = "Heat and frost" to "Temperature thresholds crossed (when the brief is off)"
    override val channelTempSwing = "Temperature swings" to "Big day-to-day temperature change (when the brief is off)"
    override val channelAqi = "Air quality" to "AQI threshold crossed (when the brief is off)"
    override val channelUv = "UV radiation" to "High UV index (when the brief is off)"
    override val channelGust = "Wind gusts" to "Gust threshold crossed (when the brief is off)"
    override val channelSevere = "Warnings" to "Storm, hail, strong wind and extreme heat — immediate, always serious"
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

    override val cfgPlacesHeader = "$ configure widget --places"
    override fun cfgAddPlaceToggle(on: Boolean) = if (on) "[x] add as another place" else "[ ] add as another place"
    override val cfgAddPlaceHint = "// tapping the city name on the widget switches places"
    override val cfgRemovePlace = "[ remove ]"
    override fun cfgPlaceLabel(name: String, live: Boolean, current: Boolean) =
        (if (current) "> " else "  ") + (if (live) "◎ " else "") + name
    override fun cfgPlaceAdded(name: String) = "// added $name"
    override val cfgPlacesFull = "// list is full — replaced the current place"
    override fun cfgBackdropHint(b: WidgetBackdrop) = when (b) {
        WidgetBackdrop.NONE -> "// no background"
        WidgetBackdrop.CITY -> "// terrain contours around the city, or a pixel skyline on flat land"
        WidgetBackdrop.SCENE -> "// rain, snow, a storm and stars behind the numbers, only when something is going on"
    }

    override fun cfgAppearanceHint(a: WidgetAppearance) = when (a) {
        WidgetAppearance.DARK -> "// dark terminal"
        WidgetAppearance.PAPER -> "// light paper, for a bright wallpaper"
        WidgetAppearance.AUTO -> "// paper by day, dark at night"
    }

    override val cfgSavedHeader = "// cities from the app — tap to pick"
    override val cfgInWidget = " ✓"
    override fun cfgDone(name: String, live: Boolean, added: Boolean) = when {
        live -> "Widget follows your location ($name)"
        added -> "Place added: $name"
        else -> "Widget city: $name"
    }
}

/** "2026-11-18T19:00" -> "18.11 19:00" (day.month hour:minute); the string itself if it isn't that shape. */
internal fun shortDateTime(iso: String): String {
    val m = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}:\d{2})""").find(iso) ?: return iso
    return "${m.groupValues[3]}.${m.groupValues[2]} ${m.groupValues[4]}"
}
