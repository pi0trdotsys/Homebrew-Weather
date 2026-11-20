package dev.pi0trdotsys.homebrewweather.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale

/**
 * "On this day": one real event from history for today's date, as a footer line.
 *
 * ## Where it comes from
 *
 * Wikipedia, no key and no backend of ours, like the forecast. English uses
 * Wikimedia's curated "selected events" feed. That feed answers 404 for Polish,
 * so Polish reads the day page of the Polish Wikipedia ("17 listopada") and takes
 * the bullets of its two events sections, "Wydarzenia w Polsce" and "Wydarzenia
 * na świecie" — the page's births and deaths use the same bullet format and are
 * left out on purpose. Mirrored in src/lib/on-this-day.ts; keep the parsing and
 * [pick] identical. The text is CC BY-SA, so the dashboard credits Wikipedia.
 *
 * ## Which event
 *
 * A line is `"<year> · <text>"`. The widget footer is one line of
 * [WellbeingTips.MAX_LINE_LENGTH] characters, so events whose line fits are
 * preferred; only when none do, the whole list is used and the line is cut at a
 * word boundary with an ellipsis. Within the pool the day of the year picks, so
 * the same event shows all day, and the dashboard — which shows the full line,
 * never cut — shows the same one.
 *
 * ## Never in the way
 *
 * The day's list is fetched once and cached; a failed fetch is not retried for
 * [FAILURE_BACKOFF_MS], and a miss means the caller falls back to a joke. Short
 * timeouts, because this runs on the widget's render path. Pure parsing and
 * picking are covered by OnThisDayTest.
 */
object OnThisDay {

    data class Event(val year: String, val text: String) {
        val line: String get() = "$year · $text"
    }

    /** How long a failed fetch is left alone before the next attempt. */
    const val FAILURE_BACKOFF_MS = 30 * 60 * 1000L

    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 6_000
    private const val USER_AGENT = "HomebrewWeather/0.6 (https://github.com/pi0trdotsys/Homebrew-Weather)"

    private val PL_MONTHS_GENITIVE = listOf(
        "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
        "lipca", "sierpnia", "września", "października", "listopada", "grudnia",
    )
    private val PL_EVENT_SECTIONS = setOf("Wydarzenia w Polsce", "Wydarzenia na świecie")

    // ---------------------------------------------------------------------
    // Pure: picking and fitting
    // ---------------------------------------------------------------------

    /**
     * The day's event: the short ones when any fit the footer, else all of them;
     * `pool[dayOfYear % pool.size]` (1-based day of the local calendar year).
     */
    fun pick(events: List<Event>, dayOfYear: Int, maxLine: Int = WellbeingTips.MAX_LINE_LENGTH): Event? {
        if (events.isEmpty()) return null
        val short = events.filter { it.line.length <= maxLine }
        val pool = short.ifEmpty { events }
        return pool[Math.floorMod(dayOfYear, pool.size)]
    }

    /** [Event.line], cut at a word boundary with "…" when it exceeds [maxLine]. */
    fun fit(event: Event, maxLine: Int = WellbeingTips.MAX_LINE_LENGTH): String {
        val line = event.line
        if (line.length <= maxLine) return line
        val room = maxLine - 1
        val cut = line.substring(0, room)
        val atWord = cut.lastIndexOf(' ')
        // A cut that would leave only the year is no better than a mid-word one.
        val head = if (atWord > event.year.length + 3) cut.substring(0, atWord) else cut
        return head.trimEnd(' ', ',', ';', ':', '·', '-', '–') + "…"
    }

    // ---------------------------------------------------------------------
    // Pure: parsing
    // ---------------------------------------------------------------------

