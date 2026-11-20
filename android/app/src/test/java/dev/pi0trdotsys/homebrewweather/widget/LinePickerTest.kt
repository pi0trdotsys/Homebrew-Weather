package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinePickerTest {

    @Test
    fun `every round of a pool shows each line exactly once`() {
        for (size in listOf(3, 4, 7, 12, 24, 60, 101)) {
            for (round in listOf(0, 1, 5, 4_000)) {
                val seen = (0 until size).map { LinePicker.index(round * size + it, size) }
                assertEquals("size $size round $round", (0 until size).toSet(), seen.toSet())
            }
        }
    }

    @Test
    fun `the same line is never shown two hours running, round boundaries included`() {
        for (size in listOf(2, 3, 5, 12, 60)) {
            var previous = LinePicker.index(0, size)
            for (hour in 1..5_000) {
                val now = LinePicker.index(hour, size)
                assertNotEquals("size $size hour $hour", previous, now)
                previous = now
            }
        }
    }

    @Test
    fun `rounds come in different orders, so a line has no fixed hour`() {
        val size = 24
        val orders = (0 until 10).map { round -> (0 until size).map { LinePicker.index(round * size + it, size) } }
        assertEquals("ten rounds, ten orders", 10, orders.toSet().size)
        // Where does line 0 land in each round? Not always the same slot.
        val slots = orders.map { it.indexOf(0) }.toSet()
        assertTrue("slots of one line: $slots", slots.size > 3)
    }

    @Test
    fun `the old rule's clock is gone - a line does not return every size hours`() {
        val size = 12
        val sameAsTwelveHoursLater = (0 until 120).count { LinePicker.index(it, size) == LinePicker.index(it + size, size) }
        assertTrue("$sameAsTwelveHoursLater of 120 repeat at +12h", sameAsTwelveHoursLater < 30)
    }

    @Test
    fun `degenerate pools and seeds are safe`() {
        assertEquals(0, LinePicker.index(5, 0))
        assertEquals(0, LinePicker.index(5, 1))
        for (seed in listOf(Int.MIN_VALUE, Int.MAX_VALUE, -1, 0)) {
            val i = LinePicker.index(seed, 9)
            assertTrue(i in 0 until 9)
        }
    }

    @Test
    fun `the same hour gives the same line`() {
        assertEquals(LinePicker.index(493_123, 60), LinePicker.index(493_123, 60))
    }

    @Test
    fun `context pools are mixed in beside the weather's own, not instead of it`() {
        val pools = mapOf(
            "rain" to listOf("r1", "r2"),
            "night" to listOf("n1"),
            "hot" to listOf("h1"),
            "friday" to listOf("f1"),
            "cloud" to listOf("c1"),
        )
        val all = (0 until 200).map { LinePicker.pickJoke(pools, "rain", true, it, 30.0, 5) }.toSet()
        assertEquals(setOf("r1", "r2", "n1", "h1", "f1"), all)
        // And nothing from a context that doesn't apply.
        val plain = (0 until 50).map { LinePicker.pickJoke(pools, "rain", false, it, 15.0, 3) }.toSet()
        assertEquals(setOf("r1", "r2"), plain)
    }

    @Test
    fun `a missing context pool is just left out`() {
        val pools = mapOf("sun" to listOf("s1", "s2"), "cloud" to listOf("c1"))
        val all = (0 until 20).map { LinePicker.pickJoke(pools, "sun", false, it, 35.0, 1) }.toSet()
        assertEquals(setOf("s1", "s2"), all)
    }

    @Test
    fun `day of the week`() {
        assertEquals(4, LinePicker.dayOfWeek("1970-01-01"))
        assertEquals(6, LinePicker.dayOfWeek("2000-01-01"))
        assertEquals(5, LinePicker.dayOfWeek("2026-07-31"))
        assertEquals(5, LinePicker.dayOfWeek("2026-07-31T23:00"))
        assertEquals(null, LinePicker.dayOfWeek("soon"))
        assertEquals(null, LinePicker.dayOfWeek(null))
    }
}
