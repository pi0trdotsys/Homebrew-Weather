package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSceneTest {

    @Test
    fun `the sky decides the scene`() {
        assertEquals(WidgetScene.Kind.RAIN, WidgetScene.kindOf("rain", isDay = true))
        assertEquals(WidgetScene.Kind.SNOW, WidgetScene.kindOf("snow", isDay = false))
        assertEquals(WidgetScene.Kind.STORM, WidgetScene.kindOf("thunder", isDay = true))
        assertEquals(WidgetScene.Kind.STARS, WidgetScene.kindOf("sun", isDay = false))
        assertEquals(WidgetScene.Kind.STARS, WidgetScene.kindOf("partly", isDay = false))
    }

    @Test
    fun `a calm day or an overcast night has no scene`() {
        assertEquals(WidgetScene.Kind.NONE, WidgetScene.kindOf("sun", isDay = true))
        assertEquals(WidgetScene.Kind.NONE, WidgetScene.kindOf("cloud", isDay = false))
        assertEquals(WidgetScene.Kind.NONE, WidgetScene.kindOf("fog", isDay = false))
        assertTrue(WidgetScene.marks(WidgetScene.Kind.NONE, 80, 40, 3, 1).isEmpty())
    }

    @Test
    fun `only precipitation animates`() {
        assertTrue(WidgetScene.animated(WidgetScene.Kind.RAIN))
        assertTrue(WidgetScene.animated(WidgetScene.Kind.SNOW))
        assertTrue(WidgetScene.animated(WidgetScene.Kind.STORM))
        assertFalse(WidgetScene.animated(WidgetScene.Kind.STARS))
        assertFalse(WidgetScene.animated(WidgetScene.Kind.NONE))
    }

    @Test
    fun `rain moves from one frame to the next`() {
        val a = WidgetScene.marks(WidgetScene.Kind.RAIN, 80, 40, 1, 7)
        val b = WidgetScene.marks(WidgetScene.Kind.RAIN, 80, 40, 2, 7)
        assertEquals(a.size, b.size)
        assertNotEquals(a, b)
    }

    @Test
    fun `the same frame draws the same scene`() {
        assertEquals(
            WidgetScene.marks(WidgetScene.Kind.SNOW, 80, 40, 5, 7),
            WidgetScene.marks(WidgetScene.Kind.SNOW, 80, 40, 5, 7),
        )
    }

    @Test
    fun `stars stay where they are`() {
        assertEquals(
            WidgetScene.marks(WidgetScene.Kind.STARS, 80, 40, 1, 7),
            WidgetScene.marks(WidgetScene.Kind.STARS, 80, 40, 9, 7),
        )
    }

    @Test
    fun `different places get different skies`() {
        assertNotEquals(
            WidgetScene.marks(WidgetScene.Kind.STARS, 80, 40, 1, 7),
            WidgetScene.marks(WidgetScene.Kind.STARS, 80, 40, 1, 8),
        )
    }

    @Test
    fun `every mark stays inside the grid, over many frames`() {
        for (kind in listOf(WidgetScene.Kind.RAIN, WidgetScene.Kind.SNOW, WidgetScene.Kind.STORM, WidgetScene.Kind.STARS)) {
            for (frame in -3..40) {
                for (m in WidgetScene.marks(kind, 61, 29, frame, 99)) {
                    assertTrue("$kind $frame $m", m.x in 0 until 61 && m.y in 0 until 29)
                    assertTrue(m.alpha in 1..255)
                }
            }
        }
    }

    @Test
    fun `a tiny widget gets nothing rather than a crowd`() {
        assertTrue(WidgetScene.marks(WidgetScene.Kind.RAIN, 3, 3, 1, 1).isEmpty())
    }

    @Test
    fun `a storm flashes on a rhythm and nothing else does`() {
        assertTrue(WidgetScene.flashing(WidgetScene.Kind.STORM, 0))
        assertTrue(WidgetScene.flashing(WidgetScene.Kind.STORM, WidgetScene.FLASH_EVERY))
        assertFalse(WidgetScene.flashing(WidgetScene.Kind.STORM, 1))
        assertFalse(WidgetScene.flashing(WidgetScene.Kind.RAIN, 0))
        val bolt = WidgetScene.bolt(80, 40, 3)
        assertTrue(bolt.isNotEmpty())
        assertTrue(bolt.all { it.x in 0 until 80 && it.y in 0 until 40 })
    }
}
