package app.superhealth.nutrition.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Base d'aliments du serveur SuperHealth (3.9) : NCC, MND et USDA (générique et marques), nettoyés et classés côté
 * serveur (foods.mjs). Seuls la requête ou le code-barres sont envoyés, sans identifiant. Serveur : celui du Groupe
 * s'il est personnalisé, sinon le serveur par défaut de l'app.
 */
object FoodServer {
    /** Sources renvoyées par le serveur, par priorité d'affichage. */
    val SOURCES = mapOf("ncc" to "NCC", "mnd" to "MND", "usda" to "USDA", "usdab" to "USDA")
    const val PREFIX = "sh-"

    fun isServerSource(source: String) = source.startsWith(PREFIX)
    fun label(source: String) = SOURCES[source.removePrefix(PREFIX)]

    private fun base(): String? {
        val s = app.superhealth.league.League.state.value
        val custom = s.relayUrl.trimEnd('/').takeIf { s.customRelay && app.superhealth.league.Relay.validBase(it) }
        return custom ?: app.superhealth.league.Relay.defaultBase()?.trimEnd('/')
    }

    suspend fun search(query: String): List<FoodEntity> = withContext(Dispatchers.IO) {
        val b = base() ?: return@withContext emptyList()
        val q = query.trim().take(80).takeIf { it.length >= 2 } ?: return@withContext emptyList()
        val json = get("$b/v2/foods/search?q=${URLEncoder.encode(q, "UTF-8")}&limit=20") ?: return@withContext emptyList()
        (json["foods"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::toFood) }.orEmpty()
    }

    /** Code-barres : délais courts, Open Food Facts prend le relais si le serveur ne répond pas. */
    suspend fun byBarcode(code: String): FoodEntity? = withContext(Dispatchers.IO) {
        val b = base() ?: return@withContext null
        if (!code.all(Char::isDigit) || code.length !in 8..14) return@withContext null
        (get("$b/v2/foods/barcode/$code", fast = true)?.get("food") as? JsonObject)?.let(::toFood)
    }

    /**
     * Connexion gardée ouverte entre deux frappes (pas de disconnect : le flux lu jusqu'au bout et fermé rend la socket
     * au pool, sans nouvelle poignée de main TLS) ; réponse compressée en gzip, décompressée par HttpURLConnection.
     */
    private fun get(url: String, fast: Boolean = false): JsonObject? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = if (fast) 3_000 else 6_000
        conn.readTimeout = if (fast) 4_000 else 8_000
        if (conn.responseCode != 200) { conn.errorStream?.close(); null }
        else AppJson.parseToJsonElement(conn.inputStream.use { Net.readCapped(it, 1024 * 1024) }) as? JsonObject
    } catch (e: Exception) {
        android.util.Log.w("FoodServer", "GET failed: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    /** Ouvre la connexion au serveur dès l'écran de recherche, pour que la première recherche ne paie pas le TLS. */
    suspend fun warmUp() = withContext(Dispatchers.IO) {
        val b = base() ?: return@withContext
        runCatching { (URL("$b/health").openConnection() as HttpURLConnection).apply { connectTimeout = 3_000; readTimeout = 3_000 }.inputStream.use { it.readBytes() } }
    }

    private fun JsonObject.str(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
    private fun JsonObject.num(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull

    internal fun toFood(o: JsonObject): FoodEntity? {
        val src = o.str("src")?.takeIf { it in SOURCES } ?: return null
        val name = o.str("name")?.takeIf { it.isNotBlank() } ?: return null
        val micros = (o["micros"] as? JsonObject)?.mapNotNull { (k, v) ->
            (v as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull?.takeIf { k in Micros.byCode && it > 0 }?.let { k to it }
        }?.toMap().orEmpty()
        // première portion du serveur (ex. « medium - 7" to 7 7/8" long », 118 g), sinon 100 g
        val serving = (o["servings"] as? JsonArray)?.firstOrNull() as? JsonArray
        val servingName = serving?.getOrNull(0)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val servingGrams = serving?.getOrNull(1)?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0 }
        return FoodEntity(
            name = name.trim(),
            brand = o.str("brand").orEmpty().trim(),
            barcode = o.str("upc"),
            servingName = if (servingName != null && servingGrams != null) servingName else "100 g",
            servingGrams = if (servingName != null && servingGrams != null) servingGrams else 100.0,
            kcal100 = o.num("kcal") ?: return null,
            protein100 = o.num("protein") ?: 0.0,
            fat100 = o.num("fat") ?: 0.0,
            carbs100 = o.num("carbs") ?: 0.0,
            fiber100 = micros["fiber"] ?: 0.0,
            sugars100 = micros["sugar"] ?: 0.0,
            satFat100 = micros["sat"] ?: 0.0,
            sodiumMg100 = micros["sodium"] ?: 0.0,
            microsJson = Micros.encode(micros),
            source = PREFIX + src,
        )
    }
}
