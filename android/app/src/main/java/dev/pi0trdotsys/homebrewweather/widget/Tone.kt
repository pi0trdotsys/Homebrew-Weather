package dev.pi0trdotsys.homebrewweather.widget

/**
 * The app's voice, set once in ./settings and applied everywhere it speaks:
 * the widget footer, notifications, and the dashboard's terminal joke.
 *
 * It exists because the app had five separate humour sources with no single
 * way to turn the volume down — and the loudest of them (the notification
 * copy) goes to a lock screen, and the widget footer sits on a home screen,
 * both of which other people see.
 *
 * - [CLEAN]: wry but inoffensive footer lines, plain factual notifications.
 * - [SIGMA]: the "sigma" footer, plain factual notifications.
 * - [RUDE]: the sigma footer and the deliberately crude notifications. This is
 *   what the app did before the setting existed, so it stays the default —
 *   nobody's app changes voice under them on update.
 *
 * Stored as a string under `settings:tone` in the shared Capacitor prefs; the
 * web app's src/lib/settings.ts writes the same key and values.
 */
enum class Tone(val storageValue: String) {
    CLEAN("clean"),
    SIGMA("sigma"),
    RUDE("rude");

    companion object {
        val DEFAULT = RUDE

        fun fromStorage(raw: String?): Tone = entries.firstOrNull { it.storageValue == raw } ?: DEFAULT
    }
}
