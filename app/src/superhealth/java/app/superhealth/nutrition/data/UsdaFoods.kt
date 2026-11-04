package app.superhealth.nutrition.data

import android.content.Context
import app.superhealth.nutrition.domain.Settings

/**
 * USDA FoodData Central, SR Legacy (domaine public), en anglais et embarquée (assets/usdafcdb.json, générée par
 * tools/import_usda_fdc.py). Hors ligne, avec micronutriments ; complète la base suisse sans la remplacer.
 */
object UsdaFoods {
    const val SOURCE = "usda"
    const val CREDIT = "USDA FoodData Central (SR Legacy), domaine public"

    private val table = FoodTable("usdafcdb.json")

    fun search(context: Context, query: String, limit: Int = 40): List<SwissFoods.Item> = table.search(context, query, limit)

    fun toFood(i: SwissFoods.Item): FoodEntity {
        val micros = i.micros + mapOf("sugar" to i.sugar, "sat" to i.sat).filterValues { it > 0 }
        return FoodEntity(
            name = i.name, brand = "USDA", servingName = "portion", servingGrams = 100.0,
            kcal100 = i.kcal, protein100 = i.protein, fat100 = i.fat, carbs100 = i.carbs, fiber100 = i.fiber,
            sugars100 = i.sugar, satFat100 = i.sat, sodiumMg100 = i.micros["sodium"] ?: (i.salt * 393.4),
            source = SOURCE, microsJson = Micros.encode(micros),
        )
    }
}

/** Activée par défaut (ses résultats passent après ceux de la langue de l'app) ; le réglage explicite l'emporte. */
val Settings.usdaOn get() = usdaDbEnabled ?: true

/**
 * Bases génériques embarquées pour la recherche, l'IA et les repas. Chaque base est d'abord cherchée telle quelle, puis avec
 * la requête traduite ([FoodLexicon]) ; la base dans la langue de l'app passe en premier.
 */
object GenericFoods {
    private fun merged(native: List<FoodEntity>, translated: List<FoodEntity>, limit: Int) =
        (native + translated).distinctBy { it.name }.take(limit)

    fun swiss(context: Context, query: String, limit: Int = 40): List<FoodEntity> = merged(
        SwissFoods.search(context, query, limit).map(SwissFoods::toFood),
        FoodLexicon.translate(query, toEnglish = false)?.let { q -> SwissFoods.search(context, q, limit).map(SwissFoods::toFood) }.orEmpty(), limit)

    fun usda(context: Context, query: String, limit: Int = 40): List<FoodEntity> = merged(
        UsdaFoods.search(context, query, limit).map(UsdaFoods::toFood),
        FoodLexicon.translate(query, toEnglish = true)?.let { q -> UsdaFoods.search(context, q, limit).map(UsdaFoods::toFood) }.orEmpty(), limit)

    /** Vrai si la base suisse (française) doit passer avant l'USDA. */
    fun frenchFirst() = !app.superhealth.ui.Lang.english

    fun search(context: Context, s: Settings, query: String, limit: Int): List<FoodEntity> {
        val swiss = if (s.swissDbEnabled) swiss(context, query, limit) else emptyList()
        val usda = if (s.usdaOn) usda(context, query, limit) else emptyList()
        return if (frenchFirst()) swiss + usda else usda + swiss
    }
}
