package app.superhealth.training.domain

/**
 * État des muscles pour la carte « Niveaux » : fatigue qui reste des séances récentes et temps depuis le dernier travail.
 * Modèle simple et lisible : chaque série de travail (½ pour un muscle secondaire) laisse une fatigue qui décroît
 * linéairement jusqu'à zéro en 48 h (petits muscles) ou 72 h (grands muscles) ; 6 séries récentes = muscle encore chargé.
 */
object MuscleStatus {
    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR
    /** Séries « fraîches » au-delà desquelles un muscle est considéré entièrement chargé. */
    const val FULL_LOAD_SETS = 6.0

    private val LARGE = setOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.LOWER_BACK, MuscleGroup.LATS,
        MuscleGroup.CHEST, MuscleGroup.UPPER_BACK, MuscleGroup.ADDUCTORS)

    fun recoveryHours(g: MuscleGroup): Int = if (g in LARGE) 72 else 48

    data class Recovery(val load: Double, val readyAt: Long?) {
        /** 0 = reposé, 1 = encore chargé. */
        val fatigue get() = (load / FULL_LOAD_SETS).coerceIn(0.0, 1.0)
    }

    private inline fun forEachMuscleSets(history: List<Workout>, db: ExerciseDb, f: (Workout, MuscleGroup, Double) -> Unit) {
        for (w in history) for (ex in w.exercises) {
            val e = db.exercise(ex.exerciseId) ?: continue
            val n = Analytics.workingSets(ex).size.toDouble()
            if (n == 0.0) continue
            e.muscleGroups.forEach { f(w, it, n) }
            e.secondaryMuscleGroups.forEach { if (it !in e.muscleGroups) f(w, it, n * Analytics.SECONDARY_WEIGHT) }
        }
    }

    /** Fatigue restante par muscle à [now] (muscles reposés absents). */
    fun recovery(history: List<Workout>, db: ExerciseDb, now: Long = System.currentTimeMillis()): Map<MuscleGroup, Recovery> {
        val load = mutableMapOf<MuscleGroup, Double>()
        val ready = mutableMapOf<MuscleGroup, Long>()
        forEachMuscleSets(history.filter { it.startTime in (now - 3 * DAY)..now }, db) { w, g, sets ->
            val window = recoveryHours(g) * HOUR
            val end = w.startTime + w.durationSec * 1000 + window
            val left = (end - now).toDouble() / window
            if (left > 0) {
                load[g] = (load[g] ?: 0.0) + sets * left
                ready[g] = maxOf(ready[g] ?: 0L, end)
            }
        }
        return load.mapValues { (g, l) -> Recovery(l, ready[g]) }
    }

    /** Jours depuis la dernière série de travail de chaque muscle (null : jamais dans l'historique). */
    fun daysSinceTrained(history: List<Workout>, db: ExerciseDb, now: Long = System.currentTimeMillis()): Map<MuscleGroup, Long?> {
        val last = mutableMapOf<MuscleGroup, Long>()
        forEachMuscleSets(history.filter { it.startTime <= now }, db) { w, g, _ -> last[g] = maxOf(last[g] ?: 0L, w.startTime) }
        return MuscleGroup.entries.associateWith { g -> last[g]?.let { (now - it) / DAY } }
    }
}
