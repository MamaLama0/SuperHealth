package app.superhealth.health.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Modules « performances » inspirés de Garmin Connect, calculés à partir des séances de la montre :
 * records personnels de course, récupération, volume hebdomadaire et charge, prédictions de course.
 */
object Performance {
    /** Distances des records (m), comme les « Records personnels » de Garmin Connect. */
    val RECORD_DISTANCES = listOf(1000.0 to "1 km", 5000.0 to "5 km", 10_000.0 to "10 km", 21_097.5 to "Semi-marathon", 42_195.0 to "Marathon")

    /** Résumé d'une séance, gardé en cache (relire un fichier .fit prend du temps). */
    @Serializable
    data class Entry(
        val id: Long, val start: Long, val end: Long, val run: Boolean, val distanceM: Double, val activeSec: Double,
        val aerobicTe: Double? = null, val anaerobicTe: Double? = null, val recoverySec: Double? = null, val avgHr: Int? = null,
        /** Meilleur temps (s) sur chaque distance de [RECORD_DISTANCES], quand la séance est assez longue. */
        val best: Map<String, Double> = emptyMap(),
        val predictions: Map<String, Double> = emptyMap(),
    )

    data class Record(val label: String, val sec: Double, val workoutId: Long, val date: Long)

    data class Week(val start: java.time.LocalDate, val runKm: Double, val minutes: Double, val sessions: Int)

