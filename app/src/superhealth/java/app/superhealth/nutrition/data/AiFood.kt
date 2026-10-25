package app.superhealth.nutrition.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/** Fournisseurs compatibles avec l'API Chat Completions d'OpenAI. */
@Serializable
enum class AiProvider(val label: String, val baseUrl: String, val defaultModel: String, val keyHelp: String) {
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "google/gemini-3.1-flash-lite",
        "Clé sur openrouter.ai."),
    CUSTOM("Autre (compatible OpenAI)", "", "", "URL de base se terminant avant /chat/completions (ex. Ollama, Groq…)."),
}

/** Réglages IA, stockés à part des réglages (jamais inclus dans la sauvegarde JSON). */
@Serializable
data class AiConfig(
    val provider: AiProvider = AiProvider.OPENROUTER,
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
) {
    val effectiveModel get() = model.ifBlank { provider.defaultModel }
    val effectiveUrl get() = (if (provider == AiProvider.CUSTOM) baseUrl else baseUrl.ifBlank { provider.baseUrl }).trimEnd('/')
    val ready get() = apiKey.isNotBlank() && effectiveModel.isNotBlank() && effectiveUrl.isNotBlank()
}

/**
 * Journalisation par IA (photo et/ou texte), calquée sur le pipeline de MacroFactor (ai_features/search, notes/16) :
 * 1. analyze_food : le modèle liste les aliments en lignes délimitées par « | » (comme AnalyzedFoods / NutritionLabelFoodDsvParser),
 *    avec ici une estimation nutritionnelle par aliment (rôle de generate_food) ;
 * 2. query_foods : recherche locale (bibliothèque + base suisse) à la place de Typesense ;
 * 3. select_search_result : le modèle choisit la meilleure entrée de la base ou « none » ;
 * 4. l'utilisateur vérifie l'« assiette » avant d'ajouter.
 */
object AiFood {
    const val NOT_A_FOOD = "ai_search_not_a_food"

    enum class Stage(val label: String) { ANALYZE("Analyse du repas"), SEARCH("Recherche dans la base"), SELECT("Choix des correspondances"), DONE("Terminé") }

    /** Aliment détecté : estimation du modèle pour la portion, et correspondance éventuelle dans la base. */
    data class Item(
        val name: String, val quantity: Double, val unit: String, val grams: Double, val type: String,
        val kcal: Double, val protein: Double, val fat: Double, val carbs: Double, val fiber: Double, val query: String,
        val match: FoodEntity? = null,
    )

    class AiException(message: String) : Exception(message)

    private val json = Json { ignoreUnknownKeys = true }

    // ----- Prompts -----

    /** Langue des noms d'aliments produits par le modèle : celle de l'interface. */
    private val foodLang get() = if (app.superhealth.nutrition.ui.Lang.english) "English" else "French"

    val ANALYZE_PROMPT get() = """
        You are the food-analysis stage of a nutrition tracking app. From the photo and/or the description, list every distinct food or drink
        that the user ate, with your best estimate of the amount actually eaten.

        Output ONLY pipe-delimited lines, one food per line, no header, no markdown, no comments:
        name | quantity | unit | weight_g | type | kcal | protein_g | fat_g | carbs_g | fiber_g | search_query

        Rules:
        - name: short $foodLang name of the food as it would appear in a food database (e.g. "Riz basmati cuit", "Poulet rôti, blanc").
        - quantity + unit: the household serving you see or that was described (e.g. 1 | assiette, 2 | tranches, 250 | ml, 1 | pièce).
        - weight_g: estimated edible weight in grams (for drinks, millilitres ≈ grams). Always a number > 0.
        - type: one of generic, branded_in_package, branded_not_in_package, restaurant.
        - kcal, protein_g, fat_g, carbs_g, fiber_g: totals for that weight (not per 100 g), numbers with a dot as decimal separator.
        - search_query: 1 to 4 French keywords to find the plain food in a generic database (e.g. "riz basmati cuit").
        - Split mixed dishes into their main components when they are visible or described (e.g. pasta, sauce, cheese), otherwise keep one line.
        - Respect amounts, brands and remarks given by the user (e.g. "half", "without sauce", "200 g"). Scale and weights printed on packages win.
        - Use realistic portion sizes; count cooking oil and sauces when likely.
        - If there is no food or drink at all, output exactly: $NOT_A_FOOD
    """.trimIndent()

