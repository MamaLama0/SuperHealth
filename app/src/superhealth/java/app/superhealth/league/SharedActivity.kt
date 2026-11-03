package app.superhealth.league

import android.content.Context
import app.superhealth.bridge.Bridge
import app.superhealth.bridge.RunImport
import app.superhealth.health.data.WorkoutDetail
import app.superhealth.health.data.WorkoutDetails
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Snapshots created only when the user selects an activity to publish. Never export private notes or a health journal. */
object SharedActivity {
    @Serializable data class Set(val kg: Double? = null, val reps: Int? = null)
    @Serializable data class Exercise(val name: String, val muscles: List<String> = emptyList(), val sets: List<Set> = emptyList())
    @Serializable data class Strength(val started: Long, val seconds: Long, val volumeKg: Double, val setCount: Int,
        val repCount: Int, val exerciseCount: Int, val exercises: List<Exercise> = emptyList())
    @Serializable data class Split(val metres: Double, val seconds: Double)
    @Serializable data class Outdoor(val sport: String, val started: Long, val seconds: Double,
        val metres: Double? = null, val kcal: Double? = null, val ascent: Double? = null,
        val avgHr: Int? = null, val maxHr: Int? = null, val cadence: Double? = null,
        val route: String? = null, val speed: List<Double> = emptyList(), val elevation: List<Double> = emptyList(),
        val heart: List<Int> = emptyList(), val splits: List<Split> = emptyList()) {
        fun withoutDetails() = copy(kcal = null, avgHr = null, maxHr = null, cadence = null,
            speed = emptyList(), elevation = emptyList(), heart = emptyList(), splits = emptyList())
    }
    private fun number(v: Double?, max: Double) = v?.takeIf { it.isFinite() && it in 0.0..max }
    private fun positive(v: Double?, max: Double) = v == null || (v.isFinite() && v in 0.0..max)
    fun valid(s: Strength, now: Long) = s.started in 1..(now + 300000) && s.seconds in 0..604800 &&
        positive(s.volumeKg, 1e9) && s.setCount in 0..10000 && s.repCount in 0..1000000 && s.exerciseCount in 0..1000 &&
        s.exercises.size <= 12 && s.exercises.size <= s.exerciseCount && s.exercises.all { e ->
            e.name.length in 1..80 && e.muscles.size <= 8 && e.muscles.all { MuscleGroup.of(it) != null } &&
                e.sets.size <= 8 && e.sets.all { positive(it.kg, 10000.0) && (it.reps == null || it.reps in 0..10000) }
        }
    fun valid(a: Outdoor, now: Long) = a.sport in Bridge.Kind.entries.map { it.name } && a.started in 1..(now + 300000) &&
        a.seconds.isFinite() && a.seconds in 1.0..604800.0 && positive(a.metres, 2e7) && positive(a.kcal, 100000.0) &&
        positive(a.ascent, 100000.0) && positive(a.cadence, 500.0) && (a.avgHr == null || a.avgHr in 20..260) &&
        (a.maxHr == null || a.maxHr in 20..260) && a.speed.size <= 90 && a.speed.all { positive(it, 150.0) } &&
        a.elevation.size <= 90 && a.elevation.all { it.isFinite() && it in -1000.0..12000.0 } &&
        a.heart.size <= 90 && a.heart.all { it in 20..260 } && a.splits.size <= 20 &&
        a.splits.all { it.metres.isFinite() && it.metres in 1.0..2e7 && it.seconds.isFinite() && it.seconds in 1.0..604800.0 } &&
        (a.route == null || Route.decode(a.route)?.size?.let { it >= 2 } == true)

