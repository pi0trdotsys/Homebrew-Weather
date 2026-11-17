package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetBackdropArtTest {

    private fun gridWithRelief(metres: Int) = Terrain.Grid(2, 2, intArrayOf(100, 100, 100, 100 + metres))

    @Test
    fun `hilly land gets contours`() {
        assertEquals(WidgetBackdropArt.Kind.CONTOURS, WidgetBackdropArt.kindFor(gridWithRelief(117)))
        assertEquals(WidgetBackdropArt.Kind.CONTOURS, WidgetBackdropArt.kindFor(gridWithRelief(WidgetBackdropArt.RELIEF_MIN_M)))
    }

    @Test
    fun `flat land gets the skyline, not contours of noise`() {
        assertEquals(WidgetBackdropArt.Kind.SKYLINE, WidgetBackdropArt.kindFor(gridWithRelief(16)))
        assertEquals(WidgetBackdropArt.Kind.SKYLINE, WidgetBackdropArt.kindFor(gridWithRelief(WidgetBackdropArt.RELIEF_MIN_M - 1)))
    }

    @Test
    fun `with no grid yet it is a skyline, never an empty background`() {
        assertEquals(WidgetBackdropArt.Kind.SKYLINE, WidgetBackdropArt.kindFor(null))
    }

    @Test
    fun `the backdrop setting reads unknown values as off`() {
        assertEquals(WidgetBackdrop.NONE, WidgetBackdrop.fromStorageId(99))
        assertEquals(WidgetBackdrop.CITY, WidgetBackdrop.fromStorageId(1))
        assertEquals(WidgetBackdrop.NONE, WidgetBackdrop.DEFAULT)
    }
}