    data class Snapshot(
        val entries: List<Entry>,
        val records: List<Record>,
        val longestRun: Entry?,
        val weeks: List<Week>,
        /** Charge des 7 derniers jours / moyenne hebdomadaire des 28 derniers (minutes × effet d'entraînement). */
        val acwr: Double?,
        val latest: Entry?,
        /** Heures de récupération restantes d'après la dernière séance. */
        val recoveryLeftH: Double?,
        val predictions: Map<String, Double>,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val ser = MapSerializer(Long.serializer(), Entry.serializer())
    private val lock = Mutex()
    private var memory: MutableMap<Long, Entry>? = null

    private fun file(ctx: Context) = File(ctx.filesDir, "performance-cache-v2.json")

    /** Lit les séances (montre), complète le cache avec les nouvelles et calcule les modules. */
    suspend fun snapshot(ctx: Context): Snapshot = withContext(Dispatchers.IO) {
        val workouts = runCatching { HealthRepository.workouts(0L, System.currentTimeMillis()) }.getOrDefault(emptyList())
        val cache = lock.withLock {
            val m = memory ?: runCatching { json.decodeFromString(ser, file(ctx).readText()).toMutableMap() }.getOrNull() ?: mutableMapOf()
            memory = m
            val missing = workouts.filter { it.id !in m }
            missing.forEach { w -> runCatching { WorkoutDetails.load(ctx, w.id) }.getOrNull()?.let { m[w.id] = entry(it) } }
            // Séances supprimées : retirées du cache.
            val ids = workouts.map { it.id }.toSet()
            m.keys.retainAll(ids)
            if (missing.isNotEmpty()) runCatching { file(ctx).writeText(json.encodeToString(ser, m)) }
            m.values.sortedByDescending { it.start }
        }
        // Séances d'autres apps (Garmin Connect, NOOP…) via Health Connect, quand la montre ne les a pas déjà.
        val hc = app.superhealth.SuperPrefs.hc.value.sessions.filter { s -> cache.none { it.start < s.end && s.start < it.end } }
            .mapNotNull { s -> app.superhealth.bridge.HcWorkouts.detail(app.superhealth.bridge.HcWorkouts.idOf(s))?.let(::entry) }
        compute((cache + hc).sortedByDescending { it.start })
    }

    private fun entry(w: WorkoutDetail): Entry {
        val run = w.usesPace && "WALK" !in w.kind.name && "HIK" !in w.kind.name
        val best = if (!run) emptyMap() else RECORD_DISTANCES.mapNotNull { (d, label) -> bestEffort(w.points, d)?.let { label to it } }.toMap()
        return Entry(
            id = w.id, start = w.start, end = w.end, run = run, distanceM = w.distanceM ?: 0.0, activeSec = w.activeSec,
            aerobicTe = w.aerobicTe, anaerobicTe = w.anaerobicTe, recoverySec = w.recoverySec, avgHr = w.avgHr,
            best = best, predictions = w.predictions.toMap(),
        )
    }

    /** Temps le plus court pour couvrir [meters] d'affilée (fenêtre glissante sur le tracé). */
    fun bestEffort(points: List<TrackPoint>, meters: Double): Double? {
        if (!meters.isFinite() || meters <= 0) return null
        var best = Double.POSITIVE_INFINITY
        var first = 0
        var atStart = 0
        var atEnd = 0
        // Pour une distance fixe, le minimum interpolé a au moins une borne
        // sur un échantillon. Tester les deux orientations en temps linéaire.
        for (i in points.indices) {
            val p = points[i]
            if (!p.t.isFinite() || !p.d.isFinite() || p.d < 0) {
                first = i + 1
                continue
            }
            if (i > first) {
                val prev = points[i - 1]
                val seconds = p.t - prev.t
                if (seconds <= 0 || p.d < prev.d || p.d - prev.d > 15 * seconds) first = i
            }
            if (i == first) {
                atStart = i
                atEnd = i
                continue
            }
            val startD = p.d - meters
            if (startD < points[first].d) continue
            while (atStart + 1 < i && points[atStart + 1].d <= startD) atStart++
            val a = points[atStart]
            val b = points[atStart + 1]
            val startT = a.t + (b.t - a.t) * (startD - a.d) / (b.d - a.d)
            best = minOf(best, p.t - startT)
            while (atEnd < i && points[atEnd].d + meters <= p.d) {
                val endD = points[atEnd].d + meters
                val prev = points[i - 1]
                if (p.d > prev.d) {
                    val endT = prev.t + (p.t - prev.t) * (endD - prev.d) / (p.d - prev.d)
                    best = minOf(best, endT - points[atEnd].t)
                }
                atEnd++
            }
        }
        return best.takeIf { it.isFinite() && it >= 1.0 }
    }

    private fun compute(entries: List<Entry>): Snapshot {
        val runs = entries.filter { it.run }
        val records = RECORD_DISTANCES.mapNotNull { (_, label) ->
            runs.mapNotNull { e -> e.best[label]?.let { e to it } }.minByOrNull { it.second }?.let { (e, s) -> Record(label, s, e.id, e.start) }
        }
        val zone = java.time.ZoneId.systemDefault()
        val monday = java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val weeks = (11 downTo 0).map { k ->
            val start = monday.minusWeeks(k.toLong())
            val from = start.atStartOfDay(zone).toInstant().toEpochMilli(); val to = start.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val inWeek = entries.filter { it.start in from until to }
            Week(start, inWeek.filter { it.run }.sumOf { it.distanceM } / 1000, inWeek.sumOf { it.activeSec } / 60, inWeek.size)
        }
        val now = System.currentTimeMillis()
        fun load(e: Entry) = e.activeSec / 60 * ((e.aerobicTe ?: 2.0) + (e.anaerobicTe ?: 0.0) / 2)
        val acute = entries.filter { now - it.start < 7 * 86_400_000L }.sumOf(::load)
        val chronic = entries.filter { now - it.start < 28 * 86_400_000L }.sumOf(::load) / 4
        val latest = entries.firstOrNull()
        val recoveryLeft = entries.filter { it.recoverySec != null }.maxOfOrNull { e -> e.recoverySec!! / 3600 - (now - e.end) / 3_600_000.0 }
            ?.takeIf { it > 0 }
        return Snapshot(
            entries = entries, records = records, longestRun = runs.maxByOrNull { it.distanceM }, weeks = weeks,
            acwr = if (chronic > 0) acute / chronic else null, latest = latest, recoveryLeftH = recoveryLeft,
            predictions = runs.firstOrNull { it.predictions.isNotEmpty() }?.predictions ?: emptyMap(),
        )
    }
}
