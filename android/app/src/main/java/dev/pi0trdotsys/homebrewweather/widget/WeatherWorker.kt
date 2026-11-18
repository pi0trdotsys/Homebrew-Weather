package dev.pi0trdotsys.homebrewweather.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.Worker
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodic background refresh for every placed widget instance. Reads the
 * refresh interval from the shared "CapacitorStorage" prefs (key
 * "brew-wx:interval", set by the main web app's settings screen), clamped
 * to WorkManager's real minimum of 15 minutes.
 */
class WeatherWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = WeatherWidgetProvider.allWidgetIds(context)
        // Run synchronously here (Worker already runs off the main thread);
        // each widget's own fetch failure is caught internally so one bad
        // city never fails the whole batch.
        ids.forEach { id ->
            try {
                val rv = WeatherWidgetProvider.buildRemoteViews(context, id)
                appWidgetManager.updateAppWidget(id, rv)
            } catch (e: Exception) {
                // best-effort, continue with the next widget
            }
        }
        // ...then the places each widget has but isn't showing, for the alerts that
        // are worth interrupting for wherever they are.
        if (WeatherNotifier.hasPermission(context) &&
            (CapacitorStorage.rainEnabled(context) || CapacitorStorage.severeEnabled(context))
        ) {
            ids.forEach { id ->
                try {
                    checkOtherPlaces(context, id)
                } catch (e: Exception) {
                    // best-effort, continue with the next widget
                }
            }
        }
        return Result.success()
    }

    /**
     * Rain, storms and official warnings for the places a widget holds besides
     * the one it shows. Before this, "work" on the widget meant a downpour at
     * "home" never notified: alerts followed the displayed place only.
     *
     * One extra fetch per other place, off the widget's own render. Forecasts are
     * cached per place, so switching to one of them later shows fresh data. The
     * morning brief, temperature and air-quality alerts stay with the shown place
     * (see [WeatherNotifier.evaluate]'s `criticalOnly`).
     */
    private fun checkOtherPlaces(context: Context, appWidgetId: Int) {
        val state = WidgetPrefs.getPlaceState(context, appWidgetId)
        if (!state.canCycle) return
        state.list.forEachIndexed { index, place ->
            if (index == state.index) return@forEachIndexed
            val cached = WidgetPrefs.getCachedWeather(context, appWidgetId, place)
            val weather = try {
                WeatherRepository.fetch(context, place, cached)
            } catch (e: Exception) {
                return@forEachIndexed
            }
            WidgetPrefs.setCachedWeather(context, appWidgetId, weather, place)
            WeatherNotifier.evaluate(context, appWidgetId, place, weather, criticalOnly = true)
        }
    }

    companion object {
        private const val WORK_NAME = "weather_widget_periodic_refresh"

        fun enqueuePeriodic(context: Context) {
            val minutes = CapacitorStorage.refreshIntervalMinutes(context)
            val request = PeriodicWorkRequest.Builder(WeatherWorker::class.java, minutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        private const val RECHECK_NAME = "weather_rain_recheck"

        /**
         * One extra refresh in [minutes]: rain is near but not yet near enough to
         * announce, and the next scheduled refresh could be half an hour away —
         * which would turn "rain in 40 minutes" into "it's raining". Replaces
         * nothing already pending, and chains itself only while rain stays near.
         */
        fun scheduleRecheck(context: Context, minutes: Long) {
            val request = OneTimeWorkRequest.Builder(WeatherWorker::class.java)
                .setInitialDelay(minutes, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(RECHECK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
