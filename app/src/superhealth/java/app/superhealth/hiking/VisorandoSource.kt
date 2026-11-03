package app.superhealth.hiking

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Public pages and the public map data loaded by those pages. No account or app credential. */
internal object VisorandoSource {
    private const val BASE = "https://www.visorando.com"
    private const val MAX_BYTES = 4 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            require(publicUrl(chain.request().url.toString()))
            chain.proceed(chain.request())
        }.build() }
    private val cache = object : LinkedHashMap<String, String>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 16
    }
    private fun publicUrl(raw: String) = runCatching { raw.toHttpUrl().let {
        it.isHttps && it.host == "www.visorando.com" && it.username.isEmpty() && it.password.isEmpty()
    } }.getOrDefault(false)
    private fun fetch(url: String, missingAllowed: Boolean = false): String? {
        require(publicUrl(url))
        synchronized(cache) { cache[url]?.let { return it } }
        return http.newCall(Request.Builder().url(url)
            .header("User-Agent", "SuperHealth/3.1.1 (https://github.com/MamaLama0/SuperHealth)").build()).execute().use { response ->
            if (missingAllowed && response.code == 404) return@use null
            if (!response.isSuccessful) throw IOException("Visorando : HTTP ${response.code}")
            val out = java.io.ByteArrayOutputStream()
            response.body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    check(out.size() + n <= MAX_BYTES) { "Visorando : réponse trop volumineuse" }
                    out.write(buffer, 0, n)
                }
            }
            out.toString(Charsets.UTF_8.name()).also { value ->
                // Bound memory as well as the number of pages. Details are saved by Hiking on demand.
                if (value.length <= 512 * 1024) synchronized(cache) { cache[url] = value }
            }
        }
    }
    suspend fun search(place: String): List<HikeRoute> = withContext(Dispatchers.IO) {
        require(place.trim().length in 2..160)
        val slug = Normalizer.normalize(place.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val listing = if (slug.isNotEmpty()) fetch("$BASE/randonnee-$slug.html", missingAllowed = true) else null
        // The generic public form may ignore a place and show unrelated editorial routes.
        // Only a matching destination page is used; worldwide proximity discovery is independent.
        val candidates = listing?.let { links(it, place) }.orEmpty()
        if (candidates.isEmpty()) return@withContext emptyList()
        val permits = Semaphore(3)
        var failure: Exception? = null
        val routes = coroutineScope { candidates.take(8).map { route -> async {
            permits.withPermit {
                try { detail(route) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { synchronized(permits) { failure = e }; null }
            }
        } }.awaitAll().filterNotNull() }
        if (routes.isEmpty() && failure != null) throw failure!!
        routes
    }
    internal fun links(html: String, place: String): List<HikeRoute> {
        val document = Jsoup.parse(html, BASE)
        val cards = document.select(".vr-card--rando a.card--link[href]")
        val anchors = if (cards.isNotEmpty()) cards else document.select("a[href]")
        return anchors.mapNotNull { anchor ->
            val url = anchor.absUrl("href").substringBefore('#')
            if (!publicUrl(url) || !url.toHttpUrl().encodedPath.matches(Regex("/randonnee-[a-z0-9-]+/"))) return@mapNotNull null
            val name = anchor.attr("title").ifBlank { anchor.selectFirst("h3")?.text() ?: anchor.text() }.trim().take(200)
            if (name.isBlank()) return@mapNotNull null
            val id = anchor.closest(".vr-card--rando")?.selectFirst("[id^=blockRando_]")?.id()?.removePrefix("blockRando_")
                ?: java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
            HikeRoute("visorando-$id", name, place.trim(), source = url, points = emptyList(), provider = "Visorando")
        }.distinctBy { it.source }.take(12)
    }
    suspend fun detail(route: HikeRoute): HikeRoute = withContext(Dispatchers.IO) {
        require(publicUrl(route.source) && route.source.toHttpUrl().encodedPath.matches(Regex("/randonnee-[a-z0-9-]+/")))
        val page = fetch(route.source) ?: error("Visorando : fiche indisponible")
        val id = Regex("\\bidRando\\s*=\\s*([0-9]{1,12})").find(page)?.groupValues?.get(1)
            ?: error("Visorando : tracé non disponible dans la fiche publique")
        val mapUrl = "$BASE/index.php".toHttpUrl().newBuilder().addQueryParameter("component", "exportData")
            .addQueryParameter("task", "getRandoGeoJson").addQueryParameter("chartData", "1")
            .addQueryParameter("wholePointsData", "1").addQueryParameter("idRandonnee", id).build().toString()
        parseDetail(route.copy(id = "visorando-$id"), page, fetch(mapUrl) ?: error("Visorando : tracé indisponible"))
    }
    internal fun parseDetail(route: HikeRoute, html: String, map: String): HikeRoute {
        val document = Jsoup.parse(html, BASE)
        val product = document.select("script[type=application/ld+json]").firstNotNullOfOrNull { script ->
            runCatching { json.parseToJsonElement(script.data()).jsonObject.takeIf { it["@type"]?.jsonPrimitive?.contentOrNull == "Product" } }.getOrNull()
        }.orEmpty()
        fun field(name: String) = product[name]?.jsonPrimitive?.contentOrNull.orEmpty()
        val metrics = document.select(".vr-walk-datasheet--dataset").associate { node ->
            val label = node.selectFirst("strong")?.text().orEmpty().substringBefore(':').trim()
            label to node.text().substringAfter(':', "").trim()
        }
        val data = json.parseToJsonElement(map).jsonObject
        val features = data["geojson"]?.jsonObject?.get("features")?.jsonArray ?: error("Visorando : géométrie absente")
        require(features.size <= 100000)
        val chart = ((data["chartdata"] as? JsonObject)?.get("data") as? JsonArray).orEmpty()
        var segment = 0
        val points = features.mapIndexedNotNull { index, value ->
            val feature = value.jsonObject
            val geometry = feature["geometry"]?.jsonObject ?: return@mapIndexedNotNull null
            if (geometry["type"]?.jsonPrimitive?.contentOrNull != "Point") { segment++; return@mapIndexedNotNull null }
            val coordinate = geometry["coordinates"]?.jsonArray ?: return@mapIndexedNotNull null
            if (coordinate.size < 2) return@mapIndexedNotNull null
            val altitude = if (coordinate.size > 2) coordinate[2].jsonPrimitive.doubleOrNull
                else if (chart.size == features.size) chart[index].jsonArray.getOrNull(1)?.jsonPrimitive?.doubleOrNull else null
            val point = HikePoint(coordinate[1].jsonPrimitive.double, coordinate[0].jsonPrimitive.double, altitude, segment = segment)
            if (feature["properties"]?.jsonObject?.get("a")?.jsonPrimitive?.intOrNull == 1) segment++
            require(HikeGeometry.valid(point)); point
        }
        require(points.size >= 2 && HikeGeometry.length(points) in 100.0..100000.0) { "Visorando : parcours incomplet" }
        val sections = document.select("section.route_sheet_text_content")
        val directions = sections.filter { it.selectFirst("h2")?.text()?.contains("Description de la randonnée", true) == true }
            .flatMap { it.select("article p").map { p -> p.text().take(4000) } }.filter(String::isNotBlank).take(200)
        val practical = sections.filter { it.selectFirst("h2")?.text()?.contains("Informations pratiques", true) == true }
            .flatMap { it.select("article p").map { p -> p.text().take(4000) } }.filter(String::isNotBlank).take(20)
        val photo = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty().takeIf(::publicUrl).orEmpty()
        val duration = metrics["Durée moyenne"]?.let { text ->
            val hours = Regex("(\\d+)\\s*h").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val minutes = Regex("h\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("(\\d+)\\s*min").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            (hours * 60 + minutes).takeIf { it > 0 }
        }
        fun number(label: String) = metrics[label]?.replace(Regex("[\\s\\u00a0\\u202f]"), "")?.let {
            Regex("[0-9]+(?:[.,][0-9]+)?").find(it)?.value?.replace(',', '.')?.toDoubleOrNull()
        }
        val description = Jsoup.parse(field("description")).text().take(6000)
        return route.copy(name = field("name").ifBlank { route.name }.take(200),
            region = metrics["Commune"] ?: product["address"]?.jsonObject?.get("addressLocality")?.jsonPrimitive?.contentOrNull ?: route.region,
            description = (description + if (practical.isEmpty()) "" else "\n\nInformations pratiques\n" + practical.joinToString("\n\n")).take(12000),
            points = points, provider = "Visorando", imageUrl = photo,
            imageCredit = if (photo.isNotEmpty()) "Visorando · photo du parcours" else "",
            difficulty = metrics["Difficulté"].orEmpty(), durationMinutes = duration,
            distanceMeters = number("Distance")?.times(1000), ascent = number("Dénivelé positif"), descent = number("Dénivelé négatif"),
            loop = metrics["Retour au départ"]?.startsWith("Oui", true) == true, directions = directions)
    }
}