    val SELECT_PROMPT = """
        You match foods estimated from a meal to entries of a nutrition database.
        For each numbered food, pick the candidate that describes the same food in the same state (raw/cooked, with/without skin, type of product).
        Prefer a correct generic entry over a vague one. If no candidate is a good match, answer none.

        Output ONLY pipe-delimited lines, one per food, no header, no markdown:
        food_number | candidate_id
        where candidate_id is the id shown in brackets, or none.
    """.trimIndent()

    // ----- Analyse -----

    suspend fun analyze(
        cfg: AiConfig, text: String, imageJpeg: ByteArray?,
        candidates: suspend (String) -> List<FoodEntity>, onStage: (Stage) -> Unit = {},
    ): List<Item> {
        if (!cfg.ready) throw AiException("Configurez d'abord une clé API (Plus → Intelligence artificielle).")
        onStage(Stage.ANALYZE)
        val user = buildString {
            append(if (text.isBlank()) "Analyse ce repas." else "Description du repas : ${text.trim()}")
        }
        val raw = chat(cfg, ANALYZE_PROMPT, user, imageJpeg)
        val items = parseAnalyzed(raw)
        if (items.isEmpty()) throw AiException(if (raw.contains(NOT_A_FOOD)) "Aucun aliment reconnu." else "Réponse du modèle illisible.")

        return match(cfg, items, candidates, onStage)
    }

    /**
     * Étapes query_foods et select_search_result : chaque aliment estimé est rapproché d'une entrée de la base locale
     * (choisie par le modèle, puis contrôlée par [plausible]) ; sans correspondance, l'estimation du modèle reste.
     */
    suspend fun match(cfg: AiConfig, items: List<Item>, candidates: suspend (String) -> List<FoodEntity>, onStage: (Stage) -> Unit = {}): List<Item> {
        onStage(Stage.SEARCH)
        val cands = items.map { candidates(it.query.ifBlank { it.name }).take(6) }
        if (cands.all { it.isEmpty() }) { onStage(Stage.DONE); return items }

        onStage(Stage.SELECT)
        val selection = runCatching {
            val prompt = buildString {
                items.forEachIndexed { i, it ->
                    append("Food ${i + 1}: ${it.name} (${num(it.grams)} g, about ${num(it.kcal / it.grams * 100)} kcal/100 g)\n")
                    cands[i].forEachIndexed { j, c ->
                        append("  [${i + 1}.${j + 1}] ${c.name}${if (c.brand.isNotBlank()) " (${c.brand})" else ""} — per 100 g: " +
                            "${num(c.kcal100)} kcal, P ${num(c.protein100)}, F ${num(c.fat100)}, C ${num(c.carbs100)}\n")
                    }
                    if (cands[i].isEmpty()) append("  (no candidate)\n")
                }
            }
            parseSelection(chat(cfg, SELECT_PROMPT, prompt, null))
        }.getOrDefault(emptyMap())
        onStage(Stage.DONE)
        return items.mapIndexed { i, it ->
            val pick = selection[i + 1]?.let { j -> cands[i].getOrNull(j - 1) }
            if (pick != null && plausible(it, pick)) it.copy(match = pick) else it
        }
    }

    // ----- Étiquette nutritionnelle (extract_product_image / NutritionLabelFoodDsvParser) -----

    /** Aliment lu sur une étiquette : valeurs pour une portion de [servingGrams] g. */
    data class Label(
        val name: String, val brand: String, val servingName: String, val servingQuantity: Double, val servingGrams: Double,
        val kcal: Double, val protein: Double, val fat: Double, val carbs: Double,
        val fiber: Double, val sugars: Double, val satFat: Double, val sodiumMg: Double,
    ) {
        fun toFood(): FoodEntity {
            val k = 100 / servingGrams
            return FoodEntity(name = name, brand = brand, servingName = servingName.ifBlank { "portion" }, servingGrams = servingGrams,
                kcal100 = kcal * k, protein100 = protein * k, fat100 = fat * k, carbs100 = carbs * k,
                fiber100 = fiber * k, sugars100 = sugars * k, satFat100 = satFat * k, sodiumMg100 = sodiumMg * k, source = "custom")
        }
    }

