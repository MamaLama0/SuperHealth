package app.superhealth.health.data

import android.content.Context
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.GregorianCalendar
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminCoordinator
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample
import nodomain.freeyourgadget.gadgetbridge.model.TimeSample
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardQuery
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.ActiveTimeWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BodyEnergyWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.CaloriesActiveWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.DistanceWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.PaiWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.SleepScoreWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.SleepWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.Spo2Widget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StepsWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StressSimpleWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.Vo2MaxAnyWidget
import org.slf4j.LoggerFactory

data class Workout(
    val id: Long,
    val name: String?,
    val kind: ActivityKind,
    val start: Date,
    val end: Date,
    val device: GBDevice,
    val distanceM: Double? = null,
    val kcal: Double? = null,
)

/** Weekly cardio minutes (Garmin intensity minutes / Amazfit PAI) against the weekly target. */
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
 * Reads health data from the Gadgetbridge database through its device-agnostic coordinators and
 * dashboard widgets, so every watch Gadgetbridge supports (Garmin or not) feeds the same screens.
 */
object HealthRepository {
    private val LOG = LoggerFactory.getLogger(HealthRepository::class.java)
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Past days don't change once synced; cache their values (cleared after each sync). */
    private val cache = ConcurrentHashMap<Pair<Metric, LocalDate>, Double>()
    private const val NONE = Double.NaN
    private val dbPermits = Semaphore(4)

    fun invalidate() = cache.clear()

