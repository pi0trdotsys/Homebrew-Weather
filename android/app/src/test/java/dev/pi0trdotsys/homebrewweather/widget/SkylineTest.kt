package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkylineTest {

    @Test
    fun `the same name always builds the same skyline`() {
        assertEquals(Skyline.buildings("Wrocław", 120, 20), Skyline.buildings("Wrocław", 120, 20))
        assertEquals(Skyline.buildings("Wrocław", 120, 20), Skyline.buildings("wrocław", 120, 20))
    }

    @Test
    fun `different cities have different silhouettes`() {
        assertNotEquals(Skyline.buildings("Wrocław", 120, 20), Skyline.buildings("Kraków", 120, 20))
    }

    @Test
    fun `buildings fill the width without leaving it, none taller than allowed`() {
        val b = Skyline.buildings("Estepona", 150, 18)
        assertTrue(b.isNotEmpty())
        assertEquals(0, b.first().x)
        b.forEach {
            assertTrue(it.x >= 0 && it.x + it.width <= 150)
            assertTrue(it.height in 3..18)
            assertTrue(it.width >= 1)
        }
        assertTrue("reaches the right-hand edge", b.last().x + b.last().width >= 140)
    }

    @Test
    fun `windows sit inside their building`() {
        Skyline.buildings("Kielce", 150, 24).forEach { b ->
            b.windows.forEach { (x, y) ->
                assertTrue(x in 1 until b.width)
                assertTrue(y in 2 until b.height)
            }
        }
    }

    @Test
    fun `there is a mix of towers and low buildings`() {
        val heights = Skyline.buildings("Warszawa", 400, 30).map { it.height }
        assertTrue(heights.max() - heights.min() >= 8)
    }

    @Test
    fun `no room means no buildings`() {
        assertTrue(Skyline.buildings("x", 0, 20).isEmpty())
        assertTrue(Skyline.buildings("x", 100, 2).isEmpty())
    }
}
