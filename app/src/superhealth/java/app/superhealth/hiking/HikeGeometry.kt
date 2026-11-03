package app.superhealth.hiking

import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import kotlin.math.*
import kotlinx.serialization.Serializable

@Serializable data class HikePoint(val lat: Double, val lon: Double, val ele: Double? = null, val time: Long? = null, val segment: Int = 0)
@Serializable data class MapFeature(val kind: String, val points: List<HikePoint>)
@Serializable data class HikeRoute(val id: String, val name: String, val region: String = "", val description: String = "",
    val source: String = "", val points: List<HikePoint>, val features: List<MapFeature> = emptyList(),
    val ascent: Double? = null, val loop: Boolean = false)

object HikeGeometry {
    fun valid(p: HikePoint) = p.lat.isFinite() && p.lon.isFinite() && p.lat in -85.0..85.0 && p.lon in -180.0..180.0 &&
        (p.ele == null || (p.ele.isFinite() && p.ele in -500.0..10000.0))
    fun distance(a: HikePoint, b: HikePoint): Double {
        val lat = Math.toRadians(b.lat - a.lat); val lon = Math.toRadians(b.lon - a.lon)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(lon / 2).pow(2)
        return 6371000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    fun length(points: List<HikePoint>) = points.zipWithNext().sumOf { (a, b) -> if (a.segment == b.segment) distance(a, b) else 0.0 }
    fun ascent(points: List<HikePoint>): Double? {
        if (points.count { it.ele != null } < 2) return null
        var gain = 0.0; var anchor = points.first()
        points.drop(1).forEach { p ->
            if (anchor.segment != p.segment || anchor.ele == null || p.ele == null) anchor = p
            else if (abs(p.ele - anchor.ele!!) >= 3) { gain += max(0.0, p.ele - anchor.ele!!); anchor = p }
        }
        return gain
    }
    data class Position(val offset: Double, val progress: Double, val remaining: Double)
    /** Project onto actual segments, never across GPX gaps. */
    fun position(points: List<HikePoint>, location: HikePoint): Position? {
        if (points.size < 2) return null
        var best = Double.MAX_VALUE; var progress = 0.0; var travelled = 0.0
        val scale = cos(Math.toRadians(location.lat)).coerceAtLeast(.01)
        points.zipWithNext().forEach { (a, b) ->
            if (a.segment != b.segment) return@forEach
            val x = (b.lon - a.lon) * scale; val y = b.lat - a.lat
            val t = if (x*x+y*y < 1e-18) 0.0 else (((location.lon-a.lon)*scale*x + (location.lat-a.lat)*y)/(x*x+y*y)).coerceIn(0.0,1.0)
            val projected = HikePoint(a.lat + t*(b.lat-a.lat), a.lon+t*(b.lon-a.lon))
            val offset = distance(location, projected); val d = distance(a, b)
            if (offset < best) { best = offset; progress = travelled + t*d }
            travelled += d
        }
        return if (best == Double.MAX_VALUE) null else Position(best, progress, (travelled-progress).coerceAtLeast(0.0))
    }
    fun estimatedMinutes(route: HikeRoute) = (length(route.points)/4000*60 + (route.ascent ?: ascent(route.points) ?: 0.0)/600*60).roundToInt().coerceAtLeast(1)
    data class Gpx(val name: String, val points: List<HikePoint>)
    fun parseGpx(text: String): Gpx {
        require(text.length <= 8 * 1024 * 1024) { "Fichier trop volumineux" }
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(text)) { "GPX non valable" }
        val points = mutableListOf<HikePoint>(); var name = ""; var segment = 0
        var point: HikePoint? = null; var capture = ""; var content = StringBuilder(); var isGpx = false
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities").forEach {
            runCatching { factory.setFeature(it, false) }
        }
        val parser = factory.newSAXParser().xmlReader
        parser.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw IllegalArgumentException("GPX non valable") }
        parser.contentHandler = object : DefaultHandler() {
            override fun startElement(uri: String?, local: String, qName: String?, a: Attributes) {
                when(local) {
                    "gpx" -> isGpx = true
                    "trkseg", "rte" -> segment++
                    "trkpt", "rtept" -> {
                        require(points.size < 100000) { "Trop de points GPS" }
                        point = HikePoint(a.getValue("lat").toDouble(), a.getValue("lon").toDouble(), segment = segment)
                    }
                    "name", "ele", "time" -> { capture = local; content = StringBuilder() }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (capture.isNotEmpty()) content.append(ch, start, length) }
            override fun endElement(uri: String?, local: String, qName: String?) {
                if (local == capture) {
                    val value = content.toString().trim()
                    when(local) {
                        "name" -> if (name.isEmpty() && point == null) name = value.take(160)
                        "ele" -> point = point?.copy(ele = value.toDoubleOrNull())
                        "time" -> point = point?.copy(time = runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull())
                    }
                    capture = ""
                }
                if (local == "trkpt" || local == "rtept") { point?.let { require(valid(it)) { "Coordonnées GPS non valables" }; points += it }; point = null }
            }
        }
        parser.parse(InputSource(StringReader(text)))
        require(isGpx && points.size >= 2 && length(points) > 1) { "Ce fichier ne contient pas de parcours GPX" }
        return Gpx(name, points)
    }
    fun exportGpx(route: HikeRoute): String {
        fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"SuperHealth\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>${escape(route.name)}</name>")
            var segment: Int? = null
            route.points.forEach { p ->
                if (segment != p.segment) { if (segment != null) append("</trkseg>"); append("<trkseg>"); segment = p.segment }
                append("<trkpt lat=\"${p.lat}\" lon=\"${p.lon}\">")
                p.ele?.let { append("<ele>$it</ele>") }; p.time?.let { append("<time>${java.time.Instant.ofEpochMilli(it)}</time>") }
                append("</trkpt>")
            }
            if (segment != null) append("</trkseg>")
            append("</trk></gpx>")
        }
    }
}