    /** Montres gérées par le module LocalHealth ; vide quand le module est désactivé. */
    fun devices(): List<GBDevice> = app.superhealth.bridge.Watch.devices()

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
            Goal(Metric.CARDIO_WEEK, cardioTarget().toDouble()),
        ).filter { it.target > 0 }
    }

    fun goal(metric: Metric): Double? = goals().firstOrNull { it.metric == metric }?.target

    private fun cardioTarget(): Int {
        val coordinators = devices().filter { it.deviceCoordinator.supportsPai(it) }.map { it.deviceCoordinator }
        // Garmin's weekly intensity-minutes goal defaults to 150 (WHO guideline); PAI targets 100
        return when {
            coordinators.any { it is GarminCoordinator } -> 150
            coordinators.isNotEmpty() -> coordinators.first().paiTarget
            else -> 150
        }
    }

    /** True when a paired device can record [metric], or it is logged by hand. */
    fun isSupported(metric: Metric): Boolean = metric.manual || devices().any { d ->
        val c = d.deviceCoordinator
        when (metric) {
            Metric.STEPS, Metric.DISTANCE, Metric.ACTIVE_MINUTES -> c.supportsStepCounter(d)
            Metric.CALORIES -> c.supportsActiveCalories(d)
            Metric.CARDIO_WEEK -> c.supportsPai(d)
            Metric.HEART_RATE -> c.supportsHeartRateMeasurement(d)
            Metric.RESTING_HR -> c.supportsHeartRateRestingMeasurement(d)
            Metric.HRV -> c.supportsHrvMeasurement(d)
            Metric.SLEEP -> c.supportsSleepMeasurement(d)
            Metric.SLEEP_SCORE -> c.supportsSleepScore(d)
            Metric.READINESS -> c.supportsTrainingReadiness(d)
            Metric.BODY_ENERGY -> c.supportsBodyEnergy(d)
            Metric.STRESS -> c.supportsStressMeasurement(d)
            Metric.SPO2 -> c.supportsSpo2(d)
            Metric.RESPIRATORY_RATE -> c.supportsRespiratoryRate(d)
            Metric.VO2MAX -> c.supportsVO2Max(d)
            Metric.TEMPERATURE -> c.supportsTemperatureMeasurement(d)
            else -> false
        }
    }

    // ---- Daily values ---------------------------------------------------------------------

    suspend fun dayValue(context: Context, metric: Metric, date: LocalDate): Double? {
        val key = metric to date
        val cacheable = date.isBefore(LocalDate.now())
        if (cacheable) cache[key]?.let { return it.takeUnless { v -> v.isNaN() } }
        val v = dbPermits.withPermit { computeDayValue(context, metric, date) }
        if (cacheable) cache[key] = v ?: NONE
        return v
    }

    private suspend fun computeDayValue(context: Context, metric: Metric, date: LocalDate): Double? {
        val endOfDay = (endMs(date) / 1000).toInt() - 1
        val query = DashboardQuery(endOfDay - 86400, endOfDay)
        val from = startMs(date)
        val to = endMs(date)
        val manual = ManualLogStore.get(context)
        return when (metric) {
            Metric.STEPS -> widget(StepsWidget, query)?.total?.toDouble()?.takeIf { it > 0 }
            Metric.DISTANCE -> widget(DistanceWidget, query)?.total?.toDouble()?.takeIf { it > 0 }?.div(1000.0)
            Metric.CALORIES -> widget(CaloriesActiveWidget, query)?.total?.toDouble()?.takeIf { it > 0 }
            Metric.ACTIVE_MINUTES -> widget(ActiveTimeWidget, query)?.total?.toDouble()?.takeIf { it > 0 }
            Metric.CARDIO_WEEK -> cardioWeek(date)?.total?.toDouble()
            Metric.SLEEP -> widget(SleepWidget, query)?.total?.toDouble()?.takeIf { it > 0 }
            Metric.SLEEP_SCORE -> widget(SleepScoreWidget, query)?.value?.toDouble()?.takeIf { it > 0 }
            Metric.BODY_ENERGY -> widget(BodyEnergyWidget, query)?.value?.toDouble()?.takeIf { it >= 0 }
            Metric.STRESS -> widget(StressSimpleWidget, query)?.value?.toDouble()?.takeIf { it > 0 }
            Metric.SPO2 -> widget(Spo2Widget, query)?.spo2?.toDouble()?.takeIf { it > 0 }
            Metric.VO2MAX -> widget(Vo2MaxAnyWidget, query)?.value?.toDouble()?.takeIf { it > 0 }
            Metric.HEART_RATE -> intraday(Metric.HEART_RATE, date).map { it.value }.averageOrNull()
            Metric.RESTING_HR -> samples(from, to) { d, s -> d.deviceCoordinator.getHeartRateRestingSampleProvider(d, s) }
                .map { it.heartRate.toDouble() }.filter { it > 0 }.averageOrNull()
            Metric.HRV -> samples(from, to) { d, s -> d.deviceCoordinator.getHrvSummarySampleProvider(d, s) }
                .mapNotNull { it.lastNightAverage?.toDouble() }.lastOrNull { it > 0 }
            Metric.RESPIRATORY_RATE -> samples(from, to) { d, s -> d.deviceCoordinator.getRespiratoryRateSampleProvider(d, s) }
                .map { it.respiratoryRate.toDouble() }.filter { it > 0 }.averageOrNull()
            Metric.TEMPERATURE -> samples(from, to) { d, s -> d.deviceCoordinator.getTemperatureSampleProvider(d, s) }
                .map { it.temperature.toDouble() }.filter { it > 0 }.averageOrNull()
            Metric.READINESS -> readiness(date)
            Metric.WEIGHT -> manual.query(metric, from, to).firstOrNull()?.value
                ?: samples(from, to) { d, s -> d.deviceCoordinator.getWeightSampleProvider(d, s) }
                    .map { it.weightKg.toDouble() }.lastOrNull { it > 0 }
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
        val from = startMs(date)
        val to = endMs(date)
        when (metric) {
            Metric.STEPS, Metric.HEART_RATE -> {
                val steps = metric == Metric.STEPS
                val bucket = if (steps) 3_600_000L else 600_000L
                val raw = db { db ->
                    devices().filter { it.deviceCoordinator.supportsActivityTracking(it) }.flatMap { d ->
                        d.deviceCoordinator.getSampleProvider(d, db.daoSession)
                            ?.getAllActivitySamples((from / 1000).toInt(), (to / 1000).toInt()).orEmpty()
                    }
                }
                raw.mapNotNull { s ->
                    val v = if (steps) s.steps else s.heartRate
                    val valid = if (steps) v > 0 else v in 30..230
                    if (valid) (s.timestamp * 1000L / bucket * bucket) to v.toDouble() else null
                }.groupBy({ it.first }, { it.second })
                    .map { (t, vs) -> Point(t, if (steps) vs.sum() else vs.average()) }
                    .sortedBy { it.timestampMs }
            }
            Metric.STRESS -> samples(from, to) { d, s -> d.deviceCoordinator.getStressSampleProvider(d, s) }
                .filter { it.stress in 1..100 }.map { Point(it.timestamp, it.stress.toDouble()) }
            Metric.BODY_ENERGY -> samples(from, to) { d, s -> d.deviceCoordinator.getBodyEnergySampleProvider(d, s) }
                .filter { it.energy in 0..100 }.map { Point(it.timestamp, it.energy.toDouble()) }
            Metric.SPO2 -> samples(from, to) { d, s -> d.deviceCoordinator.getSpo2SampleProvider(d, s) }
                .filter { it.spo2 in 50..100 }.map { Point(it.timestamp, it.spo2.toDouble()) }
            else -> emptyList()
        }
    }

    // ---- Composite values -------------------------------------------------------------------

    suspend fun cardioWeek(date: LocalDate): CardioWeek? {
        val endOfDay = (endMs(date) / 1000).toInt() - 1
        // Query from the start of the (Monday-based) week so the weekly total is available
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val query = DashboardQuery((startMs(monday) / 1000).toInt(), endOfDay)
        val data = widget(PaiWidget, query) ?: return null
        return CardioWeek(data.total, data.today, cardioTarget())
    }

    /** Garmin Training Readiness, for watches that record it. */
    private suspend fun readiness(date: LocalDate): Double? = db { db ->
        val cal = GregorianCalendar.getInstance().apply { timeInMillis = endMs(date) - 1 }
        devices().filter { it.deviceCoordinator.supportsTrainingReadiness(it) }.firstNotNullOfOrNull { d ->
            GenericMetricSampleProvider.getLatestMetricSample(db, d, MetricSample.Metric.GARMIN_TRAINING_READINESS, cal)
                ?.takeIf { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == date }
                ?.metricScore
        }
    }

    // ---- Workouts & timeline ----------------------------------------------------------------

    suspend fun workouts(from: Long, to: Long): List<Workout> = db { db ->
        val byId = devices().associateBy { DBHelper.getDevice(it, db.daoSession).id ?: -1L }
        db.daoSession.baseActivitySummaryDao.queryBuilder()
            .where(
                BaseActivitySummaryDao.Properties.StartTime.ge(Date(from)),
                BaseActivitySummaryDao.Properties.StartTime.lt(Date(to)),
            )
            .orderDesc(BaseActivitySummaryDao.Properties.StartTime)
            .list()
            .mapNotNull { s ->
                val device = byId[s.deviceId] ?: return@mapNotNull null
                val (distance, kcal) = WorkoutDetails.summaryNumbers(s.summaryData)
                Workout(s.id ?: return@mapNotNull null, s.name, ActivityKind.fromCode(s.activityKind), s.startTime, s.endTime, device, distance, kcal)
            }
    }

    suspend fun timeline(context: Context, date: LocalDate, days: Long = 1): List<TimelineEvent> {
        val from = startMs(date.minusDays(days - 1))
        val to = endMs(date)
        val logs = ManualLogStore.get(context).query(null, from, to).map { TimelineEvent.Logged(it) }
        val workouts = workouts(from, to).map { TimelineEvent.WorkoutDone(it) }
        return (logs + workouts).sortedByDescending { it.timestampMs }
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private suspend fun <T> db(block: (DBHandler) -> T): T = withContext(Dispatchers.IO) {
        GBApplication.acquireDbReadOnly().use { block(it) }
    }

    private suspend fun <T : TimeSample> samples(
        from: Long,
        to: Long,
        provider: (GBDevice, DaoSession) -> TimeSampleProvider<out T>?,
    ): List<T> = try {
        db { db ->
            devices().flatMap { d ->
                runCatching { provider(d, db.daoSession)?.getAllSamples(from, to - 1).orEmpty() }
                    .getOrElse { emptyList() }
            }.sortedBy { it.timestamp }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LOG.error("Failed to read samples", e)
        emptyList()
    }

    /** Runs a Gadgetbridge dashboard widget's loader; null when no paired device supports it. */
    private suspend fun <D> widget(widget: GBWidget<D>, query: DashboardQuery): D? {
        val instanceId = "localhealth_" + widget.id
        val config = WidgetConfig(
            WidgetInstance(instanceId, widget.id, 1),
            Prefs(GBApplication.getWidgetSharedPrefs(instanceId)),
            true,
            emptySet(),
        )
        val devices = config.resolveDevices(widget)
        if (devices.isEmpty()) return null
        return try {
            widget.loadData(WidgetDataScope(query, devices), config)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.error("Failed to load {}", widget.id, e)
            null
        }
    }

    private fun List<Double>.averageOrNull() = if (isEmpty()) null else average()
}
