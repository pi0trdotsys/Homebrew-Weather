package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAppearanceTest {

    @Test
    fun `dark is never paper, paper always is`() {
        for (day in listOf(true, false, null)) {
            assertFalse(WidgetAppearance.DARK.isPaper(day))
            assertTrue(WidgetAppearance.PAPER.isPaper(day))
        }
    }

    @Test
    fun `auto is paper by day and dark otherwise, dark while unknown`() {
        assertTrue(WidgetAppearance.AUTO.isPaper(true))
        assertFalse(WidgetAppearance.AUTO.isPaper(false))
        assertFalse(WidgetAppearance.AUTO.isPaper(null))
    }

    @Test
    fun `storage ids round trip and an unknown one is dark`() {
        for (a in WidgetAppearance.entries) assertEquals(a, WidgetAppearance.fromStorageId(a.storageId))
        assertEquals(WidgetAppearance.DARK, WidgetAppearance.fromStorageId(99))
        assertEquals(WidgetAppearance.DARK, WidgetAppearance.DEFAULT)
    }

    @Test
    fun `every widget colour can be made to read on paper`() {
        val inks = listOf(
            0xFF33FF66, 0xFF55FFFF, 0xFFFFB000, 0xFFFF5555, 0xFFDFEFFF, 0xFF7A9BFF, 0xFFFF7A1A, 0xFFA64DFF,
        ).map { it.toInt() }
        for (c in inks) {
            val ink = HighContrast.darkenToContrast(c, Palette.PAPER_BG, 4.5)
            assertTrue("%08x -> %08x".format(c, ink), HighContrast.contrast(ink, Palette.PAPER_BG) >= 4.5)
        }
    }

    @Test
    fun `a colour that already reads is left alone`() {
        val ink = 0xFF1B261B.toInt()
        assertEquals(ink, HighContrast.darkenToContrast(ink, Palette.PAPER_BG, 4.5))
    }

    @Test
    fun `darkening keeps the hue's dominant channel`() {
        val cyan = HighContrast.darkenToContrast(0xFF55FFFF.toInt(), Palette.PAPER_BG, 4.5)
        val r = (cyan shr 16) and 0xFF
        val g = (cyan shr 8) and 0xFF
        assertTrue("cyan stays greenish-blue", g > r)
    }

    @Test
    fun `large type needs less darkening than small`() {
        val big = HighContrast.darkenToContrast(0xFFFFB000.toInt(), Palette.PAPER_BG, 3.0)
        val small = HighContrast.darkenToContrast(0xFFFFB000.toInt(), Palette.PAPER_BG, 4.5)
        assertTrue(HighContrast.contrast(big, Palette.PAPER_BG) >= 3.0)
        assertTrue(((big shr 8) and 0xFF) >= ((small shr 8) and 0xFF))
    }
}
