package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two content constraints that are easy to break by hand and
 * invisible until something renders wrong on a phone: footer lines that are
 * too long to fit, and pixel-icon grids that aren't 16x16 or use a colour
 * character nothing maps.
 *
 * Plain JVM tests — neither object needs a device. [PixelIcons] does touch
 * android.graphics.Color when its colour map initialises, which is why the
 * module enables `unitTests.returnDefaultValues` (see app/build.gradle); the
 * grids themselves are just strings and are what's actually under test here.
 */
class WidgetContentTest {

    @Test
    fun `every sigma line fits the widget footer`() {
        val tooLong = SigmaJokes.pools.entries.flatMap { (kind, lines) ->
            lines.filter { it.length > SigmaJokes.MAX_LINE_LENGTH }.map { "$kind: \"$it\" (${it.length})" }
        }
        assertTrue(
            "Footer lines must be at most ${SigmaJokes.MAX_LINE_LENGTH} characters or the " +
                "punchline gets ellipsized away on a narrow widget:\n" + tooLong.joinToString("\n"),
            tooLong.isEmpty(),
        )
    }

    @Test
    fun `sigma lines are unique across every pool`() {
        val all = SigmaJokes.pools.values.flatten()
        val duplicates = all.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue("Duplicated sigma lines: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `every weather kind has a sigma pool`() {
        // "night" is the extra pool mixed in after dark, not a WeatherKind.
        val kinds = listOf("sun", "partly", "cloud", "fog", "rain", "snow", "thunder")
        kinds.forEach { kind ->
            assertTrue("no sigma pool for kind '$kind'", SigmaJokes.pools[kind]?.isNotEmpty() == true)
        }
        assertTrue(SigmaJokes.pools["night"]?.isNotEmpty() == true)
    }

    @Test
    fun `every pixel icon is a 16x16 grid of known colour characters`() {
        val known = ".YoWwDdBbSGgLlCK".toSet()
        PixelIcons.ICONS.forEach { (kind, grid) ->
            assertEquals("icon '$kind' must have 16 rows", 16, grid.size)
            grid.forEachIndexed { i, row ->
                assertEquals("icon '$kind' row $i must be 16 cells: \"$row\"", 16, row.length)
                val unknown = row.toSet() - known
                assertTrue("icon '$kind' row $i uses unmapped characters $unknown", unknown.isEmpty())
            }
        }
    }

    @Test
    fun `rain and snow keep their precipitation inside the animated band`() {
        // shiftBand() only cycles rows 9..15; a precipitation pixel above that
        // would sit frozen while everything around it falls.
        listOf("rain" to "Bb", "snow" to "S").forEach { (kind, precipChars) ->
            val grid = PixelIcons.ICONS.getValue(kind)
            grid.take(9).forEachIndexed { i, row ->
                val strays = row.toSet().intersect(precipChars.toSet())
                assertTrue("'$kind' row $i has precipitation $strays above the animated band", strays.isEmpty())
            }
        }
    }
}
