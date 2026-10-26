package app.superhealth.health.data

import app.superhealth.platform.*

import app.superhealth.platform.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.superhealth.platform.LoggerFactory
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
        if (id < 0) return@withContext app.superhealth.bridge.RunImport.detail(id) // séance importée (GPX, TCX, FIT)
        runCatching { HealthSources.current.workoutDetail(id) }.onFailure { LOG.warn("Séance $id illisible", it) }.getOrNull()
    }

    /** Distance cumulée d'un tracé GPS (points dans l'ordre), pour les sources qui ne la donnent pas. */
    fun withDistances(points: List<TrackPoint>): List<TrackPoint> {
        var cum = 0.0
        var prev: Pair<Double, Double>? = null
        return points.map { p ->
            if (p.d > 0) { cum = p.d; p } else {
                if (p.lat != null && p.lon != null) {
                    prev?.let { (a, b) -> cum += haversine(a, b, p.lat, p.lon) }
                    prev = p.lat to p.lon
                }
                p.copy(d = cum)
            }
        }
    }

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180
        val a = sin(dLat / 2).let { it * it } + cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2).let { it * it }
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
