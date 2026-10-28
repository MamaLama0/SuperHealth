package app.superhealth.health.data

import android.content.Context
import app.superhealth.bridge.Watch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries as E
import org.slf4j.LoggerFactory
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Séance enregistrée par la montre, prête à afficher (comme le détail d'activité de Garmin Connect) :
 * résumé calculé par la montre, et courbes recalculées depuis le tracé (points GPS, FC, vitesse, altitude, cadence).
 */
data class WorkoutDetail(
    val id: Long,
    val name: String,
    val kind: ActivityKind,
    val start: Long,
    val end: Long,
    val deviceName: String,
    /** Durée de mouvement (chrono de la montre, pauses exclues), en secondes. */
    val activeSec: Double,
    val distanceM: Double?,
    val kcal: Double?,
    val avgHr: Int?,
    val maxHr: Int?,
    val ascentM: Double?,
    val descentM: Double?,
    val avgCadence: Double?,
    val maxSpeedMps: Double?,
    val steps: Int?,
    val aerobicTe: Double?,
    val anaerobicTe: Double?,
    val recoverySec: Double?,
    val poolLengthM: Double?,
    val swolf: Double?,
    val strokes: Int?,
    /** Prédictions de course de la montre (5 km, 10 km, semi, marathon), en secondes. */
    val predictions: List<Pair<String, Double>>,
    /** Bornes des zones cardiaques réglées sur la montre (5 valeurs), sinon null. */
    val zoneBounds: List<Int>?,
    val points: List<TrackPoint>,
    val laps: List<Lap>,
) {
    val hasGps: Boolean get() = points.count { it.lat != null } >= 2
    val isSwim: Boolean get() = kind.name.contains("SWIM")
    /** Allure (course, marche, randonnée) plutôt que vitesse (vélo…). */
    val usesPace: Boolean get() = kind.name.let { "RUN" in it || "WALK" in it || "HIK" in it || "JOG" in it || "TRAIL" in it }
    val avgSpeedMps: Double? get() = distanceM?.takeIf { it > 0 && activeSec > 0 }?.let { it / activeSec }
}

/** Un point du tracé : [t] secondes depuis le départ, [d] distance cumulée (m). */
data class TrackPoint(
    val t: Double, val d: Double, val lat: Double?, val lon: Double?, val alt: Double?,
    val hr: Int?, val speed: Double?, val cadence: Int?,
)

/** Tour (bouton Lap de la montre, ou kilomètre automatique). */
data class Lap(val index: Int, val distanceM: Double, val sec: Double, val avgHr: Int?, val ascentM: Double?)

object WorkoutDetails {
    private val LOG = LoggerFactory.getLogger(WorkoutDetails::class.java)

    suspend fun load(ctx: Context, id: Long): WorkoutDetail? = withContext(Dispatchers.IO) {
        if (app.superhealth.bridge.HcWorkouts.isHc(id)) return@withContext app.superhealth.bridge.HcWorkouts.detail(id) // Garmin Connect, NOOP…
        if (id < 0) return@withContext app.superhealth.bridge.RunImport.detail(id) // séance importée (GPX, TCX, FIT sans montre)
        val summary = GBApplication.acquireDbReadOnly().use { db ->
            db.daoSession.baseActivitySummaryDao.load(id)?.also { it.device } // appareil chargé tant que la session est ouverte
        } ?: return@withContext null
        val device = Watch.allDevices().firstOrNull { it.address.equals(summary.device?.identifier, ignoreCase = true) }
        // Résumé : celui de l'analyse du fichier par le coordinateur (plus complet), sinon celui enregistré.
        val data: ActivitySummaryData = runCatching {
            device!!.deviceCoordinator.getActivitySummaryParser(device, ctx).parseWorkout(summary, true).data
        }.getOrElse { ActivitySummaryData.fromJson(summary.summaryData) ?: ActivitySummaryData() }
        val track = runCatching { device?.deviceCoordinator?.getActivityTrackProvider(device, ctx)?.getActivityTrack(summary) }
            .onFailure { LOG.warn("No track for workout {}", id, it) }.getOrNull()

        val t0 = summary.startTime.time
        val raw = track?.allPoints.orEmpty().sortedBy { it.time.time }
        var cum = 0.0
        var prev: Pair<Double, Double>? = null
        val points = raw.map { p ->
            val loc = p.location
            val lat = loc?.latitude?.takeIf { it != 0.0 }
            val lon = loc?.longitude?.takeIf { it != 0.0 }
            // Distance : celle de la montre quand elle la donne, sinon recalculée entre deux points GPS.
            if (p.distance > 0) cum = p.distance
            else if (lat != null && lon != null) {
                prev?.let { (a, b) -> cum += haversine(a, b, lat, lon) }
                prev = lat to lon
            }
            val alt = (loc?.altitude?.takeIf { loc.hasAltitude() } ?: p.altitude.takeIf { it != 0.0 && !it.isNaN() })
            TrackPoint(
                t = (p.time.time - t0) / 1000.0, d = cum, lat = lat, lon = lon, alt = alt,
                hr = p.heartRate.takeIf { it in 30..240 }, speed = p.speed.toDouble().takeIf { it > 0 && !it.isNaN() },
                cadence = p.cadence.takeIf { it > 0 },
            )
        }

        fun num(key: String): Double? = data.getNumber(key, null)?.toDouble()?.takeIf { !it.isNaN() && it != 0.0 }
        val active = num(E.ACTIVE_SECONDS) ?: ((summary.endTime.time - t0) / 1000.0)
        val distance = num(E.DISTANCE_METERS) ?: points.lastOrNull()?.d?.takeIf { it > 0 }
        val hrs = points.mapNotNull { it.hr }
        val laps = track?.splits.orEmpty().filter { (it.totalDistanceMeters ?: 0.0) > 0 }.mapIndexed { i, s ->
            val from = (s.startTimeSec * 1000 - t0) / 1000.0
            val to = from + (s.totalElapsedTimeSec ?: 0.0)
            Lap(i + 1, s.totalDistanceMeters ?: 0.0, s.totalTimerTimeSec ?: s.totalElapsedTimeSec ?: 0.0,
                points.filter { it.t in from..to }.mapNotNull { it.hr }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                s.totalAscentMeters?.toDouble())
        }
        val bounds = listOf(E.HR_ZONE_BOUND_1, E.HR_ZONE_BOUND_2, E.HR_ZONE_BOUND_3, E.HR_ZONE_BOUND_4, E.HR_ZONE_BOUND_5)
            .map { num(it)?.toInt() }.takeIf { l -> l.all { it != null && it > 0 } }?.map { it!! }

        WorkoutDetail(
            id = id,
            // Sans nom donné sur la montre : libellé SuperHealth du type d'activité (traduit à l'affichage).
            name = summary.name?.takeIf { it.isNotBlank() }
                ?: app.superhealth.bridge.Bridge.kindLabel(app.superhealth.bridge.Bridge.kindOf(ActivityKind.fromCode(summary.activityKind).name).name),
            kind = ActivityKind.fromCode(summary.activityKind),
            start = t0, end = summary.endTime.time,
            deviceName = device?.aliasOrName ?: summary.device?.name ?: "",
            activeSec = active,
            distanceM = distance,
            kcal = num(E.CALORIES_BURNT) ?: num(E.CALORIES_TOTAL),
            avgHr = num(E.HR_AVG)?.toInt() ?: hrs.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            maxHr = num(E.HR_MAX)?.toInt() ?: hrs.maxOrNull(),
            ascentM = num(E.TOTAL_ASCENT) ?: num(E.ASCENT_METERS),
            descentM = num(E.TOTAL_DESCENT) ?: num(E.DESCENT_METERS),
            avgCadence = num(E.CADENCE_AVG) ?: num(E.STEP_RATE_AVG),
            maxSpeedMps = num(E.SPEED_MAX),
            steps = num(E.STEPS)?.toInt(),
            aerobicTe = num(E.TRAINING_EFFECT_AEROBIC),
            anaerobicTe = num(E.TRAINING_EFFECT_ANAEROBIC),
            recoverySec = num(E.RECOVERY_TIME),
            poolLengthM = num(E.POOL_LENGTH),
            swolf = num(E.SWOLF_AVG) ?: num(E.SWOLF_INDEX),
            strokes = num(E.STROKES)?.toInt(),
            predictions = listOf(
                "5 km" to E.PROJECTED_TIME_5KM, "10 km" to E.PROJECTED_TIME_10KM,
                "Semi-marathon" to E.PROJECTED_TIME_HALF_MARATHON, "Marathon" to E.PROJECTED_TIME_MARATHON,
            ).mapNotNull { (label, key) -> num(key)?.let { label to it } },
            zoneBounds = bounds,
            points = points,
            laps = laps,
        )
    }

    /** Distance (m) et kilocalories d'une séance, pour les listes. */
    fun summaryNumbers(summaryJson: String?): Pair<Double?, Double?> = runCatching {
        val d = ActivitySummaryData.fromJson(summaryJson) ?: return@runCatching null to null
        d.getNumber(E.DISTANCE_METERS, null)?.toDouble()?.takeIf { it > 0 } to
            (d.getNumber(E.CALORIES_BURNT, null) ?: d.getNumber(E.CALORIES_TOTAL, null))?.toDouble()?.takeIf { it > 0 }
    }.getOrDefault(null to null)

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2).let { it * it } + cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2).let { it * it }
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
