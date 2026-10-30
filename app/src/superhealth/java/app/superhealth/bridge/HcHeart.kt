package app.superhealth.bridge

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Pouls minute par minute lu dans Health Connect (Garmin Connect, NOOP, autres apps), un fichier par jour : base de l'Effort,
 * des zones et de la courbe du jour quand la montre n'est pas gérée par SuperHealth. Trop volumineux pour les préférences.
 */
object HcHeart {
    private lateinit var dir: File
    private val memory = ConcurrentHashMap<Long, List<Pair<Long, Int>>>()

    fun init(ctx: Context) { dir = File(ctx.filesDir, "hc-heart").apply { mkdirs() } }

    /** Remplace les jours lus (jour → (horodatage en s, bpm), une valeur par minute) et oublie ceux de plus de 60 jours. */
    fun store(days: Map<Long, List<Pair<Long, Int>>>) {
        if (!::dir.isInitialized) return
        days.forEach { (d, pts) ->
            runCatching { File(dir, "$d.txt").writeText(pts.joinToString("\n") { "${it.first} ${it.second}" }) }
            memory[d] = pts
        }
        val old = LocalDate.now().minusDays(60).toEpochDay()
        dir.listFiles()?.filter { (it.nameWithoutExtension.toLongOrNull() ?: 0) < old }?.forEach { it.delete() }
    }

    /** Pouls (s, bpm) du jour [date], vide sans données. */
    fun minutes(date: LocalDate): List<Pair<Long, Int>> {
        if (!::dir.isInitialized) return emptyList()
        val d = date.toEpochDay()
        memory[d]?.let { return it }
        val f = File(dir, "$d.txt").takeIf { it.exists() } ?: return emptyList()
        val pts = runCatching {
            f.readLines().mapNotNull { l -> l.split(' ').takeIf { it.size == 2 }?.let { (t, v) -> t.toLong() to v.toInt() } }
        }.getOrDefault(emptyList())
        memory[d] = pts
        return pts
    }

    /** Minutes groupées par jour local, moyenne de chaque minute. */
    fun byMinute(samples: List<Pair<Long, Long>>, zone: java.time.ZoneId): Map<Long, List<Pair<Long, Int>>> =
        samples.filter { it.second in 30..230 }
            .groupBy({ it.first / 60_000 * 60 }, { it.second })
            .map { (sec, v) -> sec to v.average().toInt() }
            .groupBy { java.time.Instant.ofEpochSecond(it.first).atZone(zone).toLocalDate().toEpochDay() }
            .mapValues { (_, l) -> l.sortedBy { it.first } }
}

/**
 * Séances d'autres apps (Garmin Connect, NOOP…) affichées comme celles de la montre : résumé de l'app (distance, calories)
 * et pouls minute par minute lu dans Health Connect (courbe, zones). Pas de tracé : Health Connect ne le partage pas ici.
 */
object HcWorkouts {
    /** Identifiants négatifs sous ceux des séances importées (bornées à 2⁴⁶ secondes). */
    private const val BASE = 1L shl 47

    fun idOf(s: app.superhealth.HcSession): Long = -(BASE + s.start / 1000)
    fun isHc(id: Long) = id <= -BASE

    private val routes = ConcurrentHashMap<String, List<Pair<Double, Double>>>()

    /**
     * Tracé GPS (latitude, longitude) d'une séance Health Connect, quand Health Connect le donne sans demander d'accord
     * (séance écrite par l'app, ou accès aux tracés accordé) ; null sinon.
     */
    suspend fun route(ctx: Context, s: app.superhealth.HcSession): List<Pair<Double, Double>>? {
        val r = routes[s.id] ?: runCatching {
            val rec = androidx.health.connect.client.HealthConnectClient.getOrCreate(ctx)
                .readRecord(androidx.health.connect.client.records.ExerciseSessionRecord::class, s.id).record
            (rec.exerciseRouteResult as? androidx.health.connect.client.records.ExerciseRouteResult.Data)?.exerciseRoute?.route
                ?.map { it.latitude to it.longitude }
        }.getOrNull().orEmpty().also { routes[s.id] = it }
        return r.takeIf { it.size >= 2 }
    }

    fun detail(id: Long): app.superhealth.health.data.WorkoutDetail? {
        val s = app.superhealth.SuperPrefs.hc.value.sessions.firstOrNull { idOf(it) == id } ?: return null
        val zone = java.time.ZoneId.systemDefault()
        val days = generateSequence(java.time.Instant.ofEpochMilli(s.start).atZone(zone).toLocalDate()) { it.plusDays(1) }
            .takeWhile { !it.isAfter(java.time.Instant.ofEpochMilli(s.end).atZone(zone).toLocalDate()) }.toList()
        val pts = days.flatMap { HcHeart.minutes(it) }.filter { it.first * 1000 in s.start..s.end }
            .map { (t, bpm) -> app.superhealth.health.data.TrackPoint((t * 1000 - s.start) / 1000.0, 0.0, null, null, null, bpm, null, null) }
        val kind = when (Bridge.hcKindOf(s.type)) {
            Bridge.Kind.RUN -> nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.RUNNING
            Bridge.Kind.WALK -> nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.WALKING
            Bridge.Kind.HIKE -> nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.HIKING
            Bridge.Kind.BIKE -> nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.CYCLING
            Bridge.Kind.SWIM -> nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.SWIMMING
            Bridge.Kind.YOGA -> nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.YOGA
            Bridge.Kind.STRENGTH -> nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.STRENGTH_TRAINING
            Bridge.Kind.OTHER -> nodomain.freeyourgadget.gadgetbridge.model.ActivityKind.ACTIVITY
        }
        return app.superhealth.health.data.WorkoutDetail(
            id = id, name = s.title?.takeIf { it.isNotBlank() } ?: Bridge.kindLabel(Bridge.hcKindOf(s.type).name), kind = kind,
            start = s.start, end = s.end, deviceName = app.superhealth.health.data.DataSource.appName(s.app) ?: s.app,
            activeSec = (s.end - s.start) / 1000.0, distanceM = s.distanceM, kcal = s.kcal, avgHr = s.avgHr, maxHr = s.maxHr,
            ascentM = null, descentM = null, avgCadence = null, maxSpeedMps = null, steps = null, aerobicTe = null, anaerobicTe = null,
            recoverySec = null, poolLengthM = null, swolf = null, strokes = null, predictions = emptyList(), zoneBounds = null,
            points = pts, laps = emptyList(),
        )
    }
}
