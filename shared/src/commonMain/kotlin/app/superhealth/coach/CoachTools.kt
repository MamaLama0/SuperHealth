package app.superhealth.coach

import app.superhealth.platform.*

import app.superhealth.platform.Context
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.Metric
import app.superhealth.health.data.Nights
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.data.Micros
import app.superhealth.nutrition.domain.Macros
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.Analytics as TrainingAnalytics
import app.superhealth.training.domain.Periodization
import app.superhealth.training.domain.Program
import app.superhealth.training.domain.ProgramDay
import app.superhealth.training.domain.ProgramExercise
import app.superhealth.training.domain.TargetSet
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Outils du coach (appel de fonctions « tools » des API Chat Completions). Le résumé de 14 jours reste la base ; quand il
 * faut plus (un repas précis, un exercice sur 6 mois, une nuit, le programme complet), le modèle appelle un outil de
 * lecture, exécuté sur le téléphone, qui respecte les catégories partagées (Coach.Share).
 *
 * Les outils qui agissent (créer un programme, journaliser un repas, ouvrir un écran) ne font rien eux-mêmes : ils
 * déposent une proposition sous la réponse, appliquée seulement quand l'utilisateur touche son bouton.
 */
object CoachTools {
    /** Proposition du coach : [kind] = program / food / open ; [payload] = arguments JSON de l'outil. */
    @Serializable data class Action(val kind: String, val title: String, val detail: String, val payload: String, val done: Boolean = false)

    /** Ce qu'un outil a consulté, affiché en pastilles sous la réponse (« a regardé : Séances, Sommeil »). */
    private val labels = mapOf(
        "get_food_log" to "Journal", "get_nutrition_targets" to "Cibles", "get_workouts" to "Séances", "get_exercise_history" to "Exercice",
        "get_programs" to "Programmes", "search_exercises" to "Exercices", "get_health" to "Mesures", "get_sleep" to "Sommeil",
        "get_body" to "Poids", "get_activities" to "Activités",
    )
    fun label(tool: String) = labels[tool]

    /** Écrans que le coach peut proposer d'ouvrir (bouton sous sa réponse). */
    val SCREENS = mapOf(
        "sleep" to "h/metric/SLEEP", "vitals" to "h/vitals", "food_log" to "log", "nutrients" to "nutrients", "weight" to "weight",
        "expenditure" to "expenditure", "strategy" to "strategy", "activities" to "activities", "training_insights" to "t/insights",
        "form" to "form", "journal" to "journal", "body" to "body", "performance" to "h/performance", "history" to "history",
    )

    // ---- Définitions envoyées au modèle -----------------------------------------------------------------

