package app.superhealth.nutrition.domain

import kotlin.math.abs

/** Optional weekly correction of V3 using observed non-step exercise. All inputs precede the check-in. */
object ActivityEnergy {
    data class Week(val start: Long, val minutes: Double, val steps: Double, val residualKcal: Double)

    fun correction(history: List<Week>, minutes: Double, steps: Double, limit: Double): Double {
        if (history.size < 16) return 0.0
        val fit = history.dropLast(8)
        val stepMean = fit.map { it.steps }.average()
        val sportMean = fit.map { it.minutes }.average()
        val stepVariance = fit.sumOf { (it.steps - stepMean) * (it.steps - stepMean) }
        val stepSlope = if (stepVariance > 0) fit.sumOf { (it.steps - stepMean) * (it.minutes - sportMean) } / stepVariance else 0.0
        fun sport(m: Double, s: Double) = m - sportMean - stepSlope * (s - stepMean)
        val variance = fit.sumOf { sport(it.minutes, it.steps).let { x -> x * x } }
        if (variance < fit.size * 30.0 * 30.0) return 0.0
        val slope = fit.sumOf { sport(it.minutes, it.steps) * it.residualKcal } / variance
        if (slope <= 0 || !slope.isFinite()) return 0.0
        fun predict(m: Double, s: Double) = (0.5 * slope * sport(m, s)).coerceIn(-limit, limit)
        if (!history.takeLast(8).chunked(4).all { block ->
            block.sumOf { abs(it.residualKcal - predict(it.minutes, it.steps)) } < 0.95 * block.sumOf { abs(it.residualKcal) }
        }) return 0.0
        return predict(minutes, steps)
    }

    fun weeklyAdjustments(days: List<DayData>, levels: Map<Long, Double>, expenditure: Map<Long, Double>,
                          minutes: Map<Long, Double>): Map<Long, Double> {
        if (minutes.isEmpty() || days.size < 17 * 7) return emptyMap()
        val byDay = days.associateBy { it.epochDay }
        // A week without any recorded session is ambiguous. Only observed training weeks inform the relation.
        fun exposure(day: Long): Pair<Double, Double>? {
            val previous = (day-7 until day).mapNotNull(byDay::get)
            val steps = previous.mapNotNull { it.steps?.takeIf { v -> v.isFinite() && v >= 0 } }
            val training = previous.mapNotNull { minutes[it.epochDay] }
            if (steps.size < 6 || training.isEmpty()) return null
            return training.sum() to steps.average()
        }
        val history = ArrayDeque<Week>()
        val out = mutableMapOf<Long, Double>()
        for (d in days) {
            val start = d.epochDay
            if (Habits.weekStart(start) != start) continue
            val previous = start - 7
            val prior = exposure(previous)
            val observed = (previous until start).mapNotNull(byDay::get)
            val intake = observed.mapNotNull { Expenditure.usableIntake(it) }
            val base = expenditure[previous]
            val before = levels[previous - 1]; val after = levels[start - 1]
            if (prior != null && intake.size == 7 && observed.count { it.weightKg != null } >= 4 &&
                before != null && after != null && base != null) {
                val residual = intake.average() - 7700 * (after - before) / 7 - base
                history.addLast(Week(previous, prior.first, prior.second, residual.coerceIn(-500.0, 500.0)))
            }
            while (history.isNotEmpty() && history.first().start < start - 364) history.removeFirst()
            val exp = exposure(start) ?: continue
            val tdee = expenditure[start] ?: continue
            out[start] = correction(history.toList(), exp.first, exp.second, minOf(100.0, 0.05 * tdee))
        }
        return out
    }
}
