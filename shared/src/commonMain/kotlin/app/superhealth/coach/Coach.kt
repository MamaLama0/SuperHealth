package app.superhealth.coach

import app.superhealth.platform.*

import app.superhealth.platform.Context
import app.superhealth.SuperPrefs
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.Metric
import app.superhealth.insights.InsightData
import app.superhealth.insights.text
import app.superhealth.modules.Modules
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.data.AiConfig
import app.superhealth.nutrition.data.Anon
import app.superhealth.nutrition.data.AiFood
import app.superhealth.nutrition.data.Net
import app.superhealth.nutrition.domain.Sex
import app.superhealth.training.WorkoutApp
import app.superhealth.wellbeing.Checkin
import app.superhealth.wellbeing.Cycle
import app.superhealth.wellbeing.Form
import app.superhealth.wellbeing.Journal
import app.superhealth.training.domain.Analytics as TrainingAnalytics
import app.superhealth.ui.Lang
import app.superhealth.platform.File
import app.superhealth.platform.HttpURLConnection
import app.superhealth.platform.URL
import app.superhealth.platform.LocalDate
import app.superhealth.platform.ZoneId
import app.superhealth.platform.DateTimeFormatter
import app.superhealth.platform.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Module Coach IA. Comme WHOOP Coach, Oura Advisor ou le coach Gemini de Google Health, il répond à partir de vos propres
 * données ; ici elles sont résumées sur l'appareil (14 derniers jours, programme, constats déjà calculés par l'app) et
 * seul ce résumé part vers le fournisseur IA choisi, visible à tout moment (« Ce que voit le coach »).
 *
 * Les consignes (prompts) viennent du module téléchargé (prompts.json) : elles évoluent sans mise à jour de l'app.
 * Ce qu'il retient est une liste écrite ou validée par l'utilisateur, jamais ajoutée sans son accord.
 */
object Coach {
    enum class Tone(val label: String) { WARM("Bienveillant"), DIRECT("Direct"), SCIENCE("Scientifique") }

    @Serializable data class Txt(val fr: String, val en: String? = null) {
        val text: String get() = if (Lang.english) en ?: fr else fr
    }

    @Serializable data class Prompts(
        val version: String = "1",
        val system: String,
        val tones: Map<String, String> = emptyMap(),
        val brief: String,
        val workout: String = "",
        val metric: String = "",
        /** Bilan de la semaine : une phrase ; {week} = les chiffres de la semaine. */
        val week: String = "",
        val suggestions: List<Txt> = emptyList(),
        val temperature: Double = 0.5,
        val maxTokens: Int = 700,
    )

    /** [actions] : propositions du coach (programme, repas, écran) ; [used] : outils consultés pour répondre. */
    @Serializable data class Msg(val role: String, val text: String, val time: Long = System.currentTimeMillis(),
                                 val actions: List<CoachTools.Action> = emptyList(), val used: List<String> = emptyList())

    @Serializable data class Brief(val day: Long, val headline: String, val points: List<String>, val at: Long = 0)

    class CoachException(message: String) : Exception(message)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @kotlin.concurrent.Volatile private var prompts: Prompts? = null
    fun prompts(): Prompts? = prompts ?: runCatching {
        json.decodeFromString(Prompts.serializer(), Modules.file(Modules.COACH, "prompts.json")!!.readText())
    }.getOrNull()?.also { prompts = it }

    fun invalidate() { prompts = null }

    // ---- Conversation et point du jour (fichiers privés de l'app) ------------------------

