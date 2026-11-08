package app.superhealth.nutrition.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer

/**
 * Base de données suisse des valeurs nutritives (OSAV, naehrwertdaten.ch), version française embarquée
 * (assets/swissfcdb.json, généré par tools/import_swiss_fcdb.py). Hors ligne, avec micronutriments.
 */
object SwissFoods {
    const val SOURCE = "swiss"
    const val CREDIT = "Base de données suisse des valeurs nutritives, OSAV"

    class Item(
        val id: Int, val name: String, val synonyms: String, val category: String,
        val kcal: Double, val protein: Double, val fat: Double, val carbs: Double, val fiber: Double, val sugar: Double,
        val sat: Double, val salt: Double, val micros: Map<String, Double>, val key: String,
    )

    private val table = FoodTable("swissfcdb.json")
    val version get() = table.version

    fun load(context: Context): List<Item> = table.load(context)

    fun search(context: Context, query: String, limit: Int = 40): List<Item> = table.search(context, query, limit)

    fun toFood(i: Item): FoodEntity {
        val micros = i.micros + mapOf("sugar" to i.sugar, "sat" to i.sat).filterValues { it > 0 }
        return FoodEntity(
            name = i.name, brand = "Base suisse", servingName = "portion", servingGrams = 100.0,
            kcal100 = i.kcal, protein100 = i.protein, fat100 = i.fat, carbs100 = i.carbs, fiber100 = i.fiber,
            sugars100 = i.sugar, satFat100 = i.sat, sodiumMg100 = i.micros["sodium"] ?: (i.salt * 393.4),
            source = SOURCE, microsJson = Micros.encode(micros),
        )
    }
}

/** Table d'aliments embarquée, au format compact de tools/import_*.py (base suisse, USDA). Chargée une fois, hors ligne. */
class FoodTable(private val asset: String) {
    @Volatile private var items: List<SwissFoods.Item>? = null
    var version: String = ""; private set

    private fun norm(s: String) = Normalizer.normalize(s.lowercase().replace("œ", "oe").replace("æ", "ae"), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]+"), " ").replace(Regex("\\s+"), " ").trim()

    /** Espaces en double dans certains noms de la base (« cuite,  égouttée »). */
    private val spaces = Regex("\\s{2,}")

    fun load(context: Context): List<SwissFoods.Item> = items ?: synchronized(this) {
        items ?: runCatching {
            val root = Json.parseToJsonElement(context.assets.open(asset).use { it.readBytes().decodeToString() }).jsonObject
            version = root["v"]!!.jsonPrimitive.content
            val codes = root["n"]!!.jsonArray.map { it.jsonPrimitive.content }
            root["f"]!!.jsonArray.map { e ->
                val a = e as JsonArray
                fun d(i: Int) = (a[i] as JsonPrimitive).double
                val m = (a[12] as JsonObject).mapNotNull { (k, v) -> (v as JsonPrimitive).doubleOrNull?.let { codes[k.toInt()] to it } }.toMap()
                val name = a[1].jsonPrimitive.content.replace(spaces, " ").trim(); val syn = a[2].jsonPrimitive.content
                SwissFoods.Item(a[0].jsonPrimitive.int, name, syn, a[3].jsonPrimitive.content, d(4), d(5), d(6), d(7), d(8), d(9), d(10), d(11), m, norm("$name $syn"))
            }
        }.getOrDefault(emptyList()).also { items = it }
    }

    /** Nom en minuscules sans accents, ponctuation gardée (« Pomme, crue » reste distinct de « Pomme de terre »). */
    private fun plain(s: String) = Normalizer.normalize(s.lowercase().replace("œ", "oe").replace("æ", "ae"), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /**
     * Précision du nom pour la requête [q] (au singulier) : 0 l'aliment lui-même (« Pomme », « Pomme, crue »),
     * 1 le mot suivi d'autres mots (« Pomme de terre… »), 2 début d'un mot plus long, 3 ailleurs. Comme le serveur (foods.mjs).
     */
    private fun precision(name: String, q: String): Int {
        val forms = listOf(q, q + "s", q + "x")
        return when {
            forms.any { name == it || name.startsWith("$it,") || name.startsWith("$it (") } -> 0
            forms.any { name.startsWith("$it ") } -> 1
            name.startsWith(q) -> 2
            else -> 3
        }
    }

    /** Recherche : tous les mots de la requête présents ; score = précision du nom, mot entier, longueur courte. */
    fun search(context: Context, query: String, limit: Int = 40): List<SwissFoods.Item> {
        val words = norm(query).split(' ').filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        val q = plain(query).trim().replace(Regex("\\s+"), " ").let { if (it.length > 3 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }
        return load(context).asSequence()
            .filter { item -> words.all { item.key.contains(it) } }
            .sortedWith(compareBy<SwissFoods.Item>({ precision(plain(it.name), q) }, { !words.all { w -> " ${it.key} ".contains(" $w ") } }, { it.name.length }))
            .take(limit).toList()
    }
}
