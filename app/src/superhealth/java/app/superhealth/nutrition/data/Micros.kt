package app.superhealth.nutrition.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.round

/** Micronutriments : codes, libellés, unités, apports de référence (plancher indicatif adulte, RDA/AI). */
object Micros {
    data class Def(val code: String, val label: String, val unit: String, val category: String, val reference: Double? = null)

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
        Def("potassium", "Potassium", "mg", "Minéraux", 3400.0), Def("sodium", "Sodium", "mg", "Minéraux"),
        Def("chloride", "Chlorure", "mg", "Minéraux"), Def("zinc", "Zinc", "mg", "Minéraux", 11.0),
        Def("iodine", "Iode", "µg", "Minéraux", 150.0), Def("selenium", "Sélénium", "µg", "Minéraux", 55.0),
    )
    val byCode = defs.associateBy { it.code }

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
}
