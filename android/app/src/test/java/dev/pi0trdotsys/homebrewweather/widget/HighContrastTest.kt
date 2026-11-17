package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighContrastTest {

    private val background = 0xFF0A0F0A.toInt()
    private val primaries = mapOf(
        "green" to 0xFF33FF66.toInt(),
        "amber" to 0xFFFFB000.toInt(),
        "cyan" to 0xFF55FFFF.toInt(),
        "crimson" to 0xFFFF5555.toInt(),
    )

    @Test
    fun `contrast is the WCAG ratio`() {
        assertEquals(21.0, HighContrast.contrast(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 1e-9)
        assertEquals(1.0, HighContrast.contrast(0xFF123456.toInt(), 0xFF123456.toInt()), 1e-9)
    }

    @Test
    fun `the designed quiet green is below AA, which is why the option exists`() {
        val quiet = HighContrast.contrast(0xFF4A6A4A.toInt(), background)
        assertTrue("was $quiet", quiet < HighContrast.AA_RATIO)
    }

    @Test
    fun `every theme's dim shade reaches AA against the widget background`() {
        primaries.forEach { (name, primary) ->
            val dim = HighContrast.dim(primary, background)
            val ratio = HighContrast.contrast(dim, background)
            assertTrue("$name dim is $ratio", ratio >= HighContrast.AA_RATIO)
        }
    }

    @Test
    fun `the dim shade stays quieter than the primary and keeps its hue`() {
        primaries.forEach { (name, primary) ->
            val dim = HighContrast.dim(primary, background)
            assertTrue("$name: dim should not be brighter than primary",
                HighContrast.luminance(dim) <= HighContrast.luminance(primary))
        }
        // Green stays green: its green channel dominates.
        val green = HighContrast.dim(primaries.getValue("green"), background)
        assertTrue(((green shr 8) and 0xFF) > ((green shr 16) and 0xFF))
        assertTrue(((green shr 8) and 0xFF) > (green and 0xFF))
    }

    @Test
    fun `a primary that cannot reach the ratio is returned as it is`() {
        val grey = 0xFF202020.toInt()
        assertEquals(grey, HighContrast.dim(grey, background))
    }
}