    /**
     * The events of a Polish Wikipedia day page's wikitext: bullets like
     * `* [[1892]] – Założono Polską Partię Socjalistyczną.` under the two events
     * sections. Sub-headings (`=== …`) stay part of their section.
     */
    fun parsePolishWikitext(wikitext: String): List<Event> {
        val events = mutableListOf<Event>()
        var inEvents = false
        val bullet = Regex("""(?U)^\*\s*(?:\[\[)?(\d{3,4})(?:\]\])?\s*[–—-]\s*(.+)$""")
        val topHeading = Regex("""(?U)^==([^=].*?)==\s*$""")
        for (raw in wikitext.replace("\r\n", "\n").split('\n')) {
            val line = raw.trimEnd()
            val heading = topHeading.matchEntire(line)
            if (heading != null) {
                inEvents = cleanWikitext(heading.groupValues[1]) in PL_EVENT_SECTIONS
                continue
            }
            if (!inEvents) continue
            val m = bullet.matchEntire(line) ?: continue
            val text = cleanWikitext(m.groupValues[2])
            if (text.isNotBlank()) events += Event(m.groupValues[1], text)
        }
        return events
    }

    /** `{"selected":[{"year":1969,"text":"…"}]}` from Wikimedia's on-this-day feed. */
    fun parseEnglishFeed(json: String): List<Event> {
        val arr = JSONObject(json).optJSONArray("selected") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            // Strict about shapes, like the web side: a text that isn't a string, or a
            // year that is neither a number nor a string (JSON null included), is no event.
            val rawText = o.opt("text") as? String ?: return@mapNotNull null
            val rawYear = o.opt("year")
            val year = if (rawYear is Number || rawYear is String) rawYear.toString() else return@mapNotNull null
            val text = rawText.replace('\u00a0', ' ').trim()
            if (text.isBlank() || year.isBlank()) null else Event(year, text)
        }
    }

    /**
     * Wikitext to plain text. Mirrors `cleanWikitext` in src/lib/on-this-day.ts
     * step for step — a different result on either side would make the widget
     * and the dashboard pick different events, because which events are "short"
     * depends on the cleaned text.
     */
    internal fun cleanWikitext(raw: String): String {
        var s = raw
        s = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL).replace(s, "")
        s = Regex("""<ref\b[^>]*/>""", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("""<ref\b[^>]*>.*?</ref>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(s, "")
        // Two templates carry text the sentence needs: {{link-interwiki |Name |…}} is
        // a name without a page of its own here, {{C|19}} a temperature. Dropped
        // whole they leave "został premierem…" and "rekord temperatury: .".
        s = Regex("""\{\{\s*link-interwiki\s*\|\s*([^|{}]+?)\s*(?:\|[^{}]*)?}}""", RegexOption.IGNORE_CASE).replace(s, "$1")
        s = Regex("""\{\{\s*C\s*\|\s*([^|{}]+?)\s*}}""").replace(s, "$1°C")
        // Templates nest, so peel the innermost ones until none are left.
        var previous = ""
        while (previous != s) {
            previous = s
            s = Regex("""\{\{[^{}]*}}""").replace(s, "")
        }
        // Files and categories are not text.
        s = Regex("""\[\[(?:File|Image|Plik|Grafika|Category|Kategoria):[^\]]*]]""", RegexOption.IGNORE_CASE).replace(s, "")
        s = Regex("""\[\[[^\]|]*\|([^\]]*)]]""").replace(s, "$1")
        s = Regex("""\[\[([^\]]*)]]""").replace(s, "$1")
        // External links: [http://x label] -> label, [http://x] -> nothing.
        s = Regex("""\[(?:https?:)?//\S+\s+([^\]]+)]""").replace(s, "$1")
        s = Regex("""\[(?:https?:)?//\S+]""").replace(s, "")
        s = Regex("""'{2,}""").replace(s, "")
        s = Regex("""<[^>]+>""").replace(s, "")
        s = s.replace("&nbsp;", " ").replace("&ndash;", "–").replace("&mdash;", "—")
            .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        return s.replace(Regex("""[\s\u00a0]+"""), " ").trim()
    }

    // ---------------------------------------------------------------------
    // Network + cache
    // ---------------------------------------------------------------------

    /** The footer line for today, or null — no data yet, offline, or a recent failure. */
    fun footerLine(context: Context, lang: Lang, nowMillis: Long = System.currentTimeMillis()): String? {
        val events = eventsFor(context, lang, nowMillis) ?: return null
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return pick(events, cal.get(Calendar.DAY_OF_YEAR))?.let(::fit)
    }

    private fun eventsFor(context: Context, lang: Lang, nowMillis: Long): List<Event>? {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val key = "${lang.storageValue}_%02d-%02d".format(Locale.US, month, day)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs.getString("events_$key", null)?.let { cached ->
            readEvents(cached)?.let { return it }
        }
        val failedAt = prefs.getLong("failed_${lang.storageValue}", 0L)
        if (nowMillis - failedAt < FAILURE_BACKOFF_MS) return null

        val events = try {
            fetch(lang, month, day)
        } catch (e: Exception) {
            emptyList()
        }
        if (events.isEmpty()) {
            prefs.edit().putLong("failed_${lang.storageValue}", nowMillis).apply()
            return null
        }
        // One day's list is all that is ever kept: yesterday's would only be clutter.
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith("events_") }.forEach { edit.remove(it) }
        edit.putString("events_$key", writeEvents(events)).remove("failed_${lang.storageValue}").apply()
        return events
    }

    private fun fetch(lang: Lang, month: Int, day: Int): List<Event> = when (lang) {
        Lang.PL -> {
            val page = "${day}_${PL_MONTHS_GENITIVE[month - 1]}"
            val json = get(
                "https://pl.wikipedia.org/w/api.php?action=parse&page=$page" +
                    "&prop=wikitext&format=json&formatversion=2",
            )
            parsePolishWikitext(JSONObject(json).getJSONObject("parse").getString("wikitext"))
        }
        Lang.EN -> parseEnglishFeed(
            get("https://api.wikimedia.org/feed/v1/wikipedia/en/onthisday/selected/%02d/%02d".format(Locale.US, month, day)),
        )
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Api-User-Agent", USER_AGENT)
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode !in 200..299) throw RuntimeException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun writeEvents(events: List<Event>): String =
        JSONArray().apply {
            events.forEach { put(JSONArray().put(it.year).put(it.text)) }
        }.toString()

    private fun readEvents(raw: String): List<Event>? = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { Event(arr.getJSONArray(it).getString(0), arr.getJSONArray(it).getString(1)) }
            .takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    private const val PREFS_NAME = "on_this_day_prefs"
}

