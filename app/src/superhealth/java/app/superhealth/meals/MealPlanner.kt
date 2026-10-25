package app.superhealth.meals

import android.content.Context
import app.superhealth.SuperPrefs
import app.superhealth.backup.Secrets
import app.superhealth.modules.Modules
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.data.AiFood
import app.superhealth.nutrition.data.FoodEntity
import app.superhealth.nutrition.data.FoodEntryEntity
import app.superhealth.nutrition.data.Net
import app.superhealth.nutrition.data.SwissFoods
import app.superhealth.nutrition.domain.DietPreference
import app.superhealth.nutrition.domain.Macros
import app.superhealth.ui.Lang
import app.superhealth.ui.tr
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Plugin Planificateur de repas : propose les repas qui restent à manger aujourd'hui pour tomber sur les cibles du jour.
 *
 * Deux fournisseurs :
 * - Spoonacular (service gratuit avec clé personnelle, offre « Free ») : son générateur de menus (/mealplanner/generate)
 *   choisit de vraies recettes (photo, temps, lien), dont les valeurs nutritionnelles par portion viennent de Spoonacular ;
 * - l'IA déjà configurée (sinon) : le modèle compose des repas d'aliments simples, chaque aliment est rapproché de la base
 *   locale comme pour la saisie par IA (valeurs de la base quand la correspondance est validée).
 * Dans les deux cas, la portion de chaque repas est ensuite mise à l'échelle pour atteindre exactement sa part des calories
 * restantes (facteur borné à ×0,5–×2 pour garder des portions réalistes ; les grammes sont arrondis à 5 g).
 *
 * Les consignes du modèle et la répartition des repas viennent du plugin (planner.json), jamais de code.
 */
object MealPlanner {
    @Serializable data class Txt(val fr: String, val en: String? = null) {
        val text: String get() = if (Lang.english) en ?: fr else fr
    }

    /** Repas de la journée : libellé, part des calories, heure de journalisation (minutes) ; l'ordre suit FoodEntryEntity.meal. */
    @Serializable data class Slot(val name: Txt, val share: Double, val minute: Int)

    @Serializable data class Content(
        val version: String = "1",
        val system: String,
        val user: String,
        val slots: List<Slot>,
        val temperature: Double = 0.6,
        val maxTokens: Int = 1400,
    )

    @Serializable data class Ingredient(
        val name: String, val grams: Double, val kcal: Double, val protein: Double, val fat: Double, val carbs: Double,
        val fiber: Double = 0.0, val foodId: Long? = null,
    )

    @Serializable data class Meal(
        val slot: Int,
        val title: String,
        val ingredients: List<Ingredient> = emptyList(),
        /** Spoonacular : une portion de la recette et le nombre de portions proposé. */
        val perServing: List<Double>? = null,
        val servings: Double? = null,
        val minutes: Int? = null,
        val url: String? = null,
        val image: String? = null,
        val logged: Boolean = false,
    ) {
        val macros: Macros get() = perServing?.let { val k = servings ?: 1.0; Macros(it[0] * k, it[1] * k, it[2] * k, it[3] * k) }
            ?: ingredients.fold(Macros.ZERO) { a, i -> a + Macros(i.kcal, i.protein, i.fat, i.carbs) }
    }

    @Serializable data class Plan(val day: Long, val provider: String, val meals: List<Meal>, val createdAt: Long = System.currentTimeMillis())

    class PlanException(message: String) : Exception(message)

    const val SPOONACULAR_KEY = "spoonacular_key"
    private const val SPOONACULAR = "https://api.spoonacular.com"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var dir: File

    /** Plan par jour (les 7 derniers gardés). */
    val plans = MutableStateFlow<Map<Long, Plan>>(emptyMap())

    @Volatile private var content: Content? = null
    fun content(): Content? = content ?: runCatching {
        json.decodeFromString(Content.serializer(), Modules.file(Modules.MEALS, "planner.json")!!.readText())
    }.getOrNull()?.also { content = it }

