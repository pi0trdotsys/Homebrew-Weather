package dev.pi0trdotsys.homebrewweather.widget

/**
 * How much a widget instance shows. Picked per widget in
 * WidgetConfigureActivity, alongside theme and opacity.
 *
 * [STANDARD] is the default, and it is the point of this setting existing: it
 * shows the forecast plus only what is *out of the ordinary* today (see
 * [WidgetContent]). [FULL] is the escape hatch for anyone who wants every
 * readout all the time — it is what the widget looked like before this mode
 * existed. [MINIMAL] is for people who want a temperature and four days and
 * nothing else competing with them.
 */
enum class WidgetDensity(val storageId: Int, val label: String) {
    MINIMAL(0, "minimal"),
    STANDARD(1, "standard"),
    FULL(2, "full");

    companion object {
        val DEFAULT = STANDARD

        fun fromStorageId(id: Int): WidgetDensity = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}
