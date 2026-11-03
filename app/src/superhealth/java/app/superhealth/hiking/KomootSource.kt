package app.superhealth.hiking

import app.superhealth.nutrition.data.Net
import app.superhealth.ui.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Public discovery contracts recovered from the supplied Komoot 2026.44.2 APK.
 * No account, application key or copied authentication credentials are required.
 */
internal object KomootSource {
    private const val BASE = "https://api.komoot.de/v007/"
    private const val MAX_POINTS = 100000
    // Discovery returns both numeric smart-tour ids and editorial ids such as e2579390572.
    private val tourId = Regex("e?[0-9]{1,20}")
    private val localTourId = Regex("komoot-(${tourId.pattern})(?:-[a-f0-9]{12})?")
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    }

    suspend fun search(lat: Double, lon: Double, place: String): List<HikeRoute> = withContext(Dispatchers.IO) {
        require(HikeGeometry.valid(HikePoint(lat, lon)))
        val url = (BASE + "discover_tours/from_location/").toHttpUrl().newBuilder()
            .addQueryParameter("lat", lat.toString()).addQueryParameter("lng", lon.toString())
            .addQueryParameter("sport", "hike").addQueryParameter("format", "v2")
            .addQueryParameter("limit", "12").addQueryParameter("max_distance", "30000")
            .addQueryParameter("_embedded", "start_point,tour_line,cover_images")
            .build()
        parseSearch(fetch(url.toString()), place)
    }

    /** The generated path's query must accompany its id: the id alone describes a different canonical tour. */
    suspend fun detail(route: HikeRoute): HikeRoute = withContext(Dispatchers.IO) {
        val url = detailUrl(route) ?: return@withContext route
        parseDetail(route, fetch(url.toString()))
    }

    internal fun detailUrl(route: HikeRoute): HttpUrl? {
        val id = localTourId.matchEntire(route.id)?.groupValues?.get(1) ?: return null
        if (route.providerQuery.isBlank() || route.providerQuery.length > 2000) return null
        return (BASE + "discover_tours/$id").toHttpUrl().newBuilder()
            .addQueryParameter("query", route.providerQuery)
            .addQueryParameter("format", "v2").addQueryParameter("srid", "4326")
            .addQueryParameter("_embedded", "coordinates,directions,surfaces,way_types,cover_images,descriptions,verified_details")
            .build()
    }

    /** Enrichment must not rename a saved route, its bundled photo, favorite or generated-path reference. */
    internal fun parseDetail(route: HikeRoute, item: JsonObject): HikeRoute = parseRoute(item, route.region)?.copy(
        id = route.id, source = route.source, providerQuery = route.providerQuery, features = route.features,
    ) ?: route

    private fun fetch(url: String): JsonObject = http.newCall(Request.Builder().url(url)
        .header("User-Agent", "SuperHealth/3.1.1 (https://github.com/MamaLama0/SuperHealth)")
        .header("Accept", "application/hal+json").header("Accept-Language", Lang.locale.toLanguageTag()).build())
        .execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            Json.parseToJsonElement(response.body.byteStream().use { Net.readCapped(it, 12 * 1024 * 1024) }).jsonObject
        }

    internal fun parseSearch(root: JsonObject, place: String): List<HikeRoute> = root.obj("_embedded")?.array("items")
        .orEmpty().take(24).mapNotNull { item -> runCatching { parseRoute(item.jsonObject, place) }.getOrNull() }
        .distinctBy { it.id }.take(12)

    internal fun parseRoute(item: JsonObject, place: String): HikeRoute? {
        val id = item.text("id").takeIf { it.matches(tourId) } ?: return null
        val query = item.text("query")
        if (query.length > 2000) return null
        val name = item.text("name").trim().take(200).takeIf { it.isNotBlank() } ?: return null
        if (item.text("sport").let { it.isNotEmpty() && it != "hike" && it != "mountaineering" }) return null
        val embedded = item.obj("_embedded") ?: return null
        val geometry = embedded.obj("coordinates")?.array("items") ?: embedded.obj("tour_line")?.array("geometry") ?: return null
        if (geometry.size !in 2..MAX_POINTS) return null
        val points = geometry.map { value ->
            val p = value.jsonObject
            val lat = p.number("lat") ?: return null
            val lon = p.number("lng") ?: return null
            val altitude = p.number("alt")?.takeIf { it in -500.0..10000.0 }
            HikePoint(lat, lon, altitude).also { if (!HikeGeometry.valid(it)) return null }
        }
        val actualLength = HikeGeometry.length(points)
        if (actualLength !in 200.0..60000.0) return null
        val distance = item.number("distance")?.takeIf { it in 200.0..60000.0 }
        val photos = embedded.obj("cover_images")?.obj("_embedded")?.array("items").orEmpty()
        val photo = photos.asSequence().take(40).mapNotNull { it as? JsonObject }.firstOrNull { imageUrl(it).isNotBlank() }
        val descriptions = embedded.obj("descriptions")?.array("items").orEmpty().mapNotNull { v ->
            if (v is JsonPrimitive) v.contentOrNull else (v as? JsonObject)?.let { d -> d.text("text").ifBlank { d.text("description") } }
        }.joinToString("\n\n").replace(Regex("<[^>]*>"), "").take(12000)
        val surfaces = item.obj("summary")?.array("surfaces").orEmpty().take(32).mapNotNull { value ->
            val s = value as? JsonObject ?: return@mapNotNull null
            val key = s.text("type").removePrefix("sf#").takeIf { it.matches(Regex("[a-z_]{1,40}")) } ?: return@mapNotNull null
            val amount = s.number("amount")?.takeIf { it in 0.0..1.0 } ?: return@mapNotNull null
            key to amount
        }.toMap()
        val credit = photo?.let { p -> p.text("attribution").ifBlank { p.obj("_embedded")?.obj("creator")?.text("display_name").orEmpty() } }
            ?.take(120).orEmpty().let { if (it.isBlank()) "Komoot" else "$it · Komoot" }
        val directions = embedded.obj("directions")?.array("items").orEmpty().take(500).mapNotNull { value ->
            val d = value as? JsonObject ?: return@mapNotNull null
            val street = d.text("street_name").trim().take(160)
            val symbol = when (d.text("type")) {
                "S" -> "●"; "TS" -> "↑"; "TL" -> "←"; "TR" -> "→"; "TU" -> "↶"
                "TFL", "TSL", "TLL" -> "↖"; "TFR", "TSR", "TLR" -> "↗"; "ROUNDABOUT" -> "⟳"
                else -> ""
            }
            (symbol + if (street.isNotBlank()) " $street" else "").trim().takeIf { it.isNotBlank() }
        }
        return HikeRoute(id = localId(id, query), name = name, region = place.take(200), description = descriptions,
            source = "https://www.komoot.com/smarttour/$id", points = points,
            ascent = item.number("elevation_up")?.takeIf { it in 0.0..20000.0 },
            loop = HikeGeometry.distance(points.first(), points.last()) < 80,
            provider = "Komoot", providerQuery = query, imageUrl = photo?.let(::imageUrl).orEmpty(), imageCredit = credit,
            difficulty = item.obj("difficulty")?.text("grade")?.takeIf { it in setOf("easy", "moderate", "difficult") }.orEmpty(),
            durationMinutes = item.number("duration")?.takeIf { it in 60.0..172800.0 }?.let { (it / 60).roundToInt().coerceAtLeast(1) },
            distanceMeters = distance, descent = item.number("elevation_down")?.takeIf { it in 0.0..20000.0 },
            directions = directions, surfaces = surfaces)
    }

    /** A discovery id can describe several generated paths; favorites must identify the exact query. */
    private fun localId(id: String, query: String): String {
        val suffix = if (query.isEmpty()) "" else "-" + MessageDigest.getInstance("SHA-256")
            .digest(query.toByteArray(Charsets.UTF_8)).take(6).joinToString("") { "%02x".format(it) }
        return "komoot-$id$suffix"
    }

    private fun imageUrl(photo: JsonObject): String {
        val raw = photo.text("src").replace("{width}", "960").replace("{height}", "600").replace("{crop}", "true")
        if (raw.length > 2000 || '{' in raw || '}' in raw) return ""
        val url = runCatching { raw.toHttpUrl() }.getOrNull() ?: return ""
        return raw.takeIf { url.isHttps && url.username.isEmpty() && url.password.isEmpty() &&
            (url.host.endsWith(".cloudfront.net") || url.host.endsWith(".komoot.net") || url.host.endsWith(".komoot.com")) }.orEmpty()
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.array(key: String) = this[key] as? JsonArray
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.number(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf(Double::isFinite)
}
