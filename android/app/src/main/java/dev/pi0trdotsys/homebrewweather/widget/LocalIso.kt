package dev.pi0trdotsys.homebrewweather.widget

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Local ISO times ("2026-11-17T16:05") as whole minutes on a common timeline.
 *
 * Open-Meteo reports every time in the location's own clock, with no offset, so
 * two of them can be compared and subtracted directly once both are put on one
 * (arbitrary) timeline — here UTC, which keeps daylight-saving jumps out of it.
 * Shared by the sun events and the rain nowcast.
 */
internal object LocalIso {

    /** Minutes since the epoch for the first 16 characters ("yyyy-MM-ddTHH:mm") of [iso], or null. */
    fun minutes(iso: String): Long? = try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.parse(iso.take(16))!!.time / 60_000L
    } catch (e: Exception) {
        null
    }
}
