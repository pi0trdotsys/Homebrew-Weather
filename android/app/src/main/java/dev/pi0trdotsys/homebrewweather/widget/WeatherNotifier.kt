package dev.pi0trdotsys.homebrewweather.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.pi0trdotsys.homebrewweather.MainActivity
import dev.pi0trdotsys.homebrewweather.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Evaluates the notification rules as a side effect of a *fresh* per-widget
 * weather fetch — called from [WeatherWidgetProvider.buildRemoteViews] right
 * after a successful (non-cached) [WeatherApi.fetchWeather] call, so it
 * piggybacks on the existing WeatherWorker periodic refresh (and manual/
 * configure refreshes) instead of running its own polling loop.
 *
 * ## What it sends
 *
 * - **Rain soon** — rain or a storm due within about an hour (see
 *   [checkRainSoon]). It used to fire only once it had *already started*
 *   raining, which is when you no longer need telling.
 * - **Rain stopping** — the other half: after a spell of at least two hours,
 *   when it's about to stop (see [RainStop]). Both ride the rain toggle.
 * - **Morning brief** — one summary of the day ([MorningBrief]), replacing the
 *   separate high / low / swing / air-quality alerts.
 * - **Evening preview** — opt-in, tomorrow in one line ([EveningPreview]).
 * - **Severe weather** — storm, hail, violent gusts and extreme heat, as soon as
 *   one is in the forecast ([SevereWeather]). Always plain-voiced, whatever the
 *   tone, and independent of the brief and the rain toggle. A storm warning
 *   replaces the rain heads-up for that storm rather than doubling it.
 * - **UV and wind gusts** — brief lines; their own alerts when the brief is off.
 *
 * With the brief switched off in settings, the separate alerts come back
 * exactly as before — nothing that was configurable has stopped being so.
 *
 * All settings are read live from the shared "CapacitorStorage" prefs file
 * (see [CapacitorStorage]); dedupe state lives in [NotifStatePrefs]. Wording
 * follows the app-wide [Tone] and [Lang].
 */
object WeatherNotifier {
    private const val CHANNEL_RAIN = "rain_alerts"
    private const val CHANNEL_BRIEF = "morning_brief"
    private const val CHANNEL_EVENING = "evening_preview"
    private const val CHANNEL_TEMP_EXTREME = "temp_extreme_alerts"
    private const val CHANNEL_TEMP_SWING = "temp_swing_alerts"
    private const val CHANNEL_AQI = "aqi_alerts"
    private const val CHANNEL_UV = "uv_alerts"
    private const val CHANNEL_GUST = "gust_alerts"
    private const val CHANNEL_SEVERE = "severe_alerts"

    /** How close a rain window has to be to warn about it: the current hour
     * or the next one, i.e. roughly a 0-2 hour heads-up. */
    private const val RAIN_SOON_HOURS = 1

    /** With 15-minute data: rain this close is a heads-up (the hourly rule's "within the hour", but exact). */
    private const val RAIN_SOON_MINUTES = 30

    /** When rain is expected beyond [RAIN_SOON_MINUTES] but within the nowcast horizon, check again this soon. */
    private const val RAIN_RECHECK_MINUTES = 12L

    /** Official warnings announced at once for one place; the rest wait for the next refresh. */
    private const val MAX_OFFICIAL_NOTIFICATIONS = 3

    /** Local hours a UV alert may go out in: from the morning, until the sun is past its worst. */
    private val UV_ALERT_HOURS = 6..16

