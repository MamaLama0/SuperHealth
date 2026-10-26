package app.superhealth

import app.superhealth.platform.*

import app.superhealth.health.data.ActivityKind
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.HealthSource
import app.superhealth.health.data.Metric
import app.superhealth.health.data.Point
import app.superhealth.health.data.SourceDevice
import app.superhealth.health.data.TrackPoint
import app.superhealth.health.data.Workout
import app.superhealth.health.data.WorkoutDetail
import app.superhealth.health.data.WorkoutDetails
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Données de démonstration (aperçu ordinateur) : une Apple Watch portée jour et nuit, deux courses par semaine. */
object DemoHealthSource : HealthSource {
    override val name = "Apple Santé (démo)"
    override val available = true
    override suspend fun requestAuthorization() = true

    override fun supports(metric: Metric) = metric in setOf(Metric.STEPS, Metric.DISTANCE, Metric.CALORIES, Metric.ACTIVE_MINUTES,
        Metric.HEART_RATE, Metric.RESTING_HR, Metric.HRV, Metric.SLEEP, Metric.SPO2, Metric.RESPIRATORY_RATE, Metric.VO2MAX,
        Metric.TEMPERATURE, Metric.WEIGHT)

    private fun rnd(day: LocalDate, salt: Int) = Random(day.toEpochDay().toInt() * 31 + salt)

    override suspend fun daily(metric: Metric, date: LocalDate): Double? {
        val r = rnd(date, metric.ordinal)
        val today = date == LocalDate.now()
        val part = if (today) (LocalTime.now().toSecondOfDay() / 86_400.0) else 1.0
        return when (metric) {
            Metric.STEPS -> (6500 + r.nextInt(6500)) * part
            Metric.DISTANCE -> (4.5 + r.nextDouble() * 5) * part
            Metric.CALORIES -> (380 + r.nextInt(420)) * part
            Metric.ACTIVE_MINUTES -> (18 + r.nextInt(55)) * part
            Metric.RESTING_HR -> 52.0 + r.nextInt(7)
            Metric.HRV -> 48.0 + r.nextInt(22)
            Metric.SPO2 -> 95.0 + r.nextInt(4)
            Metric.RESPIRATORY_RATE -> 13.5 + r.nextDouble() * 2
            Metric.VO2MAX -> 47.0 + (date.toEpochDay() % 30) / 30.0
            Metric.TEMPERATURE -> 35.6 + r.nextDouble() * 0.6
            Metric.WEIGHT -> null
            Metric.SLEEP -> HealthRepository.sleepNight(date)?.filter { it.kind != ActivityKind.AWAKE_SLEEP }?.sumOf { it.end - it.start }?.div(60_000.0)
            else -> null
        }
    }

    override suspend fun samples(metric: Metric, fromMs: Long, toMs: Long): List<Point> {
        val now = nowMs()
        val day = Instant.ofEpochMilli(fromMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val r = rnd(day, 99)
        return when (metric) {
            Metric.HEART_RATE -> (fromMs until minOf(toMs, now) step 300_000L).map { t ->
                val h = ((t - fromMs) / 3_600_000.0)
                val base = if (h < 7 || h > 23) 54.0 else 72.0 + 10 * sin(h / 24 * 2 * PI)
                val run = if (h in 18.0..18.75 && day.dayOfWeek.value % 3 == 0) 75.0 else 0.0
                Point(t, base + run + r.nextDouble() * 8)
            }
            Metric.STEPS -> (fromMs until minOf(toMs, now) step 600_000L).mapNotNull { t ->
                val h = ((t - fromMs) / 3_600_000.0)
                if (h < 7.5 || h > 22.5) null else Point(t, (r.nextInt(180) + if (h in 12.0..13.0 || h in 17.5..18.5) 500 else 60).toDouble())
            }
            else -> emptyList()
        }
    }

    override suspend fun sleep(fromMs: Long, toMs: Long): List<HealthRepository.SleepPhase> {
        val zone = ZoneId.systemDefault()
        val out = ArrayList<HealthRepository.SleepPhase>()
        var d = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(toMs).atZone(zone).toLocalDate()
        while (!d.isAfter(last)) {
            val r = rnd(d, 7)
            // coucher vers 23 h la veille, lever vers 7 h
            var t = d.atStartOfDay(zone).toInstant().toEpochMilli() - (60 + r.nextInt(50)) * 60_000L
            val end = d.atStartOfDay(zone).toInstant().toEpochMilli() + (6 * 60 + 40 + r.nextInt(60)) * 60_000L
            val cycle = listOf(ActivityKind.LIGHT_SLEEP to 25, ActivityKind.DEEP_SLEEP to 30, ActivityKind.LIGHT_SLEEP to 20, ActivityKind.REM_SLEEP to 18, ActivityKind.AWAKE_SLEEP to 3)
            var i = 0
            while (t < end) {
                val (k, m) = cycle[i % cycle.size]
                val len = ((m + r.nextInt(10)) * 60_000L).coerceAtMost(end - t)
                if (t + len in fromMs..toMs) out += HealthRepository.SleepPhase(k, t, t + len)
                t += len; i++
            }
            d = d.plusDays(1)
        }
        return out
    }

    private val device = SourceDevice("healthkit", "Apple Watch")

    override suspend fun workouts(fromMs: Long, toMs: Long): List<Workout> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        return (1L..60L).map { today.minusDays(it) }.filter { it.dayOfWeek.value == 3 || it.dayOfWeek.value == 6 }.mapNotNull { d ->
            val start = d.atStartOfDay(zone).toInstant().toEpochMilli() + 18 * 3_600_000L
            if (start !in fromMs until toMs) return@mapNotNull null
            val km = 5.0 + (d.toEpochDay() % 4)
            Workout(d.toEpochDay(), null, ActivityKind.RUNNING, Date(start), Date(start + (km * 330_000).toLong()), device, km * 1000, km * 68)
        }
    }

    override suspend fun workoutDetail(id: Long): WorkoutDetail? {
        val w = workouts(0, nowMs()).firstOrNull { it.id == id } ?: return null
        val sec = (w.end.time - w.start.time) / 1000.0
        val r = Random(id.toInt())
        val pts = (0..sec.toInt() step 5).map { t ->
            val a = t / sec * 2 * PI
            TrackPoint(t.toDouble(), 0.0, 45.764 + 0.012 * sin(a), 4.835 + 0.018 * cos(a) * (1 + 0.2 * sin(3 * a)), 170 + 12 * sin(2 * a),
                (148 + 14 * sin(a * 3) + r.nextInt(5)).toInt(), 3.0 + r.nextDouble() * 0.4, 168 + r.nextInt(8))
        }
        val points = WorkoutDetails.withDistances(pts)
        return WorkoutDetail(id, "Course", ActivityKind.RUNNING, w.start.time, w.end.time, "Apple Watch", sec, w.distanceM, w.kcal,
            points.mapNotNull { it.hr }.average().toInt(), points.mapNotNull { it.hr }.max(), 48.0, 47.0, 171.0, 3.6, null, null, null, null,
            null, null, null, emptyList(), null, points, emptyList())
    }

    override suspend fun weights(fromMs: Long, toMs: Long): List<Triple<Long, Double, Double?>> = emptyList()
}
