package app.superhealth.league

import app.superhealth.bridge.Bridge
import app.superhealth.training.domain.Analytics
import app.superhealth.training.domain.ExerciseDb
import app.superhealth.training.domain.Workout
import kotlin.math.roundToInt

/** Indice d'habitudes SuperHealth v4, sur sept jours terminés. Pas une estimation médicale. */
object HealthIndex {
    const val MODEL = 4
    const val MIN_DOMAINS = 3
    val weights = listOf(20, 20, 15, 25, 15, 5)

    fun historyKey(day: Long, coverage: Int, model: Int = MODEL) = "v$model:$day:$coverage"

    fun mean28(history: Map<String, Int>, end: Long, coverage: Int): Double? =
        ((end - 27)..end).mapNotNull { history[historyKey(it, coverage)] }
            .takeIf { it.isNotEmpty() }?.average()

    /** Gain relatif en points sur 100, borné avant toute agrégation entre disciplines. */
    fun gain(current: Double?, previous: Double?): Double {
        if (current == null || previous == null || !current.isFinite() || !previous.isFinite() || previous <= 0) return 0.0
        return ((current - previous) / previous * 100).coerceIn(0.0, 10.0)
    }

    fun strengthBests(sessions: List<Workout>, db: ExerciseDb): Map<String, Double> {
        val best = mutableMapOf<String, Double>()
        for (workout in sessions) for (entry in workout.exercises) {
            val exercise = db.exercise(entry.exerciseId) ?: continue
            // Sur un exercice assisté, plus de kg ne signifie pas plus fort.
            if (!exercise.usesReps || "aw" in exercise.metrics) continue
            val value = Analytics.bestPerformance(exercise, Analytics.workingSets(entry)) ?: continue
            best[exercise.id] = maxOf(value, best[exercise.id] ?: 0.0)
        }
        return best
    }

    /** Vitesse moyenne en m/s, sur la même durée écoulée que l'allure affichée. */
    fun runSpeed(run: Bridge.Activity): Double? = run.distanceM
        ?.takeIf { run.kind == Bridge.Kind.RUN && it.isFinite() && it >= 1000 && run.end > run.start }
        ?.let { it / ((run.end - run.start) / 1000.0) }

    fun runGain(runs: List<Bridge.Activity>, previous: List<Bridge.Activity>): Double = runs.maxOfOrNull { run ->
        val distance = run.distanceM ?: return@maxOfOrNull 0.0
        val reference = previous.mapNotNull { old ->
            val metres = old.distanceM ?: return@mapNotNull null
            if (metres in distance * 0.9..distance * 1.1) runSpeed(old) else null
        }.maxOrNull()
        gain(runSpeed(run), reference)
    } ?: 0.0

    fun sport(volume: Double, target: Double, gain: Double): Int =
        (90 * (volume / target).coerceIn(0.0, 1.0) + gain.coerceIn(0.0, 10.0)).roundToInt()

    fun total(parts: List<Int?>): Int? {
        if (parts.count { it != null } < MIN_DOMAINS) return null
        val used = parts.mapIndexedNotNull { i, p -> p?.let { it.coerceIn(0, 100) to weights[i] } }
        return used.sumOf { it.first * it.second * 10 } / used.sumOf { it.second }
    }

    /** Les jours absents ne deviennent pas des zéros. Au moins quatre observations pour une partie quotidienne. */
    fun daily(values: List<Double?>, target: Double): Int? {
        val observed = values.filterNotNull().filter { it.isFinite() && it >= 0 }
        if (observed.size < 4 || target <= 0) return null
        return (observed.sumOf { (it / target).coerceIn(0.0, 1.0) } / observed.size * 100).toInt()
    }
}