    val LABEL_PROMPT get() = """
        You read nutrition facts labels for a nutrition tracking app. From the photo of a package (and the optional note), extract
        the product and its nutrition for ONE serving.

        Output ONLY one pipe-delimited line, no header, no markdown:
        food_name | brand | serving_size_name | serving_size_quantity | serving_size_weight_grams | kcal | protein_g | fat_g | carbs_g | fiber_g | sugars_g | saturated_fat_g | sodium_mg

        Rules:
        - food_name in $foodLang, short, as on a shelf (e.g. "Greek yoghurt 0 %"); brand empty if unknown.
        - If the label only gives values per 100 g or 100 ml, use serving_size_name "100 g", quantity 1, weight 100.
        - If both per 100 g and per serving are printed, use the serving column and its weight.
        - Convert kJ to kcal only when kcal is missing (1 kcal = 4.184 kJ). Salt to sodium: sodium_mg = salt_g × 400.
        - Numbers use a dot as decimal separator; unknown values = 0.
        - If the image has no readable nutrition label, output exactly: $NOT_A_FOOD
    """.trimIndent()

    fun parseLabel(raw: String): Label? = rows(raw).firstNotNullOfOrNull { c ->
        if (c.size < 9 || c[0].equals("food_name", true)) return@firstNotNullOfOrNull null
        val grams = d(c[4])?.takeIf { it > 0 } ?: return@firstNotNullOfOrNull null
        Label(c[0], c[1], c[2], d(c[3]) ?: 1.0, grams, d(c[5]) ?: return@firstNotNullOfOrNull null,
            d(c[6]) ?: 0.0, d(c[7]) ?: 0.0, d(c[8]) ?: 0.0, d(c.getOrNull(9)) ?: 0.0, d(c.getOrNull(10)) ?: 0.0,
            d(c.getOrNull(11)) ?: 0.0, d(c.getOrNull(12)) ?: 0.0)
    }

    suspend fun extractLabel(cfg: AiConfig, note: String, imageJpeg: ByteArray?): Label {
        if (!cfg.ready) throw AiException("Configurez d'abord une clé API (Plus → Intelligence artificielle).")
        val raw = chat(cfg, LABEL_PROMPT, if (note.isBlank()) "Read this label." else "Note: ${note.trim()}", imageJpeg)
        return parseLabel(raw) ?: throw AiException(if (raw.contains(NOT_A_FOOD)) "Aucune étiquette lisible." else "Réponse du modèle illisible.")
    }

    // ----- Import de recette (extract_recipe / extract_recipe_image) -----

    data class RecipeImport(val name: String, val servings: Double, val items: List<Item>)

    val RECIPE_PROMPT get() = """
        You extract recipes for a nutrition tracking app, from a photo (cookbook page, screenshot) and/or pasted text.

        Output ONLY pipe-delimited lines, no header, no markdown:
        - first line: recipe_name | servings
        - then one line per ingredient:
          ingredient_name | quantity | unit | weight_g | kcal | protein_g | fat_g | carbs_g | fiber_g

        Rules:
        - Names in $foodLang. servings = number of portions the recipe makes (1 if unknown).
        - weight_g: weight of that ingredient for the WHOLE recipe, raw, in grams (ml ≈ g). Convert cups, spoons and pieces.
        - kcal and macros: totals for that weight (not per 100 g), dot as decimal separator.
        - Skip water, salt and pepper unless given in large amounts; include cooking fats.
        - If there is no recipe, output exactly: $NOT_A_FOOD
    """.trimIndent()

    fun parseRecipe(raw: String): RecipeImport? {
        val r = rows(raw)
        val head = r.firstOrNull { it.size in 2..3 && d(it[1]) != null && !it[0].equals("recipe_name", true) } ?: return null
        val items = r.filter { it !== head && it.size >= 8 && !it[0].equals("ingredient_name", true) }.mapNotNull { c ->
            val g = d(c[3])?.takeIf { it > 0 } ?: return@mapNotNull null
            Item(c[0], d(c[1]) ?: 1.0, c[2], g, "generic", d(c[4]) ?: return@mapNotNull null, d(c[5]) ?: 0.0, d(c[6]) ?: 0.0,
                d(c[7]) ?: 0.0, d(c.getOrNull(8)) ?: 0.0, c[0])
        }
        if (items.isEmpty()) return null
        return RecipeImport(head[0], d(head[1])?.takeIf { it > 0 } ?: 1.0, items)
    }

