package app.superhealth.nutrition.domain

import app.superhealth.platform.*

import kotlin.math.abs

/**
 * Tendance de poids. V1 MacroFactor = EMA (α = 2/9 ou 0.1, observés).
 * Le rejet robuste (MAD × 1.4826) reprend la constante observée dans weight_trend_service ;
 * le seuil 3σ et la fenêtre de 14 résidus sont des choix de conception.
 */
object WeightTrend {

    data class Point(val epochDay: Long, val scale: Double?, val trend: Double)

    private const val MAD_TO_SIGMA = 1.4826
    private const val OUTLIER_SIGMAS = 3.0
    private const val RESIDUAL_WINDOW = 14

    /**
     * Calcule la tendance pour chaque jour de [fromDay, toDay] (inclus), à partir des pesées.
     * Les jours sans pesée conservent la tendance de la veille. Jours avant la première pesée : absents.
     */
    fun compute(weighIns: Map<Long, Double>, alpha: Double, fromDay: Long, toDay: Long): List<Point> {
        if (weighIns.isEmpty()) return emptyList()
        val first = weighIns.keys.min()
        var trend = weighIns.getValue(first)
        val residuals = ArrayDeque<Double>()
        val out = ArrayList<Point>()
        for (day in minOf(first, fromDay)..toDay) {
            if (day < first) continue
            val w = weighIns[day]
            if (w != null && day != first) {
                val r = w - trend
                val bounded = if (residuals.size >= 5) {
                    val mad = median(residuals.map { abs(it - median(residuals)) })
                    val limit = OUTLIER_SIGMAS * MAD_TO_SIGMA * mad
                    if (mad > 0 && abs(r) > limit) trend + limit * Math.signum(r) else w
                } else w
                trend += alpha * (bounded - trend)
                residuals.addLast(r)
                if (residuals.size > RESIDUAL_WINDOW) residuals.removeFirst()
            }
            if (day >= fromDay) out += Point(day, w, trend)
        }
        return out
    }

    private fun median(values: Collection<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }
}
