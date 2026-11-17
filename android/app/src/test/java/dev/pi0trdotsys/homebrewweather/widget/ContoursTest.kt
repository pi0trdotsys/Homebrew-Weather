package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ContoursTest {

    /** A cone: highest at the centre of a [w] x [h] field, falling away evenly. */
    private fun cone(w: Int, h: Int): FloatArray = FloatArray(w * h) { i ->
        val x = i % w
        val y = i / w
        100f - hypot(x - (w - 1) / 2f, y - (h - 1) / 2f) * 10f
    }

    @Test
    fun `a level above everything or below everything draws nothing`() {
        val f = cone(9, 9)
        assertTrue(Contours.segments(f, 9, 9, 500f).isEmpty())
        assertTrue(Contours.segments(f, 9, 9, -500f).isEmpty())
    }

    @Test
    fun `a level through a cone is a closed loop of segments around its top`() {
        val f = cone(21, 21)
        val segs = Contours.segments(f, 21, 21, 70f) // a radius-3 ring around (10, 10)
        assertTrue(segs.size > 8)
        // Every end point lies about 3 cells from the centre.
        segs.forEach { s ->
            assertEquals(3.0, hypot(s.x1 - 10.0, s.y1 - 10.0), 0.6)
            assertEquals(3.0, hypot(s.x2 - 10.0, s.y2 - 10.0), 0.6)
        }
    }

    @Test
    fun `a straight slope gives one straight line across the field`() {
        val w = 6
        val h = 4
        val f = FloatArray(w * h) { (it % w).toFloat() } // rises west to east
        val segs = Contours.segments(f, w, h, 2.5f)
        assertEquals(h - 1, segs.size)
        segs.forEach {
            assertEquals(2.5f, it.x1, 1e-4f)
            assertEquals(2.5f, it.x2, 1e-4f)
        }
    }

    @Test
    fun `segments stay inside the field`() {
        val f = cone(13, 7)
        Contours.segments(f, 13, 7, 40f).forEach {
            assertTrue(it.x1 in 0f..12f && it.x2 in 0f..12f && it.y1 in 0f..6f && it.y2 in 0f..6f)
        }
    }

    @Test
    fun `upsampling keeps the corners and fills in between`() {
        val f = floatArrayOf(0f, 10f, 20f, 30f)
        val (up, w, h) = Contours.upsample(f, 2, 2, 4)
        assertEquals(5, w)
        assertEquals(5, h)
        assertEquals(0f, up[0], 1e-4f)
        assertEquals(10f, up[4], 1e-4f)
        assertEquals(20f, up[4 * 5], 1e-4f)
        assertEquals(30f, up[24], 1e-4f)
        assertEquals(15f, up[2 * 5 + 2], 1e-4f)
    }

    @Test
    fun `smoothing flattens a spike but keeps a flat field flat`() {
        val spike = FloatArray(25) { if (it == 12) 100f else 0f }
        assertTrue(Contours.smooth(spike, 5, 5)[12] < 20f)
        val flat = FloatArray(25) { 7f }
        Contours.smooth(flat, 5, 5).forEach { assertEquals(7f, it, 1e-4f) }
    }

    @Test
    fun `the step is the first map-like one that gives at most ten lines`() {
        assertEquals(5f, Contours.niceStep(40f), 0f)
        assertEquals(10f, Contours.niceStep(90f), 0f)
        assertEquals(20f, Contours.niceStep(117f), 0f)
        assertEquals(50f, Contours.niceStep(424f), 0f)
        assertEquals(500f, Contours.niceStep(9000f), 0f)
    }
}
