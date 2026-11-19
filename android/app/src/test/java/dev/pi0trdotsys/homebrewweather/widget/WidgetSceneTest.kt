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

    // ---- the looping scene ----

    private val animatedKinds = listOf(WidgetScene.Kind.RAIN, WidgetScene.Kind.SNOW, WidgetScene.Kind.STORM)

    /** Every kind the live scene can draw. */
    private val liveKinds = animatedKinds + listOf(
        WidgetScene.Kind.STARS, WidgetScene.Kind.SUN, WidgetScene.Kind.PARTLY,
        WidgetScene.Kind.PARTLY_NIGHT, WidgetScene.Kind.CLOUD, WidgetScene.Kind.FOG,
    )

    @Test
    fun `a loop is seamless - the frame after the last is the first`() {
        for (kind in liveKinds) {
            val n = WidgetScene.LOOP_FRAMES
            assertEquals(
                "$kind",
                WidgetScene.loop(kind, 61, 29, 0, n, 5),
                WidgetScene.loop(kind, 61, 29, n, n, 5),
            )
        }
    }

    @Test
    fun `consecutive frames of a moving scene differ`() {
        for (kind in liveKinds) {
            for (f in 0 until WidgetScene.LOOP_FRAMES) {
                assertNotEquals(
                    "$kind frame $f",
                    WidgetScene.loop(kind, 61, 29, f, seed = 5),
                    WidgetScene.loop(kind, 61, 29, f + 1, seed = 5),
                )
            }
        }
    }

    @Test
    fun `loop marks stay inside the grid`() {
        for (kind in liveKinds) {
            for (f in 0 until WidgetScene.LOOP_FRAMES) {
                for (m in WidgetScene.loop(kind, 47, 23, f, seed = 11)) {
                    assertTrue("$kind $f $m", m.x in 0 until 47 && m.y in 0 until 23)
                    assertTrue(m.alpha in 1..255)
                }
            }
        }
    }

    @Test
    fun `the number of drops is the same in every frame`() {
        for (kind in animatedKinds) {
            val counts = (0 until WidgetScene.LOOP_FRAMES).map { WidgetScene.loop(kind, 61, 29, it, seed = 2).size }.toSet()
            assertEquals("$kind", 1, counts.size)
        }
    }

    @Test
    fun `a storm flashes once per loop, with an afterglow`() {
        val levels = (0 until WidgetScene.LOOP_FRAMES).map { WidgetScene.loopFlash(WidgetScene.Kind.STORM, it) }
        assertEquals(1, levels.count { it == 2 })
        assertEquals(1, levels.count { it == 1 })
        assertEquals(WidgetScene.LOOP_FRAMES - 2, levels.count { it == 0 })
        for (kind in listOf(WidgetScene.Kind.RAIN, WidgetScene.Kind.SNOW, WidgetScene.Kind.STARS)) {
            assertTrue((0 until WidgetScene.LOOP_FRAMES).all { WidgetScene.loopFlash(kind, it) == 0 })
        }
    }

    @Test
    fun `no scene, no loop`() {
        assertTrue(WidgetScene.loop(WidgetScene.Kind.NONE, 61, 29, 1, seed = 1).isEmpty())
        assertTrue(WidgetScene.loop(WidgetScene.Kind.RAIN, 3, 3, 1, seed = 1).isEmpty())
    }

    @Test
    fun `the live scene has something for every weather`() {
        val kinds = listOf("sun", "partly", "cloud", "fog", "rain", "snow", "thunder")
        for (day in listOf(true, false)) {
            for (k in kinds) {
                assertNotEquals("$k day=$day", WidgetScene.Kind.NONE, WidgetScene.liveKindOf(k, day))
            }
        }
        assertEquals(WidgetScene.Kind.SUN, WidgetScene.liveKindOf("sun", true))
        assertEquals(WidgetScene.Kind.STARS, WidgetScene.liveKindOf("sun", false))
        assertEquals(WidgetScene.Kind.PARTLY, WidgetScene.liveKindOf("partly", true))
        assertEquals(WidgetScene.Kind.PARTLY_NIGHT, WidgetScene.liveKindOf("partly", false))
        assertEquals(WidgetScene.Kind.CLOUD, WidgetScene.liveKindOf("cloud", false))
        assertEquals(WidgetScene.Kind.FOG, WidgetScene.liveKindOf("fog", true))
        assertEquals(WidgetScene.Kind.STORM, WidgetScene.liveKindOf("thunder", false))
    }

    @Test
    fun `the still scene is unchanged - calm skies stay empty`() {
        assertEquals(WidgetScene.Kind.NONE, WidgetScene.kindOf("cloud", true))
        assertEquals(WidgetScene.Kind.NONE, WidgetScene.kindOf("fog", true))
        assertEquals(WidgetScene.Kind.NONE, WidgetScene.kindOf("sun", true))
        assertTrue(WidgetScene.marks(WidgetScene.Kind.CLOUD, 80, 40, 1, 1).isEmpty())
    }

    @Test
    fun `clouds, fog and sun draw something in every frame`() {
        for (kind in listOf(WidgetScene.Kind.SUN, WidgetScene.Kind.PARTLY, WidgetScene.Kind.CLOUD, WidgetScene.Kind.FOG)) {
            for (f in 0 until WidgetScene.LOOP_FRAMES) {
                assertTrue("$kind $f", WidgetScene.loop(kind, 130, 114, f, seed = 3).isNotEmpty())
            }
        }
    }
}
