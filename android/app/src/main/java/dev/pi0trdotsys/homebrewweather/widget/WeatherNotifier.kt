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
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Evaluates the four notification rules (rain incoming, high temp, low temp,
 * big day-to-day swing) as a side effect of a *fresh* per-widget weather
 * fetch — called from [WeatherWidgetProvider.buildRemoteViews] right after a
 * successful (non-cached) [WeatherApi.fetchWeather] call, so it piggybacks on
 * the existing WeatherWorker periodic refresh (and manual/configure
 * refreshes) instead of running its own polling loop.
 *
 * All settings are read live from the shared "CapacitorStorage" prefs file
 * (see [CapacitorStorage]); dedupe state lives in [NotifStatePrefs].
 */
object WeatherNotifier {
    private const val CHANNEL_RAIN = "rain_alerts"
    private const val CHANNEL_TEMP_EXTREME = "temp_extreme_alerts"
    private const val CHANNEL_TEMP_SWING = "temp_swing_alerts"
    private const val CHANNEL_AQI = "aqi_alerts"

    private fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RAIN, "Rain alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Rain or thunderstorms starting in one of your widget cities"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TEMP_EXTREME, "Temperature extremes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "High or low temperature thresholds crossed (once a day per city)"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TEMP_SWING, "Temperature swings", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Big day-to-day temperature swings (once a day per city)"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_AQI, "Air quality alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Poor air quality thresholds crossed (once a day per city)"
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

    private fun notify(context: Context, notificationId: Int, channelId: String, title: String, text: String) {
        try {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
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
        checkRain(context, appWidgetId, city, weather)
        checkHighLow(context, appWidgetId, city, weather)
        checkSwing(context, appWidgetId, city, weather)
        checkAqi(context, appWidgetId, city, weather)
    }

    private fun checkRain(context: Context, appWidgetId: Int, city: WidgetCity, weather: WeatherApi.WeatherData) {
        val kind = Wmo.wmoToKind(weather.currentWeatherCode)
        val isRainingNow = kind == "rain" || kind == "thunder"
        val wasRaining = NotifStatePrefs.wasRaining(context, appWidgetId)
        NotifStatePrefs.setWasRaining(context, appWidgetId, isRainingNow)

        if (!CapacitorStorage.rainEnabled(context)) return
        // Edge-triggered: only notify on the transition into rain, not on every
        // refresh while it keeps raining.
        if (isRainingNow && !wasRaining) {
            notify(
                context,
                notificationId = appWidgetId * 100 + 1,
                channelId = CHANNEL_RAIN,
                title = "Homebrew Weather — ${city.name}",
                text = RudeNotifications.rain(city.name, isThunder = kind == "thunder"),
            )
        }
    }

    private fun checkHighLow(context: Context, appWidgetId: Int, city: WidgetCity, weather: WeatherApi.WeatherData) {
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
                    text = RudeNotifications.highTemp(city.name, temp.roundToInt()),
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
                    text = RudeNotifications.lowTemp(city.name, temp.roundToInt()),
                )
            }
        }
    }

    private fun checkSwing(context: Context, appWidgetId: Int, city: WidgetCity, weather: WeatherApi.WeatherData) {
        if (!CapacitorStorage.swingEnabled(context)) return
        val today = weather.daily.getOrNull(0) ?: return
        val tomorrow = weather.daily.getOrNull(1) ?: return

        val delta = tomorrow.tempMax - today.tempMax
        val threshold = CapacitorStorage.swingThreshold(context)
        if (abs(delta) < threshold) return

        val isoDate = todayIso()
        if (NotifStatePrefs.lastSwingNotifiedDate(context, appWidgetId) == isoDate) return
        NotifStatePrefs.setSwingNotifiedDate(context, appWidgetId, isoDate)

        notify(
            context,
            notificationId = appWidgetId * 100 + 4,
            channelId = CHANNEL_TEMP_SWING,
            title = "Homebrew Weather — ${city.name}",
            text = RudeNotifications.swing(
                city.name,
                todayMax = today.tempMax.roundToInt(),
                tomorrowMax = tomorrow.tempMax.roundToInt(),
                warming = delta > 0,
            ),
        )
    }

    /** Once-per-day-per-widget alert when the current US AQI reading meets or
     * exceeds the configured threshold. Skips entirely when this refresh's AQI
     * is unknown ([WeatherApi.WeatherData.usAqi] < 0, e.g. a failed AQ fetch
     * this cycle) — never notifies on missing data, same convention as
     * [checkHighLow]'s NaN-temperature guard. Category label reuses
     * [WeatherWidgetProvider.aqiLabelAndColor]'s exact breakpoints rather than
     * redefining them here. */
    private fun checkAqi(context: Context, appWidgetId: Int, city: WidgetCity, weather: WeatherApi.WeatherData) {
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
            text = RudeNotifications.aqi(city.name, aqi, label),
        )
    }
}
