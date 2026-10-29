package app.superhealth.nutrition.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.round

/** Micronutriments : codes, libellés, unités, apports de référence (plancher indicatif adulte, RDA/AI). */
object Micros {
    /** [reference] : apport conseillé (à atteindre) ; [limit] : plafond (à ne pas dépasser), pour la caféine, le sodium… */
    data class Def(val code: String, val label: String, val unit: String, val category: String, val reference: Double? = null, val limit: Double? = null)

    val defs = listOf(
        Def("sugar", "Sucres", "g", "Glucides"), Def("starch", "Amidon", "g", "Glucides"), Def("fiber", "Fibres", "g", "Glucides", 30.0),
        Def("sat", "Acides gras saturés", "g", "Lipides"), Def("mufa", "Mono-insaturés", "g", "Lipides"), Def("pufa", "Poly-insaturés", "g", "Lipides"),
        Def("ala", "Oméga-3 ALA", "g", "Lipides", 1.6), Def("epa", "Oméga-3 EPA", "g", "Lipides"), Def("dha", "Oméga-3 DHA", "g", "Lipides"),
        Def("chol", "Cholestérol", "mg", "Lipides"), Def("alcohol", "Alcool", "g", "Autres"), Def("water", "Eau", "g", "Autres"),
        Def("vitA", "Vitamine A", "µg", "Vitamines", 900.0), Def("betaCarotene", "Bêta-carotène", "µg", "Vitamines"),
        Def("b1", "Vitamine B1 (thiamine)", "mg", "Vitamines", 1.2), Def("b2", "Vitamine B2 (riboflavine)", "mg", "Vitamines", 1.3),
        Def("b3", "Vitamine B3 (niacine)", "mg", "Vitamines", 16.0), Def("b5", "Vitamine B5", "mg", "Vitamines", 5.0),
        Def("b6", "Vitamine B6", "mg", "Vitamines", 1.3), Def("folate", "Folate (B9)", "µg", "Vitamines", 400.0),
        Def("b12", "Vitamine B12", "µg", "Vitamines", 2.4), Def("vitC", "Vitamine C", "mg", "Vitamines", 90.0),
        Def("vitD", "Vitamine D", "µg", "Vitamines", 15.0), Def("vitE", "Vitamine E", "mg", "Vitamines", 15.0),
        Def("calcium", "Calcium", "mg", "Minéraux", 1000.0), Def("iron", "Fer", "mg", "Minéraux", 8.0),
        Def("magnesium", "Magnésium", "mg", "Minéraux", 400.0), Def("phosphorus", "Phosphore", "mg", "Minéraux", 700.0),
        Def("potassium", "Potassium", "mg", "Minéraux", 3400.0), Def("sodium", "Sodium", "mg", "Minéraux", limit = 2000.0),
        Def("chloride", "Chlorure", "mg", "Minéraux"), Def("zinc", "Zinc", "mg", "Minéraux", 11.0),
        Def("iodine", "Iode", "µg", "Minéraux", 150.0), Def("selenium", "Sélénium", "µg", "Minéraux", 55.0),
        // 2.2 : nutriments d'Open Food Facts jusque-là ignorés ; caféine avec son plafond (EFSA 2015 : 400 mg/j chez l'adulte)
        Def("addedSugar", "Sucres ajoutés", "g", "Glucides"), Def("trans", "Acides gras trans", "g", "Lipides"),
        Def("vitK", "Vitamine K", "µg", "Vitamines", 120.0), Def("copper", "Cuivre", "mg", "Minéraux", 0.9),
        Def("manganese", "Manganèse", "mg", "Minéraux", 2.3),
        Def("caffeine", "Caféine", "mg", "Autres", limit = 400.0),
    )
    val byCode = defs.associateBy { it.code }

