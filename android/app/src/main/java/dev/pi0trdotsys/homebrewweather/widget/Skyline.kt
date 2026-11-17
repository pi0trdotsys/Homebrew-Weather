package dev.pi0trdotsys.homebrewweather.widget

import kotlin.random.Random

/**
 * A pixel skyline made from nothing but the city's name — the widget background
 * for flat country, where contour lines would only trace noise in the elevation
 * model (a 16 m "relief" across all of Wrocław draws as meaningless loops), and
 * the placeholder while a terrain grid is still being fetched.
 *
 * Deterministic: the same name always builds the same skyline, so a city keeps
 * its own silhouette from one refresh to the next. Pure geometry in "pixel
 * units"; the renderer scales a unit to a few device pixels. Covered by
 * SkylineTest.
 */
object Skyline {

    /** One building: [x] and [width] along the ground, [height] up, and its lit windows as (x, y) offsets inside it. */
    data class Building(val x: Int, val width: Int, val height: Int, val windows: List<Pair<Int, Int>>)

    /**
     * Buildings filling [widthUnits] from the left, none taller than [maxHeightUnits].
     * A third are towers; the rest sit low, which gives the line a skyline's
     * uneven rhythm rather than a comb.
     */
    fun buildings(seed: String, widthUnits: Int, maxHeightUnits: Int): List<Building> {
        if (widthUnits <= 0 || maxHeightUnits < 4) return emptyList()
        val rnd = Random(seed.lowercase().hashCode())
        val out = ArrayList<Building>()
        var x = 0
        while (x < widthUnits) {
            val width = rnd.nextInt(4, 10).coerceAtMost(widthUnits - x)
            val tower = rnd.nextDouble() < 0.35
            val low = maxOf(3, maxHeightUnits / 3)
            val height = if (tower) rnd.nextInt(low + 2, maxHeightUnits + 1) else rnd.nextInt(3, low + 1)
            val windows = ArrayList<Pair<Int, Int>>()
            for (wx in 1 until width - 1 step 2) {
                for (wy in 2 until height - 1 step 3) {
                    if (rnd.nextDouble() < 0.4) windows += wx to wy
                }
            }
            out += Building(x, width, height.coerceAtMost(maxHeightUnits), windows)
            x += width + if (rnd.nextBoolean()) 0 else 1
        }
        return out
    }
}
