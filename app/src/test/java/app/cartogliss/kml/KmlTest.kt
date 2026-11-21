package app.cartogliss.kml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class KmlTest {
    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
          <Document>
            <name>Trip</name>
            <Style id="red"><LineStyle><color>ff0000ff</color><width>4</width></LineStyle></Style>
            <Style id="area"><PolyStyle><color>7f00ff00</color><outline>0</outline></PolyStyle></Style>
            <StyleMap id="redMap"><Pair><key>normal</key><styleUrl>#red</styleUrl></Pair><Pair><key>highlight</key><styleUrl>#area</styleUrl></Pair></StyleMap>
            <Folder>
              <name>Huts</name>
              <Placemark><name>Hut A</name><Point><coordinates>6.5,45.25,2100</coordinates></Point></Placemark>
              <Folder>
                <Placemark><name> Hut B </name><description>water</description><Point><coordinates> 7.0,46.0 </coordinates></Point></Placemark>
              </Folder>
            </Folder>
            <Placemark><name>Route</name><styleUrl>#redMap</styleUrl>
              <LineString><coordinates>6.5,45.25 6.6,45.3
                6.7,45.35</coordinates></LineString></Placemark>
            <Placemark><name>Zone</name><styleUrl>#area</styleUrl>
              <Polygon><outerBoundaryIs><LinearRing><coordinates>6,45 7,45 7,46 6,45</coordinates></LinearRing></outerBoundaryIs>
              <innerBoundaryIs><LinearRing><coordinates>6.2,45.2 6.4,45.2 6.4,45.4 6.2,45.2</coordinates></LinearRing></innerBoundaryIs></Polygon></Placemark>
            <Placemark><name>Multi</name><MultiGeometry><Point><coordinates>1,2</coordinates></Point><LineString><coordinates>1,2 3,4</coordinates></LineString></MultiGeometry></Placemark>
            <Placemark><name>Broken</name><Point><coordinates>oops</coordinates></Point></Placemark>
            <ExtendedData><Data name="keep"><value>me</value></Data></ExtendedData>
          </Document>
        </kml>
    """.trimIndent()

    private fun parse(s: String = sample) = KmlParser.parse(s.byteInputStream())

    @Test
    fun parsesSubset_flatteningFolders() {
        val d = parse()
        assertEquals("Trip", d.name)
        assertEquals(listOf("Hut A", "Hut B", "Route", "Zone", "Multi"), d.placemarks.map { it.name })
        assertEquals(LatLon(45.25, 6.5), (d.placemarks[0].geometries[0] as KmlGeometry.Point).at)
        assertEquals("water", d.placemarks[1].description)
        assertEquals(3, (d.placemarks[2].geometries[0] as KmlGeometry.Line).points.size)
        val poly = d.placemarks[3].geometries[0] as KmlGeometry.Polygon
        assertEquals(4, poly.outer.size)
        assertEquals(1, poly.holes.size)
        assertEquals(2, d.placemarks[4].geometries.size)
        assertEquals(3, d.points.size)
    }

    @Test
    fun styles_andStyleMaps_andColourConversion() {
        val d = parse()
        val route = d.placemarks.first { it.name == "Route" }.style
        assertEquals(0xFFFF0000.toInt(), route.lineColor) // ff0000ff (aabbggrr) = opaque red
        assertEquals(4f, route.lineWidth)
        val zone = d.placemarks.first { it.name == "Zone" }.style
        assertEquals(0x7F00FF00, zone.polyColor)
        assertEquals(false, zone.outline)
        assertNull(KmlParser.kmlColor("zz"))
    }

    @Test
    fun noNamespace_alsoWorks() {
        val d = parse("<kml><Placemark><name>X</name><Point><coordinates>1,2</coordinates></Point></Placemark></kml>")
        assertEquals(1, d.points.size)
    }

    @Test
    fun addPlacemark_keepsEverythingElse() {
        val doc = Xml.builder().parse(sample.byteInputStream())
        KmlWriter.addPlacemark(doc, "New spot", 45.123456789, -6.5)
        val out = ByteArrayOutputStream().also { KmlWriter.write(doc, it) }.toString("UTF-8")
        assertTrue(out.contains("ExtendedData"))
        assertTrue(out.contains("<value>me</value>"))
        assertTrue(out.contains("xmlns:gx"))
        val reparsed = KmlParser.parse(out.byteInputStream())
        assertEquals(6, reparsed.placemarks.size)
        val p = reparsed.placemarks.last()
        assertEquals("New spot", p.name)
        assertEquals(LatLon(45.1234568, -6.5), (p.geometries[0] as KmlGeometry.Point).at)
    }

    @Test
    fun repeatedEdits_doNotGrowWhitespace() {
        var bytes = sample.toByteArray()
        val sizes = (1..3).map { i ->
            val doc = Xml.builder().parse(bytes.inputStream())
            KmlWriter.addPlacemark(doc, "P$i", 1.0, 2.0)
            bytes = ByteArrayOutputStream().also { KmlWriter.write(doc, it) }.toByteArray()
            bytes.size
        }
        // Each edit adds the same amount: one placemark, no extra blank lines.
        assertEquals(sizes[1] - sizes[0], sizes[2] - sizes[1])
    }

    @Test
    fun newDocument_roundTrip() {
        val doc = KmlWriter.newDocument("My waypoints")
        KmlWriter.addPlacemark(doc, "A", 1.0, 2.0)
        val out = ByteArrayOutputStream().also { KmlWriter.write(doc, it) }.toByteArray()
        val d = KmlParser.parse(out.inputStream())
        assertEquals("My waypoints", d.name)
        assertEquals(LatLon(1.0, 2.0), d.points.single().second)
    }

    @Test
    fun kmz_prefersDocKml() {
        val zipped = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { z ->
                z.putNextEntry(ZipEntry("files/other.kml")); z.write("<kml/>".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("doc.kml")); z.write(sample.toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        assertTrue(Kmz.isZip(zipped))
        val kml = Kmz.extractKml(zipped)!!
        assertEquals("Trip", KmlParser.parse(kml.inputStream()).name)
    }

    private fun bytesOf(doc: org.w3c.dom.Document) = ByteArrayOutputStream().also { KmlWriter.write(doc, it) }.toByteArray()

    @Test
    fun removePlacemark_byIndex_keepsEverythingElse() {
        val parsed = parse()
        val route = parsed.placemarks.first { it.name == "Route" }
        val doc = Xml.builder().parse(sample.byteInputStream())
        assertTrue(KmlWriter.removePlacemark(doc, route.index, "Route"))
        val out = bytesOf(doc).toString(Charsets.UTF_8)
        assertTrue(out.contains("ExtendedData"))
        val after = KmlParser.parse(out.byteInputStream())
        assertEquals(parsed.placemarks.size - 1, after.placemarks.size)
        assertTrue(after.placemarks.none { it.name == "Route" })
        // Indices follow document order in the new file.
        val idx = after.placemarks.map { it.index }
        assertEquals(idx.sorted().distinct(), idx)
    }

    @Test
    fun removePlacemark_refusesWhenTheFileChanged() {
        val doc = Xml.builder().parse(sample.byteInputStream())
        val hutA = parse().placemarks.first { it.name == "Hut A" }
        assertEquals(false, KmlWriter.removePlacemark(doc, hutA.index, "Someone else"))
        assertEquals(false, KmlWriter.removePlacemark(doc, 99, "Hut A"))
        assertEquals(parse().placemarks.size, KmlParser.parse(bytesOf(doc).inputStream()).placemarks.size)
    }

    @Test
    fun addThenRemove_leavesTheFileAsItWas() {
        val doc0 = Xml.builder().parse(sample.byteInputStream())
        KmlWriter.addPlacemark(doc0, "seed", 1.0, 2.0) // a file that was edited once by the app
        val before = bytesOf(doc0)
        repeat(3) {
            val doc = Xml.builder().parse(bytesOf(Xml.builder().parse(before.inputStream())).inputStream())
            KmlWriter.addPlacemark(doc, "temp", 3.0, 4.0)
            val idx = KmlParser.parse(doc).placemarks.first { it.name == "temp" }.index
            assertTrue(KmlWriter.removePlacemark(doc, idx, "temp"))
            assertEquals(before.toString(Charsets.UTF_8), bytesOf(doc).toString(Charsets.UTF_8))
        }
    }
}
