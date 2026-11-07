package app.superhealth.bridge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.io.File
import kotlin.math.roundToInt
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
 * Séances d'autres apps (Garmin Connect, Fitbit, NOOP…) affichées comme celles de la montre : résumé de l'app (distance,
 * calories), pouls lu dans Health Connect (courbe, zones), et ce que l'app source y écrit pendant la séance : tracé GPS
 * (après accord de l'utilisateur, [ExerciseRouteRequestContract]), vitesse, cadence et dénivelé.
 */
object HcWorkouts {
    /** Identifiants négatifs sous ceux des séances importées (bornées à 2⁴⁶ secondes). */
    private const val BASE = 1L shl 47

    fun idOf(s: app.superhealth.HcSession): Long = stableId(s.app, s.clientRecordId ?: s.id)

    private fun stableId(app: String, recordId: String): Long {
        val identity = app + "\u0000" + recordId
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        val hash = java.nio.ByteBuffer.wrap(digest).long ushr 17
        return -((1L shl 48) + hash)
    }

    private fun legacyId(s: app.superhealth.HcSession) = -(BASE + s.start / 1000)

    internal fun sessionFor(id: Long, sessions: List<app.superhealth.HcSession>): app.superhealth.HcSession? =
        sessions.firstOrNull { idOf(it) == id || stableId(it.app, it.id) == id }
            ?: sessions.firstOrNull { legacyId(it) == id }
    fun isHc(id: Long) = id <= -BASE

    private val routes = ConcurrentHashMap<String, List<ExerciseRoute.Location>>()

    private suspend fun routeResult(ctx: Context, s: app.superhealth.HcSession): ExerciseRouteResult? = try {
        HealthConnectClient.getOrCreate(ctx).readRecord(ExerciseSessionRecord::class, s.id).record.exerciseRouteResult
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * Points du tracé d'une séance Health Connect, quand Health Connect le donne sans demander d'accord (séance écrite par
     * l'app, ou tracé déjà accordé) ; null sinon.
     */
    private suspend fun locations(ctx: Context, s: app.superhealth.HcSession): List<ExerciseRoute.Location>? {
        val r = routes[s.id] ?: (routeResult(ctx, s) as? ExerciseRouteResult.Data)?.exerciseRoute?.route.orEmpty()
            .also { if (it.size >= 2) routes[s.id] = it }
        return r.takeIf { it.size >= 2 }
    }

    /** Tracé GPS (latitude, longitude) d'une séance Health Connect, null sans tracé accessible. */
    suspend fun route(ctx: Context, s: app.superhealth.HcSession): List<Pair<Double, Double>>? =
        locations(ctx, s)?.map { it.latitude to it.longitude }

    /** Vrai quand l'app source a écrit un tracé que Health Connect ne donne qu'après accord (Fitbit, Garmin Connect…). */
    suspend fun routeNeedsConsent(ctx: Context, s: app.superhealth.HcSession): Boolean =
        routes[s.id] == null && routeResult(ctx, s) is ExerciseRouteResult.ConsentRequired

    /** Tracé rendu par [ExerciseRouteRequestContract] après accord. */
    fun keepRoute(s: app.superhealth.HcSession, route: ExerciseRoute) {
        if (route.route.size >= 2) routes[s.id] = route.route
    }

    /** [detail] complété par ce que l'app source a écrit dans Health Connect pendant la séance. */
    suspend fun fullDetail(ctx: Context, id: Long): app.superhealth.health.data.WorkoutDetail? {
        val base = detail(id) ?: return null
        val s = sessionFor(id, app.superhealth.SuperPrefs.hc.value.sessions) ?: return base
        return try {
            enrich(ctx, s, base)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            base
        }
    }

    private suspend fun enrich(ctx: Context, s: app.superhealth.HcSession, base: app.superhealth.health.data.WorkoutDetail): app.superhealth.health.data.WorkoutDetail {
        val client = HealthConnectClient.getOrCreate(ctx)
        val granted = client.permissionController.getGrantedPermissions()
        val range = TimeRangeFilter.between(java.time.Instant.ofEpochMilli(s.start), java.time.Instant.ofEpochMilli(s.end))
        suspend fun <T : Record> read(c: kotlin.reflect.KClass<T>): List<T> =
            if (HealthPermission.getReadPermission(c) !in granted) emptyList()
            else client.readRecords(ReadRecordsRequest(c, range, dataOriginFilter = setOf(DataOrigin(s.app)))).records
        val speeds = read(SpeedRecord::class).flatMap { it.samples }.map { it.time.toEpochMilli() to it.speed.inMetersPerSecond }
            .filter { it.second.isFinite() && it.second >= 0 }.sortedBy { it.first }
        val cadences = read(StepsCadenceRecord::class).flatMap { it.samples }.map { it.time.toEpochMilli() to it.rate }
            .filter { it.second.isFinite() && it.second > 0 }.sortedBy { it.first }
        val ascent = read(ElevationGainedRecord::class).sumOf { it.elevation.inMeters }.takeIf { it > 0 }
        val route = locations(ctx, s).orEmpty().filter { it.time.toEpochMilli() in s.start..s.end }.sortedBy { it.time }
        val hrs = base.points.mapNotNull { p -> p.hr?.let { s.start + (p.t * 1000).toLong() to it } }
        if (route.size < 2 && speeds.isEmpty() && cadences.isEmpty()) return base.copy(ascentM = ascent ?: base.ascentM)

        val times = if (route.size >= 2) route.map { it.time.toEpochMilli() }
            else (hrs.map { it.first } + speeds.map { it.first } + cadences.map { it.first }).distinct().sorted()
        val byTime = route.associateBy { it.time.toEpochMilli() }
        var cum = 0.0
        var prev: Long? = null
        val points = times.map { t ->
            val loc = byTime[t]
            val v = near(speeds, t, 60_000)
            val before = prev
            // Distance : le long du tracé, sinon la vitesse intégrée (trous de plus de 10 s ignorés).
            if (loc != null) before?.let { byTime[it] }?.let { cum += app.superhealth.health.data.WorkoutDetails.haversine(it.latitude, it.longitude, loc.latitude, loc.longitude) }
            else if (v != null && before != null) cum += v * ((t - before) / 1000.0).coerceAtMost(10.0)
            prev = t
            app.superhealth.health.data.TrackPoint((t - s.start) / 1000.0, cum, loc?.latitude, loc?.longitude, loc?.altitude?.inMeters,
                near(hrs, t, 90_000), v?.takeIf { it > 0 }, near(cadences, t, 60_000)?.roundToInt())
        }
        return base.copy(
            points = points,
            distanceM = base.distanceM ?: cum.takeIf { it > 0 },
            ascentM = ascent ?: base.ascentM,
            avgCadence = cadences.map { it.second }.takeIf { it.isNotEmpty() }?.average(),
            maxSpeedMps = speeds.maxOfOrNull { it.second }?.takeIf { it > 0 },
        )
    }

    /** Dernière valeur de [series] (triée par temps) au plus [maxGapMs] avant [t]. */
    private fun <V> near(series: List<Pair<Long, V>>, t: Long, maxGapMs: Long): V? {
        var lo = 0
        var hi = series.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (series[mid].first <= t) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return series.getOrNull(found)?.takeIf { t - it.first <= maxGapMs }?.second
    }

    fun detail(id: Long): app.superhealth.health.data.WorkoutDetail? {
        val s = sessionFor(id, app.superhealth.SuperPrefs.hc.value.sessions) ?: return null
        val zone = java.time.ZoneId.systemDefault()
        val days = generateSequence(java.time.Instant.ofEpochMilli(s.start).atZone(zone).toLocalDate()) { it.plusDays(1) }
            .takeWhile { !it.isAfter(java.time.Instant.ofEpochMilli(s.end).atZone(zone).toLocalDate()) }.toList()
        val pts = (s.heartRate ?: days.flatMap { HcHeart.minutes(it) }).filter { it.first * 1000 in s.start..s.end }
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
            points = pts, laps = emptyList(), totalKcal = s.totalKcal, activeTimeKnown = false,
        )
    }
}
