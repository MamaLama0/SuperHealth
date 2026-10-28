package app.superhealth.wellbeing

import app.superhealth.platform.*

import app.superhealth.platform.Context
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.Metric
import app.superhealth.health.data.Scores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.superhealth.platform.File
import app.superhealth.platform.LocalDate

/**
 * Journal du moral : chaque jour, l'humeur (1 à 5) et ce qui s'est passé (habitudes cochées). Comme le Journal de WHOOP et
 * l'humeur de NOOP, il sert à voir ce qui pèse vraiment sur votre récupération — un constat n'apparaît que s'il est net sur
 * vos propres jours (voir [impacts]).
 */
object Journal {
    @Serializable data class Entry(val mood: Int, val tags: Set<String> = emptySet(), val at: Long = 0)

    /** Habitudes proposées. [evening] : ce qui se passe le soir et joue surtout sur la nuit suivante. */
    enum class Tag(val label: String, val evening: Boolean) {
        ALCOHOL("Alcool", true),
        CAFFEINE_LATE("Caféine après 14 h", true),
        LATE_MEAL("Repas tardif", true),
        SCREENS("Écrans au lit", true),
        STRESS("Journée stressante", false),
        SICK("Malade", false),
        TRAVEL("Voyage", false),
        NAP("Sieste", false),
        OUTSIDE("Dehors à la lumière", false),
        MEDITATION("Méditation", true),
        SOCIAL("Moment entre proches", false),
    }

    val MOODS = listOf("Très mal", "Mal", "Moyen", "Bien", "Très bien")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Serializable private data class Store(val days: Map<Long, Entry> = emptyMap())
    private var file: File? = null

    /** Entrées par jour (epochDay). */
    val days = MutableStateFlow<Map<Long, Entry>>(emptyMap())

    fun init(ctx: Context) {
        val f = File(File(ctx.filesDir, "wellbeing").apply { mkdirs() }, "journal.json").also { file = it }
        days.value = runCatching { json.decodeFromString(Store.serializer(), f.readText()).days }.getOrDefault(emptyMap())
    }

    fun today(): Entry? = days.value[LocalDate.now().toEpochDay()]

    fun set(ctx: Context, day: Long, e: Entry?) {
        days.value = if (e == null) days.value - day else days.value + (day to e)
        impactsCache = null
        runCatching { file?.writeText(json.encodeToString(Store.serializer(), Store(days.value))) }
        Reminders.schedule(ctx)
    }

    fun setMood(ctx: Context, mood: Int, day: Long = LocalDate.now().toEpochDay()) {
        val old = days.value[day]
        set(ctx, day, Entry(mood.coerceIn(1, 5), old?.tags.orEmpty(), System.currentTimeMillis()))
    }

    fun toggleTag(ctx: Context, tag: String, day: Long = LocalDate.now().toEpochDay()) {
        val old = days.value[day] ?: return
        set(ctx, day, old.copy(tags = if (tag in old.tags) old.tags - tag else old.tags + tag))
    }

    /** Humeur moyenne des [n] derniers jours notés avant [day], et leur nombre. */
    fun moodBaseline(day: Long, n: Int = 28): Pair<Double, Int>? {
        val xs = days.value.filterKeys { it in (day - n) until day }.values.map { it.mood.toDouble() }
        return if (xs.isEmpty()) null else xs.average() to xs.size
    }

    // ---- Ce qui vous influence ---------------------------------------------------------------------------------

    /** Mesure du lendemain comparée avec et sans l'habitude. */
    enum class Outcome(val label: String, val unit: String) { CHARGE("Charge du lendemain", "%"), SLEEP("Sommeil de la nuit", "min") }

    data class Impact(val tag: Tag, val outcome: Outcome, val diff: Stats.Diff)

    /** Lien entre l'humeur du jour et une mesure du matin même (corrélation de Pearson). */
    data class Link(val outcome: Outcome, val r: Double, val n: Int)

    data class Findings(val impacts: List<Impact>, val links: List<Link>, val loggedDays: Int)

    @kotlin.concurrent.Volatile private var impactsCache: Pair<LocalDate, Findings>? = null

    /** Jours étudiés : les 180 derniers jours notés. */
    private const val WINDOW = 180L

    /**
     * Habitudes dont l'effet sur la Charge du lendemain ou le sommeil de la nuit suivante est net : au moins 5 jours avec et
     * 5 sans, test t de Welch, puis tri de Benjamini-Hochberg sur tous les tests faits (sinon, avec une dizaine d'habitudes,
     * un faux constat apparaîtrait régulièrement). L'humeur est reliée au sommeil de la nuit précédente et à la Charge du
     * matin (au moins 10 jours notés). Rien n'est affiché sans preuve suffisante.
     */
    suspend fun findings(ctx: Context): Findings = withContext(Dispatchers.Default) {
        val today = LocalDate.now()
        impactsCache?.takeIf { it.first == today }?.let { return@withContext it.second }
        val logged = days.value.filterKeys { it in (today.toEpochDay() - WINDOW)..today.toEpochDay() }
        suspend fun outcome(o: Outcome, d: LocalDate): Double? = when (o) {
            Outcome.CHARGE -> runCatching { Scores.chargeOf(ctx, d)?.score?.toDouble() }.getOrNull()
            Outcome.SLEEP -> Bridge.healthValue(ctx, Metric.SLEEP, d)?.takeIf { it > 60 }
        }
        // mesure du lendemain de chaque jour noté (le lendemain doit être passé ou être aujourd'hui)
        val next = HashMap<Pair<Outcome, Long>, Double?>()
        for (o in Outcome.entries) for (day in logged.keys) {
            val d = LocalDate.ofEpochDay(day + 1)
            if (!d.isAfter(today)) next[o to day] = outcome(o, d)
        }
        val candidates = ArrayList<Pair<Impact, Double>>()
        for (tag in Tag.entries) for (o in Outcome.entries) {
            val with = ArrayList<Double>(); val without = ArrayList<Double>()
            logged.forEach { (day, e) -> next[o to day]?.let { if (tag.name in e.tags) with += it else without += it } }
            Stats.welch(with, without)?.let { candidates += Impact(tag, o, it) to it.p }
        }
        val keep = Stats.significant(candidates.map { it.second })
        val impacts = candidates.filterIndexed { i, _ -> keep[i] }.map { it.first }.sortedByDescending { kotlin.math.abs(it.diff.delta) }
        val links = Outcome.entries.mapNotNull { o ->
            val pairs = logged.mapNotNull { (day, e) -> outcome(o, LocalDate.ofEpochDay(day))?.let { e.mood.toDouble() to it } }
            val res = Stats.pearson(pairs.map { it.first }, pairs.map { it.second }) ?: return@mapNotNull null
            if (res.second <= Stats.ALPHA) Link(o, res.first, pairs.size) else null
        }
        Findings(impacts, links, logged.size).also { impactsCache = today to it }
    }
}
