package app.superhealth.nutrition.domain

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Dépense énergétique V3 de MacroFactor (noyau 0x12c46ac, appelé 3 fois par 0x12c41cc).
 * Décodage complet : MacroFactor-analyse/notes/14 §3.
 */
object ExpenditureV3 {

    data class Params(
        val alpha: Double, val beta: Double, val cBalance: Double, val cCumul: Double, val cGap: Double,
        val kSlope: Double, val kSteps: Double,
    )

    val MAIN = Params(0.1625, 0.1, 0.5, 0.001, 0.02, 1.0, 0.025)
    val SHORT = Params(0.2149, 0.1176, 0.6, 0.00105, 0.021, 1.1, 0.0275)
    val LONG = Params(0.1307, 0.0869, 0.4, 0.00095, 0.019, 0.9, 0.0225)

    private const val IMPUTE_SLOPE = 3764.804
    private const val IMPUTE_WEIGHT = 251.641
    private const val KCAL_PER_KG = 7700.0
    private const val MAX_DAILY_ADJ = 150.0 / 7
    private const val BUDGET_MAX = 300.0
    private const val BUDGET_REFILL = 5.0

    /** Un jour d'entrée. [tween] = poids interpolé de la tendance V2 ; [scale] = pesée brute éventuelle. */
    data class Day(
        val epochDay: Long,
        val tween: Double,
        val scale: Double?,
        val kcal: Double?,
        val fasted: Boolean,
        val incomplete: Boolean,
        val steps: Double? = null,
    )

    /** Objectif dans la chronologie (pour l'ajustement prédictif). */
    data class GoalEvent(val startDay: Long, val type: GoalType, val ratePctPerWeek: Double)

    data class Point(
        val epochDay: Long,
        val tdee: Double,
        val short: Double,
        val long: Double,
        val paused: Boolean,
        val missingDays: Double,
        val missingWeightEntries: Double,
        val goalAdjustment: Double,
    )

    fun floorKcal(weightKg: Double) = max(598.0, 48.1 * Math.pow(weightKg, 0.783))

    fun signedRate(g: GoalEvent?) = when (g?.type) {
        GoalType.BULK -> g.ratePctPerWeek
        GoalType.CUT -> -g.ratePctPerWeek
        else -> 0.0
    }

    /** Apport retenu (0x12c63d0) : journalisé, jeûne = 0, sinon imputation. */
    private fun intake(d: Day, fallback: Double): Double {
        val k = d.kcal
        if (k != null && k > 0 && k <= 50_000 && !d.incomplete) return k
        if (d.fasted) return 0.0
        return max(0.0, fallback)
    }

    fun compute(
        days: List<Day>, initialTdee: Double, goals: List<GoalEvent>, predictive: Boolean, useSteps: Boolean = false,
    ): List<Point> {
        if (days.isEmpty()) return emptyList()
        val a = run(days, initialTdee, goals, predictive, useSteps, MAIN)
        val b = run(days, initialTdee, goals, predictive, useSteps, SHORT)
        val c = run(days, initialTdee, goals, predictive, useSteps, LONG)
        return a.indices.map { a[it].copy(short = b[it].tdee, long = c[it].tdee) }
    }

