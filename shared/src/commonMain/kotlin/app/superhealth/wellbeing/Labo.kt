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
import kotlin.random.Random

/**
 * Labo : tester une habitude sur soi (essai n-of-1). Pendant [DAYS] jours, l'appli tire au sort les jours « avec » et
 * « sans », par paires de deux jours (un de chaque, dans un ordre au hasard) : une tendance de fond (saison, période
 * chargée) touche alors autant les deux groupes, comme dans les essais croisés sur un seul patient (Guyatt 1986 ;
 * Kravitz et al. 2014, guide de l'AHRQ). Le journal du soir dit si la consigne a été suivie ; seuls ces jours comptent.
 *
 * Mesures du lendemain matin : Repos, Charge et ressenti. Verdict : test t de Welch par mesure (au moins 5 jours suivis
 * de chaque côté), Benjamini-Hochberg sur les trois — « effet net » seulement s'il ne peut pas venir du hasard.
 */
object Labo {
    @Serializable data class Experiment(val tag: String, val start: Long, val plan: List<Boolean>, val stopped: Long? = null) {
        val end: Long get() = start + plan.size
        fun assigned(day: Long): Boolean? = plan.getOrNull((day - start).toInt())
        fun running(today: Long) = stopped == null && today < end
    }

    @Serializable private data class Store(val experiments: List<Experiment> = emptyList())

    const val DAYS = 28

    /** Habitudes que l'on peut choisir de faire ou non un jour donné. */
    val TESTABLE = listOf(Journal.Tag.CAFFEINE_LATE, Journal.Tag.ALCOHOL, Journal.Tag.SCREENS, Journal.Tag.LATE_MEAL,
        Journal.Tag.MEDITATION, Journal.Tag.OUTSIDE, Journal.Tag.NAP)

    enum class Outcome(val label: String) { REST("Repos"), CHARGE("Charge"), FEEL("Ressenti") }

    data class Effect(val outcome: Outcome, val diff: Stats.Diff, val net: Boolean)

    /** [followed] : jours où le journal confirme la consigne ; [effects] : mesures testables (5 jours de chaque côté). */
    data class Result(val followed: Int, val elapsed: Int, val effects: List<Effect>)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var file: File? = null

    val experiments = MutableStateFlow<List<Experiment>>(emptyList())

    fun init(ctx: Context) {
        val f = File(File(ctx.filesDir, "wellbeing").apply { mkdirs() }, "labo.json").also { file = it }
        experiments.value = runCatching { json.decodeFromString(Store.serializer(), f.readText()).experiments }.getOrDefault(emptyList())
    }

    private fun save() { runCatching { file?.writeText(json.encodeToString(Store.serializer(), Store(experiments.value))) } }

    fun current(today: Long = LocalDate.now().toEpochDay()): Experiment? = experiments.value.lastOrNull { it.running(today) }

    /** Dernier test terminé ou arrêté. */
    fun last(today: Long = LocalDate.now().toEpochDay()): Experiment? = experiments.value.lastOrNull { !it.running(today) }

    /** Tirage par paires : chaque paire de jours compte un « avec » et un « sans », dans un ordre au hasard. */
    fun plan(random: Random, days: Int = DAYS): List<Boolean> = (0 until days / 2).flatMap { if (random.nextBoolean()) listOf(true, false) else listOf(false, true) }

    fun start(tag: Journal.Tag, today: Long = LocalDate.now().toEpochDay()) {
        experiments.value = experiments.value.map { if (it.running(today)) it.copy(stopped = today) else it } +
            Experiment(tag.name, today, plan(Random.Default))
        save()
    }

    /** Arrêté le jour même où il a commencé, le test est annulé (rien à analyser). */
    fun stop(today: Long = LocalDate.now().toEpochDay()) {
        experiments.value = experiments.value.mapNotNull { if (!it.running(today)) it else if (it.start == today) null else it.copy(stopped = today) }
        save()
    }

    /**
     * Jours suivis (consigne et journal d'accord) et mesure du lendemain de chacun : [outcome] donne la mesure du matin
     * du jour passé en argument. Pur, pour les tests.
     */
    fun analyse(e: Experiment, journal: Map<Long, Journal.Entry>, today: Long, outcome: (Outcome, Long) -> Double?): Result {
        val last = minOf(e.stopped ?: e.end, e.end, today) - 1
        val followed = (e.start..last).filter { d ->
            val a = e.assigned(d) ?: return@filter false
            val entry = journal[d] ?: return@filter false
            (e.tag in entry.tags) == a
        }
        val tests = Outcome.entries.mapNotNull { o ->
            val with = ArrayList<Double>(); val without = ArrayList<Double>()
            followed.forEach { d -> if (d + 1 <= today) outcome(o, d + 1)?.let { if (e.assigned(d) == true) with += it else without += it } }
            Stats.welch(with, without)?.let { o to it }
        }
        val keep = Stats.significant(tests.map { it.second.p })
        return Result(followed.size, (last - e.start + 1).toInt().coerceAtLeast(0), tests.mapIndexed { i, (o, d) -> Effect(o, d, keep[i]) })
    }

    suspend fun result(ctx: Context, e: Experiment): Result = withContext(Dispatchers.Default) {
        val today = LocalDate.now().toEpochDay()
        val cache = HashMap<Pair<Outcome, Long>, Double?>()
        val days = (e.start + 1..minOf(today, e.end)).toList()
        for (d in days) for (o in Outcome.entries) {
            val date = LocalDate.ofEpochDay(d)
            cache[o to d] = when (o) {
                Outcome.REST -> runCatching { Bridge.healthValue(ctx, Metric.SLEEP_SCORE, date) }.getOrNull()
                Outcome.CHARGE -> runCatching { Scores.chargeOf(ctx, date)?.score?.toDouble() }.getOrNull()
                Outcome.FEEL -> Checkin.days.value[d]?.let { (it.sleep + it.energy) / 2.0 }
            }
        }
        analyse(e, Journal.days.value, today) { o, d -> cache[o to d] }
    }
}
