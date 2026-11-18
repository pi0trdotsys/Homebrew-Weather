package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TempColorTest {

    @Test
    fun `the stops are the widget's own colours`() {
        assertEquals("#55ffff", TempColor.hex(0.0))
        assertEquals("#ffb000", TempColor.hex(22.0))
        assertEquals("#ff5555", TempColor.hex(35.0))
        assertEquals("#7a9bff", TempColor.hex(-15.0))
    }

    @Test
    fun `beyond the ends the colour stays put`() {
        assertEquals(TempColor.hex(-15.0), TempColor.hex(-40.0))
        assertEquals(TempColor.hex(35.0), TempColor.hex(48.0))
    }

    @Test
    fun `between stops it blends linearly`() {
        // Halfway between cyan (85,255,255) and amber (255,176,0) is (170, 215.5, 127.5).
        assertEquals("#aad880", TempColor.hex(11.0))
        // Halfway between -15 and 0: (122,155,255) and (85,255,255) -> (103.5, 205, 255).
        assertEquals("#68cdff", TempColor.hex(-7.5))
    }

    @Test
    fun `five degrees and thirty-two degrees no longer look alike`() {
        assertNotEquals(TempColor.rgb(5.0), TempColor.rgb(32.0))
    }

    @Test
    fun `it moves one way, cold to hot, without a jump`() {
        var previous = TempColor.rgb(-20.0)
        var t = -20.0
        while (t <= 40.0) {
            val c = TempColor.rgb(t)
            // Neighbouring tenths of a degree never differ by more than a few levels per channel.
            for (shift in listOf(16, 8, 0)) {
                assertTrue("jump at $t", Math.abs(((c shr shift) and 0xFF) - ((previous shr shift) and 0xFF)) <= 6)
            }
            previous = c
            t += 0.1
        }
    }

    @Test
    fun `unknown reads as the freezing cyan, and argb is opaque`() {
        assertEquals("#55ffff", TempColor.hex(Double.NaN))
        assertEquals(0xFF, TempColor.argb(20.0) ushr 24)
    }
}
