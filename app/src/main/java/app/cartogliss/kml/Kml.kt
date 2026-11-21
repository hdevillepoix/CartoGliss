package app.cartogliss.kml

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class LatLon(val lat: Double, val lon: Double)

/** Colours are ARGB ints (KML stores aabbggrr; converted on parse). Nulls mean "use the app default". */
data class KmlStyle(
    val lineColor: Int? = null,
    val lineWidth: Float? = null,
    val polyColor: Int? = null,
    val fill: Boolean = true,
    val outline: Boolean = true,
    val iconColor: Int? = null,
)

sealed interface KmlGeometry {
    data class Point(val at: LatLon) : KmlGeometry
    data class Line(val points: List<LatLon>) : KmlGeometry
    data class Polygon(val outer: List<LatLon>, val holes: List<List<LatLon>>) : KmlGeometry
}

data class Placemark(
    val name: String?,
    val description: String?,
    val geometries: List<KmlGeometry>,
    val style: KmlStyle,
)

data class KmlDocument(val name: String?, val placemarks: List<Placemark>) {
    val points: List<Pair<Placemark, LatLon>>
        get() = placemarks.flatMap { p -> p.geometries.filterIsInstance<KmlGeometry.Point>().map { p to it.at } }
}

/** Reads the supported KML subset: Points, LineStrings, Polygons (MultiGeometry flattened), Style/StyleMap; folders flattened. */
object KmlParser {
    fun parse(input: InputStream): KmlDocument = parse(Xml.builder().parse(input))

    fun parse(doc: Document): KmlDocument {
        val root = doc.documentElement
        val styles = HashMap<String, KmlStyle>()
        val styleMaps = HashMap<String, String>()
        for (e in root.descendants()) {
            when (e.local) {
                "Style" -> e.getAttribute("id").takeIf { it.isNotEmpty() }?.let { styles[it] = parseStyle(e) }
                "StyleMap" -> {
                    val id = e.getAttribute("id").takeIf { it.isNotEmpty() } ?: continue
                    val normal = e.children("Pair").firstOrNull { it.child("key")?.text() == "normal" }
                        ?: e.children("Pair").firstOrNull()
                    normal?.child("styleUrl")?.text()?.let { styleMaps[id] = it.removePrefix("#") }
                }
            }
        }
        fun resolve(url: String?): KmlStyle? {
            val id = url?.substringAfter('#', url)?.takeIf { it.isNotEmpty() } ?: return null
            return styles[id] ?: styleMaps[id]?.let { styles[it] }
        }
        val placemarks = root.descendants().filter { it.local == "Placemark" }.map { pm ->
            val inline = pm.child("Style")?.let(::parseStyle)
            Placemark(
                name = pm.child("name")?.text()?.trim()?.takeIf { it.isNotEmpty() },
                description = pm.child("description")?.text()?.trim()?.takeIf { it.isNotEmpty() },
                geometries = geometries(pm),
                style = inline ?: resolve(pm.child("styleUrl")?.text()?.trim()) ?: KmlStyle(),
            )
        }.filter { it.geometries.isNotEmpty() }.toList()
        val docName = root.descendants().firstOrNull { it.local == "Document" }?.child("name")?.text()?.trim()
        return KmlDocument(docName?.takeIf { it.isNotEmpty() }, placemarks)
    }

    private fun geometries(e: Element): List<KmlGeometry> = e.childElements().flatMap { c ->
        when (c.local) {
            "Point" -> c.child("coordinates")?.let { coords(it.text()).firstOrNull() }?.let { listOf(KmlGeometry.Point(it)) } ?: emptyList()
            "LineString", "LinearRing" -> c.child("coordinates")?.let { coords(it.text()) }?.takeIf { it.size >= 2 }?.let { listOf(KmlGeometry.Line(it)) } ?: emptyList()
            "Polygon" -> {
                val outer = c.child("outerBoundaryIs")?.child("LinearRing")?.child("coordinates")?.let { coords(it.text()) }
                if (outer == null || outer.size < 3) emptyList() else {
                    val holes = c.children("innerBoundaryIs").mapNotNull { ib ->
                        ib.child("LinearRing")?.child("coordinates")?.let { coords(it.text()) }?.takeIf { it.size >= 3 }
                    }.toList()
                    listOf(KmlGeometry.Polygon(outer, holes))
                }
            }
            "MultiGeometry" -> geometries(c)
            else -> emptyList()
        }
    }.toList()