    val chat = MutableStateFlow<List<Msg>>(emptyList())
    val brief = MutableStateFlow<Brief?>(null)
    private lateinit var dir: File

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "coach").apply { mkdirs() }
        chat.value = runCatching { json.decodeFromString(ListSerializer(Msg.serializer()), File(dir, "chat.json").readText()) }.getOrDefault(emptyList())
        brief.value = runCatching { json.decodeFromString(Brief.serializer(), File(dir, "brief.json").readText()) }.getOrNull()
    }

    private fun saveChat(list: List<Msg>) {
        chat.value = list.takeLast(80)
        runCatching { File(dir, "chat.json").writeText(json.encodeToString(ListSerializer(Msg.serializer()), chat.value)) }
    }

    fun clearChat() = saveChat(emptyList())

    // ---- Résumé des données ----------------------------------------------------------------

    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private fun n(v: Double, d: Int = 0): String = if (d == 0) v.roundToInt().toString() else String.format(Locale.US, "%.${d}f", v)

    /**
     * Ce que voit le coach : profil, objectif et cibles de l'app, 14 derniers jours (journal, poids tendance, pas, sommeil,
     * cœur, récupération), séances et courses, programme, constats de l'app et mémoire. Seules des valeurs, pas d'identité
     * (le prénom seulement s'il est renseigné). Chaque catégorie se coupe ([Share]) ; humeur, journal du moral et cycle ne
     * sont jamais envoyés.
     */
    /** Ce que l'on accepte d'envoyer au coach (Réglages du coach > Données envoyées) ; tout est envoyé au départ. */
    /** [optIn] : catégorie sensible, jamais envoyée tant que l'utilisateur ne l'active pas. */
    enum class Share(val label: String, val optIn: Boolean = false) {
        PROFILE("Profil : sexe, âge, taille"),
        WEIGHT("Poids et objectif"),
        FOOD("Repas et cibles"),
        HEALTH("Sommeil, pas, cœur, récupération"),
        TRAINING("Musculation"),
        ACTIVITIES("Autres activités"),
        INSIGHTS("Constats de l'appli"),
        MEMORY("Ce qu'il retient"),
        WELLBEING("Moral, habitudes et forme du jour", optIn = true),
        CYCLE("Cycle menstruel", optIn = true),
    }

    fun shares(s: Share): Boolean = SuperPrefs.settings.value.let { if (s.optIn) s.name in it.coachShared else s.name !in it.coachHidden }

    fun setShared(s: Share, on: Boolean) = SuperPrefs.update {
        if (s.optIn) it.copy(coachShared = if (on) it.coachShared + s.name else it.coachShared - s.name)
        else it.copy(coachHidden = if (on) it.coachHidden - s.name else it.coachHidden + s.name)
    }

    /** Moral, habitudes et ressenti des 14 jours, conseil de forme du jour et effets personnels des habitudes. */
    private suspend fun wellbeing(ctx: Context, today: LocalDate, from: LocalDate): String? {
        val journalOn = SuperPrefs.settings.value.let { it.features.contains(app.superhealth.Feature.JOURNAL.name) }
        val moods = Journal.days.value; val feels = Checkin.days.value
        val lines = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.mapNotNull { d ->
            val e = moods[d.toEpochDay()]; val f = feels[d.toEpochDay()]
            if (e == null && f == null) return@mapNotNull null
            "- ${d.format(dayFmt)}: " + listOfNotNull(
                e?.let { "mood ${it.mood}/5" + if (it.tags.isNotEmpty()) ", habits: " + it.tags.joinToString { t -> t.lowercase().replace('_', ' ') } else "" },
                f?.let { "morning check-in sleep ${it.sleep}/5, energy ${it.energy}/5" },
            ).joinToString("; ")
        }.toList()
        val form = runCatching { Form.today(ctx, journalOn) }.getOrNull() as? Form.Advice
        val impacts = runCatching { Journal.findings(ctx).impacts }.getOrDefault(emptyList())
        if (lines.isEmpty() && form == null && impacts.isEmpty()) return null
        return buildString {
            appendLine("## Wellbeing (mood journal 1-5, habits, morning check-in)")
            lines.forEach { appendLine(it) }
            form?.let { a ->
                appendLine("Today's readiness (app): ${a.verdict.name.lowercase()}" + (a.score?.let { ", score $it/100" } ?: "") +
                    (a.cause?.let { ", ${it.name.lowercase()} fatigue" } ?: "") +
                    ". Signals: " + a.signals.joinToString { "${it.kind.name.lowercase()} ${if (it.level > 0) "good" else if (it.level < 0) "low" else "usual"}" })
            }
            impacts.forEach { i ->
                appendLine("Personal effect found: ${i.tag.name.lowercase().replace('_', ' ')} → next-day ${i.outcome.name.lowercase()} " +
                    "${if (i.diff.delta >= 0) "+" else ""}${n(i.diff.delta, 1)} (${i.diff.nWith} days with, ${i.diff.nWithout} without)")
            }
        }
    }

    suspend fun context(ctx: Context, today: LocalDate = LocalDate.now()): String = withContext(Dispatchers.Default) {
        coroutineScope {
            val repo = NutriApp.repository
            val an = repo.analytics().first()
            val p = an.profile
            val from = today.minusDays(13)
            val zone = ZoneId.systemDefault()
            val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val eaten = repo.entries.rangeFlow(from.toEpochDay(), today.toEpochDay()).first().groupBy { it.epochDay }
            val byDay = an.days.associateBy { it.epochDay }
            val health = listOf(Metric.STEPS, Metric.SLEEP, Metric.RESTING_HR, Metric.HRV, Metric.RECOVERY, Metric.EFFORT).filter { Bridge.hasSource(it) }
            val values = health.associateWith { m ->
                generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
                    .map { d -> async { d to runCatching { Bridge.healthValue(ctx, m, d) }.getOrNull() } }
            }.mapValues { it.value.awaitAll().toMap() }
            val acts = runCatching { Bridge.activities(fromMs) }.getOrDefault(emptyList())
            val st = WorkoutApp.store.state.value
            val db = withTimeoutOrNull(2000) { WorkoutApp.db.filterNotNull().first() }
            val insights = runCatching { InsightData.insights(ctx).take(5).map { it.text(an.settings) } }.getOrDefault(emptyList())
            val sh = SuperPrefs.settings.value

            buildString {
                if (shares(Share.PROFILE) || shares(Share.WEIGHT)) appendLine("## Profile")
                if (shares(Share.PROFILE)) {
                    val age = today.year - p.birthYear
                    appendLine(listOf(if (p.sex == Sex.MALE) "male" else "female", "$age y",
                        "${p.heightCm.roundToInt()} cm").joinToString(", "))
                    // 2.2 : à partir de 60 ans, le coach connaît les règles d'âge appliquées par l'app (Senior)
                    val band = app.superhealth.nutrition.domain.Senior.band(age)
                    if (band != app.superhealth.nutrition.domain.Senior.Band.ADULT) appendLine("Age rules applied by the app (EASO 2025, ESPEN 2022): " +
                        (if (band == app.superhealth.nutrition.domain.Senior.Band.OLDER) "65+: weight loss only if BMI ≥ 30, deficit ≤ 500 kcal/day, intake ≥ 1200 kcal, never below BMI 23, protein 1.0-1.2 g/kg adjusted weight, ≥ 25 g protein per meal"
                        else "60-64: deficit ≤ 750 kcal/day, protein ≥ 1.0 g/kg adjusted weight") +
                        ". Never suggest a bigger deficit; favour resistance training, protein, vitamin D and calcium.")
                }
                if (shares(Share.WEIGHT)) {
                    an.currentTrendKg?.let { t ->
                        val past = an.trendByDay.filterKeys { it <= today.toEpochDay() - 14 }.maxByOrNull { it.key }?.value
                        appendLine("Trend weight: ${n(t, 1)} kg" + (past?.let { " (${if (t - it >= 0) "+" else ""}${n(t - it, 1)} kg over 14 days)" } ?: ""))
                    }
                    an.currentGoal?.let { g ->
                        appendLine("Goal: ${g.type.name.lowercase()}" + (if (g.ratePctPerWeek > 0) " at ${n(g.ratePctPerWeek, 2)} % body weight/week" else "") +
                            (g.targetWeightKg?.let { ", target ${n(it, 1)} kg" } ?: ""))
                    }
                }
                if (shares(Share.FOOD)) {
                    val t = an.targetFor(today.toEpochDay()).macros
                    appendLine("App-computed expenditure (TDEE): ${n(an.currentTdee)} kcal/day")
                    appendLine("Today's targets: ${n(t.kcal)} kcal, protein ${n(t.protein)} g, fat ${n(t.fat)} g, carbs ${n(t.carbs)} g")
                    val todayFood = eaten[today.toEpochDay()].orEmpty()
                    if (todayFood.isNotEmpty()) appendLine("Eaten so far today: ${n(todayFood.sumOf { it.kcal })} kcal, protein ${n(todayFood.sumOf { it.protein })} g, " +
                        "fat ${n(todayFood.sumOf { it.fat })} g, carbs ${n(todayFood.sumOf { it.carbs })} g (${todayFood.size} items)")
                    else appendLine("Nothing logged yet today.")
                }

                // colonnes des catégories partagées seulement
                val cols = buildList<Pair<String, (LocalDate) -> String>> {
                    fun v(m: Metric, d: LocalDate, f: (Double) -> String) = values[m]?.get(d)?.let(f) ?: "-"
                    if (shares(Share.FOOD)) {
                        add("kcal eaten/target" to { d -> val food = eaten[d.toEpochDay()].orEmpty(); val tg = an.targetFor(d.toEpochDay()).macros
                            if (food.isEmpty()) "- / ${n(tg.kcal)}" else "${n(food.sumOf { it.kcal })} / ${n(tg.kcal)}" })
                        add("protein g" to { d -> eaten[d.toEpochDay()].orEmpty().takeIf { it.isNotEmpty() }?.let { f -> n(f.sumOf { it.protein }) } ?: "-" })
                    }
                    if (shares(Share.WEIGHT)) {
                        add("scale kg" to { d -> byDay[d.toEpochDay()]?.weightKg?.let { n(it, 1) } ?: "-" })
                        add("trend kg" to { d -> an.trendByDay[d.toEpochDay()]?.let { n(it, 1) } ?: "-" })
                    }
                    if (shares(Share.HEALTH)) {
                        add("steps" to { d -> v(Metric.STEPS, d) { n(it) } }); add("sleep h" to { d -> v(Metric.SLEEP, d) { n(it / 60, 1) } })
                        add("resting HR" to { d -> v(Metric.RESTING_HR, d) { n(it) } }); add("HRV ms" to { d -> v(Metric.HRV, d) { n(it) } })
                        add("recovery %" to { d -> v(Metric.RECOVERY, d) { n(it) } }); add("effort" to { d -> v(Metric.EFFORT, d) { n(it) } })
                    }
                }
                if (cols.isNotEmpty()) {
                    appendLine()
                    appendLine("## Last 14 days (oldest first; '-' = no data)")
                    appendLine("date | " + cols.joinToString(" | ") { it.first })
                    generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.forEach { d ->
                        val cells = cols.map { it.second(d) }
                        // jour sans aucune donnée : une ligne courte (moins de texte envoyé, même information)
                        appendLine(d.format(dayFmt) + " | " + if (cells.all { it == "-" || it.startsWith("- /") }) "no data" else cells.joinToString(" | "))
                    }
                }

                if (shares(Share.TRAINING)) {
                    appendLine()
                    appendLine("## Strength training (last 14 days)")
                    val prog = st.programs.firstOrNull { it.id == st.settings.activeProgramId }
                    if (prog != null) {
                        val next = prog.days.getOrNull(prog.dayIndex)
                        appendLine("Active program: ${prog.name} (${prog.days.size} days/cycle, week ${prog.cycleIndex + 1} of ${prog.numCycles})" +
                            (next?.let { ", next session: ${if (it.isRest) "rest day" else it.name}" } ?: ""))
                    } else appendLine("No active program.")
                    val history = st.history.sortedBy { it.startTime }
                    val recent = history.filter { it.startTime >= fromMs }
                    if (recent.isEmpty()) appendLine("No strength session logged.")
                    recent.forEach { w ->
                        val before = history.filter { it.startTime < w.startTime }
                        val prs = db?.let { TrainingAnalytics.newRecords(w, before, it).size } ?: 0
                        val top = w.exercises.mapNotNull { ex ->
                            val name = db?.exercise(ex.exerciseId)?.name ?: return@mapNotNull null
                            val best = ex.sets.filter { it.done && it.weight != null && it.reps != null }.maxByOrNull { it.weight!! } ?: return@mapNotNull null
                            "$name ${n(best.weight!!, 1)} kg×${best.reps}" + (best.rir?.let { " @RIR $it" } ?: "")
                        }.take(4)
                        appendLine("- ${TrainingAnalytics.day(w.startTime).format(dayFmt)}: ${w.name}, ${w.durationSec / 60} min, " +
                            "volume ${n(w.exercises.sumOf { TrainingAnalytics.volume(it.sets) })} kg, ${w.exercises.sumOf { e -> e.sets.count { it.done } }} sets" +
                            (w.rpe?.let { ", perceived effort $it/10" } ?: "") +
                            (if (prs > 0) ", $prs new records" else "") + (if (top.isNotEmpty()) ". Top sets: ${top.joinToString("; ")}" else ""))
                    }
                }
                val cardio = if (shares(Share.ACTIVITIES)) acts.filter { it.source != Bridge.Source.GYM } else emptyList()
                if (cardio.isNotEmpty()) {
                    appendLine()
                    appendLine("## Cardio and other activities (watch / Apple Santé)")
                    cardio.sortedBy { it.start }.forEach { a ->
                        val km = a.distanceM?.takeIf { it > 0 }?.let { ", ${n(it / 1000, 1)} km" } ?: ""
                        val kcal = (a.watch?.kcal ?: a.run?.kcal)?.takeIf { it > 0 }?.let { ", ${n(it)} kcal" } ?: ""
                        appendLine("- ${TrainingAnalytics.day(a.start).format(dayFmt)}: ${a.title} (${a.kind.name.lowercase()}), ${a.minutes} min$km$kcal")
                    }
                }
                if (shares(Share.WELLBEING)) wellbeing(ctx, today, from)?.let { appendLine(); append(it) }
                if (shares(Share.CYCLE)) Cycle.info()?.let { c ->
                    appendLine()
                    appendLine("## Menstrual cycle")
                    appendLine("Day ${c.dayOfCycle}" + (c.phase?.let { ", ${it.name.lowercase()} phase" } ?: "") +
                        (c.length?.let { ", usual cycle $it days" } ?: "") +
                        (c.nextStart?.let { ", next period around ${LocalDate.ofEpochDay(it).format(dayFmt)}" } ?: ""))
                }
                if (insights.isNotEmpty() && shares(Share.INSIGHTS)) {
                    appendLine()
                    appendLine("## Patterns already detected by the app")
                    insights.forEach { appendLine("- $it") }
                }
                if (sh.coachMemory.isNotEmpty() && shares(Share.MEMORY)) {
                    appendLine()
                    appendLine("## Memory (facts the user asked you to remember)")
                    sh.coachMemory.forEach { appendLine("- $it") }
                }
                if (isBlank()) appendLine("The user chose not to share any data; answer from the conversation only.")
            }.trim()
        }
    }

    // ---- Appel au modèle ---------------------------------------------------------------------

    private fun config(): AiConfig {
        val cfg = NutriApp.repository.prefs.ai.value
        val m = SuperPrefs.settings.value.coachModel.trim()
        return if (m.isNotEmpty() && cfg.provider != app.superhealth.nutrition.data.AiProvider.OFFERED) cfg.copy(model = m) else cfg
    }

    fun ready(): Boolean = config().ready && prompts() != null

    private fun system(p: Prompts, context: String): String {
        val tone = runCatching { Tone.valueOf(SuperPrefs.settings.value.coachTone) }.getOrDefault(Tone.WARM)
        return p.system
            .replace("{tone}", p.tones[tone.name] ?: tone.label)
            .replace("{language}", if (Lang.english) "English" else "French")
            .replace("{date}", LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)))
            .replace("{name}", if (Lang.english) "the user" else "l'utilisateur") +
            "\n\n# Data snapshot\n" + context
    }

    /** Fournisseur sans appel de fonctions (refus de « tools ») : on ne les renvoie plus pendant cette session. */
    @kotlin.concurrent.Volatile private var toolsOff = false

    /** Appel Chat Completions (même fournisseur que l'IA de la nutrition), conversation complète, sans outils. */
    private suspend fun complete(p: Prompts, system: String, messages: List<Msg>): String = converse(null, p, system, messages, tools = false).text

    private class Reply(val text: String, val actions: List<CoachTools.Action>, val used: List<String>)

    /**
     * Conversation avec outils : tant que le modèle demande des outils (6 tours au plus), ils s'exécutent sur le téléphone
     * et leurs résultats lui sont renvoyés ; les propositions s'accumulent sous la réponse finale.
     */
    private suspend fun converse(ctx: Context?, p: Prompts, system: String, messages: List<Msg>, tools: Boolean): Reply = withContext(Dispatchers.IO) {
        val cfg = config()
        if (!cfg.ready) throw CoachException(app.superhealth.ui.tr("Configurez d'abord une clé API (Nutrition > Intelligence artificielle)."))
        val endpoint = cfg.effectiveUrl + "/chat/completions"
        if (!Net.canSendSecret(endpoint)) throw CoachException(app.superhealth.ui.tr("Adresse refusée : HTTPS requis (HTTP seulement vers le réseau local)."))
        val useTools = tools && ctx != null && !toolsOff
        val convo = mutableListOf<kotlinx.serialization.json.JsonElement>()
        convo += buildJsonObject { put("role", "system"); put("content", Anon.name(system + if (useTools) CoachTools.GUIDE else "")) }
        // rien d'identifiant : ni nom (même tapé dans une question), ni e-mail, ni téléphone
        messages.forEach { m -> convo += buildJsonObject { put("role", m.role); put("content", if (m.role == "user") Anon.typed(m.text) else Anon.name(m.text)) } }
        val actions = mutableListOf<CoachTools.Action>()
        val used = linkedSetOf<String>()
        var withTools = useTools
        repeat(7) { round ->
            val body = buildJsonObject {
                put("model", cfg.effectiveModel)
                put("temperature", p.temperature)
                put(cfg.maxTokensParam, p.maxTokens)
                put("messages", kotlinx.serialization.json.JsonArray(convo))
                // dernier tour : plus d'outils, le modèle doit conclure
                if (withTools && round < 6) { put("tools", CoachTools.definitions()); put("tool_choice", "auto") }
            }
            val (code, text) = post(cfg, endpoint, body.toString())
            if (code !in 200..299) {
                // fournisseur qui refuse les outils : même question sans eux
                if (withTools && round == 0 && code in listOf(400, 404, 422)) { toolsOff = true; withTools = false; return@repeat }
                val msg = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull()
                throw CoachException("Erreur $code${msg?.let { " : $it" } ?: ""}")
            }
            val message = json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                ?: throw CoachException(app.superhealth.ui.tr("Pas de réponse du modèle."))
            val calls = (message["tool_calls"] as? kotlinx.serialization.json.JsonArray).orEmpty()
            if (calls.isEmpty() || !withTools) {
                val answer = message["content"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let(Anon::restore)
                    ?: throw CoachException(app.superhealth.ui.tr("Pas de réponse du modèle."))
                return@withContext Reply(answer, actions, used.toList())
            }
            convo += buildJsonObject {
                put("role", "assistant")
                put("content", message["content"] ?: kotlinx.serialization.json.JsonNull)
                put("tool_calls", kotlinx.serialization.json.JsonArray(calls))
            }
            calls.forEach { c ->
                val call = c.jsonObject
                val fn = call["function"]?.jsonObject
                val name = fn?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
                val args = runCatching { json.parseToJsonElement(fn?.get("arguments")?.jsonPrimitive?.contentOrNull ?: "{}").jsonObject }
                    .getOrDefault(kotlinx.serialization.json.JsonObject(emptyMap()))
                val out = CoachTools.run(ctx!!, name, args)
                out.action?.let { actions += it }
                CoachTools.label(name)?.let { used += it }
                convo += buildJsonObject {
                    put("role", "tool"); put("tool_call_id", call["id"]?.jsonPrimitive?.contentOrNull.orEmpty()); put("content", Anon.name(out.result))
                }
            }
        }
        throw CoachException(app.superhealth.ui.tr("Pas de réponse du modèle."))
    }

    private fun post(cfg: AiConfig, endpoint: String, body: String): Pair<Int, String> {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15000; readTimeout = 90000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${cfg.effectiveKey}")
            setRequestProperty("X-Title", "SuperHealth Coach")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            return code to (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { Net.readCapped(it) }.orEmpty()
        } finally { conn.disconnect() }
    }

    private val rememberTag = Regex("""<<\s*(?:remember|retenir|mémoire)\s*:\s*(.+?)\s*>>""", RegexOption.IGNORE_CASE)

    /** Réponse sans la balise mémoire, et le fait à retenir proposé (s'il y en a un). */
    fun split(answer: String): Pair<String, String?> {
        val m = rememberTag.find(answer)
        return answer.replace(rememberTag, "").trim() to m?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Pose une question (avec les 12 derniers messages) ; la réponse est ajoutée à la conversation. */
    suspend fun ask(ctx: Context, question: String): Msg {
        saveChat(chat.value + Msg("user", question.trim()))
        return answer(ctx)
    }

    /** Répond au dernier message de l'utilisateur (après une erreur, ou pour une autre réponse). */
    suspend fun answer(ctx: Context): Msg {
        val p = prompts() ?: throw CoachException(app.superhealth.ui.tr("Contenu du plugin introuvable : réinstallez-le dans Réglages > Plugins."))
        val history = chat.value.takeLast(12)
        if (history.lastOrNull()?.role != "user") throw CoachException(app.superhealth.ui.tr("Pas de question à laquelle répondre."))
        val r = converse(ctx, p, system(p, context(ctx)), history, tools = true)
        return Msg("assistant", r.text, actions = r.actions, used = r.used).also { saveChat(chat.value + it) }
    }

    /** Proposition appliquée (bouton touché) : marquée faite dans la conversation ; renvoie l'écran à ouvrir. */
    suspend fun apply(msg: Msg, action: CoachTools.Action): String? {
        val route = CoachTools.apply(action)
        if (action.kind != "open") saveChat(chat.value.map { m -> if (m.time == msg.time && m.role == msg.role)
            m.copy(actions = m.actions.map { if (it == action) it.copy(done = true) else it }) else m })
        return route
    }

    /** Autre réponse à la dernière question : la dernière réponse est retirée puis redemandée. */
    suspend fun regenerate(ctx: Context): Msg {
        if (chat.value.lastOrNull()?.role == "assistant") saveChat(chat.value.dropLast(1))
        return answer(ctx)
    }

    /** Retire la dernière question restée sans réponse (pour la modifier). */
    fun dropPendingQuestion(): String? = chat.value.lastOrNull()?.takeIf { it.role == "user" }?.also { saveChat(chat.value.dropLast(1)) }?.text

    /** Point du jour (comme le Daily Outlook de WHOOP) : préparé une fois par jour, gardé jusqu'au lendemain. */
    suspend fun makeBrief(ctx: Context): Brief {
        val p = prompts() ?: throw CoachException(app.superhealth.ui.tr("Contenu du plugin introuvable : réinstallez-le dans Réglages > Plugins."))
        // 2.2 : le point du jour peut aller chercher le détail (repas, nuit, séances) avec les outils de lecture
        val raw = split(converse(ctx, p, system(p, context(ctx)), listOf(Msg("user", p.brief)), tools = true).text).first
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val head = lines.firstOrNull { !it.startsWith("•") && !it.startsWith("-") && !it.startsWith("*") }?.trim('*', '#', ' ') ?: ""
        val points = lines.filter { it.startsWith("•") || it.startsWith("-") || (it.startsWith("*") && !it.startsWith("**")) }
            .map { it.trimStart('•', '-', '*', ' ') }.take(3)
        val b = Brief(LocalDate.now().toEpochDay(), head.ifEmpty { points.firstOrNull() ?: raw.take(80) }, points, System.currentTimeMillis())
        brief.value = b
        runCatching { File(dir, "brief.json").writeText(json.encodeToString(Brief.serializer(), b)) }
        return b
    }

    /** Une question isolée (hors conversation), réponse sans balise mémoire. */
    suspend fun once(ctx: Context, p: Prompts, question: String): String = split(complete(p, system(p, context(ctx)), listOf(Msg("user", question)))).first

    fun briefIsToday(): Boolean = brief.value?.day == LocalDate.now().toEpochDay()

    /** Retient un fait (proposé par le coach et validé, ou ajouté à la main). */
    fun remember(fact: String) {
        val f = fact.trim().take(160)
        if (f.isEmpty()) return
        SuperPrefs.update { s -> if (s.coachMemory.any { it.equals(f, true) }) s else s.copy(coachMemory = (s.coachMemory + f).takeLast(30)) }
    }

    fun forget(fact: String) = SuperPrefs.update { s -> s.copy(coachMemory = s.coachMemory - fact) }
}
