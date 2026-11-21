package app.cartogliss.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocDirTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun dir(name: String) = FileDocDir(tmp.root.resolve(name))
    private fun DocDir.text(name: String) = read(name) { it.readBytes().decodeToString() }
    private fun DocDir.put(name: String, text: String) = write(name) { it.write(text.encodeToByteArray()) }

    @Test
    fun writeReplaceListDelete() {
        val d = dir("a")
        d.put("x.kml", "one")
        d.put("x.kml", "two")
        assertEquals("two", d.text("x.kml"))
        assertEquals(listOf("x.kml"), d.list().map { it.name })
        assertTrue(d.delete("x.kml"))
        assertFalse(d.exists("x.kml"))
    }

    @Test
    fun failedWrite_keepsOldContent() {
        val d = dir("a")
        d.put("x.gpx", "old")
        runCatching { d.write("x.gpx") { it.write("half".encodeToByteArray()); error("boom") } }
        assertEquals("old", d.text("x.gpx"))
        assertEquals(listOf("x.gpx"), d.list().map { it.name }) // no temp file left behind
    }

    @Test
    fun uniqueName_addsSuffix() {
        val d = dir("a")
        assertEquals("t.gpx", d.uniqueName("t", "gpx") { "-$it" })
        d.put("t.gpx", "")
        d.put("t-2.gpx", "")
        assertEquals("t-3.gpx", d.uniqueName("t", "gpx") { "-$it" })
    }

    @Test
    fun migration_movesAndResolvesClashes() {
        val from = dir("private")
        val to = dir("shared")
        from.put("same.kml", "A")
        to.put("same.kml", "A")
        from.put("empty.kml", "E")
        to.put("empty.kml", "full")
        from.put("clash.kml", "mine")
        to.put("clash.kml", "theirs")
        from.put("new.kml", "N")

        val renamed = Migration.move(from, to) { name, _ -> name == "empty.kml" }

        assertTrue(from.list().isEmpty())
        assertEquals(mapOf("clash.kml" to "clash (2).kml"), renamed)
        assertEquals("theirs", to.text("clash.kml"))
        assertEquals("mine", to.text("clash (2).kml"))
        assertEquals("full", to.text("empty.kml"))
        assertEquals("N", to.text("new.kml"))
        assertEquals(setOf("same.kml", "empty.kml", "clash.kml", "clash (2).kml", "new.kml"), to.list().map { it.name }.toSet())
    }
}
