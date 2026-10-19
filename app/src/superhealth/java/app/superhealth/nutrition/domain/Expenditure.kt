package app.superhealth.nutrition.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Dépense énergétique (TDEE) par bilan énergétique — reconstruction du modèle V1 (notes/09 §3.3, notes/11 §3).
 *
 * Observé dans libapp.so : densités 7700 (perte) / 4400 (gain) kcal/kg ; fenêtre réduite tant que
 * la couverture < 85 %, minimum 7 jours ; plancher max(598, 48.1·poids^0.783) ; plafond 50 000 ;
 * « hold » sans données ; 1re mise à jour au jour 3 ; jeûne = 0 kcal ; jours partiels exclus.
 * Choix de conception : fenêtre max 28 j, lissage α = 0.1/jour (pondérations V2/V3 non décodées),
 * ancrage sur l'estimation initiale pendant 14 jours, hold si < 4 jours journalisés ou aucune pesée sur 7 j.
 */
object Expenditure {

    const val KCAL_PER_KG_LOSS = 7700.0
    const val KCAL_PER_KG_GAIN = 4400.0
    const val MIN_COVERAGE = 0.85
    const val MIN_WINDOW = 7
    const val MAX_WINDOW = 28
    const val SMOOTHING = 0.1
    const val ANCHOR_DAYS = 14
    const val FIRST_UPDATE_DAY = 2 // index 0-based → 3e jour
    const val MAX_TDEE = 50_000.0

    enum class Status { INITIAL, UPDATING, HOLDING }

    data class Point(val epochDay: Long, val tdee: Double, val status: Status, val raw: Double?)

    fun storedEnergy(deltaTrendKg: Double) = when {
        deltaTrendKg < 0 -> deltaTrendKg * KCAL_PER_KG_LOSS
        deltaTrendKg > 0 -> deltaTrendKg * KCAL_PER_KG_GAIN
        else -> 0.0
    }

    fun floor(weightKg: Double) = max(598.0, 48.1 * weightKg.pow(0.783))

    /** Apport retenu pour le calcul : jeûne = 0, partiel = exclu, non journalisé = exclu. */
    fun usableIntake(d: DayData): Double? = when {
        d.partial -> null
        d.fasted -> d.intakeKcal ?: 0.0
        else -> d.intakeKcal
    }

    /**
     * @param days jours consécutifs triés (sans trous), du début du programme à aujourd'hui.
     * @param trend tendance par epochDay.
     * @param initialTdee estimation initiale (BMR × activité ou saisie).
     */
    fun compute(days: List<DayData>, trend: Map<Long, Double>, initialTdee: Double): List<Point> {
        val out = ArrayList<Point>(days.size)
        var current = initialTdee
        for (i in days.indices) {
            val day = days[i]
            if (i < FIRST_UPDATE_DAY) {
                out += Point(day.epochDay, current, Status.INITIAL, null)
                continue
            }
            val last7 = days.subList(max(0, i - 6), i + 1)
            val logged7 = last7.count { usableIntake(it) != null }
            val weighed7 = last7.any { it.weightKg != null }
            val enough = if (i < MIN_WINDOW) logged7 >= min(3, i + 1) && days.subList(0, i + 1).any { it.weightKg != null }
                         else logged7 >= 4 && weighed7
            val raw = if (enough) rawEstimate(days, i, trend) else null
            if (raw == null) {
                out += Point(day.epochDay, current, Status.HOLDING, null)
                continue
            }
            // Ancrage : au début, la confiance dans les données est faible.
            val confidence = min(1.0, (i + 1).toDouble() / ANCHOR_DAYS)
            val target = confidence * raw + (1 - confidence) * initialTdee
            val k = max(SMOOTHING, 1.0 / (i + 1))
            current += k * (target - current)
            val w = trend[day.epochDay]
            if (w != null) current = current.coerceIn(floor(w), MAX_TDEE)
            out += Point(day.epochDay, current, Status.UPDATING, raw)
        }
        return out
    }

    /** Estimation brute sur la plus grande fenêtre ≤ MAX_WINDOW dont la couverture ≥ 85 %. */
    fun rawEstimate(days: List<DayData>, endIndex: Int, trend: Map<Long, Double>): Double? {
        var window = min(MAX_WINDOW, endIndex + 1)
        val minWindow = min(MIN_WINDOW, endIndex + 1)
        while (window >= minWindow) {
            val slice = days.subList(endIndex - window + 1, endIndex + 1)
            val intakes = slice.mapNotNull { usableIntake(it) }
            val coverage = intakes.size.toDouble() / window
            if (coverage >= MIN_COVERAGE || window == minWindow) {
                if (intakes.isEmpty()) return null
                val startDay = slice.first().epochDay - 1
                val endTrend = trend[slice.last().epochDay] ?: return null
                val startTrend = trend[startDay] ?: trend[slice.first().epochDay] ?: return null
                val span = if (trend[startDay] != null) window else window - 1
                if (span <= 0) return null
                val stored = storedEnergy(endTrend - startTrend)
                return intakes.average() - stored / span
            }
            window--
        }
        return null
    }
}