    private fun run(
        days: List<Day>, t0: Double, goalsIn: List<GoalEvent>, predictive: Boolean, useSteps: Boolean, p: Params,
    ): List<Point> {
        val goals = goalsIn.sortedBy { it.startDay }
        val out = ArrayList<Point>(days.size)
        var level = 0.0; var slope = 0.0; var iBar = 0.0; var iPrev = 0.0; var tdee = t0
        var steps = 0.0; var cumul = 0.0; var wPrev = 0.0
        var budgetUp = BUDGET_MAX; var budgetDown = BUDGET_MAX
        val adjustmentByGoal = HashMap<Long, Double>()

        for (i in days.indices) {
            val d = days[i]
            val n = min(7, i + 1)
            // Jours de nutrition valides parmi les n jours précédents (0x12c67d4).
            val nutri = (1..n).count { k ->
                val prev = days.getOrNull(i - k) ?: return@count false
                val ok = (prev.kcal != null && prev.kcal > 0 && prev.kcal <= 50_000) || prev.fasted
                ok && !prev.incomplete
            }
            val missing = floor(max(0.0, 7 - 7.0 * nutri / n))
            val weighed = (0 until n).count { k -> (days.getOrNull(i - k)?.scale ?: 0.0) >= 25 }
            val missingW = floor(max(0.0, 7 - 7.0 * weighed / n))
            val w = d.tween
            val stepToday = if (useSteps) days.getOrNull(i - 1)?.steps?.takeIf { it > 0 } else null

            when (i) {
                0 -> {
                    level = w; wPrev = w; slope = 0.0
                    iPrev = intake(d, tdee); iBar = (tdee + iPrev) / 2
                    steps = stepToday ?: 0.0; cumul = 0.0
                    out += Point(d.epochDay, tdee, tdee, tdee, false, 7.0, 7.0, 0.0)
                }
                1 -> {
                    val l2 = level + p.beta * (w - level)
                    val delta = l2 - level
                    slope = delta / 2 + p.alpha * (delta / 2)
                    iBar += p.alpha * (iPrev - iBar)
                    iPrev = intake(d, tdee + delta * IMPUTE_SLOPE + (w - wPrev) * IMPUTE_WEIGHT)
                    if (stepToday != null) steps += p.beta * (stepToday - steps)
                    level = l2; wPrev = w
                    out += Point(d.epochDay, tdee, tdee, tdee, false, missing, missingW, 0.0)
                }
                else -> {
                    val hold = missing > 4 || i == 2
                    val l2 = level + p.beta * (w - level)
                    val delta = l2 - level
                    val s2 = slope + p.alpha * (delta - slope)
                    val todayIntake = intake(d, tdee + delta * IMPUTE_SLOPE + (w - wPrev) * IMPUTE_WEIGHT)
                    val iBar2 = iBar + p.alpha * (iPrev - iBar)
                    val fl = floorKcal(l2)
                    val raw = max(fl, iBar2 - KCAL_PER_KG * s2)
                    val r = (raw - tdee) / tdee
                    val cumul2 = if (cumul < 0 && r < 0) cumul + r else r
                    val e = (iBar - tdee) / KCAL_PER_KG / l2
                    val rho = s2 / l2
                    var delta3 = 0.0; var kSteps = 0.0
                    var steps2 = steps
                    if (stepToday != null) {
                        steps2 = steps + p.beta * (stepToday - steps)
                        if (steps > 0) {
                            val rel = ((steps2 - steps) / steps).coerceIn(-0.1, 0.1)
                            if ((e < 0 && rel < 0) || (e > 0 && rel > 0)) { delta3 = rel; kSteps = p.kSteps }
                        }
                    }
                    val wgt = (7 - missing) / 7
                    val tStar = tdee * (1 + p.cGap * wgt * r + p.cCumul * wgt * cumul2 + p.cBalance * wgt * e +
                        p.kSlope * rho + kSteps * delta3)

                    var adj = if (predictive) goalAdjustment(d.epochDay, goals, tdee, l2, s2, adjustmentByGoal) else 0.0
                    adj = adj.coerceIn(-MAX_DAILY_ADJ, MAX_DAILY_ADJ)
                    if (adj > 0) adj = min(adj, budgetUp) else if (adj < 0) adj = max(adj, -budgetDown)
                    if (adj < 0 && fl > tStar - 300) adj = 0.0
                    if (hold) adj = 0.0
                    when {
                        adj > 1e-6 -> budgetUp = max(0.0, budgetUp - adj)
                        adj < -1e-6 -> budgetDown = max(0.0, budgetDown - abs(adj))
                        else -> { budgetUp = min(BUDGET_MAX, budgetUp + BUDGET_REFILL); budgetDown = min(BUDGET_MAX, budgetDown + BUDGET_REFILL) }
                    }
                    val newTdee = if (hold) tdee else max(fl, tStar + adj)
                    out += Point(d.epochDay, newTdee, newTdee, newTdee, hold, missing, missingW, adj)
                    level = l2; wPrev = w; slope = s2; iPrev = todayIntake; iBar = iBar2
                    steps = steps2; tdee = newTdee; cumul = cumul2
                }
            }
        }
        return out
    }

    /** Ajustement prédictif (0x12c5fe4) : 14 jours après un changement de type d'objectif. */
    private fun goalAdjustment(
        day: Long, goals: List<GoalEvent>, tdee: Double, level: Double, slope: Double, memo: HashMap<Long, Double>,
    ): Double {
        val idx = goals.indexOfLast { it.startDay <= day }
        if (idx < 1) return 0.0
        val cur = goals[idx]; val prev = goals[idx - 1]
        if (cur.type == prev.type) return 0.0
        val since = day - cur.startDay
        if (since <= 0 || since > 14 || level <= 0) return 0.0
        // [À VÉRIFIER] MacroFactor soustrait une fraction (7·s/L) à un pourcentage : reproduit tel quel.
        val total = memo.getOrPut(cur.startDay) { tdee * (signedRate(cur) - 7 * slope / level) * 4 / 100 }
        return if (total == 0.0) 0.0 else total / 14
    }

    /** Dépense initiale V3 (0x12c6584) : BMR × (activité + bonus exercice), ± ajustement prédictif selon le taux visé. */
    fun initialTdee(bmr: Double, activityFactor: Double, exerciseBonus: Double, goal: GoalEvent?, predictive: Boolean): Double {
        val base = bmr * (activityFactor + exerciseBonus)
        val rate = signedRate(goal)
        return if (predictive && rate != 0.0) base * (1 + rate * 4 / 100) else base
    }
}
