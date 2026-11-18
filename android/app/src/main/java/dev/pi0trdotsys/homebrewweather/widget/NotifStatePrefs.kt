package dev.pi0trdotsys.homebrewweather.widget

import android.content.Context

/**
 * Dedicated SharedPreferences file ("notif_state_prefs") holding just enough
 * per-widget-instance state to de-duplicate notifications:
 *  - whether it was raining/storming as of the *previous* check (so rain
 *    notifications are edge-triggered, not repeated every refresh), and
 *  - the last calendar date (yyyy-MM-dd) each once-per-day alert type fired,
 *    per widget instance.
 *
 * Keyed by appWidgetId, mirroring the convention in [WidgetPrefs] (city_$id,
 * weather_cache_$id) — each widget instance already has its own city, so the
 * appWidgetId is a sufficient and simpler key than deriving one from the city.
 */
object NotifStatePrefs {
    private const val PREFS_NAME = "notif_state_prefs"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun wasRaining(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean("rain_$appWidgetId", false)

    fun setWasRaining(context: Context, appWidgetId: Int, value: Boolean) {
        prefs(context).edit().putBoolean("rain_$appWidgetId", value).apply()
    }

    fun lastHighNotifiedDate(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("high_date_$appWidgetId", null)

    fun setHighNotifiedDate(context: Context, appWidgetId: Int, isoDate: String) {
        prefs(context).edit().putString("high_date_$appWidgetId", isoDate).apply()
    }

    fun lastLowNotifiedDate(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("low_date_$appWidgetId", null)

    fun setLowNotifiedDate(context: Context, appWidgetId: Int, isoDate: String) {
        prefs(context).edit().putString("low_date_$appWidgetId", isoDate).apply()
    }

    fun lastSwingNotifiedDate(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("swing_date_$appWidgetId", null)

    fun setSwingNotifiedDate(context: Context, appWidgetId: Int, isoDate: String) {
        prefs(context).edit().putString("swing_date_$appWidgetId", isoDate).apply()
    }

    /** Date the morning brief last went out for this widget's city. */
    fun lastBriefDate(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("brief_date_$appWidgetId", null)

    fun setBriefDate(context: Context, appWidgetId: Int, isoDate: String) {
        prefs(context).edit().putString("brief_date_$appWidgetId", isoDate).apply()
    }

    /** Date the evening preview of tomorrow last went out for this widget. */
    fun lastEveningDate(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("evening_date_$appWidgetId", null)

    fun setEveningDate(context: Context, appWidgetId: Int, isoDate: String) {
        prefs(context).edit().putString("evening_date_$appWidgetId", isoDate).apply()
    }

    /** When the current rain spell was first seen falling (epoch ms), or 0
     * when it isn't raining — how long it has rained decides whether its end
     * is worth a notification (see RainStop). */
    fun rainingSince(context: Context, appWidgetId: Int): Long =
        prefs(context).getLong("raining_since_$appWidgetId", 0L)

    fun setRainingSince(context: Context, appWidgetId: Int, epochMs: Long) {
        prefs(context).edit().putLong("raining_since_$appWidgetId", epochMs).apply()
    }

    /** Whether this spell's "stopping soon" notification already went out. */
    fun stopSent(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean("stop_sent_$appWidgetId", false)

    fun setStopSent(context: Context, appWidgetId: Int, value: Boolean) {
        prefs(context).edit().putBoolean("stop_sent_$appWidgetId", value).apply()
    }

    fun lastAqiNotifiedDate(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("aqi_date_$appWidgetId", null)

    fun setAqiNotifiedDate(context: Context, appWidgetId: Int, isoDate: String) {
        prefs(context).edit().putString("aqi_date_$appWidgetId", isoDate).apply()
    }

    /** Whether a severe-weather warning of this kind is currently in force for
     * the widget — edge-triggered like rain, so one storm is one notification
     * rather than one per refresh while it lasts. */
    fun severeActive(context: Context, appWidgetId: Int, kind: SevereWeather.Kind): Boolean =
        prefs(context).getBoolean("severe_${kind.name.lowercase()}_$appWidgetId", false)

    fun setSevereActive(context: Context, appWidgetId: Int, kind: SevereWeather.Kind, value: Boolean) {
        prefs(context).edit().putBoolean("severe_${kind.name.lowercase()}_$appWidgetId", value).apply()
    }

    fun lastHeatWarningDate(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("heat_warn_date_$appWidgetId", null)

    fun setHeatWarningDate(context: Context, appWidgetId: Int, isoDate: String) {
        prefs(context).edit().putString("heat_warn_date_$appWidgetId", isoDate).apply()
    }

    fun lastUvNotifiedDate(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("uv_date_$appWidgetId", null)

    fun setUvNotifiedDate(context: Context, appWidgetId: Int, isoDate: String) {
        prefs(context).edit().putString("uv_date_$appWidgetId", isoDate).apply()
    }

    fun lastGustNotifiedDate(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("gust_date_$appWidgetId", null)

    fun setGustNotifiedDate(context: Context, appWidgetId: Int, isoDate: String) {
        prefs(context).edit().putString("gust_date_$appWidgetId", isoDate).apply()
    }

    /** Ids of official warnings already announced for this place (see WeatherNotifier.checkOfficial). */
    fun officialSeen(context: Context, appWidgetId: Int): Set<String> =
        prefs(context).getStringSet("official_seen_$appWidgetId", emptySet())?.toSet() ?: emptySet()

    fun setOfficialSeen(context: Context, appWidgetId: Int, ids: Set<String>) {
        prefs(context).edit().putStringSet("official_seen_$appWidgetId", ids).apply()
    }
}