    /**
     * Apport de référence selon l'âge et le sexe du profil (RDA/AI de l'Institute of Medicine) ; [Def.reference] est la
     * valeur de l'homme adulte. Femmes : vit. A 700 µg, C 75 mg, K 90 µg, B1/B2 1,1 mg, B3 14 mg, zinc 8 mg, magnésium
     * 320 mg, fer 18 mg avant 51 ans. Âge : calcium 1 200 mg (femmes après 50 ans, hommes après 70 ans), vit. D 20 µg
     * (800 UI) après 70 ans (IOM 2010).
     */
    fun reference(d: Def): Double? {
        val p = runCatching { app.superhealth.nutrition.NutriApp.repository.prefs.profile.value }.getOrNull() ?: return d.reference
        val age = java.time.LocalDate.now().year - p.birthYear
        val female = p.sex == app.superhealth.nutrition.domain.Sex.FEMALE
        return when (d.code) {
            "vitD" -> if (age > 70) 20.0 else d.reference
            "calcium" -> if ((female && age > 50) || age > 70) 1200.0 else d.reference
            "iron" -> if (female && age < 51) 18.0 else d.reference
            "vitA" -> if (female) 700.0 else d.reference
            "vitC" -> if (female) 75.0 else d.reference
            "vitK" -> if (female) 90.0 else d.reference
            "b1", "b2" -> if (female) 1.1 else d.reference
            "b3" -> if (female) 14.0 else d.reference
            "zinc" -> if (female) 8.0 else d.reference
            "magnesium" -> if (female) 320.0 else d.reference
            else -> d.reference
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(s: String?): Map<String, Double> = if (s.isNullOrBlank()) emptyMap() else runCatching {
        (json.parseToJsonElement(s) as JsonObject).mapNotNull { (k, v) -> (v as? JsonPrimitive)?.doubleOrNull?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())

    fun encode(m: Map<String, Double>): String? = if (m.isEmpty()) null else
        JsonObject(m.mapValues { JsonPrimitive(round(it.value * 10000) / 10000) }).toString()

    fun scale(s: String?, factor: Double): String? = s?.let { encode(parse(it).mapValues { it.value * factor }) }

    fun sum(list: List<String?>): Map<String, Double> {
        val out = HashMap<String, Double>()
        list.forEach { parse(it).forEach { (k, v) -> out[k] = (out[k] ?: 0.0) + v } }
        return out
    }

    /**
     * Nutriments d'Open Food Facts → codes Micros. Les valeurs « _100g » d'OFF sont en grammes (hors énergie et alcool,
     * en % vol.) : facteur vers l'unité du code. Alcool : % vol. × 0,789 (masse volumique de l'éthanol) ≈ g pour 100 ml.
     */
    val OFF = listOf(
        "sugars" to ("sugar" to 1.0), "added-sugars" to ("addedSugar" to 1.0), "starch" to ("starch" to 1.0),
        "saturated-fat" to ("sat" to 1.0), "monounsaturated-fat" to ("mufa" to 1.0), "polyunsaturated-fat" to ("pufa" to 1.0),
        "trans-fat" to ("trans" to 1.0), "cholesterol" to ("chol" to 1e3), "alpha-linolenic-acid" to ("ala" to 1.0),
        "eicosapentaenoic-acid" to ("epa" to 1.0), "docosahexaenoic-acid" to ("dha" to 1.0),
        "vitamin-a" to ("vitA" to 1e6), "beta-carotene" to ("betaCarotene" to 1e6), "vitamin-d" to ("vitD" to 1e6),
        "vitamin-e" to ("vitE" to 1e3), "vitamin-k" to ("vitK" to 1e6), "vitamin-c" to ("vitC" to 1e3),
        "vitamin-b1" to ("b1" to 1e3), "vitamin-b2" to ("b2" to 1e3), "vitamin-pp" to ("b3" to 1e3), "pantothenic-acid" to ("b5" to 1e3),
        "vitamin-b6" to ("b6" to 1e3), "vitamin-b9" to ("folate" to 1e6), "folates" to ("folate" to 1e6), "vitamin-b12" to ("b12" to 1e6),
        "potassium" to ("potassium" to 1e3), "calcium" to ("calcium" to 1e3), "phosphorus" to ("phosphorus" to 1e3),
        "iron" to ("iron" to 1e3), "magnesium" to ("magnesium" to 1e3), "zinc" to ("zinc" to 1e3), "copper" to ("copper" to 1e3),
        "manganese" to ("manganese" to 1e3), "selenium" to ("selenium" to 1e6), "iodine" to ("iodine" to 1e6),
        "chloride" to ("chloride" to 1e3), "sodium" to ("sodium" to 1e3), "caffeine" to ("caffeine" to 1e3),
        "alcohol" to ("alcohol" to 0.789),
    )

    /**
     * Micros d'un aliment pour 100 g : son JSON, complété des colonnes sucres / saturés / sodium (aliments d'Open Food Facts
     * et personnels, qui ne les avaient qu'en colonnes) et de la caféine connue des boissons courantes.
     */
    fun of(json: String?, name: String, sugars100: Double, satFat100: Double, sodiumMg100: Double): Map<String, Double> {
        val m = parse(json).toMutableMap()
        if ("sugar" !in m && sugars100 > 0) m["sugar"] = sugars100
        if ("sat" !in m && satFat100 > 0) m["sat"] = satFat100
        if ("sodium" !in m && sodiumMg100 > 0) m["sodium"] = sodiumMg100
        if ("caffeine" !in m) Caffeine.per100(name)?.let { m["caffeine"] = it }
        return m
    }
}

/**
 * Caféine des aliments qui ne la donnent pas (base suisse, aliments saisis) : valeurs USDA FoodData Central (SR Legacy),
 * mg pour 100 g, reconnues au nom. Le décaféiné et les infusions n'en ont pas.
 */
object Caffeine {
    private val table = listOf(
        listOf("espresso", "expresso", "ristretto") to 212.0,
        listOf("café soluble", "cafe soluble", "instant coffee", "nescafé", "nescafe") to 26.0,
        listOf("café", "cafe", "coffee", "cappuccino", "latte", "americano", "macchiato") to 40.0,
        listOf("thé vert", "the vert", "green tea") to 12.0,
        listOf("thé", "the noir", "black tea", "tea", "earl grey") to 20.0,
        listOf("red bull", "monster", "boisson énergisante", "boisson energisante", "energy drink") to 32.0,
        listOf("cola", "coca", "pepsi") to 10.0,
        listOf("cacao en poudre", "cocoa powder", "poudre de cacao") to 230.0,
        listOf("chocolat noir", "dark chocolate") to 80.0,
        listOf("chocolat au lait", "milk chocolate") to 20.0,
    )
    private val none = listOf("décaf", "decaf", "sans caféine", "caffeine free", "caffeine-free", "tisane", "infusion", "rooibos", "herbal",
        "thon", "théâtre", "glace", "ice cream", "gâteau", "cake", "yaourt", "yogurt", "zero caf")

    fun per100(name: String): Double? {
        val n = " " + name.lowercase() + " "
        if (none.any { it in n }) return null
        return table.firstOrNull { (keys, _) -> keys.any { k -> Regex("(^|[^a-zà-ÿ])" + Regex.escape(k) + "($|[^a-zà-ÿ])").containsMatchIn(n) } }?.second
    }
}
