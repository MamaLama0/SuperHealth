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
import app.superhealth.nutrition.data.AiFood
import app.superhealth.nutrition.data.Net
import app.superhealth.nutrition.domain.Sex
import app.superhealth.training.WorkoutApp
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

    @Serializable data class Msg(val role: String, val text: String, val time: Long = System.currentTimeMillis())

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
     * (le prénom seulement s'il est renseigné).
     */
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
                appendLine("## Profile")
                val age = today.year - p.birthYear
                appendLine(listOfNotNull(app.superhealth.ui.userName()?.let { "Name: $it" }, if (p.sex == Sex.MALE) "male" else "female", "$age y",
                    "${p.heightCm.roundToInt()} cm").joinToString(", "))
                an.currentTrendKg?.let { t ->
                    val past = an.trendByDay.filterKeys { it <= today.toEpochDay() - 14 }.maxByOrNull { it.key }?.value
                    appendLine("Trend weight: ${n(t, 1)} kg" + (past?.let { " (${if (t - it >= 0) "+" else ""}${n(t - it, 1)} kg over 14 days)" } ?: ""))
                }
                an.currentGoal?.let { g ->
                    appendLine("Goal: ${g.type.name.lowercase()}" + (if (g.ratePctPerWeek > 0) " at ${n(g.ratePctPerWeek, 2)} % body weight/week" else "") +
                        (g.targetWeightKg?.let { ", target ${n(it, 1)} kg" } ?: ""))
                }
                val t = an.targetFor(today.toEpochDay()).macros
                appendLine("App-computed expenditure (TDEE): ${n(an.currentTdee)} kcal/day")
                appendLine("Today's targets: ${n(t.kcal)} kcal, protein ${n(t.protein)} g, fat ${n(t.fat)} g, carbs ${n(t.carbs)} g")
                val todayFood = eaten[today.toEpochDay()].orEmpty()
                if (todayFood.isNotEmpty()) appendLine("Eaten so far today: ${n(todayFood.sumOf { it.kcal })} kcal, protein ${n(todayFood.sumOf { it.protein })} g, " +
                    "fat ${n(todayFood.sumOf { it.fat })} g, carbs ${n(todayFood.sumOf { it.carbs })} g (${todayFood.size} items)")
                else appendLine("Nothing logged yet today.")

                appendLine()
                appendLine("## Last 14 days (oldest first; '-' = no data)")
                appendLine("date | kcal eaten/target | protein g | scale kg | trend kg | steps | sleep h | resting HR | HRV ms | recovery % | effort")
                generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.forEach { d ->
                    val e = d.toEpochDay()
                    val dd = byDay[e]
                    val food = eaten[e].orEmpty()
                    val tg = an.targetFor(e).macros
                    fun v(m: Metric, f: (Double) -> String) = values[m]?.get(d)?.let(f) ?: "-"
                    val cells = listOf(
                        if (food.isEmpty()) "- / ${n(tg.kcal)}" else "${n(food.sumOf { it.kcal })} / ${n(tg.kcal)}",
                        if (food.isEmpty()) "-" else n(food.sumOf { it.protein }),
                        dd?.weightKg?.let { n(it, 1) } ?: "-",
                        an.trendByDay[e]?.let { n(it, 1) } ?: "-",
                        v(Metric.STEPS) { n(it) }, v(Metric.SLEEP) { n(it / 60, 1) }, v(Metric.RESTING_HR) { n(it) }, v(Metric.HRV) { n(it) },
                        v(Metric.RECOVERY) { n(it) }, v(Metric.EFFORT) { n(it) },
                    )
                    // jour sans aucune donnée : une ligne courte (moins de texte envoyé, même information)
                    appendLine(d.format(dayFmt) + " | " + if (cells.drop(1).all { it == "-" } && cells[0].startsWith("-")) "no data" else cells.joinToString(" | "))
                }

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
                        (if (prs > 0) ", $prs new records" else "") + (if (top.isNotEmpty()) ". Top sets: ${top.joinToString("; ")}" else ""))
                }
                val cardio = acts.filter { it.source != Bridge.Source.GYM }
                if (cardio.isNotEmpty()) {
                    appendLine()
                    appendLine("## Cardio and other activities (watch / Apple Santé)")
                    cardio.sortedBy { it.start }.forEach { a ->
                        val km = a.distanceM?.takeIf { it > 0 }?.let { ", ${n(it / 1000, 1)} km" } ?: ""
                        val kcal = (a.watch?.kcal ?: a.run?.kcal)?.takeIf { it > 0 }?.let { ", ${n(it)} kcal" } ?: ""
                        appendLine("- ${TrainingAnalytics.day(a.start).format(dayFmt)}: ${a.title} (${a.kind.name.lowercase()}), ${a.minutes} min$km$kcal")
                    }
                }
                if (insights.isNotEmpty()) {
                    appendLine()
                    appendLine("## Patterns already detected by the app")
                    insights.forEach { appendLine("- $it") }
                }
                if (sh.coachMemory.isNotEmpty()) {
                    appendLine()
                    appendLine("## Memory (facts the user asked you to remember)")
                    sh.coachMemory.forEach { appendLine("- $it") }
                }
            }.trim()
        }
    }

    // ---- Appel au modèle ---------------------------------------------------------------------

    private fun config(): AiConfig {
        val cfg = NutriApp.repository.prefs.ai.value
        val m = SuperPrefs.settings.value.coachModel.trim()
        return if (m.isNotEmpty()) cfg.copy(model = m) else cfg
    }

    fun ready(): Boolean = config().ready && prompts() != null

    private fun system(p: Prompts, context: String): String {
        val tone = runCatching { Tone.valueOf(SuperPrefs.settings.value.coachTone) }.getOrDefault(Tone.WARM)
        return p.system
            .replace("{tone}", p.tones[tone.name] ?: tone.label)
            .replace("{language}", if (Lang.english) "English" else "French")
            .replace("{date}", LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)))
            .replace("{name}", app.superhealth.ui.userName() ?: if (Lang.english) "the user" else "l'utilisateur") +
            "\n\n# Data snapshot\n" + context
    }

    /** Appel Chat Completions (même fournisseur que l'IA de la nutrition), conversation complète. */
    private suspend fun complete(p: Prompts, system: String, messages: List<Msg>): String = withContext(Dispatchers.IO) {
        val cfg = config()
        if (!cfg.ready) throw CoachException(app.superhealth.ui.tr("Configurez d'abord une clé API (Nutrition > Intelligence artificielle)."))
        val body = buildJsonObject {
            put("model", cfg.effectiveModel)
            put("temperature", p.temperature)
            put("max_tokens", p.maxTokens)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                messages.forEach { m -> addJsonObject { put("role", m.role); put("content", m.text) } }
            }
        }
        val endpoint = cfg.effectiveUrl + "/chat/completions"
        if (!Net.canSendSecret(endpoint)) throw CoachException(app.superhealth.ui.tr("Adresse refusée : HTTPS requis (HTTP seulement vers le réseau local)."))
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15000; readTimeout = 90000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${cfg.apiKey.trim()}")
            setRequestProperty("X-Title", "SuperHealth Coach")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { Net.readCapped(it) }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull()
                throw CoachException("Erreur $code${msg?.let { " : $it" } ?: ""}")
            }
            json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")
                ?.jsonPrimitive?.contentOrNull?.trim() ?: throw CoachException(app.superhealth.ui.tr("Pas de réponse du modèle."))
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
        val answer = complete(p, system(p, context(ctx)), history)
        return Msg("assistant", answer).also { saveChat(chat.value + it) }
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
        val raw = split(complete(p, system(p, context(ctx)), listOf(Msg("user", p.brief)))).first
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