    fun strength(w: Workout, db: ExerciseDb?): Strength {
        val working = w.exercises.map { it to Analytics.workingSets(it) }.filter { it.second.isNotEmpty() }
        return Strength(w.startTime, w.durationSec.coerceIn(0, 604800), working.sumOf { Analytics.volume(it.second) },
            working.sumOf { it.second.size }, working.sumOf { it.second.sumOf { s -> s.reps ?: 0 } }, working.size,
            working.take(12).map { (entry, sets) ->
                val e = db?.exercise(entry.exerciseId)
                Exercise((e?.name ?: "Exercice").take(80), (e?.muscleGroups.orEmpty() + e?.secondaryMuscleGroups.orEmpty()).distinct().take(8).map { it.key },
                    sets.take(8).map { Set(it.weight?.takeIf { v -> v.isFinite() && v in 0.0..10000.0 }, it.reps?.coerceIn(0, 10000)) })
            })
    }
    fun fromDetail(d: WorkoutDetail, sport: String): Outdoor {
        val points = d.points
        return Outdoor(sport, d.start, d.activeSec.takeIf { it.isFinite() }?.coerceIn(1.0, 604800.0) ?: 1.0, number(d.distanceM, 2e7), number(d.kcal, 100000.0), number(d.ascentM, 100000.0),
            d.avgHr?.takeIf { it in 20..260 }, d.maxHr?.takeIf { it in 20..260 }, number(d.avgCadence, 500.0),
            Route.encode(points.mapNotNull { p -> if (p.lat != null && p.lon != null) p.lat to p.lon else null }),
            sample(points.mapNotNull { it.speed?.takeIf { n -> n.isFinite() && n in 0.0..150.0 } }, 90).map { round(it) },
            sample(points.mapNotNull { it.alt?.takeIf { n -> n.isFinite() && n in -1000.0..12000.0 } }, 90).map { round(it) },
            sample(points.mapNotNull { it.hr?.takeIf { n -> n in 20..260 } }, 90),
            d.laps.take(20).mapNotNull { l -> if (l.distanceM.isFinite() && l.distanceM in 1.0..2e7 && l.sec.isFinite() && l.sec in 1.0..604800.0) Split(round(l.distanceM), round(l.sec)) else null })
    }
    suspend fun draft(ctx: Context, a: Bridge.Activity): SocialPosts.Draft = withContext(Dispatchers.IO) {
        val detail = when {
            a.run != null -> RunImport.detail(a.run.id)
            a.watch != null -> WorkoutDetails.load(ctx, a.watch.id)
            else -> null
        }
        val snapshot = detail?.let { fromDetail(it, a.kind.name) } ?: Outdoor(a.kind.name, a.start,
            ((a.end - a.start) / 1000.0).coerceIn(1.0, 604800.0), a.distanceM, a.hc?.kcal,
            avgHr = a.hc?.avgHr, maxHr = a.hc?.maxHr)
        SocialPosts.activityDraft(a).copy(activity = snapshot.takeIf { valid(it, System.currentTimeMillis()) })
    }
    /** Older posts retain their exact recorded figures, but cannot acquire a missing GPS trace. */
    fun legacyActivity(p: SocialPosts.Post, now: Long = System.currentTimeMillis()): Outdoor? {
        if (p.activity != null || p.workout != null || p.kind !in setOf("RUN", "WORKOUT")) return null
        val parts = p.detail.split('·').map { it.trim() }
        if (parts.size != 2 || !parts[0].endsWith(" km") || !parts[1].endsWith(" min")) return null
        val km = parts[0].removeSuffix(" km").filterNot { it.isWhitespace() }.replace(',', '.').toDoubleOrNull() ?: return null
        val minutes = parts[1].removeSuffix(" min").toDoubleOrNull() ?: return null
        return Outdoor(if (p.kind == "RUN") "RUN" else "OTHER", p.created, minutes * 60, km * 1000).takeIf { valid(it, now) }
    }
    fun legacyStrength(p: SocialPosts.Post, now: Long = System.currentTimeMillis()): Strength? {
        if (p.activity != null || p.workout != null || p.kind != "WORKOUT") return null
        val lines = p.detail.lines()
        val minutes = lines.firstOrNull()?.removeSuffix(" min")?.toLongOrNull() ?: return null
        if (lines.size < 2) return null
        val exercises = ArrayList<Exercise>(); val allSets = ArrayList<Set>()
        for (line in lines.drop(1)) {
            val pair = line.split(" · ", limit = 2); if (pair.size != 2) return null
            val sets = ArrayList<Set>()
            for (description in pair[1].split(" / ")) {
                var kg: Double? = null; var reps: Int? = null
                for (value in description.split(" × ")) {
                    when {
                        value.endsWith(" kg") -> kg = value.removeSuffix(" kg").toDoubleOrNull() ?: return null
                        value.endsWith(" reps") -> reps = value.removeSuffix(" reps").toIntOrNull() ?: return null
                        else -> return null
                    }
                }
                sets += Set(kg, reps)
            }
            allSets += sets
            exercises += Exercise(pair[0].take(80), sets = sets.take(8))
        }
        val sets = allSets
        return Strength(p.created, minutes * 60, sets.sumOf { (it.kg ?: 0.0) * (it.reps ?: 0) }, sets.size,
            sets.sumOf { it.reps ?: 0 }, exercises.size, exercises.take(12)).takeIf { valid(it, now) }
    }
    private fun round(v: Double) = (v * 10).roundToInt() / 10.0
    internal fun <T> sample(values: List<T>, max: Int): List<T> = if (values.size <= max) values else
        (0 until max).map { values[(it.toLong() * (values.size - 1) / (max - 1)).toInt()] }

    /** Bounded, standard precision-5 polyline. Coordinates are sent only with the visible route-sharing option. */
    object Route {
        fun encode(raw: List<Pair<Double, Double>>): String? {
            val valid = raw.filter { (lat, lon) -> lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 }
            if (valid.size < 2) return null
            val points = sample(valid, 400)
            val out = StringBuilder(); var lat = 0; var lon = 0
            fun write(delta: Int) {
                var n = if (delta < 0) (delta shl 1).inv() else delta shl 1
                while (n >= 32) { out.append(((n and 31) or 32).plus(63).toChar()); n = n ushr 5 }
                out.append((n + 63).toChar())
            }
            for ((a, b) in points) { val x = (a * 1e5).roundToInt(); val y = (b * 1e5).roundToInt(); write(x - lat); write(y - lon); lat = x; lon = y }
            return out.toString().takeIf { it.length <= 6000 }
        }
        fun decode(raw: String): List<Pair<Double, Double>>? = runCatching {
            require(raw.length in 2..6000)
            var i = 0; var lat = 0L; var lon = 0L; val result = ArrayList<Pair<Double, Double>>()
            fun read(): Long {
                var value = 0L; var shift = 0
                while (true) {
                    require(i < raw.length && shift <= 30)
                    val n = raw[i++].code - 63; require(n in 0..63)
                    value = value or ((n and 31).toLong() shl shift)
                    if (n < 32) break
                    shift += 5
                }
                require(value <= 72000000)
                return if (value and 1L == 1L) (value shr 1).inv() else value shr 1
            }
            while (i < raw.length) {
                lat += read(); lon += read(); require(lat in -9000000L..9000000L && lon in -18000000L..18000000L && result.size < 400)
                result += lat / 1e5 to lon / 1e5
            }
            require(result.size >= 2); result
        }.getOrNull()
    }
}
