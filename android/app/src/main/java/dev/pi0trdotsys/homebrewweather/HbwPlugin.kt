package dev.pi0trdotsys.homebrewweather

import android.Manifest
import android.appwidget.AppWidgetManager
import android.os.Build
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import dev.pi0trdotsys.homebrewweather.widget.CapacitorStorage
import dev.pi0trdotsys.homebrewweather.widget.DeviceGeocoder
import dev.pi0trdotsys.homebrewweather.widget.WeatherApi
import dev.pi0trdotsys.homebrewweather.widget.WeatherNotifier
import dev.pi0trdotsys.homebrewweather.widget.WeatherWidgetProvider
import dev.pi0trdotsys.homebrewweather.widget.WidgetCity
import dev.pi0trdotsys.homebrewweather.widget.WidgetPrefs
import java.util.concurrent.Executors

/**
 * The few things the web app needs from the native side, exposed as the
 * Capacitor plugin "Hbw" (see src/lib/native.ts).
 *
 * - [reverseGeocode]: a place name for GPS coordinates from the device's own
 *   geocoder. The web app used Open-Meteo's `/v1/reverse`, which answers 404
 *   for every coordinate, so a located position always showed as
 *   "unknown_location". The widget switched to the device geocoder long ago
 *   (DeviceGeocoder); now the app does too.
 * - [notificationStatus] / [requestNotifications]: notification permission,
 *   asked for when the user turns a notification on or sends a test — the
 *   moment it's obviously needed — rather than only when adding a widget.
 * - [sendTestNotifications]: one of each notification, right now, from real
 *   data (WeatherNotifier.sendTest).
 * - [refreshWidgets]: re-render every widget, so a language or tone change in
 *   the app shows on the home screen immediately rather than at the next
 *   scheduled refresh.
 */
@CapacitorPlugin(
    name = "Hbw",
    permissions = [
        Permission(strings = [Manifest.permission.POST_NOTIFICATIONS], alias = HbwPlugin.NOTIFICATIONS),
    ],
)
class HbwPlugin : Plugin() {

    private val executor = Executors.newSingleThreadExecutor()

    @PluginMethod
    fun reverseGeocode(call: PluginCall) {
        val lat = call.getDouble("lat")
        val lon = call.getDouble("lon")
        if (lat == null || lon == null) {
            call.reject("lat and lon are required")
            return
        }
        executor.execute {
            val name = DeviceGeocoder.cityName(context, lat, lon)
            call.resolve(JSObject().apply { put("name", name) })
        }
    }

    @PluginMethod
    fun notificationStatus(call: PluginCall) {
        call.resolve(statusObject())
    }

    @PluginMethod
    fun requestNotifications(call: PluginCall) {
        if (Build.VERSION.SDK_INT < 33 || getPermissionState(NOTIFICATIONS) == PermissionState.GRANTED) {
            call.resolve(statusObject())
            return
        }
        requestPermissionForAlias(NOTIFICATIONS, call, "notificationPermissionResult")
    }

    @PermissionCallback
    private fun notificationPermissionResult(call: PluginCall) {
        call.resolve(statusObject())
    }

    @PluginMethod
    fun sendTestNotifications(call: PluginCall) {
        if (!WeatherNotifier.hasPermission(context)) {
            call.resolve(statusObject().apply { put("sent", 0) })
            return
        }
        executor.execute {
            try {
                val city = testCity()
                if (city == null) {
                    call.reject("no city yet")
                    return@execute
                }
                val weather = WeatherApi.fetchWeather(city.lat, city.lon)
                val sent = WeatherNotifier.sendTest(context, city, weather)
                call.resolve(statusObject().apply { put("sent", sent) })
            } catch (e: Exception) {
                call.reject("could not fetch the forecast: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun refreshWidgets(call: PluginCall) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = WeatherWidgetProvider.allWidgetIds(context)
        ids.forEach { WeatherWidgetProvider.refreshWidget(context, manager, it) }
        call.resolve(JSObject().apply { put("count", ids.size) })
    }

    /** The app's own city, else the first widget's. */
    private fun testCity(): WidgetCity? =
        CapacitorStorage.lastAppCity(context)
            ?: WeatherWidgetProvider.allWidgetIds(context).asList().firstNotNullOfOrNull { WidgetPrefs.getCity(context, it) }

    private fun statusObject() = JSObject().apply {
        put("granted", WeatherNotifier.hasPermission(context))
    }

    companion object {
        const val NOTIFICATIONS = "notifications"
    }
}
