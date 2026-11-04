package app.superhealth.training.domain

/** Règles communes aux réglages persistants et à la séance en cours. */
object SessionWarmUps {
    fun mode(settings: Settings, config: ExerciseConfig?): WarmUpMode =
        if (settings.warmUp == WarmUpMode.NONE) WarmUpMode.NONE else config?.warmUp ?: settings.warmUp

    fun withoutPending(session: Session, exerciseId: String? = null): Session = session.copy(
        exercises = session.exercises.map { ex ->
            if (exerciseId == null || ex.exerciseId == exerciseId)
                ex.copy(sets = ex.sets.filter { it.done || it.type != SetType.WARMUP }) else ex
        })

    /** Un bloc se prépare avant de commencer l'exercice, jamais après une série validée. */
    fun canAdd(ex: SessionExercise) = ex.sets.none { it.done }

    fun replace(ex: SessionExercise, warm: List<SetLog>): SessionExercise =
        if (!canAdd(ex)) ex else ex.copy(sets = warm + ex.sets.filter { it.type != SetType.WARMUP })
}

object SessionFlow {
    fun nextFocus(s: Session, i: Int, completedSet: Int): Int? {
        val ex = s.exercises.getOrNull(i) ?: return null
        if (ex.sets.getOrNull(completedSet)?.type == SetType.WARMUP) return null
        fun pending(k: Int) = s.exercises[k].sets.any { !it.done }
        if (ex.supersetGroup != null) {
            val group = s.exercises.indices.filter { s.exercises[it].supersetGroup == ex.supersetGroup }
            val order = group.dropWhile { it != i }.drop(1) + group.takeWhile { it != i }
            order.firstOrNull { pending(it) }?.let { return it }
        }
        if (pending(i)) return null
        return ((i + 1 until s.exercises.size) + (0 until i)).firstOrNull { pending(it) }
    }
}
