package app.superhealth.training.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Cibles qui suivent la pratique : on observe les RIR et répétitions réellement faits sur un exercice du programme et on
 * propose, sans jamais l'appliquer seul, une cible qui leur ressemble.
 *
 * - RIR : RIR noté, dans l'échelle ressentie de la personne (le biais appris par [StrengthModel] ne sert qu'à estimer la
 *   force : le corriger ici ferait viser plus dur que souhaité) ; des partielles ou une série « échec » valent RIR 0.
 * - Répétitions : plage décalée quand la majorité des séries tombe au-dessus ou au-dessous.
 * - Partielles : si la dernière série en reçoit souvent, proposer de la viser à l'échec.
 * Au moins [MIN_SESSIONS] séances récentes et une tendance nette (70 % des séries du même côté) sont nécessaires.
 */
object TargetTuning {
    const val MIN_SESSIONS = 4
    private const val RECENT_SESSIONS = 8
    private const val SIDE = 0.7

    enum class Kind { RIR, REPS, LAST_SET_FAILURE }

    data class Tip(
        val exerciseId: String, val kind: Kind,
        val fromRir: Int, val toRir: Int,
        val fromMin: Int, val fromMax: Int, val toMin: Int, val toMax: Int,
        val sessions: Int,
    ) {
        /** Clé de refus : la même proposition n'est plus faite, une autre valeur peut l'être. */
        val key get() = "$exerciseId:$kind:" + when (kind) { Kind.RIR -> "$toRir"; Kind.REPS -> "$toMin-$toMax"; Kind.LAST_SET_FAILURE -> "0" }
    }

    private data class Observed(val sets: List<SetLog>, val lastSets: List<SetLog>, val sessions: Int)

    private fun observed(exerciseId: String, history: List<Workout>): Observed? {
        val recent = history.asSequence().distinctBy { it.id }.sortedByDescending { it.startTime }
            .mapNotNull { w -> w.exercises.filter { it.exerciseId == exerciseId }.flatMap { Analytics.workingSets(it) }
                .filter { (it.reps ?: 0) > 0 }.takeIf { it.isNotEmpty() } }
            .take(RECENT_SESSIONS).toList()
        if (recent.size < MIN_SESSIONS) return null
        return Observed(recent.flatten(), recent.map { it.last() }, recent.size)
    }

    private fun notedRir(set: SetLog): Double? = when {
        set.partialReps > 0 || set.type == SetType.FAILURE -> 0.0
        else -> set.rir?.toDouble()
    }

    /** Propositions pour le programme [program] au cycle courant, la plus nette d'abord. */
    fun tips(program: Program, history: List<Workout>, dismissed: Set<String> = emptySet()): List<Tip> {
        val cycle = program.cycleIndex
        val out = mutableListOf<Pair<Double, Tip>>()
        val seen = mutableSetOf<String>()
        for (pe in program.days.flatMap { it.exercises }) {
            if (!seen.add(pe.exerciseId)) continue
            val targets = Generator.targetsFor(pe, cycle).filter { it.type != SetType.WARMUP }
            val target = targets.firstOrNull() ?: continue
            val obs = observed(pe.exerciseId, history) ?: continue

            val rirs = obs.sets.mapNotNull(::notedRir)
            if (rirs.size >= 10) {
                val med = rirs.sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }
                val to = med.roundToInt().coerceIn(0, 4)
                val side = if (to < target.rir) rirs.count { it < target.rir - 0.5 } else rirs.count { it > target.rir + 0.5 }
                if (abs(med - target.rir) >= 1 && to != target.rir && side >= SIDE * rirs.size)
                    out += abs(med - target.rir) to Tip(pe.exerciseId, Kind.RIR, target.rir, to, target.minReps, target.maxReps, target.minReps, target.maxReps, obs.sessions)
            }

            val reps = obs.sets.mapNotNull { it.reps }
            val above = reps.count { it > target.maxReps }; val below = reps.count { it < target.minReps }
            if (reps.size >= 10 && (above >= SIDE * reps.size || below >= SIDE * reps.size)) {
                val med = reps.sorted()[reps.size / 2]
                val width = target.maxReps - target.minReps
                val toMin = (med - width / 2).coerceIn(1, 30 - width)
                val toMax = toMin + width
                if (toMin != target.minReps)
                    out += (abs(toMin - target.minReps) / 2.0) to Tip(pe.exerciseId, Kind.REPS, target.rir, target.rir, target.minReps, target.maxReps, toMin, toMax, obs.sessions)
            }

            val last = targets.last()
            val withPartials = obs.lastSets.count { it.partialReps > 0 || it.type == SetType.FAILURE }
            if (last.rir > 0 && withPartials >= 0.5 * obs.lastSets.size)
                out += 0.5 to Tip(pe.exerciseId, Kind.LAST_SET_FAILURE, last.rir, 0, last.minReps, last.maxReps, last.minReps, last.maxReps, obs.sessions)
        }
        return out.filter { it.second.key !in dismissed }.sortedByDescending { it.first }.map { it.second }
    }

    /** Applique [tip] à tous les cycles de l'exercice : décalage relatif, la périodisation choisie est conservée. */
    fun apply(program: Program, tip: Tip): Program = program.copy(days = program.days.map { day ->
        day.copy(exercises = day.exercises.map { pe ->
            if (pe.exerciseId != tip.exerciseId) pe else pe.copy(cycles = pe.cycles.map { sets ->
                when (tip.kind) {
                    Kind.RIR -> sets.map { if (it.type == SetType.WARMUP) it else it.copy(rir = (it.rir + tip.toRir - tip.fromRir).coerceIn(0, 5)) }
                    Kind.REPS -> sets.map {
                        if (it.type == SetType.WARMUP) it else {
                            val d = tip.toMin - tip.fromMin
                            val min = (it.minReps + d).coerceIn(1, 30)
                            it.copy(minReps = min, maxReps = (it.maxReps + d).coerceIn(min, 40))
                        }
                    }
                    Kind.LAST_SET_FAILURE -> {
                        val i = sets.indexOfLast { it.type != SetType.WARMUP }
                        if (i < 0) sets else sets.toMutableList().also { it[i] = it[i].copy(rir = 0) }
                    }
                }
            })
        })
    })
}
