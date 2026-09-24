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
 * ## Two kinds of notification now
 *
 * - **Rain soon** — the one thing worth interrupting for, sent as soon as rain
 *   or a storm is due within about an hour (see [checkRainSoon]). It used to
 *   fire only once it had *already started* raining, which is when you no
 *   longer need telling.
 * - **Morning brief** — one summary of the day ([MorningBrief]), replacing the
 *   separate high / low / swing / air-quality alerts, which used to arrive one
 *   by one whenever each threshold happened to trip.
 *
 * With the brief switched off in settings, the separate alerts come back
 * exactly as before — nothing that was configurable has stopped being so.
 *
 * All settings are read live from the shared "CapacitorStorage" prefs file
 * (see [CapacitorStorage]); dedupe state lives in [NotifStatePrefs]. Wording
 * follows the app-wide [Tone].
 */
object WeatherNotifier {
    private const val CHANNEL_RAIN = "rain_alerts"
    private const val CHANNEL_BRIEF = "morning_brief"
    private const val CHANNEL_TEMP_EXTREME = "temp_extreme_alerts"
    private const val CHANNEL_TEMP_SWING = "temp_swing_alerts"
    private const val CHANNEL_AQI = "aqi_alerts"

    /** How close a rain window has to be to warn about it: the current hour
     * or the next one, i.e. roughly a 0-2 hour heads-up. */
    private const val RAIN_SOON_HOURS = 1

