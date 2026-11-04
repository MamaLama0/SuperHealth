package app.superhealth.league

import app.superhealth.platform.*
import app.superhealth.bridge.Bridge
import app.superhealth.training.domain.*
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

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
    fun valid(a: Outdoor, now: Long) = a.sport in (Bridge.Kind.entries.map { it.name } + "HIKE") && a.started in 1..(now + 300000) &&
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
