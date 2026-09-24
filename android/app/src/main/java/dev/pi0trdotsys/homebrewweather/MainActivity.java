package dev.pi0trdotsys.homebrewweather;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Capacitor host for the web app, plus one job of its own: when it is opened by
 * tapping a home-screen widget, show that widget's city.
 *
 * Before this, a widget tap opened the app on whatever location the app had
 * saved for itself, which is unrelated to the widget's — so tapping a widget
 * showing Tolox could land on Málaga. The widget now puts its city in the
 * launch intent (see WeatherWidgetProvider), and this writes it into the same
 * Capacitor Preferences entry the web app loads its location from
 * (`brew-wx:coords` in the "CapacitorStorage" SharedPreferences file — see
 * src/lib/settings.ts). The web app then simply boots on that city, and
 * keeps it: it's the city you last asked to look at.
 *
 * If the app is already running, rewriting storage isn't enough because the
 * web app has already read it, so {@link #onNewIntent} also tells the live
 * page directly (see the `hbw:open-city` listener in src/routes/index.tsx).
 */
public class MainActivity extends BridgeActivity {

    public static final String EXTRA_CITY_LAT = "dev.pi0trdotsys.homebrewweather.CITY_LAT";
    public static final String EXTRA_CITY_LON = "dev.pi0trdotsys.homebrewweather.CITY_LON";
    public static final String EXTRA_CITY_NAME = "dev.pi0trdotsys.homebrewweather.CITY_NAME";

    private static final String CAPACITOR_PREFS = "CapacitorStorage";
    private static final String COORDS_KEY = "brew-wx:coords";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Before super.onCreate(): the web app reads its saved location as
        // soon as it boots, so the widget's city has to be in storage first.
        storeCityFrom(getIntent());
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String json = storeCityFrom(intent);
        if (json == null || getBridge() == null) return;
        WebView webView = getBridge().getWebView();
        if (webView == null) return;
        // JSONObject output is a valid JS object literal, and escapes quotes
        // and "</" in the city name, so it can be spliced in as-is.
        webView.post(() -> webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('hbw:open-city', { detail: " + json + " }));",
            null
        ));
    }

    /** Writes the intent's widget city to Capacitor storage; returns its JSON, or null if none. */
    private String storeCityFrom(Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_CITY_LAT) || !intent.hasExtra(EXTRA_CITY_LON)) {
            return null;
        }
        double lat = intent.getDoubleExtra(EXTRA_CITY_LAT, Double.NaN);
        double lon = intent.getDoubleExtra(EXTRA_CITY_LON, Double.NaN);
        String name = intent.getStringExtra(EXTRA_CITY_NAME);
        if (Double.isNaN(lat) || Double.isNaN(lon)) return null;
        try {
            String json = new JSONObject()
                .put("lat", lat)
                .put("lon", lon)
                .put("name", name != null ? name : "?")
                .toString();
            getSharedPreferences(CAPACITOR_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(COORDS_KEY, json)
                .apply();
            return json;
        } catch (JSONException e) {
            return null;
        }
    }
}
