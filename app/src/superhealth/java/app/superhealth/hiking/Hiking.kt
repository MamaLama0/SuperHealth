package app.superhealth.hiking

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import app.superhealth.modules.Modules
import app.superhealth.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

object Hiking {
    @Serializable data class Catalog(val routes: List<HikeRoute> = emptyList())
    @Serializable data class Completed(val route: String, val time: Long, val meters: Double, val seconds: Long, val ascent: Double? = null, val end: Long? = null, val name: String = "")
    @Serializable data class Library(val routes: List<HikeRoute> = emptyList(), val favorites: Set<String> = emptySet(), val completed: List<Completed> = emptyList())
    @Serializable data class Session(val route: HikeRoute? = null, val started: Long, val points: List<HikePoint> = emptyList(),
        val paused: Boolean = false, val seconds: Long = 0, val resumed: Long = started, val segment: Int = 0)
    val searchResults = MutableStateFlow<List<HikeRoute>?>(null)
    val searchPlace = MutableStateFlow("")
    val library = MutableStateFlow(Library())
    val session = MutableStateFlow<Session?>(null)
    val location = MutableStateFlow<HikePoint?>(null)
    val gpsError = MutableStateFlow<String?>(null)
    val tracking = MutableStateFlow(false)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var catalog: Catalog? = null
    private val http by lazy { OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).callTimeout(65, TimeUnit.SECONDS).build() }
    private fun file(ctx: Context, name: String) = AtomicFile(File(ctx.filesDir, "hiking/$name.json").apply { parentFile?.mkdirs() })
    private fun write(ctx: Context, name: String, text: String) {
        val f = file(ctx, name); val out = f.startWrite()
        try { out.write(text.toByteArray()); f.finishWrite(out) } catch (e: Exception) { f.failWrite(out); throw e }
    }
    fun load(ctx: Context) {
        library.value = runCatching { file(ctx, "library").openRead().bufferedReader().use { json.decodeFromString(Library.serializer(), it.readText()) } }.getOrDefault(Library())
        session.value = runCatching { file(ctx, "session").openRead().bufferedReader().use { json.decodeFromString(Session.serializer(), it.readText()) } }.getOrNull()
        invalidate()
        session.value = session.value?.let { s -> s.copy(route = s.route?.let { route(it.id) ?: it }) }
    }
    fun invalidate() { catalog = null }
    fun catalog(): List<HikeRoute> {
        if (catalog == null) catalog = runCatching { Modules.file(Modules.HIKING, "hiking.json")?.readText()?.let { json.decodeFromString(Catalog.serializer(), it) } }.getOrNull() ?: Catalog()
        return catalog!!.routes.filter(::validRoute)
    }
    private fun validRoute(r: HikeRoute) = r.id.matches(Regex("[a-zA-Z0-9_-]{1,80}")) && r.name.isNotBlank() && r.name.length <= 200 &&
        r.points.size in 2..100000 && r.points.all(HikeGeometry::valid) && r.features.size <= 1000 && r.features.sumOf { it.points.size } <= 200000 && r.features.all { f -> f.points.all(HikeGeometry::valid) }
    fun routes() = (library.value.routes + catalog()).distinctBy { it.id }
    fun route(id: String) = routes().firstOrNull { it.id == id }
    @Synchronized private fun update(ctx: Context, change: (Library) -> Library) {
        val next = change(library.value); write(ctx, "library", json.encodeToString(Library.serializer(), next)); library.value = next
    }
    fun favorite(ctx: Context, id: String) = update(ctx) { it.copy(favorites = if (id in it.favorites) it.favorites-id else it.favorites+id) }
    fun save(ctx: Context, route: HikeRoute) {
        require(validRoute(route)) { tr("Parcours non valable") }
        require(library.value.routes.size < 200 || library.value.routes.any { it.id == route.id }) { tr("Bibliothèque pleine") }
        update(ctx) { it.copy(routes = listOf(route) + it.routes.filterNot { r -> r.id == route.id }) }
    }
    fun remove(ctx: Context, id: String) = update(ctx) { it.copy(routes = it.routes.filterNot { r -> r.id == id }, favorites = it.favorites-id) }
    suspend fun import(ctx: Context, uri: Uri): HikeRoute = withContext(Dispatchers.IO) {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytesCapped(8*1024*1024) } ?: error(tr("Fichier indisponible"))
        val gpx = HikeGeometry.parseGpx(bytes.toString(Charsets.UTF_8))
        val route = HikeRoute(UUID.randomUUID().toString(), gpx.name.ifBlank { tr("Parcours importé") }, tr("Mes parcours"), points = gpx.points,
            loop = HikeGeometry.distance(gpx.points.first(),gpx.points.last()) < 80)
        save(ctx, route); route
    }
    private fun java.io.InputStream.readBytesCapped(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = read(buffer); if (n < 0) break; check(out.size()+n <= max) { tr("Fichier trop volumineux") }; out.write(buffer,0,n) }
        return out.toByteArray()
    }
    private fun fetch(url: String): JsonElement = http.newCall(Request.Builder().url(url).header("User-Agent", "SuperHealth/3.1 (https://github.com/MamaLama0/SuperHealth)").build()).execute().use { r ->
        check(r.isSuccessful) { "HTTP ${r.code}" }; json.parseToJsonElement(r.body.byteStream().use { it.readBytesCapped(16*1024*1024).toString(Charsets.UTF_8) })
    }
    private fun overpass(query: String) = fetch("https://overpass-api.de/api/interpreter".toHttpUrl().newBuilder().addQueryParameter("data", query).build().toString())
    /** A deliberate place search: no autocomplete, no automatic background geocoding. */
    suspend fun search(place: String): List<HikeRoute> = withContext(Dispatchers.IO) {
        require(place.trim().length in 2..160)
        val geo = fetch("https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder().addQueryParameter("q",place.trim())
            .addQueryParameter("format","jsonv2").addQueryParameter("limit","1").build().toString()).jsonArray.firstOrNull()?.jsonObject
            ?: error(tr("Lieu introuvable"))
        val lat = geo.getValue("lat").jsonPrimitive.double; val lon = geo.getValue("lon").jsonPrimitive.double
        // A bounded box uses the OSM spatial index; relation 'around' queries are much more expensive.
        val deltaLon = .06 / kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(.05)
        val box = "${(lat-.06).coerceAtLeast(-85.0)},${(lon-deltaLon).coerceAtLeast(-180.0)},${(lat+.06).coerceAtMost(85.0)},${(lon+deltaLon).coerceAtMost(180.0)}"
        val data = overpass("[out:json][timeout:25];rel[route=hiking][name][network~\"lwn|rwn\"]($box);out geom 12;")
        check(data.jsonObject["remark"] == null) { tr("Service cartographique occupé. Réessayez.") }
        data.jsonObject["elements"]?.jsonArray.orEmpty().mapNotNull { el ->
            runCatching {
                val obj = el.jsonObject; val tags = obj["tags"]!!.jsonObject
                val parts = obj["members"]!!.jsonArray.mapNotNull { m ->
                    val member = m.jsonObject
                    if (member["type"]?.jsonPrimitive?.content != "way" || member["role"]?.jsonPrimitive?.content in setOf("alternative", "excursion")) null
                    else member["geometry"]?.jsonArray?.map { p -> HikePoint(p.jsonObject.getValue("lat").jsonPrimitive.double, p.jsonObject.getValue("lon").jsonPrimitive.double) }?.takeIf { it.size >= 2 }
                }.toMutableList()
                val points = stitch(parts) ?: return@runCatching null
                val id = obj.getValue("id").jsonPrimitive.content
                HikeRoute("osm-$id", tags.getValue("name").jsonPrimitive.content, place.trim(), source = "https://www.openstreetmap.org/relation/$id",
                    points = points, ascent = tags["ascent"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                    loop = tags["roundtrip"]?.jsonPrimitive?.content == "yes" || HikeGeometry.distance(points.first(),points.last()) < 80)
                    .takeIf { validRoute(it) && HikeGeometry.length(points) in 500.0..60000.0 }
            }.getOrNull()
        }.distinctBy { it.id }.sortedBy { HikeGeometry.length(it.points) }
    }
    internal fun stitch(parts: MutableList<List<HikePoint>>): List<HikePoint>? {
        if (parts.isEmpty()) return null
        var line = parts.removeAt(0)
        while (parts.isNotEmpty()) {
            data class Join(val i: Int, val front: Boolean, val reverse: Boolean, val distance: Double)
            val best = parts.flatMapIndexed { i,p -> listOf(
                Join(i,false,false,HikeGeometry.distance(line.last(),p.first())), Join(i,false,true,HikeGeometry.distance(line.last(),p.last())),
                Join(i,true,false,HikeGeometry.distance(line.first(),p.last())), Join(i,true,true,HikeGeometry.distance(line.first(),p.first()))) }.minBy { it.distance }
            if (best.distance > 15) return null // Incomplete route: do not invent a connection.
            val p = parts.removeAt(best.i).let { if (best.reverse) it.reversed() else it }
            line = if (best.front) p+line else line+p
            if (line.size > 100000) return null
        }
        return line.distinctUntilChanged()
    }
    private fun <T> List<T>.distinctUntilChanged() = filterIndexed { i, v -> i == 0 || v != this[i-1] }
    suspend fun downloadMap(ctx: Context, route: HikeRoute): HikeRoute = withContext(Dispatchers.IO) {
        val pts = route.points; val south = pts.minOf { it.lat }-.006; val north = pts.maxOf { it.lat }+.006
        val west = pts.minOf { it.lon }-.006; val east = pts.maxOf { it.lon }+.006
        require(HikeGeometry.distance(HikePoint(south,west),HikePoint(north,east)) < 35000) { tr("Zone trop grande : importez un parcours plus court pour sa carte.") }
        val box = "$south,$west,$north,$east"
        val data = overpass("[out:json][timeout:40];(way[highway~\"path|footway|track|steps|residential|tertiary|secondary\"]($box);way[natural~\"water|wood\"]($box);way[landuse=forest]($box);way[waterway]($box););out geom 500;")
        check(data.jsonObject["remark"] == null) { tr("Service cartographique occupé. Réessayez.") }
        val features = data.jsonObject["elements"]?.jsonArray.orEmpty().mapNotNull { el ->
            val obj = el.jsonObject; val tags = obj["tags"]?.jsonObject.orEmpty()
            val points = obj["geometry"]?.jsonArray?.map { p -> HikePoint(p.jsonObject.getValue("lat").jsonPrimitive.double, p.jsonObject.getValue("lon").jsonPrimitive.double) } ?: return@mapNotNull null
            val kind = when { tags["natural"]?.jsonPrimitive?.content == "water" || "waterway" in tags -> "water"
                tags["natural"]?.jsonPrimitive?.content == "wood" || tags["landuse"]?.jsonPrimitive?.content == "forest" -> "forest"
                tags["highway"]?.jsonPrimitive?.content in setOf("path","footway","steps","track") -> "path"; else -> "road" }
            MapFeature(kind,points)
        }
        require(features.isNotEmpty()) { tr("Aucune carte disponible pour cette zone") }
        route.copy(features = features).also { save(ctx,it) }
    }
    @Synchronized fun setSession(ctx: Context, next: Session?) {
        if(next?.started != session.value?.started) location.value = null
        if(next == null) gpsError.value = null
        if (next == null) file(ctx,"session").delete() else write(ctx,"session",json.encodeToString(Session.serializer(),next.copy(route = next.route?.copy(features = emptyList()))))
        session.value = next
    }
    fun elapsed(s: Session, now: Long = System.currentTimeMillis()) = s.seconds + if (s.paused || !tracking.value) 0 else ((now-s.resumed)/1000).coerceAtLeast(0)
    fun finish(ctx: Context): HikeRoute? {
        val s = session.value ?: return null
        if (s.points.size < 2) { setSession(ctx,null); return null }
        val recorded = HikeRoute("recording-${s.started}", s.route?.name ?: tr("Ma randonnée"), tr("Mes sorties"), points = s.points)
        save(ctx, recorded)
        update(ctx) { it.copy(completed = (listOf(Completed(recorded.id,s.started,HikeGeometry.length(s.points),elapsed(s),HikeGeometry.ascent(s.points),System.currentTimeMillis(),recorded.name))+it.completed.filterNot { old -> old.route==recorded.id }).take(5000)) }
        setSession(ctx,null); return recorded
    }
}