/** Which kind of line the widget footer (and the dashboard) shows. */
enum class FooterSource(val storageValue: String) {
    JOKE("joke"),
    WELLBEING("wellbeing"),
    HISTORY("history");

    companion object {
        fun fromStorage(raw: String?): FooterSource = entries.firstOrNull { it.storageValue == raw } ?: JOKE
    }
}

/**
 * The widget footer's text, by [FooterSource]. A joke follows the tone; a
 * well-being tip follows the weather; "on this day" is a real event — and when
 * that one can't be had (offline, a recent failure) the footer is a joke, never
 * a blank line.
 */
object FooterContent {

    fun line(
        context: Context,
        source: FooterSource,
        tone: Tone,
        lang: Lang,
        weather: WeatherApi.WeatherData,
        kind: String,
        isNight: Boolean,
        seed: Int,
    ): String {
        val text = when (source) {
            FooterSource.WELLBEING -> WellbeingTips.pick(lang, weather, seed)
            FooterSource.HISTORY -> OnThisDay.footerLine(context, lang)
            FooterSource.JOKE -> null
        }
        // Hot and cold days and the day of the week have lines of their own, mixed in with the weather's.
        return text ?: Jokes.pick(
            tone, lang, kind, isNight, seed,
            temperature = weather.currentTemperature,
            dayOfWeek = LinePicker.dayOfWeek(weather.daily.firstOrNull()?.date),
        )
    }
}
