package app.superhealth.training.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Statistiques : volume par muscle, records, historique d'un exercice (tableau de bord et « Niveaux »). */
object Analytics {
    /** Séries par muscle : 1 pour un groupe principal, 0,5 pour un groupe secondaire [INFÉRÉ]. */
    const val SECONDARY_WEIGHT = 0.5
    /** Repères de séries hebdomadaires par muscle (constantes 8 et 16 de levels_view) [INFÉRÉ]. */
    const val SETS_LOW = 8.0
    const val SETS_HIGH = 16.0

    fun workingSets(ex: SessionExercise) = ex.sets.filter { it.done && it.type != SetType.WARMUP }

    fun setsByMuscle(workouts: List<Workout>, db: ExerciseDb): Map<MuscleGroup, Double> {
        val out = mutableMapOf<MuscleGroup, Double>()
        for (w in workouts) for (ex in w.exercises) {
            val e = db.exercise(ex.exerciseId) ?: continue
            val n = workingSets(ex).size.toDouble()
            if (n == 0.0) continue
            e.muscleGroups.forEach { out[it] = (out[it] ?: 0.0) + n }
            e.secondaryMuscleGroups.forEach { if (it !in e.muscleGroups) out[it] = (out[it] ?: 0.0) + n * SECONDARY_WEIGHT }
        }
        return out
    }

    fun volume(sets: List<SetLog>) = sets.filter { it.done && it.type != SetType.WARMUP }.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }

    fun day(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Semaine (lundi) d'une date. */
    fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

    data class Records(val bestE1rm: Double?, val heaviest: Double?, val bestSetVolume: Double?, val mostReps: Int?)

    data class ExercisePoint(val time: Long, val e1rm: Double, val volume: Double, val heaviest: Double, val reps: Int, val sets: Int)

    fun history(exerciseId: String, workouts: List<Workout>, db: ExerciseDb): List<ExercisePoint> {
        val upper = db.exercise(exerciseId)?.upperBody ?: true
        return workouts.sortedBy { it.startTime }.mapNotNull { w ->
            val sets = w.exercises.filter { it.exerciseId == exerciseId }.flatMap { workingSets(it) }
            if (sets.isEmpty()) null else ExercisePoint(
                w.startTime,
                sets.filter { (it.weight ?: 0.0) > 0 && (it.reps ?: 0) > 0 }.maxOfOrNull { Strength.e1rm(it.weight!!, it.reps!!, it.rir, upper) } ?: 0.0,
                volume(sets), sets.maxOf { it.weight ?: 0.0 }, sets.maxOf { it.reps ?: 0 }, sets.size,
            )
        }
    }

    fun records(points: List<ExercisePoint>) = Records(
        points.maxOfOrNull { it.e1rm }?.takeIf { it > 0 }, points.maxOfOrNull { it.heaviest }?.takeIf { it > 0 },
        null, points.maxOfOrNull { it.reps },
    )

    /** Records battus par une séance par rapport à l'historique antérieur (volume et 1RM, WorkoutRecordType). */
    data class NewRecord(val exerciseId: String, val kind: String, val value: Double)

    fun newRecords(w: Workout, before: List<Workout>, db: ExerciseDb): List<NewRecord> {
        val out = mutableListOf<NewRecord>()
        for (ex in w.exercises) {
            val e = db.exercise(ex.exerciseId) ?: continue
            val sets = workingSets(ex).filter { (it.reps ?: 0) > 0 }
            if (sets.isEmpty()) continue
            val prev = before.flatMap { b -> b.exercises.filter { it.exerciseId == ex.exerciseId }.flatMap { workingSets(it) } }
            if (prev.isEmpty()) continue
            val vol = volume(ex.sets)
            val prevVol = before.maxOfOrNull { b -> b.exercises.filter { it.exerciseId == ex.exerciseId }.sumOf { volume(it.sets) } } ?: 0.0
            if (vol > prevVol && vol > 0) out += NewRecord(e.id, "Volume", vol)
            val best = sets.filter { (it.weight ?: 0.0) > 0 }.maxOfOrNull { Strength.e1rm(it.weight!!, it.reps!!, it.rir, e.upperBody) } ?: 0.0
            val prevBest = prev.filter { (it.weight ?: 0.0) > 0 && (it.reps ?: 0) > 0 }.maxOfOrNull { Strength.e1rm(it.weight!!, it.reps!!, it.rir, e.upperBody) } ?: 0.0
            if (best > prevBest && best > 0) out += NewRecord(e.id, "1RM estimé", best)
            val reps = sets.maxOf { it.reps!! }; val prevReps = prev.maxOf { it.reps ?: 0 }
            if (e.usesReps && !e.usesWeight && reps > prevReps) out += NewRecord(e.id, "Répétitions", reps.toDouble())
        }
        return out
    }
}
