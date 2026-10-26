package app.superhealth.health.data

import app.superhealth.platform.*

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class Workout(
    val id: Long,
    val name: String?,
    val kind: ActivityKind,
    val start: Date,
    val end: Date,
    val device: SourceDevice,
    val distanceM: Double? = null,
    val kcal: Double? = null,
)

/** Weekly cardio minutes (Apple : minutes d'exercice) against the weekly target. */
data class CardioWeek(val total: Int, val today: Int, val target: Int) {
    val progress: Float get() = if (target > 0) total.toFloat() / target else 0f
}

data class Goal(val metric: Metric, val target: Double)

sealed interface TimelineEvent {
    val timestampMs: Long

    data class Logged(val log: ManualLog) : TimelineEvent {
        override val timestampMs get() = log.timestampMs
    }

    data class WorkoutDone(val workout: Workout) : TimelineEvent {
        override val timestampMs get() = workout.end.time
    }
}

data class Point(val timestampMs: Long, val value: Double)

/**
 * Données santé de la plateforme (HealthSources : HealthKit sur iOS) et saisies à la main, avec la même interface que la
 * version Android (qui lisait la base Gadgetbridge) : les écrans, les scores et les insights restent identiques.
 */
object HealthRepository {
    private val LOG = LoggerFactory.getLogger("HealthRepository")
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val source: HealthSource get() = HealthSources.current

    /** Past days don't change once synced; cache their values (cleared after each sync). */
    private val cache = ConcurrentHashMap<Pair<Metric, LocalDate>, Double>()
    private const val NONE = Double.NaN
    private val dbPermits = Semaphore(4)

    fun invalidate() { cache.clear(); Scores.invalidate() }

    /** Sources gérées (Apple Santé) ; vide quand le module santé est désactivé. */
    fun devices(): List<SourceDevice> = app.superhealth.bridge.Watch.devices()

