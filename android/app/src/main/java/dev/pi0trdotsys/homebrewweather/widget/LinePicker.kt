package dev.pi0trdotsys.homebrewweather.widget

import kotlin.math.abs

/**
 * How the footer picks its line: which pools it draws from, and which line of them.
 *
 * ## No repeats until the whole pool has been seen
 *
 * The footer used to show `pool[hour % pool.size]`. Hours count up one at a time, so the
 * lines came round in the same fixed order, and a pool of 12 lines was back after 12 hours:
 * the same joke twice a day, always at the same two hours. Now every stretch of `size` hours
 * is a fresh shuffle of the pool ("a shuffle bag"): each line is shown once before any is
 * shown again, in a different order each round, and the last line of one round is never
 * the first of the next. Stable within the hour, like before, and the same on the dashboard.
 *
 * ## Which pools
 *
 * A line is drawn from the weather's own pool plus whichever of these apply: after dark, a hot
 * day, a freezing one, and the day of the week (Monday, Friday, the weekend). They are mixed in
 * with the weather's lines, not instead of them, so a hot Friday night in the rain is one
 * pool made of five.
 *
 * Mirrored in src/lib/line-picker.ts and checked against testdata/parity/line-picker.json.
 */
object LinePicker {

    /** From this temperature (C) the "hot" lines are mixed in. */
    const val HOT_FROM_C = 28

    /** Up to this temperature (C) the "cold" lines are mixed in. */
    const val COLD_UP_TO_C = 2

    /** The weather kinds that have their own pool; anything else uses "cloud". */
    val KINDS = listOf("sun", "partly", "cloud", "fog", "rain", "snow", "thunder")

    /** The contextual pools mixed in beside the weather's own, besides "night". */
    val CONTEXT_KEYS = listOf("hot", "cold", "monday", "friday", "weekend")

    /** Index into a pool of [size] lines for [seed] (the hour). 0 for a pool of one line or none. */
    fun index(seed: Int, size: Int): Int {
        if (size <= 1) return 0
        val h = if (seed == Int.MIN_VALUE) 0 else abs(seed)
        if (size == 2) return h % 2
        val cycle = h / size
        return permutation(cycle, size)[h % size]
    }

    /** A pass through all [n] (>= 3) lines: a seeded shuffle, never starting where the last pass ended. */
    private fun permutation(cycle: Int, n: Int): IntArray {
        val perm = shuffled(cycle, n)
        if (cycle > 0 && perm[0] == shuffled(cycle - 1, n)[n - 1]) {
            val first = perm[0]
            perm[0] = perm[1]
            perm[1] = first
        }
        return perm
    }

    private fun shuffled(cycle: Int, n: Int): IntArray {
        val rng = Mulberry32(cycle * 0x9E3779B1.toInt() + n * 0x85EBCA6B.toInt())
        val a = IntArray(n) { it }
        for (i in n - 1 downTo 1) {
            val j = rng.nextInt(0, i + 1)
            val t = a[i]
            a[i] = a[j]
            a[j] = t
        }
        return a
    }

    /**
     * The pools a joke is drawn from, in the order they are joined: night, then the contextual
     * ones (hot, cold, the day of the week), then the weather kind's own.
     * [dayOfWeek] is 0 for Sunday .. 6 for Saturday, null if unknown; [temperature] may be NaN.
     */
    fun jokeKeys(kind: String, isNight: Boolean, temperature: Double, dayOfWeek: Int?): List<String> {
        val keys = ArrayList<String>()
        if (isNight) keys += "night"
        if (!temperature.isNaN() && temperature >= HOT_FROM_C) keys += "hot"
        if (!temperature.isNaN() && temperature <= COLD_UP_TO_C) keys += "cold"
        when (dayOfWeek) {
            1 -> keys += "monday"
            5 -> keys += "friday"
            0, 6 -> keys += "weekend"
        }
        keys += if (kind in KINDS) kind else "cloud"
        return keys
    }

    /** The joke for [seed] from [pools] (a pool a language or tone does not have is simply left out). */
    fun pickJoke(
        pools: Map<String, List<String>>,
        kind: String,
        isNight: Boolean,
        seed: Int,
        temperature: Double = Double.NaN,
        dayOfWeek: Int? = null,
    ): String {
        val keys = jokeKeys(kind, isNight, temperature, dayOfWeek)
        val pool = keys.flatMap { pools[it].orEmpty() }.ifEmpty { pools.getValue("cloud") }
        return pool[index(seed, pool.size)]
    }

    /** Day of the week of an ISO date (`2026-07-31` or a longer timestamp): 0 Sunday .. 6 Saturday; null if it isn't one. */
    fun dayOfWeek(isoDate: String?): Int? {
        if (isoDate == null || isoDate.length < 10) return null
        val y = isoDate.substring(0, 4).toIntOrNull() ?: return null
        val m = isoDate.substring(5, 7).toIntOrNull() ?: return null
        val d = isoDate.substring(8, 10).toIntOrNull() ?: return null
        if (isoDate[4] != '-' || isoDate[7] != '-' || m !in 1..12 || d !in 1..31) return null
        val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
        val yy = if (m < 3) y - 1 else y
        return (yy + yy / 4 - yy / 100 + yy / 400 + t[m - 1] + d) % 7
    }
}
