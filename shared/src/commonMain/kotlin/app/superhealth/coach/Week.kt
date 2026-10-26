package app.superhealth.coach

import app.superhealth.platform.*

import app.superhealth.platform.Context
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.Metric
import app.superhealth.nutrition.NutriApp
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.Analytics as TrainingAnalytics
import app.superhealth.platform.File
import app.superhealth.platform.LocalDate
import app.superhealth.platform.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bilan de la semaine du coach : les 7 derniers jours complets (hier compris, aujourd'hui exclu), comptés sur l'appareil
 * à partir des mêmes données que le reste de l'app. Le modèle n'ajoute qu'une phrase (« le mot du coach »), une fois par
 * semaine glissante, et seulement si le plugin fournit la consigne `week`.
 */
object Week {
    @Serializable data class Stats(
        /** Dernier jour compté (epochDay). */
        val end: Long,
        val loggedDays: Int,
        val kcalAvg: Double?, val kcalTarget: Double?,
        val proteinAvg: Double?, val proteinTarget: Double?,
        /** Variation du poids tendance sur la période (kg). */
        val trendDelta: Double?,
        val sessions: Int, val volumeKg: Double,
        val cardioMin: Int,
        val sleepH: Double?, val steps: Double?,
    )

    @Serializable data class Note(val end: Long, val text: String)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Bilan déjà calculé (cache du module : la carte ne clignote pas en revenant sur le tableau de bord). */
    val stats = MutableStateFlow<Stats?>(null)
    val note = MutableStateFlow<Note?>(null)

    private fun file(ctx: Context) = File(File(ctx.filesDir, "coach").apply { mkdirs() }, "week.json")

    fun load(ctx: Context) {
        if (note.value == null) note.value = runCatching { json.decodeFromString(Note.serializer(), file(ctx).readText()) }.getOrNull()
    }

    suspend fun compute(ctx: Context, today: LocalDate = LocalDate.now()): Stats = withContext(Dispatchers.Default) {
        val end = today.minusDays(1)
        val from = today.minusDays(7)
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        val repo = NutriApp.repository
        val an = repo.analytics().first()
        val eaten = repo.entries.rangeFlow(from.toEpochDay(), end.toEpochDay()).first().groupBy { it.epochDay }
        val logged = days.filter { eaten[it.toEpochDay()].orEmpty().isNotEmpty() }
        fun avg(xs: List<Double>) = if (xs.isEmpty()) null else xs.average()
        // moyennes sur les jours journalisés seulement : un jour vide n'est pas un jour à 0 kcal
        val kcal = avg(logged.map { d -> eaten.getValue(d.toEpochDay()).sumOf { it.kcal } })
        val prot = avg(logged.map { d -> eaten.getValue(d.toEpochDay()).sumOf { it.protein } })
        val kcalT = avg(logged.map { an.targetFor(it.toEpochDay()).macros.kcal })
        val protT = avg(logged.map { an.targetFor(it.toEpochDay()).macros.protein })
        // tendance : dernière valeur connue au plus tard la veille du début, et au plus tard le dernier jour
        val t0 = an.trendByDay.filterKeys { it <= from.toEpochDay() - 1 }.maxByOrNull { it.key }?.value
        val t1 = an.trendByDay.filterKeys { it <= end.toEpochDay() }.maxByOrNull { it.key }?.value
        val zone = ZoneId.systemDefault()
        val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val gym = WorkoutApp.store.state.value.history.filter { it.startTime in fromMs until toMs }
        val cardio = runCatching { Bridge.activities(fromMs, toMs) }.getOrDefault(emptyList()).filter { it.source != Bridge.Source.GYM }
        suspend fun health(m: Metric): Double? = if (!Bridge.hasSource(m)) null
            else avg(days.mapNotNull { d -> runCatching { Bridge.healthValue(ctx, m, d) }.getOrNull()?.takeIf { it > 0 } })
        Stats(
            end = end.toEpochDay(), loggedDays = logged.size,
            kcalAvg = kcal, kcalTarget = kcalT, proteinAvg = prot, proteinTarget = protT,
            trendDelta = if (t0 != null && t1 != null) t1 - t0 else null,
            sessions = gym.size, volumeKg = gym.sumOf { w -> w.exercises.sumOf { TrainingAnalytics.volume(it.sets) } },
            cardioMin = cardio.sumOf { it.minutes }.toInt(),
            sleepH = health(Metric.SLEEP)?.let { it / 60 }, steps = health(Metric.STEPS),
        ).also { stats.value = it }
    }

    /** Les chiffres tels qu'envoyés au modèle (en plus du résumé habituel du coach). */
    fun describe(s: Stats): String = buildString {
        fun n(v: Double, d: Int = 0) = if (d == 0) v.roundToInt().toString() else String.format(Locale.US, "%.${d}f", v)
        appendLine("Last 7 complete days (computed by the app, exact):")
        appendLine("- food logged on ${s.loggedDays}/7 days")
        if (s.kcalAvg != null) appendLine("- average intake ${n(s.kcalAvg)} kcal vs target ${n(s.kcalTarget ?: 0.0)} kcal (logged days)")
        if (s.proteinAvg != null) appendLine("- average protein ${n(s.proteinAvg)} g vs target ${n(s.proteinTarget ?: 0.0)} g")
        s.trendDelta?.let { appendLine("- trend weight change ${if (it >= 0) "+" else ""}${n(it, 2)} kg") }
        appendLine("- ${s.sessions} strength sessions, volume ${n(s.volumeKg)} kg; ${s.cardioMin} min of cardio")
        s.sleepH?.let { appendLine("- average sleep ${n(it, 1)} h") }
        s.steps?.let { appendLine("- average steps ${n(it)}") }
    }.trim()

    /** Le mot du coach pour ce bilan (une fois par semaine glissante, gardé sur l'appareil). */
    suspend fun makeNote(ctx: Context, s: Stats): Note? {
        val p = Coach.prompts() ?: return null
        if (p.week.isBlank() || !Coach.ready()) return null
        val text = Coach.once(ctx, p, p.week.replace("{week}", describe(s))).lines().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?.trim('*', '"', '«', '»', ' ') ?: return null
        return Note(s.end, text).also { note.value = it; runCatching { file(ctx).writeText(json.encodeToString(Note.serializer(), it)) } }
    }
}
