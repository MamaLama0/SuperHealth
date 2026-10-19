package app.superhealth.training.domain

import java.util.UUID

/**
 * « Workout Changes » (PlanDiffSheet observé) : écarts entre la séance faite et le jour du programme dont elle vient —
 * exercices ajoutés, retirés, modifiés (nombre de séries de travail, superset) et ordre des exercices.
 * Chaque écart peut être annulé avant d'être reporté dans le programme (« Undo »).
 */
data class PlanDiff(
    val added: List<SessionExercise>,
    val removed: List<ProgramExercise>,
    /** Exercice du programme et son pendant dans la séance. */
    val modified: List<Pair<ProgramExercise, SessionExercise>>,
    val reordered: Boolean,
) {
    val isEmpty get() = added.isEmpty() && removed.isEmpty() && modified.isEmpty() && !reordered

    /** Choix de l'utilisateur : identifiants (séance pour les ajouts, programme pour le reste) des écarts à reporter. */
    data class Selection(val added: Set<String>, val removed: Set<String>, val modified: Set<String>, val order: Boolean)

    fun all() = Selection(added.map { it.id }.toSet(), removed.map { it.id }.toSet(), modified.map { it.first.id }.toSet(), reordered)

    companion object {
        private fun working(ex: SessionExercise) = ex.sets.filter { it.type != SetType.WARMUP }

        /** Appariement dans l'ordre : chaque exercice de séance prend le premier exercice du jour non apparié de même id. */
        private fun match(day: ProgramDay, s: Session): Map<String, ProgramExercise> {
            val free = day.exercises.toMutableList()
            val out = LinkedHashMap<String, ProgramExercise>()
            s.exercises.forEach { ex ->
                val i = free.indexOfFirst { it.exerciseId == ex.exerciseId }
                if (i >= 0) out[ex.id] = free.removeAt(i)
            }
            return out
        }

        fun compute(day: ProgramDay, s: Session): PlanDiff {
            val cycle = s.cycleIndex ?: 0
            val m = match(day, s)
            val matched = m.values.map { it.id }.toSet()
            val added = s.exercises.filter { it.id !in m }
            val removed = day.exercises.filter { it.id !in matched }
            val modified = s.exercises.mapNotNull { ex ->
                val pe = m[ex.id] ?: return@mapNotNull null
                val sameSets = working(ex).size == Generator.targetsFor(pe, cycle).size
                val sameSuperset = (pe.supersetGroup == null) == (ex.supersetGroup == null)
                if (sameSets && sameSuperset) null else pe to ex
            }
            val sessionOrder = s.exercises.mapNotNull { m[it.id]?.id }
            val dayOrder = day.exercises.map { it.id }.filter { it in matched }
            return PlanDiff(added, removed, modified, sessionOrder != dayOrder)
        }

        /** Cibles d'un exercice ajouté : celles de la séance, sinon la plage autour des répétitions faites. */
        private fun targetsOf(ex: SessionExercise): List<TargetSet> = working(ex).map { set ->
            set.target ?: (set.reps ?: 10).let { r -> TargetSet(set.type, (r - 2).coerceAtLeast(1), r + 2, set.rir ?: 2, ex.restSec) }
        }.ifEmpty { listOf(TargetSet(restSec = ex.restSec)) }

        /** Nouvelles cibles : même nombre de séries que la séance, dans chaque cycle. */
        private fun resized(pe: ProgramExercise, n: Int) = pe.copy(cycles = pe.cycles.map { c ->
            if (n <= c.size) c.take(n) else c + List(n - c.size) { c.lastOrNull() ?: TargetSet() }
        })

        fun apply(day: ProgramDay, s: Session, diff: PlanDiff, sel: Selection): ProgramDay {
            val m = match(day, s)
            fun updated(ex: SessionExercise, pe: ProgramExercise): ProgramExercise =
                if (pe.id in sel.modified && diff.modified.any { it.first.id == pe.id })
                    resized(pe, working(ex).size.coerceAtLeast(1)).copy(supersetGroup = ex.supersetGroup)
                else pe
            fun fresh(ex: SessionExercise) = ProgramExercise(UUID.randomUUID().toString(), ex.exerciseId, listOf(targetsOf(ex)), ex.supersetGroup)

            val kept = day.exercises.filter { it.id !in sel.removed || diff.removed.none { r -> r.id == it.id } }
            val bySession = m.entries.associate { (sid, pe) -> pe.id to s.exercises.first { it.id == sid } }
            val out = if (sel.order && diff.reordered) {
                // ordre de la séance ; un exercice retiré mais conservé reprend sa place d'origine
                val list = s.exercises.mapNotNull { ex ->
                    m[ex.id]?.let { updated(ex, it) } ?: if (ex.id in sel.added) fresh(ex) else null
                }.toMutableList()
                day.exercises.forEachIndexed { i, pe -> if (pe in kept && pe.id !in bySession) list.add(i.coerceAtMost(list.size), pe) }
                list
            } else {
                kept.map { pe -> bySession[pe.id]?.let { updated(it, pe) } ?: pe } +
                    s.exercises.filter { it.id !in m && it.id in sel.added }.map(::fresh)
            }
            // un superset réduit à un seul exercice n'en est plus un
            val counts = out.groupingBy { it.supersetGroup }.eachCount()
            return day.copy(exercises = out.map { if (it.supersetGroup != null && counts[it.supersetGroup] == 1) it.copy(supersetGroup = null) else it })
        }
    }
}