    suspend fun extractRecipe(cfg: AiConfig, text: String, imageJpeg: ByteArray?): RecipeImport {
        if (!cfg.ready) throw AiException("Configurez d'abord une clé API (Plus → Intelligence artificielle).")
        val raw = chat(cfg, RECIPE_PROMPT, if (text.isBlank()) "Extract this recipe." else text.trim(), imageJpeg)
        return parseRecipe(raw) ?: throw AiException(if (raw.contains(NOT_A_FOOD)) "Aucune recette trouvée." else "Réponse du modèle illisible.")
    }

    /** Garde-fou : refuse une correspondance dont la densité énergétique s'écarte trop de l'estimation (×2,5). */
    private fun plausible(it: Item, f: FoodEntity): Boolean {
        if (it.grams <= 0 || it.kcal <= 0 || f.kcal100 <= 0) return true
        val r = (f.kcal100 / 100) / (it.kcal / it.grams)
        return r in 0.4..2.5
    }

    private fun num(v: Double) = if (v.isFinite()) String.format(java.util.Locale.US, "%.1f", v) else "?"

    // ----- Analyse des réponses (DSV « | », comme NutritionLabelFoodDsvParser) -----

    private val stripPipeEnds = Regex("^\\s*\\|\\s*|\\s*\\|\\s*$")
    private val delimiter = Regex("\\s*\\|\\s*")

    private fun rows(raw: String): List<List<String>> = raw.lines()
        .filterNot { it.trim().startsWith("```") }
        .map { it.replace(stripPipeEnds, "").trim() }
        .filter { it.isNotEmpty() && it.contains('|') }
        .map { it.split(delimiter) }

    private fun d(s: String?): Double? = s?.replace(',', '.')?.replace(Regex("[^0-9.\\-]"), "")?.toDoubleOrNull()

    fun parseAnalyzed(raw: String): List<Item> = rows(raw).mapNotNull { c ->
        if (c.size < 10 || c[0].equals("name", true)) return@mapNotNull null
        val grams = d(c[3])?.takeIf { it > 0 } ?: return@mapNotNull null
        val kcal = d(c[5]) ?: return@mapNotNull null
        Item(
            name = c[0], quantity = d(c[1]) ?: 1.0, unit = c[2], grams = grams, type = c[4].lowercase(),
            kcal = kcal, protein = d(c[6]) ?: 0.0, fat = d(c[7]) ?: 0.0, carbs = d(c[8]) ?: 0.0, fiber = d(c[9]) ?: 0.0,
            query = c.getOrNull(10).orEmpty(),
        )
    }

    /** « 1 | 1.3 » ou « 1 | 3 » → numéro de candidat ; « none » ignoré. */
    fun parseSelection(raw: String): Map<Int, Int> = rows(raw).mapNotNull { c ->
        val food = d(c.getOrNull(0))?.toInt() ?: return@mapNotNull null
        val id = c.getOrNull(1)?.trim()?.removePrefix("[")?.removeSuffix("]") ?: return@mapNotNull null
        if (id.equals("none", true)) return@mapNotNull null
        val cand = id.substringAfter('.', id).toIntOrNull() ?: return@mapNotNull null
        food to cand
    }.toMap()

    // ----- Appel réseau (Chat Completions) -----

    suspend fun chat(cfg: AiConfig, system: String, user: String, imageJpeg: ByteArray?, temperature: Double = 0.2, maxTokens: Int? = null): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", cfg.effectiveModel)
            put("temperature", temperature)
            maxTokens?.let { put("max_tokens", it) }
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject { put("type", "text"); put("text", user) }
                        if (imageJpeg != null) addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageJpeg)) }
                        }
                    }
                }
            }
        }
        val endpoint = cfg.effectiveUrl + "/chat/completions"
        if (!Net.canSendSecret(endpoint)) throw AiException("Adresse refusée : HTTPS requis (HTTP seulement vers le réseau local).")
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15000; readTimeout = 90000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${cfg.apiKey.trim()}")
            if (cfg.provider == AiProvider.OPENROUTER) { setRequestProperty("X-Title", "La Bouffe") }
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { Net.readCapped(it) }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull()
                    ?: runCatching { json.parseToJsonElement(text).jsonArray[0].jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull()
                throw AiException("Erreur $code${msg?.let { " : $it" } ?: ""}")
            }
            val root = json.parseToJsonElement(text).jsonObject
            root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                ?: throw AiException("Pas de réponse texte du modèle.")
        } finally { conn.disconnect() }
    }
}
