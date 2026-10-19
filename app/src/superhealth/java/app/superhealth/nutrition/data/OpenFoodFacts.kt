package app.superhealth.nutrition.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Client Open Food Facts — seul appel réseau de l'application (remplace la base Typesense de MacroFactor, notes/10 §4).
 * Aucun identifiant utilisateur n'est envoyé ; uniquement la requête de recherche ou le code-barres.
 */
object OpenFoodFacts {
    private const val BASE = "https://world.openfoodfacts.org"
    private const val SEARCH = "https://search.openfoodfacts.org"
    private const val UA = "LaBouffe/5.0 (usage personnel)"
    private const val FIELDS = "code,product_name,product_name_fr,brands,nutriments,serving_quantity,serving_size"

    suspend fun byBarcode(code: String): FoodEntity? = withContext(Dispatchers.IO) {
        val json = get("$BASE/api/v2/product/${URLEncoder.encode(code, "UTF-8")}.json?fields=$FIELDS") ?: return@withContext null
        val product = json["product"] as? JsonObject ?: return@withContext null
        toFood(product)
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

    private fun get(url: String): JsonObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", UA)
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
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
            source = "off",
        )
    }
}