    fun invalidate() { content = null }

    fun available(): Boolean = Modules.isActive(Modules.MEALS)

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "meals").apply { mkdirs() }
        plans.value = runCatching { json.decodeFromString<Map<Long, Plan>>(File(dir, "plans.json").readText()) }.getOrDefault(emptyMap())
    }

    private fun save(map: Map<Long, Plan>) {
        plans.value = map.entries.sortedByDescending { it.key }.take(7).associate { it.key to it.value }
        runCatching { File(dir, "plans.json").writeText(json.encodeToString(plans.value)) }
    }

    fun clear(day: Long) = save(plans.value - day)

    fun hasSpoonacular(ctx: Context) = !Secrets.get(ctx, SPOONACULAR_KEY).isNullOrBlank()

    // ---- Génération -------------------------------------------------------------------------------------

    /**
     * Repas à prévoir : ceux de la journée où rien n'est encore journalisé ; chacun reçoit sa part (renormalisée) de ce qui
     * reste des cibles. Tout est déjà mangé mais il reste des calories : une collation.
     */
    suspend fun generate(ctx: Context, day: Long, target: Macros, logged: List<FoodEntryEntity>): Plan {
        val c = content() ?: throw PlanException(tr("Contenu du plugin introuvable : réinstallez-le dans Réglages > Plugins."))
        val left = left(target, logged)
        if (left.kcal < 80) throw PlanException(tr("Vos cibles du jour sont déjà atteintes."))
        val usedSlots = logged.map { it.meal }.toSet()
        var open = c.slots.indices.filter { it !in usedSlots }
        if (open.isEmpty()) open = listOf(c.slots.lastIndex)
        val shareSum = open.sumOf { c.slots[it].share }
        val goals = goals(c, left, open)

        val provider = if (hasSpoonacular(ctx)) "spoonacular" else "ai"
        // Spoonacular planifie une journée entière (petit-déjeuner, déjeuner, dîner) : calories de ces trois repas à ce rythme
        val mainShare = c.slots.take(3).sumOf { it.share }
        val meals = if (provider == "spoonacular") spoonacular(ctx, goals, left.kcal / shareSum * mainShare) else ai(ctx.applicationContext, c, goals, logged)
        val plan = Plan(day, provider, meals.sortedBy { it.slot })
        save(plans.value + (day to plan))
        return plan
    }

    /** Ce qui reste des cibles du jour (jamais négatif). */
    fun left(target: Macros, logged: List<FoodEntryEntity>): Macros {
        val eaten = logged.fold(Macros.ZERO) { a, e -> a + Macros(e.kcal, e.protein, e.fat, e.carbs) }
        return Macros((target.kcal - eaten.kcal).coerceAtLeast(0.0), (target.protein - eaten.protein).coerceAtLeast(0.0),
            (target.fat - eaten.fat).coerceAtLeast(0.0), (target.carbs - eaten.carbs).coerceAtLeast(0.0))
    }

    /** Part de [left] de chaque repas de [open] (parts du plugin renormalisées). */
    private fun goals(c: Content, left: Macros, open: List<Int>): Map<Int, Macros> {
        val shareSum = open.sumOf { c.slots[it].share }
        return open.associateWith { i -> val k = c.slots[i].share / shareSum; Macros(left.kcal * k, left.protein * k, left.fat * k, left.carbs * k) }
    }

    /**
     * Cible d'un repas choisi : sa part de ce qui reste, partagée avec les autres repas encore vides (un repas déjà commencé
     * garde sa part, ce qui y est déjà journalisé étant compté dans ce qui reste).
     */
    fun slotGoal(target: Macros, logged: List<FoodEntryEntity>, slot: Int): Macros? {
        val c = content() ?: return null
        val used = logged.map { it.meal }.toSet()
        val open = (c.slots.indices.filter { it !in used } + slot).distinct()
        return goals(c, left(target, logged), open)[slot]
    }

    /**
     * Repas composé avec les aliments choisis : grammes calculés sur l'appareil ([Portions]), aucune IA ni réseau.
     * Remplace un éventuel repas déjà prévu pour le même créneau.
     */
    fun compose(day: Long, slot: Int, foods: List<FoodEntity>, goal: Macros): Pair<Meal, Portions.Result> {
        val r = Portions.solve(foods.map { Portions.Food(it.kcal100, it.protein100, it.fat100, it.carbs100) },
            Portions.Goal(goal.kcal, goal.protein, goal.fat, goal.carbs))
        val ings = foods.zip(r.grams).map { (f, g) ->
            val k = g / 100
            Ingredient(f.name, g, f.kcal100 * k, f.protein100 * k, f.fat100 * k, f.carbs100 * k, f.fiber100 * k, f.id.takeIf { it != 0L })
        }
        val title = foods.take(3).joinToString(", ") { it.name } + if (foods.size > 3) "…" else ""
        val meal = Meal(slot, title, ings)
        val p = plans.value[day]
        val others = p?.meals.orEmpty().filter { it.slot != slot }
        save(plans.value + (day to Plan(day, if (p == null || others.isEmpty()) "foods" else p.provider, (others + meal).sortedBy { it.slot })))
        return meal to r
    }

    /** Facteur qui amène [kcal] sur [goal] (borné), pour des portions réalistes. */
    private fun scale(kcal: Double, goal: Double) = if (kcal <= 0) 1.0 else (goal / kcal).coerceIn(0.5, 2.0)

    // ---- Fournisseur IA ---------------------------------------------------------------------------------------

    private suspend fun ai(ctxApp: Context, c: Content, goals: Map<Int, Macros>, logged: List<FoodEntryEntity>): List<Meal> {
        val cfg = NutriApp.repository.prefs.ai.value
        if (!cfg.ready) throw PlanException(tr("Ajoutez une clé Spoonacular (gratuite) ou configurez l'IA (Nutrition > Intelligence artificielle)."))
        val program = NutriApp.repository.prefs.program.value
        val diet = when (program.dietPreference) {
            DietPreference.BALANCED -> "balanced"; DietPreference.LOW_FAT -> "low fat"; DietPreference.LOW_CARB -> "low carb"; DietPreference.KETO -> "ketogenic"
        }
        fun n(v: Double) = v.roundToInt().toString()
        val slots = goals.entries.joinToString("\n") { (i, m) ->
            "- slot $i (${c.slots[i].name.en ?: c.slots[i].name.fr}): ${n(m.kcal)} kcal, protein ${n(m.protein)} g, fat ${n(m.fat)} g, carbs ${n(m.carbs)} g"
        }
        val user = c.user
            .replace("{slots}", slots)
            .replace("{diet}", diet)
            .replace("{exclude}", SuperPrefs.settings.value.mealsExclude.ifBlank { "nothing" })
            .replace("{eaten}", logged.joinToString(", ") { it.name }.ifBlank { "nothing yet" })
            .replace("{language}", if (Lang.english) "English" else "French")
        val raw = AiFood.chat(cfg, c.system.replace("{language}", if (Lang.english) "English" else "French"), user, null, c.temperature, c.maxTokens)
        // slot | dish | ingredient | weight_g | kcal | protein_g | fat_g | carbs_g | fiber_g | search_query
        data class Row(val slot: Int, val dish: String, val item: AiFood.Item)
        val rows = raw.lines().map { it.trim().trim('|') }.filter { it.count { ch -> ch == '|' } >= 9 }.mapNotNull { l ->
            val p = l.split('|').map { it.trim() }
            val slot = p[0].filter { it.isDigit() }.toIntOrNull()?.takeIf { it in goals } ?: return@mapNotNull null
            fun d(i: Int) = p.getOrNull(i)?.replace(',', '.')?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull() ?: 0.0
            val g = d(3).takeIf { it > 0 } ?: return@mapNotNull null
            Row(slot, p[1], AiFood.Item(p[2], g, "g", g, "generic", d(4), d(5), d(6), d(7), d(8), p.getOrNull(9) ?: p[2]))
        }
        if (rows.isEmpty()) throw PlanException(tr("Réponse du modèle illisible."))
        val swiss = NutriApp.repository.prefs.settings.value.swissDbEnabled
        val matched = AiFood.match(cfg, rows.map { it.item }, candidates = { q ->
            withContext(Dispatchers.Default) {
                NutriApp.repository.foods.search(q).take(3) + (if (swiss) SwissFoods.search(ctxApp, q, 5).map(SwissFoods::toFood) else emptyList())
            }
        })
        val saved = HashMap<String, FoodEntity>()
        return rows.zip(matched).groupBy { it.first.slot }.map { (slot, list) ->
            // valeurs de la base quand la correspondance est validée, sinon l'estimation du modèle
            val base = list.map { (_, it) ->
                val f = it.match
                if (f != null) {
                    // aliment de la bibliothèque tel quel ; base suisse : ajouté une fois à la bibliothèque (dédupliqué par nom)
                    val food = if (f.id != 0L) f else saved.getOrPut(f.name + f.brand) { NutriApp.repository.saveFood(f) }
                    val k = it.grams / 100
                    Ingredient(food.name, it.grams, food.kcal100 * k, food.protein100 * k, food.fat100 * k, food.carbs100 * k, food.fiber100 * k, food.id)
                } else Ingredient(it.name, it.grams, it.kcal, it.protein, it.fat, it.carbs, it.fiber)
            }
            val s = scale(base.sumOf { it.kcal }, goals.getValue(slot).kcal)
            Meal(slot, list.first().first.dish, base.map { i ->
                val g = (i.grams * s / 5).roundToInt().coerceAtLeast(1) * 5.0
                val k = g / i.grams
                i.copy(grams = g, kcal = i.kcal * k, protein = i.protein * k, fat = i.fat * k, carbs = i.carbs * k, fiber = i.fiber * k)
            })
        }
    }

    // ---- Fournisseur Spoonacular -----------------------------------------------------------------------------

    private suspend fun spoonacular(ctx: Context, goals: Map<Int, Macros>, dayKcal: Double): List<Meal> = withContext(Dispatchers.IO) {
        val key = Secrets.get(ctx, SPOONACULAR_KEY)!!.trim()
        val diet = when (NutriApp.repository.prefs.program.value.dietPreference) { DietPreference.KETO -> "ketogenic"; else -> null }
        val q = buildString {
            append("timeFrame=day&targetCalories=").append(dayKcal.roundToInt())
            diet?.let { append("&diet=").append(it) }
            SuperPrefs.settings.value.mealsExclude.takeIf { it.isNotBlank() }?.let { append("&exclude=").append(enc(it)) }
        }
        val gen = get("$SPOONACULAR/mealplanner/generate?$q", key)
        val recipes = gen["meals"]?.jsonArray.orEmpty().map { it.jsonObject }
        if (recipes.isEmpty()) throw PlanException(tr("Spoonacular n'a proposé aucun repas."))
        val ids = recipes.mapNotNull { it["id"]?.jsonPrimitive?.longOrNull }
        val info = get("$SPOONACULAR/recipes/informationBulk?ids=${ids.joinToString(",")}&includeNutrition=true", key, array = true)
        val byId = info["items"]!!.jsonArray.map { it.jsonObject }.associateBy { it["id"]?.jsonPrimitive?.longOrNull }
        // recette i du générateur → repas i (0 petit-déjeuner, 1 déjeuner, 2 dîner) s'il reste à manger ;
        // tout est mangé sauf la collation : la recette du petit-déjeuner, en portion de collation
        val pairs = goals.keys.filter { it <= 2 }.map { it to it }.ifEmpty { goals.keys.map { it to 0 } }
        pairs.mapNotNull { (slot, i) ->
            val r = recipes.getOrNull(i) ?: return@mapNotNull null
            val id = r["id"]?.jsonPrimitive?.longOrNull
            val full = byId[id] ?: r
            val nutr = full["nutrition"]?.jsonObject?.get("nutrients")?.jsonArray.orEmpty().map { it.jsonObject }
            fun v(name: String) = nutr.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == name }?.get("amount")?.jsonPrimitive?.doubleOrNull ?: 0.0
            val per = listOf(v("Calories"), v("Protein"), v("Fat"), v("Carbohydrates"))
            val servings = if (per[0] > 0) ((goals.getValue(slot).kcal / per[0]) * 4).roundToInt().coerceIn(2, 8) / 4.0 else 1.0
            Meal(slot, full["title"]?.jsonPrimitive?.contentOrNull ?: "?",
                ingredients = full["extendedIngredients"]?.jsonArray.orEmpty().mapNotNull { e ->
                    e.jsonObject["original"]?.jsonPrimitive?.contentOrNull?.let { Ingredient(it, 0.0, 0.0, 0.0, 0.0, 0.0) }
                },
                perServing = per, servings = servings,
                minutes = full["readyInMinutes"]?.jsonPrimitive?.intOrNull,
                url = full["sourceUrl"]?.jsonPrimitive?.contentOrNull ?: full["spoonacularSourceUrl"]?.jsonPrimitive?.contentOrNull,
                image = full["image"]?.jsonPrimitive?.contentOrNull ?: id?.let { "https://img.spoonacular.com/recipes/$it-312x231.jpg" })
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** GET JSON (clé dans l'en-tête, pas dans l'adresse) ; une réponse tableau est rendue sous « items ». */
    private fun get(url: String, key: String, array: Boolean = false): JsonObject {
        if (!Net.canSendSecret(url)) throw PlanException(tr("Adresse refusée : HTTPS requis (HTTP seulement vers le réseau local)."))
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000
            setRequestProperty("x-api-key", key)
        }
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { Net.readCapped(it) }.orEmpty()
            if (code == 401 || code == 403) throw PlanException(tr("Clé Spoonacular refusée."))
            if (code == 402) throw PlanException(tr("Quota gratuit de Spoonacular atteint pour aujourd'hui."))
            if (code !in 200..299) throw PlanException("Spoonacular : erreur $code")
            val el = json.parseToJsonElement(text)
            return if (array) JsonObject(mapOf("items" to el.jsonArray)) else el.jsonObject
        } finally { conn.disconnect() }
    }

    // ---- Journal -------------------------------------------------------------------------------------------

    /** Ajoute le repas au journal du jour, à l'heure de son créneau (ou maintenant si l'heure est passée). */
    suspend fun log(day: Long, meal: Meal, minute: Int) {
        val repo = NutriApp.repository
        val c = content()
        val at = c?.slots?.getOrNull(meal.slot)?.minute ?: minute
        if (meal.perServing != null) {
            val m = meal.macros
            repo.entries.insert(FoodEntryEntity(epochDay = day, meal = meal.slot, minuteOfDay = at,
                name = meal.title + " (" + app.superhealth.ui.formatNumber(meal.servings ?: 1.0, 2) + " " + tr("portion") + ")",
                kcal = m.kcal, protein = m.protein, fat = m.fat, carbs = m.carbs))
        } else meal.ingredients.forEach { i ->
            repo.entries.insert(FoodEntryEntity(epochDay = day, meal = meal.slot, minuteOfDay = at, foodId = i.foodId, name = i.name, grams = i.grams,
                kcal = i.kcal, protein = i.protein, fat = i.fat, carbs = i.carbs, fiber = i.fiber))
            i.foodId?.let { repo.foods.markUsed(it) }
        }
        plans.value[day]?.let { p -> save(plans.value + (day to p.copy(meals = p.meals.map { if (it == meal) it.copy(logged = true) else it }))) }
    }
}
