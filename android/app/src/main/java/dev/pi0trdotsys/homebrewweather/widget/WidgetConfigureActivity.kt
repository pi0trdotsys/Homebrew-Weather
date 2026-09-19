package dev.pi0trdotsys.homebrewweather.widget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.pi0trdotsys.homebrewweather.R
import java.util.concurrent.Executors

/**
 * Widget "configure" screen: launched automatically by Android the moment a
 * user drags the widget onto their home screen (registered as
 * android:configure on the appwidget-provider), and also relaunchable later
 * from the widget's own "change city" button, passing appWidgetId directly.
 */
class WidgetConfigureActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var cityInput: EditText
    private lateinit var statusText: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var suggestionBtn: TextView

    private lateinit var themeButtons: Map<WidgetTheme, TextView>
    private lateinit var transparencySeekbar: SeekBar
    private lateinit var transparencyValueText: TextView

    private var locationListener: LocationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_configure)

        // Default result is CANCELED until the user actually picks a city — if the
        // host launched us via the widget-placement flow and the user backs out,
        // Android will not add the widget.
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        cityInput = findViewById(R.id.city_input)
        statusText = findViewById(R.id.status_text)
        resultsContainer = findViewById(R.id.results_container)
        suggestionBtn = findViewById(R.id.suggestion_btn)

        findViewById<Button>(R.id.search_btn).setOnClickListener { doSearch() }
        findViewById<Button>(R.id.locate_btn).setOnClickListener { useCurrentLocation() }

        showLastAppCitySuggestion()
        maybeRequestNotificationPermission()
        setupPersonalization()
    }

    /**
     * Android 13+ (API 33) requires the runtime POST_NOTIFICATIONS permission before
     * WeatherNotifier can post anything. There's no way to prompt for it from a
     * Worker (no UI), so this configure screen — the first native-UI touchpoint the
     * user hits when placing/reconfiguring a widget — is the natural place to ask.
     * If denied, WeatherNotifier's own permission check just silently skips
     * notifications; nothing here depends on the result.
     */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST,
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationListener?.let {
            (getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.removeUpdates(it)
        }
        bgExecutor.shutdown()
    }

    private fun showLastAppCitySuggestion() {
        val last = CapacitorStorage.lastAppCity(this) ?: return
        suggestionBtn.text = "> use last app location: ${last.name}"
        suggestionBtn.visibility = View.VISIBLE
        suggestionBtn.setOnClickListener { selectCity(last) }
    }

    private fun doSearch() {
        val query = cityInput.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) {
            statusText.text = "// type a city name first"
            return
        }
        statusText.text = "// searching..."
        resultsContainer.removeAllViews()
        bgExecutor.execute {
            val results = try {
                WeatherApi.geocode(query)
            } catch (e: Exception) {
                null
            }
            mainHandler.post {
                if (results == null) {
                    statusText.text = "// search failed, check connection"
                } else if (results.isEmpty()) {
                    statusText.text = "// no results for \"$query\""
                } else {
                    statusText.text = "// ${results.size} result(s)"
                    results.forEach { r -> resultsContainer.addView(buildResultRow(r)) }
                }
            }
        }
    }

    private fun buildResultRow(r: WeatherApi.GeoResult): TextView {
        val label = buildString {
            append(r.name)
            if (!r.admin1.isNullOrBlank()) append(", ${r.admin1}")
            if (r.country.isNotBlank()) append(", ${r.country}")
        }
        return TextView(this).apply {
            text = "> $label"
            setTextColor(0xFF33FF66.toInt())
            textSize = 14f
            setPadding(8, 20, 8, 20)
            typeface = android.graphics.Typeface.MONOSPACE
            setOnClickListener {
                selectCity(WidgetCity(r.latitude, r.longitude, r.name))
            }
        }
    }

    private fun useCurrentLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST,
            )
            return
        }
        requestLocationFix()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                requestLocationFix()
            } else {
                statusText.text = "// location permission denied — search instead"
            }
        }
        // NOTIFICATION_PERMISSION_REQUEST result is intentionally not handled here:
        // whatever the user chooses, WeatherNotifier checks the live permission state
        // itself before posting anything, so there's nothing else to react to.
    }

    private fun requestLocationFix() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        statusText.text = "// locating..."

        val lastKnown = safeLastKnown(lm, LocationManager.GPS_PROVIDER)
            ?: safeLastKnown(lm, LocationManager.NETWORK_PROVIDER)
        if (lastKnown != null) {
            reverseGeocodeAndSelect(lastKnown)
            return
        }

        // No cached fix — ask for a fresh one with a short timeout.
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            statusText.text = "// no location provider enabled — search instead"
            return
        }
        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lm.removeUpdates(this)
                    locationListener = null
                    reverseGeocodeAndSelect(location)
                }
            }
            locationListener = listener
            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            mainHandler.postDelayed({
                if (locationListener === listener) {
                    lm.removeUpdates(listener)
                    locationListener = null
                    statusText.text = "// no fix in time — try again outdoors, or search instead"
                }
            }, 10_000)
        } catch (e: SecurityException) {
            statusText.text = "// location permission denied — search instead"
        }
    }

    private fun safeLastKnown(lm: LocationManager, provider: String): Location? = try {
        lm.getLastKnownLocation(provider)
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun reverseGeocodeAndSelect(location: Location) {
        statusText.text = "// resolving location name..."
        val lat = location.latitude
        val lon = location.longitude
        bgExecutor.execute {
            // DeviceGeocoder (on-device, no network) first — WeatherApi.reverseGeocode
            // (Open-Meteo's `/v1/reverse`) is a dead endpoint (confirmed hard 404 for
            // every coordinate, not just this one) kept only as a last-ditch fallback
            // in case that ever changes. If both fail, an honest coordinate label
            // beats a static "current location" placeholder that never self-heals.
            val name = DeviceGeocoder.cityName(applicationContext, lat, lon)
                ?: try { WeatherApi.reverseGeocode(lat, lon)?.name } catch (e: Exception) { null }
                ?: DeviceGeocoder.coordinateLabel(lat, lon)
            // "Use my location" always saves live/follow mode, not a one-time
            // snapshot — see WidgetCity.isLive doc + WeatherWidgetProvider's
            // refresh path, which re-acquires the location on every refresh
            // from here on. Searching a city by name (buildResultRow /
            // suggestionBtn) stays a plain fixed point (isLive defaults false).
            val city = WidgetCity(lat, lon, name, isLive = true)
            mainHandler.post { selectCity(city) }
        }
    }

    private fun selectCity(city: WidgetCity) {
        statusText.text = "// saving ${city.name}..."
        WidgetPrefs.setCity(this, appWidgetId, city)

        bgExecutor.execute {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                val rv = WeatherWidgetProvider.buildRemoteViews(applicationContext, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, rv)
            } catch (e: Exception) {
                // widget will retry on the next periodic/manual refresh
            }
            WeatherWorker.enqueuePeriodic(applicationContext)
            mainHandler.post { finishAfterSelection(city.isLive) }
        }
    }

    /**
     * Wires up the theme swatches + transparency seekbar (beta
     * "personalization" section below the city picker) and restores whatever
     * this widget instance already has saved — [WidgetTheme.DEFAULT] /
     * [WidgetTransparency.DEFAULT] (Phosphor Green / Opaque) for a widget
     * that's never had these set, matching the pre-personalization look
     * exactly. Both controls apply immediately on change, same as
     * [selectCity] does for the city picker above — no separate save step.
     */
    private fun setupPersonalization() {
        themeButtons = mapOf(
            WidgetTheme.PHOSPHOR_GREEN to findViewById<TextView>(R.id.theme_phosphor_btn),
            WidgetTheme.AMBER_TERMINAL to findViewById<TextView>(R.id.theme_amber_btn),
            WidgetTheme.CYAN to findViewById<TextView>(R.id.theme_cyan_btn),
            WidgetTheme.CRIMSON to findViewById<TextView>(R.id.theme_crimson_btn),
            WidgetTheme.AUTO_HEALTH to findViewById<TextView>(R.id.theme_auto_btn),
        )
        themeButtons.forEach { (theme, btn) -> btn.setOnClickListener { selectTheme(theme) } }
        highlightSelectedTheme(WidgetPrefs.getTheme(this, appWidgetId))

        transparencySeekbar = findViewById(R.id.transparency_seekbar)
        transparencyValueText = findViewById(R.id.transparency_value)
        val currentTransparency = WidgetPrefs.getTransparency(this, appWidgetId)
        transparencySeekbar.progress = currentTransparency.storageId
        transparencyValueText.text = "> ${currentTransparency.label}"
        transparencySeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val transparency = WidgetTransparency.fromStorageId(progress)
                transparencyValueText.text = "> ${transparency.label}"
                WidgetPrefs.setTransparency(this@WidgetConfigureActivity, appWidgetId, transparency)
                pushWidgetUpdate()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun selectTheme(theme: WidgetTheme) {
        WidgetPrefs.setTheme(this, appWidgetId, theme)
        highlightSelectedTheme(theme)
        pushWidgetUpdate()
    }

    private fun highlightSelectedTheme(selected: WidgetTheme) {
        themeButtons.forEach { (theme, btn) ->
            btn.setBackgroundColor(if (theme == selected) THEME_BTN_SELECTED_BG else THEME_BTN_UNSELECTED_BG)
        }
    }

    /** Pushes a fresh [WeatherWidgetProvider.buildRemoteViews] update to this widget
     * instance right away — same fire-and-forget background pattern [selectCity]
     * already uses — so a theme/transparency change is visible immediately without
     * needing to leave this screen. */
    private fun pushWidgetUpdate() {
        bgExecutor.execute {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                val rv = WeatherWidgetProvider.buildRemoteViews(applicationContext, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, rv)
            } catch (e: Exception) {
                // widget will retry on the next periodic/manual refresh
            }
        }
    }

    private fun finishAfterSelection(isLive: Boolean) {
        val message = if (isLive) {
            "Widget will follow your current location"
        } else {
            "Widget city set to saved location"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        if (intent?.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE) {
            val resultValue = android.content.Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
        }
        finish()
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 4201
        private const val NOTIFICATION_PERMISSION_REQUEST = 4202

        private const val THEME_BTN_UNSELECTED_BG = 0xFF0A0F0A.toInt()
        private const val THEME_BTN_SELECTED_BG = 0xFF1A3A1A.toInt()
    }
}