    private fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Creating a channel that exists updates its name and description, so
     * this also relabels them when the language changes. */
    private fun ensureChannels(context: Context, texts: Texts) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        listOf(
            CHANNEL_RAIN to texts.channelRain,
            CHANNEL_BRIEF to texts.channelBrief,
            CHANNEL_EVENING to texts.channelEvening,
            CHANNEL_TEMP_EXTREME to texts.channelTempExtreme,
            CHANNEL_TEMP_SWING to texts.channelTempSwing,
            CHANNEL_AQI to texts.channelAqi,
            CHANNEL_UV to texts.channelUv,
            CHANNEL_GUST to texts.channelGust,
            CHANNEL_SEVERE to texts.channelSevere,
        ).forEach { (id, label) ->
            // A warning is the one notification that should be allowed to make a sound.
            val importance = if (id == CHANNEL_SEVERE) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            nm.createNotificationChannel(
                NotificationChannel(id, label.first, importance).apply {
                    description = label.second
                },
            )
        }
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun notify(
        context: Context,
        notificationId: Int,
        channelId: String,
        title: String,
        text: String,
        expanded: String? = null,
        city: WidgetCity? = null,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    ) {
        try {
            // Opening a notification opens the app on the city it's about,
            // same as tapping the widget (see MainActivity).
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (city != null) {
                    putExtra(MainActivity.EXTRA_CITY_LAT, city.lat)
                    putExtra(MainActivity.EXTRA_CITY_LON, city.lon)
                    putExtra(MainActivity.EXTRA_CITY_NAME, city.name)
                    putExtra(MainActivity.EXTRA_CITY_LIVE, city.isLive)
                }
            }
            val pending = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(priority)
            if (expanded != null && expanded != text) {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            }
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Permission revoked between the areNotificationsEnabled() check and here
            // (or on some OEM skins that fib about it) — never let a notification
            // crash the widget/worker.
        }
    }

    private fun titleFor(city: WidgetCity) = "Homebrew Weather — ${city.name}"

    /**
     * The key one place's notification state and ids live under. State used to be
     * keyed by the widget alone, which was fine while a widget had one city; with
     * several, "it was raining" for the place on show must not be mistaken for the
     * same flag of the place you've just switched to. Stable for a place however
     * the list is reordered, and small enough that `scope * 100 + k` stays an Int.
     */
    fun scopeOf(appWidgetId: Int, city: WidgetCity): Int =
        Math.floorMod(appWidgetId * 31 + Places.cacheKey(city).hashCode(), 5_000_000)

    /**
     * Entry point — called once per widget place right after a fresh weather fetch.
     *
     * [criticalOnly] is for the places a widget has but isn't showing: rain, storms
     * and official warnings are worth interrupting for wherever they are, but the
     * morning brief, the temperature and air-quality alerts are about where you
     * are, and a brief for every saved city each morning would be noise.
     */
    fun evaluate(
        context: Context,
        appWidgetId: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        criticalOnly: Boolean = false,
    ) {
        if (!hasPermission(context)) return
        val lang = CapacitorStorage.lang(context)
        val texts = lang.texts
        ensureChannels(context, texts)
        val tone = CapacitorStorage.tone(context)
        val scope = scopeOf(appWidgetId, city)
        checkSevere(context, scope, city, weather, texts)
        checkRainSoon(context, scope, city, weather, tone, texts)
        checkRainStop(context, scope, city, weather, texts)
        if (criticalOnly) return
        if (CapacitorStorage.briefEnabled(context)) {
            checkBrief(context, scope, city, weather, tone, texts)
        } else {
            checkHighLow(context, scope, city, weather, tone, texts)
            checkSwing(context, scope, city, weather, tone, texts)
            checkAqi(context, scope, city, weather, tone, texts)
            checkUv(context, scope, city, weather, tone, texts)
            checkGust(context, scope, city, weather, tone, texts)
        }
        if (CapacitorStorage.eveningEnabled(context)) checkEvening(context, scope, city, weather, texts)
    }

    /**
     * Whether rain (or a storm) is imminent or already falling — the state the
     * rain heads-up is edge-triggered on.
     *
     * With 15-minute data the nowcast decides, by amounts: rain within
     * [RAIN_SOON_MINUTES], or falling now, and at least [RainNowcast.NOTIFY_MIN_MM]
     * in that spell, so a few drops never ring. Without it (an old cache, a place
     * the 15-minute model doesn't cover) the hourly window does, as before.
     */
    private fun rainImminent(weather: WeatherApi.WeatherData, nowMillis: Long): Boolean {
        val nowcast = RainNowcast.forWeather(weather, nowMillis)
        if (nowcast != null) {
            if (nowcast.totalMm < RainNowcast.NOTIFY_MIN_MM) return false
            return nowcast.ongoing || (nowcast.startsInMinutes ?: Int.MAX_VALUE) <= RAIN_SOON_MINUTES
        }
        return RainWindow.find(weather.hourly, weather.currentWeatherCode)
            ?.let { it.startsInHours <= RAIN_SOON_HOURS } == true
    }

    /**
     * Rain or a storm about to start — or already falling.
     *
     * Edge-triggered on "not imminent" -> "imminent", tracked in the same
     * per-place flag the old "it started raining" alert used. That's what
     * makes one rain spell produce one notification: a window that is
     * *ongoing* gets a new start time every hour it keeps raining, so keying
     * on the window itself would re-alert hourly for as long as it rains.
     */
    private fun checkRainSoon(
        context: Context,
        scope: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        tone: Tone,
        texts: Texts,
    ) {
        val now = System.currentTimeMillis()
        val imminent = rainImminent(weather, now)
        // Rain close but not yet close enough to announce: look again soon, rather
        // than after the next scheduled refresh (up to half an hour away).
        if (CapacitorStorage.rainEnabled(context) && !imminent) {
            val starts = RainNowcast.forWeather(weather, now)?.startsInMinutes
            if (starts != null && starts > RAIN_SOON_MINUTES) WeatherWorker.scheduleRecheck(context, RAIN_RECHECK_MINUTES)
        }
        val was = NotifStatePrefs.wasRaining(context, scope)
        NotifStatePrefs.setWasRaining(context, scope, imminent)
        if (!CapacitorStorage.rainEnabled(context) || !imminent || was) return

        val hourly = RainWindow.find(weather.hourly, weather.currentWeatherCode)
        // A storm is the severe alert's to announce (plain voice, own channel);
        // sending the rain heads-up as well would be the same storm twice.
        if (hourly?.kind == RainWindow.Kind.THUNDER && CapacitorStorage.severeEnabled(context)) return

        val nowcast = RainNowcast.forWeather(weather, now)
        if (nowcast != null) {
            sendNowcastRain(context, scope, city, hourly?.kind ?: RainWindow.Kind.RAIN, nowcast, tone, texts)
        } else if (hourly != null) {
            sendRainSoon(context, scope, city, hourly, tone, texts, prefix = "")
        }
    }

    /** "Wrocław: deszcz za ok. 10 min, 1,2 mm łącznie" — the by-the-minute heads-up. */
    private fun sendNowcastRain(
        context: Context,
        scope: Int,
        city: WidgetCity,
        kind: RainWindow.Kind,
        nowcast: RainNowcast.Result,
        tone: Tone,
        texts: Texts,
    ) {
        val lead = if (nowcast.ongoing) {
            texts.nowcastOngoing(kind, nowcast.endsInMinutes?.let(RainNowcast::roundForSpeech))
        } else {
            texts.nowcastStart(kind, RainNowcast.roundForSpeech(nowcast.startsInMinutes ?: 0))
        }
        val amount = if (nowcast.totalMm >= RainWindow.MIN_SHOWN_MM) ", ${texts.rainTotal(nowcast.totalMm)}" else ""
        val text = "${city.name}: $lead$amount"
        val expanded = if (tone == Tone.RUDE) {
            "$text\n\n${RudeNotifications.rainTail(kind == RainWindow.Kind.THUNDER, lang = texts.lang)}"
        } else {
            null
        }
        notify(context, scope * 100 + 1, CHANNEL_RAIN, titleFor(city), text, expanded, city)
    }

    private fun sendRainSoon(
        context: Context,
        scope: Int,
        city: WidgetCity,
        window: RainWindow.Window,
        tone: Tone,
        texts: Texts,
        prefix: String,
    ) {
        val isThunder = window.kind == RainWindow.Kind.THUNDER
        val text = "$prefix${city.name}: ${RainWindow.long(window, texts)}"
        val expanded = if (tone == Tone.RUDE) "$text\n\n${RudeNotifications.rainTail(isThunder, lang = texts.lang)}" else null
        notify(context, scope * 100 + 1, CHANNEL_RAIN, titleFor(city), text, expanded, city)
    }

    /**
     * It's been raining for a while and is about to stop ([RainStop]).
     * Once per spell: the spell's start is remembered while it rains, and the
     * "sent" flag clears when it stops.
     */
    private fun checkRainStop(
        context: Context,
        scope: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        texts: Texts,
    ) {
        val now = System.currentTimeMillis()
        val window = RainWindow.find(weather.hourly, weather.currentWeatherCode)
        val raining = window?.ongoing == true
        if (!raining) {
            NotifStatePrefs.setRainingSince(context, scope, 0L)
            NotifStatePrefs.setStopSent(context, scope, false)
            return
        }
        var since = NotifStatePrefs.rainingSince(context, scope)
        if (since == 0L) {
            since = now
            NotifStatePrefs.setRainingSince(context, scope, now)
        }
        val hours = ((now - since) / 3_600_000L).toInt()
        if (!CapacitorStorage.rainEnabled(context) || NotifStatePrefs.stopSent(context, scope)) return
        if (!RainStop.isDue(window, hours)) return
        NotifStatePrefs.setStopSent(context, scope, true)
        val end = window?.end ?: return
        notify(
            context,
            scope * 100 + 7,
            CHANNEL_RAIN,
            titleFor(city),
            "${city.name}: ${texts.rainStop(window.kind, end)}",
            city = city,
        )
    }

    private fun thresholds(context: Context) = MorningBrief.Thresholds(
        highEnabled = CapacitorStorage.highEnabled(context),
        highThreshold = CapacitorStorage.highThreshold(context),
        lowEnabled = CapacitorStorage.lowEnabled(context),
        lowThreshold = CapacitorStorage.lowThreshold(context),
        swingEnabled = CapacitorStorage.swingEnabled(context),
        swingThreshold = CapacitorStorage.swingThreshold(context),
        aqiEnabled = CapacitorStorage.aqiEnabled(context),
        aqiThreshold = CapacitorStorage.aqiThreshold(context),
        uvEnabled = CapacitorStorage.uvEnabled(context),
        uvThreshold = CapacitorStorage.uvThreshold(context),
        gustEnabled = CapacitorStorage.gustEnabled(context),
        gustThreshold = CapacitorStorage.gustThreshold(context),
        pollenEnabled = CapacitorStorage.pollenEnabled(context),
    )

    private fun composeBrief(
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        tone: Tone,
        texts: Texts,
        context: Context,
    ): MorningBrief.Brief? {
        val seed = (System.currentTimeMillis() / (60 * 60 * 1000L)).toInt()
        val tail = when (tone) {
            Tone.RUDE -> RudeNotifications.briefTail(seed, texts.lang)
            Tone.SIGMA -> Jokes.pick(tone, texts.lang, Wmo.wmoToKind(weather.daily.firstOrNull()?.weatherCode ?: 0), false, seed)
            Tone.CLEAN -> null
        }
        return MorningBrief.compose(
            cityName = city.name,
            weather = weather,
            thresholds = thresholds(context),
            aqiLabel = texts::aqiLabel,
            tail = tail,
            texts = texts,
        )
    }

    private fun checkBrief(
        context: Context,
        scope: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        tone: Tone,
        texts: Texts,
    ) {
        val today = todayIso()
        val due = MorningBrief.isDue(
            hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            briefHour = CapacitorStorage.briefHour(context),
            alreadySentToday = NotifStatePrefs.lastBriefDate(context, scope) == today,
            refreshMinutes = CapacitorStorage.refreshIntervalMinutes(context),
        )
        if (!due) return
        val brief = composeBrief(city, weather, tone, texts, context) ?: return
        NotifStatePrefs.setBriefDate(context, scope, today)
        notify(context, scope * 100 + 6, CHANNEL_BRIEF, brief.title, brief.summary, brief.full, city)
    }

    private fun checkEvening(
        context: Context,
        scope: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        texts: Texts,
    ) {
        val today = todayIso()
        val due = EveningPreview.isDue(
            hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            eveningHour = CapacitorStorage.eveningHour(context),
            alreadySentToday = NotifStatePrefs.lastEveningDate(context, scope) == today,
            refreshMinutes = CapacitorStorage.refreshIntervalMinutes(context),
        )
        if (!due) return
        val preview = EveningPreview.compose(city.name, weather, texts) ?: return
        NotifStatePrefs.setEveningDate(context, scope, today)
        notify(context, scope * 100 + 8, CHANNEL_EVENING, preview.title, preview.summary, city = city)
    }

    /**
     * Sends one of each kind — rain heads-up, morning brief, evening preview —
     * right now, built from real data for [city], each titled "[test]". For
     * the "send test notifications" button in the app's settings, so what the
     * notifications look like can be checked without waiting for 7am or rain.
     * Returns how many were posted; 0 without notification permission.
     */
    fun sendTest(context: Context, city: WidgetCity, weather: WeatherApi.WeatherData): Int {
        if (!hasPermission(context)) return 0
        val lang = CapacitorStorage.lang(context)
        val texts = lang.texts
        ensureChannels(context, texts)
        val tone = CapacitorStorage.tone(context)
        val id = TEST_ID_BASE
        var sent = 0

        val window = RainWindow.find(weather.hourly, weather.currentWeatherCode)
        if (window != null) {
            sendRainSoon(context, id, city, window, tone, texts, prefix = texts.testPrefix)
        } else {
            notify(context, id * 100 + 1, CHANNEL_RAIN, titleFor(city), "${texts.testPrefix}${city.name}: ${texts.testNoRain}", city = city)
        }
        sent++

        composeBrief(city, weather, tone, texts, context)?.let {
            notify(context, id * 100 + 6, CHANNEL_BRIEF, texts.testPrefix + it.title, it.summary, it.full, city)
            sent++
        }
        EveningPreview.compose(city.name, weather, texts)?.let {
            notify(context, id * 100 + 8, CHANNEL_EVENING, texts.testPrefix + it.title, it.summary, city = city)
            sent++
        }
        // A real warning if there is one, otherwise a sample storm — either way
        // titled "[test]", so nobody mistakes the sample for a forecast.
        val warning = SevereWeather.find(weather).firstOrNull() ?: SevereWeather.Warning(SevereWeather.Kind.STORM, 1)
        notify(
            context, id * 100 + 10, CHANNEL_SEVERE,
            texts.testPrefix + texts.severeTitle(warning.kind), texts.severeText(city.name, warning),
            city = city, priority = NotificationCompat.PRIORITY_HIGH,
        )
        sent++
        return sent
    }

    /** Notification id base for test sends — no real widget id is this large. */
    private const val TEST_ID_BASE = 9_999_990

    /**
     * Storm, hail, violent gusts or extreme heat — derived from the forecast (see
     * [SevereWeather]) — and, in Poland, the official IMGW warnings for the
     * county. Edge-triggered per kind — one storm is one warning, and a later,
     * separate one warns again — except heat, which stays true all day and is
     * therefore capped at one a day.
     *
     * An official warning for the same hazard speaks for the derived one: the
     * derived flag is still tracked, but only one notification goes out about a
     * storm. Deliberately outside the tone and the brief: plain-voiced whatever
     * the tone, and sent on the refresh that sees it, not at 7am.
     */
    private fun checkSevere(
        context: Context,
        scope: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        texts: Texts,
    ) {
        val enabled = CapacitorStorage.severeEnabled(context)
        val officialKinds = weather.official.mapNotNull { ImgwWarnings.kindOf(it.event) }.toSet()
        val found = SevereWeather.find(weather).associateBy { it.kind }
        for (kind in SevereWeather.Kind.entries) {
            val warning = found[kind]
            val wasActive = NotifStatePrefs.severeActive(context, scope, kind)
            NotifStatePrefs.setSevereActive(context, scope, kind, warning != null)
            if (!enabled || warning == null || wasActive || kind in officialKinds) continue
            if (kind == SevereWeather.Kind.HEAT) {
                val today = todayIso()
                if (NotifStatePrefs.lastHeatWarningDate(context, scope) == today) continue
                NotifStatePrefs.setHeatWarningDate(context, scope, today)
            }
            notify(
                context, scope * 100 + 10 + kind.ordinal, CHANNEL_SEVERE,
                texts.severeTitle(kind), texts.severeText(city.name, warning),
                city = city, priority = NotificationCompat.PRIORITY_HIGH,
            )
        }
        checkOfficial(context, scope, city, weather, texts, enabled)
    }

    /** Each official warning is announced once, by its id; the seen set is trimmed to what the feed still lists. */
    private fun checkOfficial(
        context: Context,
        scope: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        texts: Texts,
        enabled: Boolean,
    ) {
        val seen = NotifStatePrefs.officialSeen(context, scope)
        val keep = seen.intersect(weather.official.map { it.id }.toSet()).toMutableSet()
        if (enabled) {
            weather.official.take(MAX_OFFICIAL_NOTIFICATIONS).forEachIndexed { i, w ->
                if (w.id in seen) return@forEachIndexed
                notify(
                    context, scope * 100 + 20 + i, CHANNEL_SEVERE,
                    texts.officialTitle(w.event, w.degree), "${city.name}: ${officialSummary(w, texts)}",
                    city = city, priority = NotificationCompat.PRIORITY_HIGH,
                )
                keep += w.id
            }
        }
        NotifStatePrefs.setOfficialSeen(context, scope, keep)
    }

    /** The warning's first sentence (at most ~140 characters) and when it applies. */
    internal fun officialSummary(w: WeatherApi.OfficialWarning, texts: Texts): String {
        val text = w.text.replace(Regex("""\s+"""), " ").trim()
        val firstSentence = Regex("""^(.+?[.!?])(\s|$)""").find(text)?.groupValues?.get(1) ?: text
        val cut = if (firstSentence.length > 140) firstSentence.take(137).trimEnd() + "…" else firstSentence
        return "$cut (${texts.officialValidity(w.from, w.to)})"
    }

    /**
     * Brings the edge-triggered state (rain heads-up, severe warnings, which
     * official warnings were seen) in line with [weather] *without* sending
     * anything. Used when the user switches a widget to another place: what that
     * place's forecast says is already on the widget in front of them, and the
     * next scheduled refresh must not announce it a second time as if it had
     * just appeared.
     */
    fun primeState(context: Context, appWidgetId: Int, city: WidgetCity, weather: WeatherApi.WeatherData) {
        val scope = scopeOf(appWidgetId, city)
        NotifStatePrefs.setWasRaining(context, scope, rainImminent(weather, System.currentTimeMillis()))
        val found = SevereWeather.find(weather).associateBy { it.kind }
        for (kind in SevereWeather.Kind.entries) {
            NotifStatePrefs.setSevereActive(context, scope, kind, found[kind] != null)
        }
        NotifStatePrefs.setOfficialSeen(context, scope, weather.official.map { it.id }.toSet())
    }

    private fun localHour(weather: WeatherApi.WeatherData): Int =
        weather.hourly.firstOrNull()?.time?.substringAfter('T', "")?.take(2)?.toIntOrNull()
            ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    /** UV alert (brief off): today's peak at or over the threshold, once a day,
     * and only while there's a sun to protect yourself from. */
    private fun checkUv(
        context: Context,
        scope: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        tone: Tone,
        texts: Texts,
    ) {
        if (!CapacitorStorage.uvEnabled(context)) return
        val uv = weather.daily.firstOrNull()?.uvIndexMax ?: return
        if (uv.isNaN() || uv < CapacitorStorage.uvThreshold(context)) return
        if (localHour(weather) !in UV_ALERT_HOURS) return
        val today = todayIso()
        if (NotifStatePrefs.lastUvNotifiedDate(context, scope) == today) return
        NotifStatePrefs.setUvNotifiedDate(context, scope, today)

        val index = uv.roundToInt()
        val text = if (tone == Tone.RUDE) RudeNotifications.uv(city.name, index, lang = texts.lang) else texts.uvAlert(city.name, index)
        notify(context, scope * 100 + 14, CHANNEL_UV, titleFor(city), text, city = city)
    }

    /** Gust alert (brief off): today's strongest gust at or over the threshold, once a day. */
    private fun checkGust(
        context: Context,
        scope: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        tone: Tone,
        texts: Texts,
    ) {
        if (!CapacitorStorage.gustEnabled(context)) return
        val gust = weather.daily.firstOrNull()?.windGustMaxKmh ?: return
        if (gust.isNaN() || gust < CapacitorStorage.gustThreshold(context)) return
        val today = todayIso()
        if (NotifStatePrefs.lastGustNotifiedDate(context, scope) == today) return
        NotifStatePrefs.setGustNotifiedDate(context, scope, today)

        val kmh = gust.roundToInt()
        val text = if (tone == Tone.RUDE) RudeNotifications.gust(city.name, kmh, lang = texts.lang) else texts.gustAlert(city.name, kmh)
        notify(context, scope * 100 + 15, CHANNEL_GUST, titleFor(city), text, city = city)
    }

    // -------------------------------------------------------------------------
    // Separate threshold alerts — only when the morning brief is switched off.
    // Unchanged in behaviour from before the brief existed.
    // -------------------------------------------------------------------------

    private fun checkHighLow(
        context: Context,
        scope: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        tone: Tone,
        texts: Texts,
    ) {
        val temp = weather.currentTemperature
        if (temp.isNaN()) return
        val today = todayIso()
        val t = temp.roundToInt()

        if (CapacitorStorage.highEnabled(context)) {
            val threshold = CapacitorStorage.highThreshold(context)
            if (temp >= threshold && NotifStatePrefs.lastHighNotifiedDate(context, scope) != today) {
                NotifStatePrefs.setHighNotifiedDate(context, scope, today)
                val text = if (tone == Tone.RUDE) RudeNotifications.highTemp(city.name, t, lang = texts.lang) else texts.highTemp(city.name, t)
                notify(context, scope * 100 + 2, CHANNEL_TEMP_EXTREME, titleFor(city), text, city = city)
            }
        }

        if (CapacitorStorage.lowEnabled(context)) {
            val threshold = CapacitorStorage.lowThreshold(context)
            if (temp <= threshold && NotifStatePrefs.lastLowNotifiedDate(context, scope) != today) {
                NotifStatePrefs.setLowNotifiedDate(context, scope, today)
                val text = if (tone == Tone.RUDE) RudeNotifications.lowTemp(city.name, t, lang = texts.lang) else texts.lowTemp(city.name, t)
                notify(context, scope * 100 + 3, CHANNEL_TEMP_EXTREME, titleFor(city), text, city = city)
            }
        }
    }

    private fun checkSwing(
        context: Context,
        scope: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        tone: Tone,
        texts: Texts,
    ) {
        if (!CapacitorStorage.swingEnabled(context)) return
        val today = weather.daily.getOrNull(0) ?: return
        val tomorrow = weather.daily.getOrNull(1) ?: return

        val delta = tomorrow.tempMax - today.tempMax
        val threshold = CapacitorStorage.swingThreshold(context)
        if (abs(delta) < threshold) return

        val isoDate = todayIso()
        if (NotifStatePrefs.lastSwingNotifiedDate(context, scope) == isoDate) return
        NotifStatePrefs.setSwingNotifiedDate(context, scope, isoDate)

        val todayMax = today.tempMax.roundToInt()
        val tomorrowMax = tomorrow.tempMax.roundToInt()
        val text = if (tone == Tone.RUDE) {
            RudeNotifications.swing(city.name, todayMax, tomorrowMax, warming = delta > 0, lang = texts.lang)
        } else {
            texts.swing(city.name, todayMax, tomorrowMax, warming = delta > 0)
        }
        notify(context, scope * 100 + 4, CHANNEL_TEMP_SWING, titleFor(city), text, city = city)
    }

    /** Once-per-day-per-widget alert when the current US AQI reading meets or
     * exceeds the configured threshold. Skips entirely when this refresh's AQI
     * is unknown ([WeatherApi.WeatherData.usAqi] < 0, e.g. a failed AQ fetch
     * this cycle) — never notifies on missing data. */
    private fun checkAqi(
        context: Context,
        scope: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        tone: Tone,
        texts: Texts,
    ) {
        if (!CapacitorStorage.aqiEnabled(context)) return
        val aqi = weather.usAqi
        if (aqi < 0) return

        val threshold = CapacitorStorage.aqiThreshold(context)
        if (aqi < threshold) return

        val today = todayIso()
        if (NotifStatePrefs.lastAqiNotifiedDate(context, scope) == today) return
        NotifStatePrefs.setAqiNotifiedDate(context, scope, today)

        val label = texts.aqiLabel(aqi)
        val text = if (tone == Tone.RUDE) {
            RudeNotifications.aqi(city.name, aqi, label, lang = texts.lang)
        } else {
            texts.aqiAlert(city.name, aqi, label)
        }
        notify(context, scope * 100 + 5, CHANNEL_AQI, titleFor(city), text, city = city)
    }
}