    private fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RAIN, "Rain alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Rain or thunderstorms about to start in one of your widget cities"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BRIEF, "Morning brief", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One summary of the day per widget city, in the morning"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TEMP_EXTREME, "Temperature extremes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "High or low temperature thresholds crossed (when the morning brief is off)"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TEMP_SWING, "Temperature swings", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Big day-to-day temperature swings (when the morning brief is off)"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_AQI, "Air quality alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Poor air quality thresholds crossed (when the morning brief is off)"
            },
        )
    }

    private fun hasPermission(context: Context): Boolean {
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

    /** Entry point — called once per widget instance right after a fresh weather fetch. */
    fun evaluate(context: Context, appWidgetId: Int, city: WidgetCity, weather: WeatherApi.WeatherData) {
        if (!hasPermission(context)) return
        ensureChannels(context)
        val tone = CapacitorStorage.tone(context)
        checkRainSoon(context, appWidgetId, city, weather, tone)
        if (CapacitorStorage.briefEnabled(context)) {
            checkBrief(context, appWidgetId, city, weather, tone)
        } else {
            checkHighLow(context, appWidgetId, city, weather, tone)
            checkSwing(context, appWidgetId, city, weather, tone)
            checkAqi(context, appWidgetId, city, weather, tone)
        }
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
    ) {
        val window = RainWindow.find(weather.hourly, weather.currentWeatherCode)
            ?.takeIf { it.startsInHours <= RAIN_SOON_HOURS }
        val imminent = window != null
        val was = NotifStatePrefs.wasRaining(context, appWidgetId)
        NotifStatePrefs.setWasRaining(context, appWidgetId, imminent)

        if (!CapacitorStorage.rainEnabled(context) || window == null || was) return

        val isThunder = window.kind == RainWindow.Kind.THUNDER
        val text = "${city.name}: ${RainWindow.long(window)}"
        val expanded = if (tone == Tone.RUDE) "$text\n\n${RudeNotifications.rainTail(isThunder)}" else null
        notify(
            context,
            notificationId = appWidgetId * 100 + 1,
            channelId = CHANNEL_RAIN,
            title = "Homebrew Weather — ${city.name}",
            text = text,
            expanded = expanded,
            city = city,
        )
    }

    private fun checkBrief(
        context: Context,
        appWidgetId: Int,
        city: WidgetCity,
        weather: WeatherApi.WeatherData,
        tone: Tone,
    ) {
        val today = todayIso()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val refreshMinutes = CapacitorStorage.refreshIntervalMinutes(context)
        val due = MorningBrief.isDue(
            hourNow = hour,
            briefHour = CapacitorStorage.briefHour(context),
            alreadySentToday = NotifStatePrefs.lastBriefDate(context, appWidgetId) == today,
            refreshMinutes = refreshMinutes,
        )
        if (!due) return

        val seed = (System.currentTimeMillis() / (60 * 60 * 1000L)).toInt()
        val tail = when (tone) {
            Tone.RUDE -> RudeNotifications.briefTail(seed)
            Tone.SIGMA -> SigmaJokes.pick(Wmo.wmoToKind(weather.daily.firstOrNull()?.weatherCode ?: 0), false, seed)
            Tone.CLEAN -> null
        }
        val brief = MorningBrief.compose(
            cityName = city.name,
            weather = weather,
            thresholds = MorningBrief.Thresholds(
                highEnabled = CapacitorStorage.highEnabled(context),
                highThreshold = CapacitorStorage.highThreshold(context),
                lowEnabled = CapacitorStorage.lowEnabled(context),
                lowThreshold = CapacitorStorage.lowThreshold(context),
                swingEnabled = CapacitorStorage.swingEnabled(context),
                swingThreshold = CapacitorStorage.swingThreshold(context),
                aqiEnabled = CapacitorStorage.aqiEnabled(context),
                aqiThreshold = CapacitorStorage.aqiThreshold(context),
            ),
            aqiLabel = { WeatherWidgetProvider.aqiLabelAndColor(it).first },
            tail = tail,
        ) ?: return

        NotifStatePrefs.setBriefDate(context, appWidgetId, today)
        notify(
            context,
            notificationId = appWidgetId * 100 + 6,
            channelId = CHANNEL_BRIEF,
            title = brief.title,
            text = brief.summary,
            expanded = brief.full,
            city = city,
        )
    }

    // -------------------------------------------------------------------------
    // Separate threshold alerts — only when the morning brief is switched off.
    // Unchanged in behaviour from before the brief existed.
    // -------------------------------------------------------------------------

    private fun checkHighLow(context: Context, appWidgetId: Int, city: WidgetCity, weather: WeatherApi.WeatherData, tone: Tone) {
        val temp = weather.currentTemperature
        if (temp.isNaN()) return
        val today = todayIso()

        if (CapacitorStorage.highEnabled(context)) {
            val threshold = CapacitorStorage.highThreshold(context)
            if (temp >= threshold && NotifStatePrefs.lastHighNotifiedDate(context, appWidgetId) != today) {
                NotifStatePrefs.setHighNotifiedDate(context, appWidgetId, today)
                notify(
                    context,
                    notificationId = appWidgetId * 100 + 2,
                    channelId = CHANNEL_TEMP_EXTREME,
                    title = "Homebrew Weather — ${city.name}",
                    text = if (tone == Tone.RUDE) {
                        RudeNotifications.highTemp(city.name, temp.roundToInt())
                    } else {
                        PlainNotifications.highTemp(city.name, temp.roundToInt())
                    },
                    city = city,
                )
            }
        }

        if (CapacitorStorage.lowEnabled(context)) {
            val threshold = CapacitorStorage.lowThreshold(context)
            if (temp <= threshold && NotifStatePrefs.lastLowNotifiedDate(context, appWidgetId) != today) {
                NotifStatePrefs.setLowNotifiedDate(context, appWidgetId, today)
                notify(
                    context,
                    notificationId = appWidgetId * 100 + 3,
                    channelId = CHANNEL_TEMP_EXTREME,
                    title = "Homebrew Weather — ${city.name}",
                    text = if (tone == Tone.RUDE) {
                        RudeNotifications.lowTemp(city.name, temp.roundToInt())
                    } else {
                        PlainNotifications.lowTemp(city.name, temp.roundToInt())
                    },
                    city = city,
                )
            }
        }
    }

    private fun checkSwing(context: Context, appWidgetId: Int, city: WidgetCity, weather: WeatherApi.WeatherData, tone: Tone) {
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
        notify(
            context,
            notificationId = appWidgetId * 100 + 4,
            channelId = CHANNEL_TEMP_SWING,
            title = "Homebrew Weather — ${city.name}",
            text = if (tone == Tone.RUDE) {
                RudeNotifications.swing(city.name, todayMax, tomorrowMax, warming = delta > 0)
            } else {
                PlainNotifications.swing(city.name, todayMax, tomorrowMax, warming = delta > 0)
            },
            city = city,
        )
    }

    /** Once-per-day-per-widget alert when the current US AQI reading meets or
     * exceeds the configured threshold. Skips entirely when this refresh's AQI
     * is unknown ([WeatherApi.WeatherData.usAqi] < 0, e.g. a failed AQ fetch
     * this cycle) — never notifies on missing data. Category label reuses
     * [WeatherWidgetProvider.aqiLabelAndColor]'s exact breakpoints rather than
     * redefining them here. */
    private fun checkAqi(context: Context, appWidgetId: Int, city: WidgetCity, weather: WeatherApi.WeatherData, tone: Tone) {
        if (!CapacitorStorage.aqiEnabled(context)) return
        val aqi = weather.usAqi
        if (aqi < 0) return

        val threshold = CapacitorStorage.aqiThreshold(context)
        if (aqi < threshold) return

        val today = todayIso()
        if (NotifStatePrefs.lastAqiNotifiedDate(context, appWidgetId) == today) return
        NotifStatePrefs.setAqiNotifiedDate(context, appWidgetId, today)

        val (label, _) = WeatherWidgetProvider.aqiLabelAndColor(aqi)
        notify(
            context,
            notificationId = appWidgetId * 100 + 5,
            channelId = CHANNEL_AQI,
            title = "Homebrew Weather — ${city.name}",
            text = if (tone == Tone.RUDE) {
                RudeNotifications.aqi(city.name, aqi, label)
            } else {
                PlainNotifications.aqi(city.name, aqi, label)
            },
            city = city,
        )
    }
}