    private fun fn(name: String, description: String, props: JsonObject = JsonObject(emptyMap()), required: List<String> = emptyList()) = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", name); put("description", description)
            putJsonObject("parameters") {
                put("type", "object"); put("properties", props)
                putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
            }
        }
    }
    private fun p(type: String, description: String, enum: List<String>? = null) = buildJsonObject {
        put("type", type); put("description", description)
        enum?.let { e -> putJsonArray("enum") { e.forEach { add(JsonPrimitive(it)) } } }
    }
    private fun props(vararg ps: Pair<String, JsonObject>) = JsonObject(ps.toMap())

    private val healthMetrics = listOf(Metric.STEPS, Metric.RESTING_HR, Metric.HRV, Metric.RESPIRATORY_RATE, Metric.SPO2, Metric.VO2MAX,
        Metric.BODY_ENERGY, Metric.STRESS, Metric.RECOVERY, Metric.EFFORT, Metric.CARDIO_WEEK, Metric.HYDRATION, Metric.TEMPERATURE)

    fun definitions(): JsonArray = buildJsonArray {
        val date = "Date YYYY-MM-DD"
        add(fn("get_food_log", "Food diary between two dates (max 31 days): every item with grams, kcal, protein, fat, carbs, plus daily vitamins, minerals, fiber, sugar, sodium, caffeine and alcohol totals.",
            props("from" to p("string", date), "to" to p("string", date)), listOf("from", "to")))
        add(fn("get_nutrition_targets", "Current calorie and macro targets, expenditure (TDEE), goal, diet and protein preferences."))
        add(fn("get_workouts", "Strength sessions between two dates (max 90 days) with every exercise and set (weight, reps, RIR), duration and perceived effort.",
            props("from" to p("string", date), "to" to p("string", date)), listOf("from", "to")))
        add(fn("get_exercise_history", "Best set and estimated 1RM per session for an exercise (searched by name), all history.",
            props("query" to p("string", "Exercise name, e.g. bench press")), listOf("query")))
        add(fn("get_programs", "All training programs: days, exercises (with ids), sets, rep ranges, RIR, rest; which one is active and the next session."))
        add(fn("search_exercises", "Search the exercise database (ids are needed to propose a program). Filters are optional.",
            props("query" to p("string", "Name or muscle, e.g. 'squat', 'chest'"), "available_only" to p("boolean", "Only exercises doable with the user's gym equipment"),
                "limit" to p("integer", "Max results, default 15"))))
        add(fn("get_health", "Daily values of a health metric from the watch / Health Connect (max 180 days).",
            props("metric" to p("string", "Metric", healthMetrics.map { it.name }), "days" to p("integer", "Number of days back from today")), listOf("metric", "days")))
        add(fn("get_sleep", "Nights with bedtime, wake time, time asleep, deep/REM/light minutes, awakenings and efficiency (max 60 nights).",
            props("days" to p("integer", "Number of nights back from last night")), listOf("days")))
        add(fn("get_body", "Weight trend (weekly, last 26 weeks), scale weigh-ins and body measurements."))
        add(fn("get_activities", "Cardio and other watch / Health Connect activities (max 90 days).",
            props("days" to p("integer", "Number of days back")), listOf("days")))
        add(fn("propose_program", "Propose a strength program. Nothing is created until the user taps the button under your answer. Use exercise ids from search_exercises or get_programs.",
            props("name" to p("string", "Program name"), "weeks" to p("integer", "Number of weeks (cycles), 4 to 12"),
                "days" to buildJsonObject {
                    put("type", "array"); put("description", "Training days in order (a rest day = a day with no exercise)")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            put("name", p("string", "Day name, e.g. Upper A"))
                            putJsonObject("exercises") {
                                put("type", "array")
                                putJsonObject("items") {
                                    put("type", "object")
                                    put("properties", props("exercise_id" to p("string", "Exercise id"), "sets" to p("integer", "Working sets"),
                                        "rep_min" to p("integer", "Min reps"), "rep_max" to p("integer", "Max reps"), "rir" to p("integer", "Reps in reserve"),
                                        "rest_sec" to p("integer", "Rest seconds")))
                                    putJsonArray("required") { listOf("exercise_id", "sets", "rep_min", "rep_max").forEach { add(JsonPrimitive(it)) } }
                                }
                            }
                        }
                        putJsonArray("required") { add(JsonPrimitive("name")); add(JsonPrimitive("exercises")) }
                    }
                }), listOf("name", "days")))
        add(fn("propose_food_log", "Propose to add foods to today's diary (estimated values). Nothing is logged until the user taps the button.",
            props("time" to p("string", "HH:mm, default now"), "items" to buildJsonObject {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    put("properties", props("name" to p("string", "Food"), "grams" to p("number", "Grams"), "kcal" to p("number", "kcal"),
                        "protein" to p("number", "g"), "fat" to p("number", "g"), "carbs" to p("number", "g")))
                    putJsonArray("required") { listOf("name", "kcal", "protein", "fat", "carbs").forEach { add(JsonPrimitive(it)) } }
                }
            }), listOf("items")))
        add(fn("show_screen", "Add a button under your answer that opens a screen of the app, when looking at it helps.",
            props("screen" to p("string", "Screen", SCREENS.keys.toList()), "label" to p("string", "Short button label in the user's language")), listOf("screen")))
    }

    /** Consignes ajoutées au prompt système quand les outils sont disponibles. */
    const val GUIDE = "\n\n# Tools\nThe data snapshot covers 14 days. Call the get_* tools when you need more detail or a longer period " +
        "(exact meals, micronutrients, every set, an exercise's history, nights, programs). To build or change a training program, " +
        "first read get_programs and search_exercises (available_only true), then call propose_program; to log food the user describes, call " +
        "propose_food_log; when a screen of the app shows what you talk about, call show_screen. The user confirms proposals with a button: " +
        "never say something was created or logged, say you propose it. Keep answers short."

    // ---- Exécution -------------------------------------------------------------------------------

    class Outcome(val result: String, val action: Action? = null)

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull ?: (this[k] as? JsonPrimitive)?.doubleOrNull?.roundToInt()
    private fun JsonObject.dbl(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull == "true"
    private fun date(s: String?, def: LocalDate) = s?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: def
    private fun n(v: Double, d: Int = 0) = if (d == 0) v.roundToInt().toString() else String.format(Locale.US, "%.${d}f", v)
    private const val HIDDEN = "Not available: the user chose not to share this category with the coach."

    suspend fun run(ctx: Context, name: String, args: JsonObject): Outcome = runCatching { exec(ctx, name, args) }
        .getOrElse { Outcome("Error: ${it.message ?: it.javaClass.simpleName}") }
        .let { o -> Outcome(o.result.take(12_000), o.action) }

    private suspend fun exec(ctx: Context, name: String, a: JsonObject): Outcome {
        val today = LocalDate.now()
        return when (name) {
            "get_food_log" -> {
                if (!Coach.shares(Coach.Share.FOOD)) return Outcome(HIDDEN)
                val to = date(a.str("to"), today); val from = date(a.str("from"), to.minusDays(6)).let { if (it.isBefore(to.minusDays(30))) to.minusDays(30) else it }
                val list = NutriApp.repository.entries.rangeFlow(from.toEpochDay(), to.toEpochDay()).first().groupBy { it.epochDay }
                Outcome(buildString {
                    generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.forEach { d ->
                        val items = list[d.toEpochDay()].orEmpty().sortedBy { it.minuteOfDay ?: 0 }
                        appendLine("## $d" + if (items.isEmpty()) ": nothing logged" else ": ${n(items.sumOf { it.kcal })} kcal, P ${n(items.sumOf { it.protein })} g, F ${n(items.sumOf { it.fat })} g, C ${n(items.sumOf { it.carbs })} g, fiber ${n(items.sumOf { it.fiber }, 1)} g")
                        items.forEach { e -> appendLine("- ${e.minuteOfDay.takeIf { it >= 0 }?.let { "%02d:%02d ".format(it / 60, it % 60) } ?: ""}${e.name}" + (e.grams?.let { " ${n(it)} g" } ?: "") +
                            ": ${n(e.kcal)} kcal, P ${n(e.protein, 1)}, F ${n(e.fat, 1)}, C ${n(e.carbs, 1)}") }
                        val micros = Micros.sum(items.map { it.microsJson })
                        if (micros.isNotEmpty()) appendLine("Micros: " + micros.entries.mapNotNull { (k, v) -> Micros.byCode[k]?.let { "${it.code} ${n(v, if (v < 10) 1 else 0)} ${it.unit}" } }.joinToString(", "))
                    }
                })
            }
            "get_nutrition_targets" -> {
                if (!Coach.shares(Coach.Share.FOOD)) return Outcome(HIDDEN)
                val an = NutriApp.repository.analytics().first()
                val t = an.targetFor(today.toEpochDay()).macros
                Outcome("Targets today: ${n(t.kcal)} kcal, protein ${n(t.protein)} g, fat ${n(t.fat)} g, carbs ${n(t.carbs)} g. Expenditure ${n(an.currentTdee)} kcal/day. " +
                    (an.currentGoal?.let { "Goal ${it.type.name.lowercase()}, rate ${n(it.ratePctPerWeek, 2)} %/week" + (it.targetWeightKg?.let { w -> ", target ${n(w, 1)} kg" } ?: "") } ?: "No goal.") +
                    " Week targets: " + (0..6).joinToString("; ") { i -> val d = today.plusDays(i.toLong()); "${d.dayOfWeek.name.take(3)} ${n(an.targetFor(d.toEpochDay()).macros.kcal)}" })
            }
            "get_workouts" -> {
                if (!Coach.shares(Coach.Share.TRAINING)) return Outcome(HIDDEN)
                val to = date(a.str("to"), today); val from = date(a.str("from"), to.minusDays(13)).let { if (it.isBefore(to.minusDays(89))) to.minusDays(89) else it }
                val db = withTimeoutOrNull(2000) { WorkoutApp.db.filterNotNull().first() }
                val list = WorkoutApp.store.state.value.history.filter { val d = TrainingAnalytics.day(it.startTime); !d.isBefore(from) && !d.isAfter(to) }.sortedBy { it.startTime }
                Outcome(if (list.isEmpty()) "No session in this period." else buildString {
                    list.forEach { w ->
                        appendLine("## ${TrainingAnalytics.day(w.startTime)} ${w.name}: ${w.durationSec / 60} min" + (w.rpe?.let { ", RPE $it/10" } ?: "") + (w.note.takeIf { it.isNotBlank() }?.let { ", note: $it" } ?: ""))
                        w.exercises.forEach { ex ->
                            appendLine("- ${db?.exercise(ex.exerciseId)?.name ?: ex.exerciseId}: " + ex.sets.filter { it.done }.joinToString(", ") { s ->
                                (if (s.type.name == "WARMUP") "w " else "") + "${s.weight?.let { n(it, 1) } ?: "bw"}×${s.reps ?: 0}" + (s.rir?.let { "@$it" } ?: "") })
                        }
                    }
                })
            }
            "get_exercise_history" -> {
                if (!Coach.shares(Coach.Share.TRAINING)) return Outcome(HIDDEN)
                val db = withTimeoutOrNull(2000) { WorkoutApp.db.filterNotNull().first() } ?: return Outcome("Exercise database not loaded.")
                val history = WorkoutApp.store.state.value.history.sortedBy { it.startTime }
                val done = history.flatMap { w -> w.exercises.map { it.exerciseId } }.toSet()
                val match = db.search(a.str("query").orEmpty()).firstOrNull { it.id in done } ?: return Outcome("No logged exercise matches.")
                Outcome("${match.name}:\n" + history.mapNotNull { w ->
                    val sets = w.exercises.filter { it.exerciseId == match.id }.flatMap { it.sets }.filter { it.done && it.type.name != "WARMUP" && it.reps != null }
                    val best = sets.maxByOrNull { (it.weight ?: 0.0) * (1 + (it.reps ?: 0) / 30.0) } ?: return@mapNotNull null
                    "${TrainingAnalytics.day(w.startTime)}: best ${best.weight?.let { n(it, 1) } ?: "bw"}×${best.reps}" + (best.rir?.let { "@RIR $it" } ?: "") +
                        (best.weight?.let { ", e1RM ${n(it * (1 + best.reps!! / 30.0), 1)} kg" } ?: "") + ", ${sets.size} sets"
                }.takeLast(60).joinToString("\n"))
            }
            "get_programs" -> {
                if (!Coach.shares(Coach.Share.TRAINING)) return Outcome(HIDDEN)
                val st = WorkoutApp.store.state.value
                val db = withTimeoutOrNull(2000) { WorkoutApp.db.filterNotNull().first() }
                val gym = st.gyms.firstOrNull { it.id == st.settings.activeGymId }
                Outcome(buildString {
                    appendLine("Experience: ${st.settings.experience.name.lowercase()}" + (gym?.let { ", gym: ${it.name} (${it.equipmentIds.size} equipment items)" } ?: ""))
                    if (st.programs.none { !it.archived }) appendLine("No program yet.")
                    st.programs.filter { !it.archived }.forEach { p ->
                        appendLine("## ${p.name}" + (if (p.id == st.settings.activeProgramId) " (ACTIVE, week ${p.cycleIndex + 1}/${p.numCycles}, next day ${p.days.getOrNull(p.dayIndex)?.name})" else "") +
                            ", goal ${p.goal.name.lowercase()}")
                        p.days.forEach { d ->
                            appendLine("- ${d.name}" + if (d.isRest) ": rest" else ":")
                            d.exercises.forEach { pe ->
                                val t = pe.cycles.getOrNull(minOf(p.cycleIndex, pe.cycles.lastIndex))?.filter { it.type.name != "WARMUP" }.orEmpty()
                                appendLine("  - ${db?.exercise(pe.exerciseId)?.name ?: "?"} [${pe.exerciseId}]: ${t.size}×${t.firstOrNull()?.let { "${it.minReps}-${it.maxReps} @RIR ${it.rir}, rest ${it.restSec}s" } ?: ""}")
                            }
                        }
                    }
                })
            }
            "search_exercises" -> {
                val db = withTimeoutOrNull(2000) { WorkoutApp.db.filterNotNull().first() } ?: return Outcome("Exercise database not loaded.")
                val st = WorkoutApp.store.state.value
                val owned = st.gyms.firstOrNull { it.id == st.settings.activeGymId }?.equipmentIds
                val pool = if (a.bool("available_only") && owned != null) db.exercises.filter { db.isAvailable(it, owned) } else db.exercises
                val q = a.str("query").orEmpty()
                val found = (if (q.isBlank()) pool else db.search(q, pool).ifEmpty {
                    pool.filter { e -> (e.primaryGroups + e.primaryMuscles).any { it.contains(q, true) } }
                }).take((a.int("limit") ?: 15).coerceIn(1, 40))
                Outcome(found.joinToString("\n") { e -> "[${e.id}] ${e.name} (${e.type}; ${e.primaryGroups.joinToString("/")})" }.ifEmpty { "No match." })
            }
            "get_health" -> {
                if (!Coach.shares(Coach.Share.HEALTH)) return Outcome(HIDDEN)
                val m = runCatching { Metric.valueOf(a.str("metric").orEmpty()) }.getOrNull()?.takeIf { it in healthMetrics } ?: return Outcome("Unknown metric.")
                if (!Bridge.hasSource(m)) return Outcome("No source for ${m.name}.")
                val days = (a.int("days") ?: 30).coerceIn(1, 180)
                val vals = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { d -> d to runCatching { Bridge.healthValue(ctx, m, d) }.getOrNull() }
                Outcome("${m.name} (${m.unit ?: ""}):\n" + vals.filter { it.second != null }.joinToString("\n") { (d, v) -> "$d ${n(v!!, 1)}" }.ifEmpty { "no data" })
            }
            "get_sleep" -> {
                if (!Coach.shares(Coach.Share.HEALTH)) return Outcome(HIDDEN)
                val days = (a.int("days") ?: 14).coerceIn(1, 60)
                val nights = Nights.range(today.minusDays(days.toLong() - 1), today).mapNotNull { it.second }
                val zone = ZoneId.systemDefault()
                fun clock(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().toString().take(5)
                Outcome(nights.joinToString("\n") { s ->
                    "${s.date}: ${clock(s.bedMs)}-${clock(s.wakeMs)}, asleep ${n(s.asleepMin / 60, 1)} h" +
                        (if (s.staged) ", deep ${n(s.deepMin)} min, REM ${n(s.remMin)} min, light ${n(s.lightMin)} min, ${s.awakenings} awakenings, efficiency ${n(s.efficiency * 100)} %" else "")
                }.ifEmpty { "No night recorded." })
            }
            "get_body" -> {
                if (!Coach.shares(Coach.Share.WEIGHT)) return Outcome(HIDDEN)
                val repo = NutriApp.repository
                val an = repo.analytics().first()
                val t0 = today.toEpochDay()
                val weekly = (25 downTo 0).mapNotNull { w -> an.trendByDay.filterKeys { it <= t0 - w * 7 }.maxByOrNull { it.key }?.let { (d, v) -> "${LocalDate.ofEpochDay(d)} ${n(v, 1)}" } }.distinct()
                val scale = an.days.filter { it.weightKg != null && it.epochDay > t0 - 30 }.joinToString(", ") { "${LocalDate.ofEpochDay(it.epochDay)} ${n(it.weightKg!!, 1)}" }
                val metrics = repo.body.metricsFlow().first().groupBy { it.metric }.mapValues { (_, l) -> l.sortedBy { it.epochDay }.takeLast(4) }
                Outcome("Trend weight (kg), weekly:\n" + weekly.joinToString("\n") + "\nScale (30 d): $scale\n" +
                    metrics.entries.joinToString("\n") { (k, l) -> "Measure $k (cm): " + l.joinToString(", ") { "${LocalDate.ofEpochDay(it.epochDay)} ${n(it.value, 1)}" } })
            }
            "get_activities" -> {
                if (!Coach.shares(Coach.Share.ACTIVITIES)) return Outcome(HIDDEN)
                val days = (a.int("days") ?: 30).coerceIn(1, 90)
                val from = today.minusDays(days.toLong() - 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val acts = Bridge.activities(from).filter { it.source != Bridge.Source.GYM }.sortedBy { it.start }
                Outcome(acts.joinToString("\n") { x ->
                    "${TrainingAnalytics.day(x.start)} ${x.title} (${x.kind.name.lowercase()}), ${x.minutes} min" +
                        (x.distanceM?.takeIf { it > 0 }?.let { ", ${n(it / 1000, 2)} km" } ?: "") + ((x.watch?.kcal ?: x.run?.kcal)?.takeIf { it > 0 }?.let { ", ${n(it)} kcal" } ?: "")
                }.ifEmpty { "No activity." })
            }
            "propose_program" -> {
                val db = withTimeoutOrNull(2000) { WorkoutApp.db.filterNotNull().first() } ?: return Outcome("Exercise database not loaded.")
                val days = a["days"]?.jsonArray.orEmpty().map { it.jsonObject }
                val unknown = days.flatMap { d -> d["exercises"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.str("exercise_id")?.takeIf { id -> db.exercise(id) == null } } }
                if (days.isEmpty()) return Outcome("Error: no day.")
                if (unknown.isNotEmpty()) return Outcome("Error: unknown exercise ids ${unknown.joinToString()}. Use search_exercises.")
                val name = a.str("name") ?: "Programme"
                val training = days.count { d -> d["exercises"]?.jsonArray.orEmpty().isNotEmpty() }
                Outcome("Proposal shown to the user with a 'Create' button.", Action("program", name,
                    app.superhealth.ui.trf("%1\$s jours d'entraînement · %2\$s semaines", training, (a.int("weeks") ?: 6).coerceIn(2, 16)), a.toString()))
            }
            "propose_food_log" -> {
                val items = a["items"]?.jsonArray.orEmpty().map { it.jsonObject }
                if (items.isEmpty()) return Outcome("Error: no item.")
                val kcal = items.sumOf { it.dbl("kcal") ?: 0.0 }
                Outcome("Proposal shown to the user with an 'Add' button.", Action("food", items.joinToString(", ") { it.str("name") ?: "?" },
                    "${n(kcal)} kcal · P ${n(items.sumOf { it.dbl("protein") ?: 0.0 })} · L ${n(items.sumOf { it.dbl("fat") ?: 0.0 })} · G ${n(items.sumOf { it.dbl("carbs") ?: 0.0 })}", a.toString()))
            }
            "show_screen" -> {
                val key = a.str("screen")?.takeIf { it in SCREENS } ?: return Outcome("Unknown screen.")
                Outcome("Button added.", Action("open", a.str("label")?.take(40) ?: key, "", a.toString()))
            }
            else -> Outcome("Unknown tool.")
        }
    }

    // ---- Application d'une proposition (bouton de l'utilisateur) -----------------------------------------

    /** Applique la proposition ; renvoie la route à ouvrir ensuite (programme créé, journal, écran), ou null. */
    suspend fun apply(action: Action): String? {
        val a = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(action.payload).jsonObject }.getOrNull() ?: return null
        return when (action.kind) {
            "program" -> {
                fun uid() = UUID.randomUUID().toString()
                val weeks = (a.int("weeks") ?: 6).coerceIn(2, 16)
                val days = a["days"]?.jsonArray.orEmpty().map { it.jsonObject }.map { d ->
                    ProgramDay(uid(), d.str("name") ?: "Jour", d["exercises"]?.jsonArray.orEmpty().map { it.jsonObject }.map { e ->
                        val lo = (e.int("rep_min") ?: 8).coerceIn(1, 50); val hi = (e.int("rep_max") ?: 12).coerceIn(lo, 60)
                        val set = TargetSet(minReps = lo, maxReps = hi, rir = (e.int("rir") ?: 2).coerceIn(0, 5), restSec = (e.int("rest_sec") ?: 120).coerceIn(30, 600))
                        ProgramExercise(uid(), e.str("exercise_id")!!, listOf(List((e.int("sets") ?: 3).coerceIn(1, 10)) { set }))
                    })
                }
                val st = WorkoutApp.store.state.value
                val p = Program(uid(), action.title, days = days, numCycles = weeks, periodization = Periodization.STATIC, gymId = st.settings.activeGymId)
                // actif s'il n'y en a pas, sinon rangé dans la bibliothèque (l'utilisateur l'active s'il le veut)
                WorkoutApp.store.update { s -> s.copy(programs = s.programs + p,
                    settings = if (s.settings.activeProgramId == null) s.settings.copy(activeProgramId = p.id) else s.settings) }
                "program/${p.id}"
            }
            "food" -> {
                val minute = a.str("time")?.let { t -> runCatching { LocalTime.parse(t.take(5)) }.getOrNull() }?.let { it.hour * 60 + it.minute }
                    ?: LocalTime.now().let { it.hour * 60 + it.minute }
                val repo = NutriApp.repository
                a["items"]?.jsonArray.orEmpty().map { it.jsonObject }.forEach { i ->
                    repo.logEstimated(LocalDate.now().toEpochDay(), minute, i.str("name") ?: "?", i.dbl("grams"),
                        Macros(i.dbl("kcal") ?: 0.0, i.dbl("protein") ?: 0.0, i.dbl("fat") ?: 0.0, i.dbl("carbs") ?: 0.0))
                }
                "log"
            }
            "open" -> SCREENS[a.str("screen")]
            else -> null
        }
    }
}
