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

/** Activée par défaut quand l'app est en anglais ; le réglage explicite l'emporte. */
val Settings.usdaOn get() = usdaDbEnabled ?: app.superhealth.ui.Lang.english

/** Bases génériques embarquées (suisse puis USDA) selon les réglages, pour l'IA et les repas. */
object GenericFoods {
    fun search(context: Context, s: Settings, query: String, limit: Int): List<FoodEntity> =
        (if (s.swissDbEnabled) SwissFoods.search(context, query, limit).map(SwissFoods::toFood) else emptyList()) +
            (if (s.usdaOn) UsdaFoods.search(context, query, limit).map(UsdaFoods::toFood) else emptyList())
}