    /** "lon,lat[,alt] lon,lat[,alt] …" */
    fun coords(text: String): List<LatLon> = text.trim().split(Regex("\\s+")).mapNotNull { t ->
        val parts = t.split(',')
        if (parts.size < 2) return@mapNotNull null
        val lon = parts[0].toDoubleOrNull() ?: return@mapNotNull null
        val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) null else LatLon(lat, lon)
    }

    private fun parseStyle(s: Element): KmlStyle {
        val line = s.child("LineStyle")
        val poly = s.child("PolyStyle")
        val icon = s.child("IconStyle")
        return KmlStyle(
            lineColor = line?.child("color")?.text()?.let(::kmlColor),
            lineWidth = line?.child("width")?.text()?.trim()?.toFloatOrNull(),
            polyColor = poly?.child("color")?.text()?.let(::kmlColor),
            fill = poly?.child("fill")?.text()?.trim() != "0",
            outline = poly?.child("outline")?.text()?.trim() != "0",
            iconColor = icon?.child("color")?.text()?.let(::kmlColor),
        )
    }

    /** KML aabbggrr → ARGB. */
    fun kmlColor(text: String): Int? {
        val t = text.trim().removePrefix("#")
        if (t.length != 8) return null
        val v = t.toLongOrNull(16) ?: return null
        val a = (v shr 24) and 0xFF
        val b = (v shr 16) and 0xFF
        val g = (v shr 8) and 0xFF
        val r = v and 0xFF
        return ((a shl 24) or (r shl 16) or (g shl 8) or b).toInt()
    }
}

/** Edits KML files in place through the DOM, so everything we don't understand is kept. */
object KmlWriter {
    private const val KML_NS = "http://www.opengis.net/kml/2.2"

    fun newDocument(name: String): Document {
        val doc = Xml.builder().newDocument()
        val kml = doc.createElementNS(KML_NS, "kml")
        doc.appendChild(kml)
        val d = doc.createElementNS(KML_NS, "Document")
        kml.appendChild(d)
        d.appendChild(doc.createElementNS(KML_NS, "name").apply { textContent = name })
        return doc
    }

    /** Appends a Point Placemark to the first Document (or Folder) of [doc], creating a Document if needed. */
    fun addPlacemark(doc: Document, name: String, lat: Double, lon: Double, description: String? = null) {
        val root = doc.documentElement
        val ns: String? = root.namespaceURI
        fun el(tag: String): Element {
            val prefix = root.prefix
            return if (ns != null) doc.createElementNS(ns, if (prefix.isNullOrEmpty()) tag else "$prefix:$tag") else doc.createElement(tag)
        }
        val container = root.descendants().firstOrNull { it.local == "Document" }
            ?: root.descendants().firstOrNull { it.local == "Folder" }
            ?: el("Document").also { root.appendChild(it) }
        val pm = el("Placemark")
        pm.appendChild(el("name").apply { textContent = name })
        description?.let { pm.appendChild(el("description").apply { textContent = it }) }
        val point = el("Point")
        point.appendChild(el("coordinates").apply { textContent = "${fmt(lon)},${fmt(lat)}" })
        pm.appendChild(point)
        container.appendChild(doc.createTextNode("\n"))
        container.appendChild(pm)
        container.appendChild(doc.createTextNode("\n"))
    }

    private fun fmt(v: Double) = String.format(java.util.Locale.ROOT, "%.7f", v).trimEnd('0').trimEnd('.')

    /** [indent] only for files we create: re-indenting an edited file on every save would pile up whitespace. */
    fun write(doc: Document, out: OutputStream, indent: Boolean = false) {
        val t = TransformerFactory.newInstance().newTransformer()
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        t.setOutputProperty(OutputKeys.INDENT, if (indent) "yes" else "no")
        t.transform(DOMSource(doc), StreamResult(out))
    }

    /** Write via a temp file and rename, so a crash never leaves a half-written collection. */
    fun writeAtomically(doc: Document, file: File, indent: Boolean = false) {
        val tmp = File(file.path + ".tmp")
        tmp.outputStream().use { write(doc, it, indent) }
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Could not replace $file" }
        }
    }
}

object Kmz {
    /** True when the bytes start with the ZIP signature. */
    fun isZip(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** The main KML of a KMZ: doc.kml if present, otherwise the first .kml entry. */
    fun extractKml(bytes: ByteArray): ByteArray? {
        var first: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory || !e.name.endsWith(".kml", ignoreCase = true)) continue
                val data = zip.readBytes()
                if (e.name.substringAfterLast('/').equals("doc.kml", ignoreCase = true)) return data
                if (first == null) first = data
            }
        }
        return first
    }
}

internal object Xml {
    fun builder(): DocumentBuilder {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        runCatching { f.isExpandEntityReferences = false }
        // Imported files are untrusted: refuse DTDs / external entities where the parser supports it.
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        return f.newDocumentBuilder()
    }
}

// ---- DOM helpers ----

internal val Element.local: String get() = localName ?: nodeName.substringAfter(':')

internal fun Element.childElements(): Sequence<Element> = sequence {
    var n: Node? = firstChild
    while (n != null) {
        if (n is Element) yield(n)
        n = n.nextSibling
    }
}

internal fun Element.child(name: String): Element? = childElements().firstOrNull { it.local == name }
internal fun Element.children(name: String): Sequence<Element> = childElements().filter { it.local == name }
internal fun Element.text(): String = textContent ?: ""

internal fun Element.descendants(): Sequence<Element> = sequence {
    for (c in childElements()) {
        yield(c)
        yieldAll(c.descendants())
    }
}
