package app.superhealth.health.data

import app.superhealth.platform.*

import app.superhealth.platform.Context
import app.superhealth.platform.File
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

    data class Week(val start: LocalDate, val runKm: Double, val minutes: Double, val sessions: Int)

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

    private fun file(ctx: Context) = File(ctx.filesDir, "performance-cache.json")

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
        compute(cache)
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
        val p = points.filter { it.d > 0 }
        if (p.isEmpty() || p.last().d - p.first().d < meters) return null
        var best = Double.MAX_VALUE
        var j = 0
        for (i in p.indices) {
            while (j < p.size && p[j].d - p[i].d < meters) j++
            if (j >= p.size) break
            // Interpolation au mètre près entre les deux points qui encadrent la distance visée.
            val a = p[j - 1]; val b = p[j]
            val f = if (b.d > a.d) (p[i].d + meters - a.d) / (b.d - a.d) else 0.0
            val t = a.t + (b.t - a.t) * f - p[i].t
            if (t in 1.0..best) best = t
        }
        return best.takeIf { it < Double.MAX_VALUE }
    }

    private fun compute(entries: List<Entry>): Snapshot {
        val runs = entries.filter { it.run }
        val records = RECORD_DISTANCES.mapNotNull { (_, label) ->
            runs.mapNotNull { e -> e.best[label]?.let { e to it } }.minByOrNull { it.second }?.let { (e, s) -> Record(label, s, e.id, e.start) }
        }
        val zone = ZoneId.systemDefault()
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
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
