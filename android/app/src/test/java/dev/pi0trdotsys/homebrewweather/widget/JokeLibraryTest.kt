package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The size and hygiene of the footer text library (see also WidgetContentTest for lengths). */
class JokeLibraryTest {

    private val libraries: Map<String, Map<String, List<String>>> = mapOf(
        "pl sigma" to SigmaJokes.pools,
        "pl clean" to CleanJokes.pools,
        "en clean" to EnJokes.cleanPools,
        "en sigma" to EnJokes.sigmaPools,
    )

    private val weatherKeys = LinePicker.KINDS + "night"

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.replace(Regex("\\s+"), " ").trim()

    @Test
    fun `every library has every weather pool and every context pool`() {
        val expected = (weatherKeys + LinePicker.CONTEXT_KEYS).toSet()
        for ((name, pools) in libraries) assertEquals(name, expected, pools.keys)
    }

    @Test
    fun `each weather pool is deep enough that a line comes back at most every two days`() {
        for ((name, pools) in libraries) {
            for (key in weatherKeys) {
                val size = pools.getValue(key).size
                assertTrue("$name/$key has $size lines", size >= 48)
            }
        }
    }

    @Test
    fun `each context pool has a real range of its own`() {
        for ((name, pools) in libraries) {
            for (key in LinePicker.CONTEXT_KEYS) {
                val size = pools.getValue(key).size
                assertTrue("$name/$key has $size lines", size >= 20)
            }
        }
    }

    @Test
    fun `no line appears twice in a library, in any pool`() {
        for ((name, pools) in libraries) {
            val seen = HashMap<String, String>()
            for ((key, lines) in pools) {
                for (line in lines) {
                    val clash = seen.put(norm(line), key)
                    assertTrue("$name: \"$line\" is in $key and ${clash ?: ""}", clash == null)
                }
            }
        }
    }

    @Test
    fun `the well-being tips are deep too, and unique`() {
        for (lang in listOf(Lang.PL, Lang.EN)) {
            val all = WellbeingTips.Situation.entries.map { s ->
                val pool = (1..400).map { WellbeingTips.pick(lang, s, it) }.toSet()
                assertTrue("$lang/${s.key} shows only ${pool.size} lines in 400 hours", pool.size >= 15)
                pool
            }.flatMap { it }
            assertEquals("$lang: a tip in two pools", all.size, all.map { norm(it) }.toSet().size)
        }
    }

    @Test
    fun `a whole round of hours shows every line once`() {
        // On steady rain the footer draws from the rain pool; one round of that many hours is
        // every line exactly once (the shuffle bag), where the old rule came round in a fixed order.
        val size = SigmaJokes.pools.getValue("rain").size
        val round = 8_000
        val shown = (round * size until (round + 1) * size).map { SigmaJokes.pick("rain", false, it) }
        assertEquals(size, shown.toSet().size)
    }
}