    fun startMs(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endMs(date: LocalDate) = startMs(date.plusDays(1))

    fun goals(): List<Goal> {
        val u = ActivityUser()
        return listOf(
            Goal(Metric.STEPS, u.stepsGoal.toDouble()),
            Goal(Metric.CALORIES, u.caloriesBurntGoal.toDouble()),
            Goal(Metric.DISTANCE, u.distanceGoalMeters / 1000.0),
            Goal(Metric.ACTIVE_MINUTES, u.activeTimeGoalMinutes.toDouble()),
            Goal(Metric.SLEEP, u.sleepDurationGoal.toDouble()),
            Goal(Metric.HYDRATION, u.hydrationGoalMl.toDouble()),
            Goal(Metric.CARDIO_WEEK, CARDIO_TARGET.toDouble()),
        ).filter { it.target > 0 }
    }

    fun goal(metric: Metric): Double? = goals().firstOrNull { it.metric == metric }?.target

    /** Recommandation de l'OMS : 150 minutes d'activité modérée par semaine. */
    private const val CARDIO_TARGET = 150

    /** True when the health source can record [metric], or it is logged by hand. */
    fun isSupported(metric: Metric): Boolean = metric.manual || (devices().isNotEmpty() && when (metric) {
        Metric.RECOVERY -> source.supports(Metric.HRV)
        Metric.EFFORT -> source.supports(Metric.HEART_RATE)
        Metric.SLEEP_DEBT -> source.supports(Metric.SLEEP)
        Metric.CARDIO_WEEK -> source.supports(Metric.ACTIVE_MINUTES)
        else -> source.supports(metric)
    })

    // ---- Daily values ---------------------------------------------------------------------

    suspend fun dayValue(context: Context, metric: Metric, date: LocalDate): Double? {
        val key = metric to date
        val cacheable = date.isBefore(LocalDate.now())
        if (cacheable) cache[key]?.let { return it.takeUnless { v -> v.isNaN() } }
        // les scores calculés relisent d'autres mesures : hors du sémaphore (sinon ils pourraient s'y bloquer entre eux)
        val v = if (metric.computed) computeDayValue(context, metric, date) else dbPermits.withPermit { computeDayValue(context, metric, date) }
        if (cacheable) cache[key] = v ?: NONE
        return v
    }

    private suspend fun computeDayValue(context: Context, metric: Metric, date: LocalDate): Double? {
        val from = startMs(date)
        val to = endMs(date)
        val manual = ManualLogStore.get(context)
        suspend fun read(m: Metric): Double? = if (devices().isNotEmpty() && source.supports(m)) safe { source.daily(m, date) } else null
        return when (metric) {
            Metric.STEPS, Metric.DISTANCE, Metric.CALORIES, Metric.ACTIVE_MINUTES, Metric.SLEEP, Metric.SLEEP_SCORE,
            Metric.BODY_ENERGY, Metric.STRESS, Metric.SPO2, Metric.VO2MAX, Metric.RESTING_HR, Metric.HRV,
            Metric.RESPIRATORY_RATE, Metric.TEMPERATURE, Metric.READINESS,
            Metric.STAND_HOURS, Metric.FLIGHTS, Metric.WALKING_HR, Metric.HR_RECOVERY, Metric.AFIB_BURDEN, Metric.WALKING_STEADINESS,
            Metric.WALKING_SPEED, Metric.DAYLIGHT, Metric.MINDFUL, Metric.NOISE -> read(metric)?.takeIf { it > 0 || metric == Metric.BODY_ENERGY }
            // zéro trouble respiratoire est une bonne nouvelle, pas une absence de donnée
            Metric.BREATHING_DISTURBANCES -> read(metric)
            Metric.CARDIO_WEEK -> cardioWeek(date)?.total?.toDouble()
            Metric.HEART_RATE -> intraday(Metric.HEART_RATE, date).map { it.value }.averageOrNull()
            Metric.RECOVERY -> Scores.chargeOf(context, date)?.score?.toDouble()
            Metric.EFFORT -> Scores.effortOf(context, date)
            Metric.SLEEP_DEBT -> Scores.sleepDebtOf(context, date)
            Metric.WEIGHT -> manual.query(metric, from, to).firstOrNull()?.value ?: read(Metric.WEIGHT)
            Metric.HYDRATION -> manual.query(metric, from, to).sumOf { it.value }.takeIf { it > 0 }
            Metric.BODY_FAT, Metric.GLUCOSE, Metric.BODY_TEMPERATURE -> {
                val logs = manual.query(metric, from, to)
                if (metric.aggregation == Aggregation.LAST) logs.firstOrNull()?.value else logs.map { it.value }.averageOrNull()
            }
        }
    }

    /** Most recent value on or before [date] (Google Health shows the last known weight, VO2 max…). */
    suspend fun latestValue(context: Context, metric: Metric, date: LocalDate, lookbackDays: Long = 30): Pair<LocalDate, Double>? {
        if (metric.manual && metric != Metric.HYDRATION) {
            ManualLogStore.get(context).latestBefore(metric, endMs(date))?.let {
                return Instant.ofEpochMilli(it.timestampMs).atZone(zone).toLocalDate() to it.value
            }
        }
        for (i in 0L until lookbackDays) {
            val d = date.minusDays(i)
            dayValue(context, metric, d)?.let { return d to it }
        }
        return null
    }

    suspend fun series(context: Context, metric: Metric, from: LocalDate, toInclusive: LocalDate): List<Pair<LocalDate, Double?>> =
        coroutineScope {
            generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(toInclusive) }.toList()
                .map { d -> async(Dispatchers.IO) { d to dayValue(context, metric, d) } }
                .awaitAll()
        }

    // ---- Intraday ---------------------------------------------------------------------------

    /** Samples within [date]: steps summed per hour, heart rate averaged per 10 minutes, others raw. */
    suspend fun intraday(metric: Metric, date: LocalDate): List<Point> = withContext(Dispatchers.IO) {
        if (devices().isEmpty() || !source.supports(metric)) return@withContext emptyList()
        val from = startMs(date)
        val to = endMs(date)
        val raw = safe { source.samples(metric, from, to) }.orEmpty()
        when (metric) {
            Metric.STEPS, Metric.HEART_RATE -> {
                val steps = metric == Metric.STEPS
                val bucket = if (steps) 3_600_000L else 600_000L
                raw.filter { if (steps) it.value > 0 else it.value in 30.0..230.0 }
                    .groupBy({ it.timestampMs / bucket * bucket }, { it.value })
                    .map { (t, vs) -> Point(t, if (steps) vs.sum() else vs.average()) }
                    .sortedBy { it.timestampMs }
            }
            Metric.STRESS -> raw.filter { it.value in 1.0..100.0 }
            Metric.BODY_ENERGY -> raw.filter { it.value in 0.0..100.0 }
            Metric.SPO2 -> raw.filter { it.value in 50.0..100.0 }
            else -> emptyList()
        }
    }

