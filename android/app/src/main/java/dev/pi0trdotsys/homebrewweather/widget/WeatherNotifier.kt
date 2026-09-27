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

    /** How close a rain window has to be to warn about it: the current hour
     * or the next one, i.e. roughly a 0-2 hour heads-up. */
    private const val RAIN_SOON_HOURS = 1

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
        ).forEach { (id, label) ->
            nm.createNotificationChannel(
                NotificationChannel(id, label.first, NotificationManager.IMPORTANCE_DEFAULT).apply {
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
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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

    /** Entry point — called once per widget instance right after a fresh weather fetch. */
    fun evaluate(context: Context, appWidgetId: Int, city: WidgetCity, weather: WeatherApi.WeatherData) {
        if (!hasPermission(context)) return
        val lang = CapacitorStorage.lang(context)
        val texts = lang.texts
        ensureChannels(context, texts)
        val tone = CapacitorStorage.tone(context)
        checkRainSoon(context, appWidgetId, city, weather, tone, texts)
        checkRainStop(context, appWidgetId, city, weather, texts)
        if (CapacitorStorage.briefEnabled(context)) {
            checkBrief(context, appWidgetId, city, weather, tone, texts)
        } else {
            checkHighLow(context, appWidgetId, city, weather, tone, texts)
            checkSwing(context, appWidgetId, city, weather, tone, texts)
            checkAqi(context, appWidgetId, city, weather, tone, texts)
        }
        if (CapacitorStorage.eveningEnabled(context)) checkEvening(context, appWidgetId, city, weather, texts)
    }

    /**
     * Rain or a storm due within [RAIN_SOON_HOURS] — or already falling.
     *
     * Edge-triggered on "not imminent" -> "imminent", tracked in the same
     * per-widget flag the old "it started raining" alert used. That's what
     * makes one rain spell produce one notification: a window that is
     * *ongoing* gets a new start time every hour it keeps raining, so keying
     * on the window itself would re-alert hourly for as long as it rains.
     */
    private fun checkRainSoon(
        context: Context,
        appWidgetId: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        tone: Tone,
        texts: Texts,
    ) {
        val window = RainWindow.find(weather.hourly, weather.currentWeatherCode)
            ?.takeIf { it.startsInHours <= RAIN_SOON_HOURS }
        val imminent = window != null
        val was = NotifStatePrefs.wasRaining(context, appWidgetId)
        NotifStatePrefs.setWasRaining(context, appWidgetId, imminent)

        if (!CapacitorStorage.rainEnabled(context) || window == null || was) return
        sendRainSoon(context, appWidgetId, city, window, tone, texts, prefix = "")
    }

    private fun sendRainSoon(
        context: Context,
        appWidgetId: Int,
        city: WidgetCity,
        window: RainWindow.Window,
        tone: Tone,
        texts: Texts,
        prefix: String,
    ) {
        val isThunder = window.kind == RainWindow.Kind.THUNDER
        val text = "$prefix${city.name}: ${RainWindow.long(window, texts)}"
        val expanded = if (tone == Tone.RUDE) "$text\n\n${RudeNotifications.rainTail(isThunder, lang = texts.lang)}" else null
        notify(context, appWidgetId * 100 + 1, CHANNEL_RAIN, titleFor(city), text, expanded, city)
    }

    /**
     * It's been raining for a while and is about to stop ([RainStop]).
     * Once per spell: the spell's start is remembered while it rains, and the
     * "sent" flag clears when it stops.
     */
    private fun checkRainStop(
        context: Context,
        appWidgetId: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        texts: Texts,
    ) {
        val now = System.currentTimeMillis()
        val window = RainWindow.find(weather.hourly, weather.currentWeatherCode)
        val raining = window?.ongoing == true
        if (!raining) {
            NotifStatePrefs.setRainingSince(context, appWidgetId, 0L)
            NotifStatePrefs.setStopSent(context, appWidgetId, false)
            return
        }
        var since = NotifStatePrefs.rainingSince(context, appWidgetId)
        if (since == 0L) {
            since = now
            NotifStatePrefs.setRainingSince(context, appWidgetId, now)
        }
        val hours = ((now - since) / 3_600_000L).toInt()
        if (!CapacitorStorage.rainEnabled(context) || NotifStatePrefs.stopSent(context, appWidgetId)) return
        if (!RainStop.isDue(window, hours)) return
        NotifStatePrefs.setStopSent(context, appWidgetId, true)
        val end = window?.end ?: return
        notify(
            context,
            appWidgetId * 100 + 7,
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
        appWidgetId: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        tone: Tone,
        texts: Texts,
    ) {
        val today = todayIso()
        val due = MorningBrief.isDue(
            hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            briefHour = CapacitorStorage.briefHour(context),
            alreadySentToday = NotifStatePrefs.lastBriefDate(context, appWidgetId) == today,
            refreshMinutes = CapacitorStorage.refreshIntervalMinutes(context),
        )
        if (!due) return
        val brief = composeBrief(city, weather, tone, texts, context) ?: return
        NotifStatePrefs.setBriefDate(context, appWidgetId, today)
        notify(context, appWidgetId * 100 + 6, CHANNEL_BRIEF, brief.title, brief.summary, brief.full, city)
    }

    private fun checkEvening(
        context: Context,
        appWidgetId: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        texts: Texts,
    ) {
        val today = todayIso()
        val due = EveningPreview.isDue(
            hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            eveningHour = CapacitorStorage.eveningHour(context),
            alreadySentToday = NotifStatePrefs.lastEveningDate(context, appWidgetId) == today,
            refreshMinutes = CapacitorStorage.refreshIntervalMinutes(context),
        )
        if (!due) return
        val preview = EveningPreview.compose(city.name, weather, texts) ?: return
        NotifStatePrefs.setEveningDate(context, appWidgetId, today)
        notify(context, appWidgetId * 100 + 8, CHANNEL_EVENING, preview.title, preview.summary, city = city)
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
        return sent
    }

    /** Notification id base for test sends — no real widget id is this large. */
    private const val TEST_ID_BASE = 9_999_990

    // -------------------------------------------------------------------------
    // Separate threshold alerts — only when the morning brief is switched off.
    // Unchanged in behaviour from before the brief existed.
    // -------------------------------------------------------------------------

    private fun checkHighLow(
        context: Context,
        appWidgetId: Int,
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
            if (temp >= threshold && NotifStatePrefs.lastHighNotifiedDate(context, appWidgetId) != today) {
                NotifStatePrefs.setHighNotifiedDate(context, appWidgetId, today)
                val text = if (tone == Tone.RUDE) RudeNotifications.highTemp(city.name, t, lang = texts.lang) else texts.highTemp(city.name, t)
                notify(context, appWidgetId * 100 + 2, CHANNEL_TEMP_EXTREME, titleFor(city), text, city = city)
            }
        }

        if (CapacitorStorage.lowEnabled(context)) {
            val threshold = CapacitorStorage.lowThreshold(context)
            if (temp <= threshold && NotifStatePrefs.lastLowNotifiedDate(context, appWidgetId) != today) {
                NotifStatePrefs.setLowNotifiedDate(context, appWidgetId, today)
                val text = if (tone == Tone.RUDE) RudeNotifications.lowTemp(city.name, t, lang = texts.lang) else texts.lowTemp(city.name, t)
                notify(context, appWidgetId * 100 + 3, CHANNEL_TEMP_EXTREME, titleFor(city), text, city = city)
            }
        }
    }

    private fun checkSwing(
        context: Context,
        appWidgetId: Int,
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
        if (NotifStatePrefs.lastSwingNotifiedDate(context, appWidgetId) == isoDate) return
        NotifStatePrefs.setSwingNotifiedDate(context, appWidgetId, isoDate)

        val todayMax = today.tempMax.roundToInt()
        val tomorrowMax = tomorrow.tempMax.roundToInt()
        val text = if (tone == Tone.RUDE) {
            RudeNotifications.swing(city.name, todayMax, tomorrowMax, warming = delta > 0, lang = texts.lang)
        } else {
            texts.swing(city.name, todayMax, tomorrowMax, warming = delta > 0)
        }
        notify(context, appWidgetId * 100 + 4, CHANNEL_TEMP_SWING, titleFor(city), text, city = city)
    }

    /** Once-per-day-per-widget alert when the current US AQI reading meets or
     * exceeds the configured threshold. Skips entirely when this refresh's AQI
     * is unknown ([WeatherApi.WeatherData.usAqi] < 0, e.g. a failed AQ fetch
     * this cycle) — never notifies on missing data. */
    private fun checkAqi(
        context: Context,
        appWidgetId: Int,
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
        if (NotifStatePrefs.lastAqiNotifiedDate(context, appWidgetId) == today) return
        NotifStatePrefs.setAqiNotifiedDate(context, appWidgetId, today)

        val label = texts.aqiLabel(aqi)
        val text = if (tone == Tone.RUDE) {
            RudeNotifications.aqi(city.name, aqi, label, lang = texts.lang)
        } else {
            texts.aqiAlert(city.name, aqi, label)
        }
        notify(context, appWidgetId * 100 + 5, CHANNEL_AQI, titleFor(city), text, city = city)
    }
}
