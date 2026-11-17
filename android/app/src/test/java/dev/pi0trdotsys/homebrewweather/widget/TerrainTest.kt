package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainTest {

    @Test
    fun `a grid is the sample points north to south, west to east`() {
        val pts = Terrain.samplePoints(50.0, 20.0)
        assertEquals(Terrain.COLS * Terrain.ROWS, pts.size)
        assertTrue("first row is the northern edge", pts.first().first > pts.last().first)
        assertTrue("first column is the western edge", pts[0].second < pts[Terrain.COLS - 1].second)
        assertEquals("a row shares one latitude", pts[0].first, pts[Terrain.COLS - 1].first, 1e-9)
    }

    @Test
    fun `the area is centred on the place and 14 km wide`() {
        val pts = Terrain.samplePoints(50.0, 20.0)
        val lons = pts.map { it.second }
        assertEquals(20.0, (lons.min() + lons.max()) / 2, 1e-6)
        val widthKm = (lons.max() - lons.min()) * 111.0 * Math.cos(Math.toRadians(50.0))
        assertEquals(Terrain.SPAN_KM_X, widthKm, 0.1)
        val lats = pts.map { it.first }
        assertEquals(50.0, (lats.min() + lats.max()) / 2, 1e-6)
    }

    @Test
    fun `near the pole the longitude span stays finite`() {
        assertTrue(Terrain.samplePoints(89.99, 10.0).all { it.second.isFinite() })
    }

    @Test
    fun `a place and its neighbours share a cache cell, a far one does not`() {
        assertEquals(Terrain.cellKey(51.10286, 17.03006), Terrain.cellKey(51.1049, 17.0301))
        assertTrue(Terrain.cellKey(51.1, 17.0) != Terrain.cellKey(51.3, 17.0))
    }

    @Test
    fun `elevations are read from the response`() {
        assertEquals(listOf(290.0, 234.0, 302.0), Terrain.parseElevations("""{"elevation":[290.0, 234.0, 302.0]}"""))
        assertNull(Terrain.parseElevations("""{"error":true,"reason":"Minutely API request limit exceeded."}"""))
        assertNull(Terrain.parseElevations("not json"))
    }

    @Test
    fun `relief is the highest minus the lowest`() {
        val g = Terrain.Grid(2, 2, intArrayOf(10, 130, -5, 40))
        assertEquals(135, g.relief)
        assertEquals(130, g[0, 1])
        assertEquals(-5, g[1, 0])
    }

    @Test
    fun `a grid survives being stored`() {
        val g = Terrain.Grid(3, 2, intArrayOf(1, 2, 3, 4, 5, 6))
        val back = Terrain.fromJson(Terrain.toJson(g))
        assertNotNull(back)
        assertEquals(3, back!!.cols)
        assertEquals(2, back.rows)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), back.elevation.toList())
        assertNull(Terrain.fromJson(null))
        assertNull(Terrain.fromJson("""{"c":2,"r":2,"e":[1]}"""))
    }
}