    /** Phase d'une nuit : type (léger, profond, paradoxal, éveil) et bornes en ms. */
    data class SleepPhase(val kind: ActivityKind, val start: Long, val end: Long)

    /**
     * Nuit qui se termine le jour [date] (la plus longue s'il y en a plusieurs), phase par phase ; null sans nuit enregistrée.
     * Les phases à moins d'une heure d'écart appartiennent à la même nuit.
     */
    suspend fun sleepNight(date: LocalDate): List<SleepPhase>? = runCatching {
        if (devices().isEmpty() || !source.supports(Metric.SLEEP)) return@runCatching null
        val phases = source.sleep(startMs(date) - 18 * 3_600_000L, endMs(date)).sortedBy { it.start }
        if (phases.isEmpty()) return@runCatching null
        val nights = mutableListOf<MutableList<SleepPhase>>()
        phases.forEach { p ->
            val last = nights.lastOrNull()
            if (last != null && p.start - last.maxOf { it.end } < 3_600_000L) last += p else nights += mutableListOf(p)
        }
        nights.filter { n -> n.maxOf { it.end } in startMs(date) until endMs(date) }
            .maxByOrNull { n -> n.maxOf { it.end } - n.minOf { it.start } }
    }.getOrNull()

    /** Pouls minute par minute (horodatage en s, bpm) du jour : base de l'Effort et des zones. */
    suspend fun minuteHeart(date: LocalDate): List<Pair<Long, Int>> = withContext(Dispatchers.IO) {
        if (devices().isEmpty() || !source.supports(Metric.HEART_RATE)) return@withContext emptyList()
        runCatching {
            source.samples(Metric.HEART_RATE, startMs(date), endMs(date))
                .filter { it.value in 30.0..230.0 }
                .groupBy({ it.timestampMs / 60_000 * 60 }, { it.value })
                .map { (t, v) -> t to v.average().toInt() }.sortedBy { it.first }
        }.getOrDefault(emptyList())
    }

    // ---- Composite values -------------------------------------------------------------------

    /** Minutes d'exercice cumulées depuis lundi (anneau « Exercice » d'Apple). */
    suspend fun cardioWeek(date: LocalDate): CardioWeek? {
        if (devices().isEmpty() || !source.supports(Metric.ACTIVE_MINUTES)) return null
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = generateSequence(monday) { it.plusDays(1) }.takeWhile { !it.isAfter(date) }.toList()
        val values = days.map { d -> if (d == date) safe { source.daily(Metric.ACTIVE_MINUTES, d) } else dayValue(appContext, Metric.ACTIVE_MINUTES, d) }
        val today = values.lastOrNull() ?: 0.0
        return CardioWeek(values.sumOf { it ?: 0.0 }.toInt(), (today ?: 0.0).toInt(), CARDIO_TARGET)
    }

    // ---- Workouts & timeline ----------------------------------------------------------------

    suspend fun workouts(from: Long, to: Long): List<Workout> =
        if (devices().isEmpty()) emptyList() else safe { source.workouts(from, to) }.orEmpty().sortedByDescending { it.start.time }

    suspend fun timeline(context: Context, date: LocalDate, days: Long = 1): List<TimelineEvent> {
        val from = startMs(date.minusDays(days - 1))
        val to = endMs(date)
        val logs = ManualLogStore.get(context).query(null, from, to).map { TimelineEvent.Logged(it) }
        val workouts = workouts(from, to).map { TimelineEvent.WorkoutDone(it) }
        return (logs + workouts).sortedByDescending { it.timestampMs }
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private suspend fun <T> safe(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LOG.error("Lecture santé impossible", e)
        null
    }

    private fun List<Double>.averageOrNull() = if (isEmpty()) null else average()
}
