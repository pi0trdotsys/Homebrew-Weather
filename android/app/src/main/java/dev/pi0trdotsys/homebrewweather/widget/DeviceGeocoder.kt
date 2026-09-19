package dev.pi0trdotsys.homebrewweather.widget

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * On-device reverse geocoding (a real city name from lat/lon), NOT
 * [WeatherApi.reverseGeocode] (Open-Meteo's `/v1/reverse` endpoint) — that
 * endpoint now returns a hard HTTP 404 for *every* coordinate tested,
 * including known-good ones (confirmed directly with curl, not a
 * coordinate-specific gap in their database), so it can never resolve a
 * name anymore. This is why "use my location" got permanently stuck showing
 * the literal string "current location": [WeatherApi.reverseGeocode] always
 * failed, and that placeholder was never retried away.
 *
 * [Geocoder] is backed by an on-device geocoding service (present on
 * virtually every real device that ships with Google Play Services — which
 * is effectively all of them) and needs no network call of our own, so it
 * doesn't share Open-Meteo's failure mode at all.
 */
object DeviceGeocoder {

    /**
     * Blocking — call off the main thread. Returns a short, real place name
     * (locality, falling back to subAdminArea/adminArea/countryName), or
     * null if nothing could be resolved (no on-device geocoder backend, no
     * result for these coordinates, or the platform threw).
     */
    fun cityName(context: Context, lat: Double, lon: Double): String? {
        return try {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale.getDefault())
            val address: Address? = if (Build.VERSION.SDK_INT >= 33) {
                getFromLocationBlocking33(geocoder, lat, lon)
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            }
            address?.let { placeName(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun placeName(a: Address): String? =
        listOfNotNull(a.locality, a.subAdminArea, a.adminArea, a.countryName).firstOrNull()

    /**
     * API 33+ deprecated the synchronous [Geocoder.getFromLocation] overload
     * in favor of a listener-based one; every caller here already runs on a
     * background thread and wants a plain synchronous result, so block on a
     * short-timeout latch rather than threading a callback through this
     * object's simple `String?`-returning contract.
     */
    private fun getFromLocationBlocking33(geocoder: Geocoder, lat: Double, lon: Double): Address? {
        val latch = CountDownLatch(1)
        var result: Address? = null
        geocoder.getFromLocation(lat, lon, 1) { addresses ->
            result = addresses.firstOrNull()
            latch.countDown()
        }
        latch.await(8, TimeUnit.SECONDS)
        return result
    }

    /**
     * Honest last-resort label when no geocoder (on-device or Open-Meteo)
     * can resolve a name at all — a formatted coordinate at least tells the
     * user which real spot is being synced, unlike a static, meaningless
     * placeholder string.
     */
    fun coordinateLabel(lat: Double, lon: Double): String {
        val latHem = if (lat >= 0) "N" else "S"
        val lonHem = if (lon >= 0) "E" else "W"
        return String.format(Locale.US, "%.2f°%s %.2f°%s", Math.abs(lat), latHem, Math.abs(lon), lonHem)
    }
}
