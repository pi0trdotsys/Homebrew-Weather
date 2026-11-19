package dev.pi0trdotsys.homebrewweather.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Read-only export of the outdoor temperature the widget already has cached,
 * for the user's other app (Kropi).
 *
 * `content://<applicationId>.export/outdoor`, query() only; any other URI is null.
 * One row, columns [OutdoorTemperatureExport.COLUMNS]; no rows when there is no
 * usable cached forecast.
 *
 * Exported WITHOUT a permission, because the two apps are signed with different
 * keys and a signature permission cannot work. So anyone on the device can read
 * it, which is why it carries only numbers and never a place name, coordinates
 * or anything else that reveals location. Keep it that way.
 */
class OutdoorExportProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val context = context ?: return null
        if (uri.authority != "${context.packageName}.export" ||
            uri.pathSegments != listOf(OutdoorTemperatureExport.PATH)
        ) return null

        val now = System.currentTimeMillis()
        val candidates = WeatherWidgetProvider.allWidgetIds(context).toList().mapNotNull { id ->
            val place = WidgetPrefs.getCity(context, id) ?: return@mapNotNull null
            val weather = WidgetPrefs.getCachedWeather(context, id, place) ?: return@mapNotNull null
            OutdoorTemperatureExport.Candidate(weather, WidgetPrefs.getCachedWeatherUpdatedAt(context, id, place))
        }
        val cursor = MatrixCursor(OutdoorTemperatureExport.COLUMNS)
        OutdoorTemperatureExport.pick(candidates, now)?.let { cursor.addRow(it.toColumns()) }
        return cursor
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")
}
