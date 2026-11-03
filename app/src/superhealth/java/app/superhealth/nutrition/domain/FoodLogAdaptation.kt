package app.superhealth.nutrition.domain

import kotlin.math.abs
import kotlin.math.sqrt

/** Individual, bounded corrections. Each validation block follows the training observations in time. */
object FoodLogAdaptation {
    data class Observation(val day: Long, val predictor: Double, val residual: Double)

    fun weightShifts(points: List<WeightTrendV2.Point>, carbsByDay: Map<Long, Double>): Map<Long, Double> {
        val past = ArrayDeque<Observation>()
        val shifts = mutableMapOf<Long, Double>()
        for (i in 1 until points.size) {
            val point = points[i]
            val carbs = carbsByDay[point.epochDay - 1] ?: continue
            while (past.isNotEmpty() && past.first().day < point.epochDay - 90) past.removeFirst()
            val limit = (point.predictedTween * 0.008).coerceIn(0.25, 0.8)
            if (past.size >= 42) {
                val history = past.toList()
                val fit = history.dropLast(14)
                val mean = fit.map { it.predictor }.average()
                val variance = fit.sumOf { (it.predictor - mean) * (it.predictor - mean) }
                val sd = sqrt(variance / fit.size)
                if (sd >= 15) {
                    val slope = fit.sumOf { (it.predictor - mean) * it.residual } / variance
                    fun shift(x: Double) = (0.75 * slope * (x - mean).coerceIn(-2 * sd, 2 * sd)).coerceIn(-limit, limit)
                    val repeats = history.takeLast(14).chunked(7).all { block ->
                        block.sumOf { abs(it.residual - shift(it.predictor)) } < 0.95 * block.sumOf { abs(it.residual) }
                    }
                    if (repeats) shifts[point.epochDay] = shift(carbs)
                }
            }
            point.scale?.let { kg ->
                past.addLast(Observation(point.epochDay, carbs, (kg - point.predictedTween).coerceIn(-2.0, 2.0)))
            }
        }
        return shifts
    }

    /** Shares are learnt per complete day, so a single very large meal cannot dominate the history. */
    fun mealShares(defaults: List<Double>, daily: List<List<Double>>): List<Double> {
        val complete = daily.filter { row -> row.size == defaults.size && row.all { it.isFinite() && it >= 0 } && row.count { it > 0 } >= 2 }
        if (complete.size < 7) return defaults
        val mass = defaults.sum()
        if (mass <= 0) return defaults
        val proportions = complete.map { row -> val total = row.sum(); row.map { it / total } }
        val learned = (complete.size / (complete.size + 14.0)).coerceAtMost(0.6)
        return defaults.indices.map { slot ->
            defaults[slot] * (1 - learned) + learned * mass * proportions.map { it[slot] }.average()
        }
    }
}
