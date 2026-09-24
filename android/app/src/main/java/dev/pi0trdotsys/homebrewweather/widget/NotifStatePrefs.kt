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

    fun lastAqiNotifiedDate(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("aqi_date_$appWidgetId", null)

    fun setAqiNotifiedDate(context: Context, appWidgetId: Int, isoDate: String) {
        prefs(context).edit().putString("aqi_date_$appWidgetId", isoDate).apply()
    }
}
