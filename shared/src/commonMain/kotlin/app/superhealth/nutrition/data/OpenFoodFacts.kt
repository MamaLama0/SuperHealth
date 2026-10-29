package app.superhealth.nutrition.data

import app.superhealth.platform.*

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import app.superhealth.platform.HttpURLConnection
import app.superhealth.platform.URL
import app.superhealth.platform.URLEncoder

/**
 * Client Open Food Facts — seul appel réseau de l'application (remplace la base Typesense de MacroFactor, notes/10 §4).
 * Aucun identifiant utilisateur n'est envoyé ; uniquement la requête de recherche ou le code-barres.
 */
object OpenFoodFacts {
    private const val BASE = "https://world.openfoodfacts.org"
    private const val SEARCH = "https://search.openfoodfacts.org"
    private const val UA = "LaBouffe/5.0 (usage personnel)"
    private const val FIELDS = "code,product_name,product_name_fr,brands,nutriments,serving_quantity,serving_size"

    /**
     * Produit scanné : un seul appel rapporte les macros et la fiche (scores, ingrédients…), mise en cache pour que la
     * fiche aliment s'ouvre complète. Délais courts : un serveur lent mène à la création plutôt qu'à une longue attente.
     */
    suspend fun byBarcode(code: String): FoodEntity? = withContext(Dispatchers.IO) {
        val json = get("$BASE/api/v2/product/${URLEncoder.encode(code, "UTF-8")}.json?fields=$FIELDS,$DETAIL_FIELDS&lc=fr", fast = true)
            ?: return@withContext null
        val product = json["product"] as? JsonObject ?: return@withContext null
        runCatching { detailsCache[code] = parseDetails(code, product) }
        toFood(product)
    }

    /** Fiche produit d'Open Food Facts (scores, ingrédients, allergènes, photo), affichée dans la fiche aliment. */
    data class Details(
        val code: String,
        val imageUrl: String?,
        val nutriScore: String?,
        val nova: Int?,
        val greenScore: String?,
        val ingredients: String?,
        val allergens: List<String>,
        val additives: List<String>,
        val labels: List<String>,
        val quantity: String?,
        val origins: String?,
    )

    private const val DETAIL_FIELDS = "code,image_front_small_url,image_front_url,nutriscore_grade,nova_group,ecoscore_grade," +
        "ingredients_text_fr,ingredients_text,allergens_tags,additives_tags,labels_tags,quantity,origins"
    private val detailsCache = ConcurrentHashMap<String, Details>()

    /** Fiche déjà chargée (par le scan ou une ouverture précédente), sans réseau. */
    fun cachedDetails(code: String): Details? = detailsCache[code]

    suspend fun details(code: String): Details? = detailsCache[code] ?: withContext(Dispatchers.IO) {
        val json = get("$BASE/api/v2/product/${URLEncoder.encode(code, "UTF-8")}.json?fields=$DETAIL_FIELDS&lc=fr") ?: return@withContext null
        val p = json["product"] as? JsonObject ?: return@withContext null
        parseDetails(code, p).also { detailsCache[code] = it }
    }

    private fun parseDetails(code: String, p: JsonObject): Details {
        fun tags(key: String) = (p[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.map { it.substringAfter(':').replace('-', ' ').replaceFirstChar { c -> c.uppercase() } }.orEmpty()
        return Details(
            code = code,
            imageUrl = p.str("image_front_small_url") ?: p.str("image_front_url"),
            nutriScore = p.str("nutriscore_grade")?.lowercase()?.takeIf { it in listOf("a", "b", "c", "d", "e") },
            nova = p.num("nova_group")?.toInt()?.takeIf { it in 1..4 },
            greenScore = p.str("ecoscore_grade")?.lowercase()?.takeIf { it in listOf("a-plus", "a", "b", "c", "d", "e", "f") },
            ingredients = (p.str("ingredients_text_fr")?.takeIf { it.isNotBlank() } ?: p.str("ingredients_text"))?.trim()?.takeIf { it.isNotBlank() },
            allergens = tags("allergens_tags"),
            additives = (p["additives_tags"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull?.substringAfter(':')?.uppercase() }.orEmpty(),
            labels = tags("labels_tags").take(8),
            quantity = p.str("quantity")?.takeIf { it.isNotBlank() },
            origins = p.str("origins")?.takeIf { it.isNotBlank() },
        )
    }

    /** Photo du produit (petite taille fournie par OFF). */
    suspend fun image(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000; conn.readTimeout = 15_000
                conn.setRequestProperty("User-Agent", UA)
                if (conn.responseCode != 200) null else conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally { conn.disconnect() }
        }.getOrNull()
    }

    /** API de recherche actuelle (search-a-licious) ; repli sur l'ancien CGI si elle ne répond pas. */
    suspend fun search(query: String): List<FoodEntity> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        get("$SEARCH/search?q=$q&page_size=30&langs=fr,en&fields=$FIELDS")?.get("hits")?.jsonArray
            ?.mapNotNull { (it as? JsonObject)?.let(::toFood) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return@withContext it }
        val json = get("$BASE/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1&page_size=30&fields=$FIELDS")
            ?: return@withContext emptyList()
        json["products"]?.jsonArray?.mapNotNull { (it as? JsonObject)?.let(::toFood) } ?: emptyList()
    }

    private fun get(url: String, fast: Boolean = false): JsonObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = if (fast) 4_000 else 10_000
            conn.readTimeout = if (fast) 6_000 else 15_000
            conn.setRequestProperty("User-Agent", UA)
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.use { Net.readCapped(it) }
            AppJson.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun JsonObject.str(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
    private fun JsonObject.num(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

    internal fun toFood(p: JsonObject): FoodEntity? {
        val name = p.str("product_name_fr")?.takeIf { it.isNotBlank() } ?: p.str("product_name")?.takeIf { it.isNotBlank() } ?: return null
        val n = p["nutriments"] as? JsonObject ?: return null
        val kcal = n.num("energy-kcal_100g") ?: n.num("energy_100g")?.let { it / 4.184 } ?: return null
        val serving = p.num("serving_quantity")?.takeIf { it > 0 } ?: 100.0
        return FoodEntity(
            name = name.trim(),
            brand = ((p["brands"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull
                ?: p.str("brands")?.split(',')?.firstOrNull())?.trim().orEmpty(),
            barcode = p.str("code"),
            servingName = p.str("serving_size")?.takeIf { it.isNotBlank() } ?: "100 g",
            servingGrams = serving,
            kcal100 = kcal,
            protein100 = n.num("proteins_100g") ?: 0.0,
            fat100 = n.num("fat_100g") ?: 0.0,
            carbs100 = n.num("carbohydrates_100g") ?: 0.0,
            fiber100 = n.num("fiber_100g") ?: 0.0,
            sugars100 = n.num("sugars_100g") ?: 0.0,
            satFat100 = n.num("saturated-fat_100g") ?: 0.0,
            sodiumMg100 = (n.num("sodium_100g") ?: 0.0) * 1000,
            // 2.2 : vitamines, minéraux, caféine, alcool… (avant, seuls les macros étaient gardés)
            microsJson = Micros.encode(Micros.OFF.mapNotNull { (key, to) -> n.num("${key}_100g")?.takeIf { it > 0 }?.let { to.first to it * to.second } }.toMap()),
            source = "off",
        )
    }
}
