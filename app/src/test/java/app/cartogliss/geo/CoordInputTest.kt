package app.cartogliss.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordInputTest {
    private fun lat(s: String) = CoordInput.parse(s, isLat = true)
    private fun lon(s: String) = CoordInput.parse(s, isLat = false)

    @Test
    fun decimalDegrees() {
        assertEquals(45.1234, lat("45.1234")!!, 1e-12)
        assertEquals(-45.1234, lat("-45.1234")!!, 1e-12)
        assertEquals(-45.5, lat("S45.5")!!, 1e-12)
        assertEquals(-6.5, lon("6,5 W")!!, 1e-12)
        assertEquals(6.5, lon("E 6.5°")!!, 1e-12)
    }

    @Test
    fun degreesDecimalMinutes() {
        assertEquals(45 + 7.408 / 60, lat("N 45°07.408'")!!, 1e-12)
        assertEquals(-(6 + 30.0 / 60), lon("W6 30")!!, 1e-12)
    }

    @Test
    fun degreesMinutesSeconds() {
        assertEquals(45 + 7 / 60.0 + 24.4 / 3600, lat("45°7'24.4\"N")!!, 1e-12)
        assertEquals(45 + 7 / 60.0 + 24.4 / 3600, lat("45 7 24.4 N")!!, 1e-12)
        assertEquals(45 + 7 / 60.0 + 24.4 / 3600, lat("45°7′24.4″")!!, 1e-12)
    }

    @Test
    fun rejectsInvalid() {
        assertNull(lat(""))
        assertNull(lat("91"))
        assertNull(lon("181"))
        assertNull(lat("45 61"))
        assertNull(lat("45.5 30"))      // decimals only on the last component
        assertNull(lat("E 45"))         // wrong hemisphere letter for a latitude
        assertNull(lat("S -45"))        // two signs
        assertNull(lat("45 7 24 1"))
        assertNull(lat("abc"))
    }

    @Test
    fun spaceBecomesNextSeparator() {
        fun type(old: String, ch: Char = ' '): String {
            val new = old + ch
            return CoordInput.applySpace(old, new, new.length).first
        }
        assertEquals("45°", type("45"))
        assertEquals("45°07'", type("45°07"))
        assertEquals("45°07'24.4\"", type("45°07'24.4"))
        assertEquals("N ", type("N"))           // after a letter, a space stays a space
        assertEquals("45°07'24\" ", type("45°07'24\"")) // after a separator too
        assertEquals("45°7'24\"5 ", type("45°7'24\"5")) // all separators used: plain space
    }

    @Test
    fun spaceInTheMiddle() {
        val (t, c) = CoordInput.applySpace("4530", "45 30", 3)
        assertEquals("45°30", t)
        assertEquals(3, c)
    }
}
