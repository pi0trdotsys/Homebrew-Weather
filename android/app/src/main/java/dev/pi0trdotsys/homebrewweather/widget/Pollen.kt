package dev.pi0trdotsys.homebrewweather.widget

/**
 * When pollen is a lot — the one thing worth saying about it, for the widget's
 * meta line, the morning brief and a well-being tip.
 *
 * Open-Meteo gives raw counts (grains/m³) per kind, in Europe only, and no
 * categories. [Species.highFrom] is this app's own line for each: roughly where
 * the common regional scales put "high". It is a nudge for people who already
 * know they react, not a medical scale, and the widget says so by saying nothing
 * below it. Three times that is "very high".
 *
 * Pure — covered by PollenTest.
 */
object Pollen {

    /** A kind of pollen with the count (grains/m³) from which it is called high. */
    enum class Species(val highFrom: Int) {
        ALDER(50),
        BIRCH(90),
        GRASS(30),
        MUGWORT(30),
        OLIVE(50),
        RAGWEED(30),
    }

    const val VERY_HIGH_FACTOR = 3

    data class Hit(val species: Species, val grains: Int, val veryHigh: Boolean)

    fun count(reading: WeatherApi.PollenReading, species: Species): Double = when (species) {
        Species.ALDER -> reading.alder
        Species.BIRCH -> reading.birch
        Species.GRASS -> reading.grass
        Species.MUGWORT -> reading.mugwort
        Species.OLIVE -> reading.olive
        Species.RAGWEED -> reading.ragweed
    }

    /**
     * The kind that is furthest over its line, or null when none is (or the
     * reading is unknown). "Furthest over" is by ratio to the kind's own line, so
     * a grass count of 90 outranks a birch count of 95.
     */
    fun top(reading: WeatherApi.PollenReading?): Hit? {
        if (reading == null) return null
        return Species.entries
            .mapNotNull { sp ->
                val n = count(reading, sp)
                if (n.isNaN() || n < sp.highFrom) null else Triple(sp, n, n / sp.highFrom)
            }
            .maxByOrNull { it.third }
            ?.let { (sp, n, ratio) -> Hit(sp, Math.round(n).toInt(), veryHigh = ratio >= VERY_HIGH_FACTOR) }
    }
}
