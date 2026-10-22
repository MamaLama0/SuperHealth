package app.superhealth.insights

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Insights automatiques, façon MacroFactor : des constats calculés sur les données, sans IA. Chaque règle a ses seuils
 * (nombre minimal de jours, écart minimal, et test de Welch |t| ≥ 2 quand on compare deux groupes de jours) : en dessous,
 * pas de phrase. Règles propres à SuperHealth (MacroFactor ne les publie pas), couvertes par InsightsTest.
 *
 * Le sommeil d'une date est celui de la nuit qui s'y termine (réveil ce jour-là), comme dans Gadgetbridge et Health Connect.
 */
object Insights {
    enum class Type { LIFT, SLEEP_TRAINING, STEPS_WEEK, WEIGHT_RATE, KCAL_WEEK, PROTEIN_WEEK, RESTING_HR, TRAINING_MONTH, STRAIN_SIGNALS }

    /**
     * Un constat. [value] : la grandeur de la phrase (variation en %, minutes, kg / semaine, bpm…), [count] et [total] :
     * compte associé (semaines, jours atteints sur jours journalisés, séances), [subject] : exercice concerné.
     * [score] classe les constats (≈ 1 au seuil, plus haut quand l'écart est net).
     */
    data class Insight(val type: Type, val value: Double, val count: Int = 0, val total: Int = 0, val subject: String? = null,
                       val subjectId: String? = null, val score: Double)

    /** 1RM estimé d'un exercice, par séance. */
    data class Lift(val id: String, val name: String, val points: List<Pair<LocalDate, Double>>)

    data class Input(
        val today: LocalDate,
        val sleepMin: Map<LocalDate, Double> = emptyMap(),
        val steps: Map<LocalDate, Double> = emptyMap(),
        val restingHr: Map<LocalDate, Double> = emptyMap(),
        val trainingDays: Set<LocalDate> = emptySet(),
        val lifts: List<Lift> = emptyList(),
        val trendKg: Map<LocalDate, Double> = emptyMap(),
        val intakeKcal: Map<LocalDate, Double> = emptyMap(),
        val kcalTarget: Map<LocalDate, Double> = emptyMap(),
        val protein: Map<LocalDate, Double> = emptyMap(),
        val proteinTarget: Map<LocalDate, Double> = emptyMap(),
        val hrv: Map<LocalDate, Double> = emptyMap(),
        val resp: Map<LocalDate, Double> = emptyMap(),
        val skinTemp: Map<LocalDate, Double> = emptyMap(),
    )

    /** Tous les constats valides, du plus marquant au moins marquant. */
    fun compute(i: Input): List<Insight> =
        listOfNotNull(lift(i), sleepTraining(i), steps(i), weight(i), kcal(i), protein(i), restingHr(i), training(i), strain(i)).sortedByDescending { it.score }

    // --- seuils ---
    const val LIFT_WINDOW = 42L
    const val LIFT_MIN_SESSIONS = 4
    const val LIFT_MIN_SPAN = 21L
    const val LIFT_MIN_PCT = 2.0
    const val SLEEP_WINDOW = 42L
    const val SLEEP_MIN_NIGHTS = 4
    const val SLEEP_MIN_DIFF = 15.0
    const val STEPS_MIN_PCT = 10.0
    const val WEIGHT_WEEKS = 4
    const val WEIGHT_STABLE = 0.05
    const val KCAL_MIN_DAYS = 4
    const val KCAL_MIN_PCT = 5.0
    const val PROTEIN_HIT = 0.9
    const val RHR_MIN_DIFF = 2.0
    const val T_MIN = 2.0

    /**
     * Force : exercice dont le 1RM estimé a le plus varié sur les 6 dernières semaines. Meilleur 1RM des deux premières
     * séances de la fenêtre contre celui des deux dernières (une séance isolée est trop bruitée) ; au moins 4 séances
     * étalées sur 3 semaines, variation ≥ 2 %.
     */
    fun lift(i: Input): Insight? = i.lifts.mapNotNull { l ->
        val pts = l.points.filter { it.second > 0 && !it.first.isAfter(i.today) && it.first.isAfter(i.today.minusDays(LIFT_WINDOW)) }.sortedBy { it.first }
        if (pts.size < LIFT_MIN_SESSIONS) return@mapNotNull null
        val span = ChronoUnit.DAYS.between(pts.first().first, pts.last().first)
        if (span < LIFT_MIN_SPAN) return@mapNotNull null
        val early = pts.take(2).maxOf { it.second }
        val late = pts.takeLast(2).maxOf { it.second }
        val pct = (late / early - 1) * 100
        if (abs(pct) < LIFT_MIN_PCT) null
        else Insight(Type.LIFT, pct, count = (span / 7.0).roundToInt().coerceAtLeast(1), subject = l.name, subjectId = l.id, score = abs(pct) / LIFT_MIN_PCT)
    }.maxByOrNull { abs(it.value) }

    /**
     * Sommeil et entraînement : nuits qui suivent un jour sans séance contre nuits qui suivent une séance (6 semaines).
     * [Insight.value] > 0 : on dort plus après les jours sans séance. Au moins 4 nuits de chaque côté, écart ≥ 15 min, |t| ≥ 2.
     */
    fun sleepTraining(i: Input): Insight? {
        val train = mutableListOf<Double>(); val rest = mutableListOf<Double>()
        for (k in 1..SLEEP_WINDOW) {
            val d = i.today.minusDays(k)
            val night = i.sleepMin[d.plusDays(1)]?.takeIf { it > 0 } ?: continue
            if (d in i.trainingDays) train += night else rest += night
        }
        if (train.size < SLEEP_MIN_NIGHTS || rest.size < SLEEP_MIN_NIGHTS) return null
        val diff = rest.average() - train.average()
        if (abs(diff) < SLEEP_MIN_DIFF || abs(welchT(rest, train)) < T_MIN) return null
        return Insight(Type.SLEEP_TRAINING, diff, count = rest.size, total = train.size, score = abs(diff) / SLEEP_MIN_DIFF)
    }

    /** Pas : moyenne des 7 derniers jours complets contre les 28 jours d'avant ; ≥ 5 et ≥ 14 jours mesurés, écart ≥ 10 %. */
    fun steps(i: Input): Insight? {
        val week = days(i.today, 1, 7).mapNotNull { i.steps[it]?.takeIf { v -> v > 0 } }
        val before = days(i.today, 8, 35).mapNotNull { i.steps[it]?.takeIf { v -> v > 0 } }
        if (week.size < 5 || before.size < 14) return null
        val pct = (week.average() / before.average() - 1) * 100
        if (abs(pct) < STEPS_MIN_PCT) return null
        return Insight(Type.STEPS_WEEK, pct, count = week.average().roundToInt(), score = abs(pct) / STEPS_MIN_PCT)
    }

    /** Poids : pente de la tendance sur 4 semaines (kg / semaine) ; « stable » sous 0,05 kg / semaine. */
    fun weight(i: Input): Insight? {
        val now = i.trendKg.entries.filter { !it.key.isAfter(i.today) }.maxByOrNull { it.key } ?: return null
        if (ChronoUnit.DAYS.between(now.key, i.today) > 7) return null
        val then = i.trendKg[now.key.minusWeeks(WEIGHT_WEEKS.toLong())] ?: return null
        val rate = (now.value - then) / WEIGHT_WEEKS
        return Insight(Type.WEIGHT_RATE, rate, count = WEIGHT_WEEKS, score = if (abs(rate) < WEIGHT_STABLE) 0.8 else 1.0 + abs(rate))
    }

    /** Calories : apports des 7 derniers jours complets journalisés contre l'objectif ; ≥ 4 jours, « dans l'objectif » sous ±5 %. */
    fun kcal(i: Input): Insight? {
        val logged = days(i.today, 1, 7).filter { (i.intakeKcal[it] ?: 0.0) > 0 && (i.kcalTarget[it] ?: 0.0) > 0 }
        if (logged.size < KCAL_MIN_DAYS) return null
        val pct = (logged.sumOf { i.intakeKcal[it]!! } / logged.sumOf { i.kcalTarget[it]!! } - 1) * 100
        return Insight(Type.KCAL_WEEK, pct, count = logged.size, score = if (abs(pct) < KCAL_MIN_PCT) 0.9 else abs(pct) / KCAL_MIN_PCT)
    }

    /** Protéines : jours (7 derniers complets, ≥ 4 journalisés) où l'on atteint au moins 90 % de l'objectif. */
    fun protein(i: Input): Insight? {
        val logged = days(i.today, 1, 7).filter { (i.intakeKcal[it] ?: 0.0) > 0 && (i.proteinTarget[it] ?: 0.0) > 0 }
        if (logged.size < KCAL_MIN_DAYS) return null
        val hit = logged.count { (i.protein[it] ?: 0.0) >= PROTEIN_HIT * i.proteinTarget[it]!! }
        val f = hit.toDouble() / logged.size
        return Insight(Type.PROTEIN_WEEK, f * 100, count = hit, total = logged.size, score = if (f >= 0.8 || f <= 0.4) 1.1 else 0.6)
    }

    /** Pouls au repos : 7 derniers jours contre les 28 d'avant ; ≥ 5 et ≥ 14 mesures, écart ≥ 2 bpm, |t| ≥ 2. */
    fun restingHr(i: Input): Insight? {
        val week = days(i.today, 0, 6).mapNotNull { i.restingHr[it]?.takeIf { v -> v > 0 } }
        val before = days(i.today, 7, 34).mapNotNull { i.restingHr[it]?.takeIf { v -> v > 0 } }
        if (week.size < 5 || before.size < 14) return null
        val diff = week.average() - before.average()
        if (abs(diff) < RHR_MIN_DIFF || abs(welchT(week, before)) < T_MIN) return null
        return Insight(Type.RESTING_HR, diff, score = abs(diff) / RHR_MIN_DIFF)
    }

    /** Régularité : séances des 28 derniers jours contre les 28 d'avant ; écart ≥ 2 séances. */
    fun training(i: Input): Insight? {
        val now = days(i.today, 0, 27).count { it in i.trainingDays }
        val before = days(i.today, 28, 55).count { it in i.trainingDays }
        if (before == 0 || abs(now - before) < 2) return null
        return Insight(Type.TRAINING_MONTH, (now - before).toDouble(), count = now, total = before, score = abs(now - before) / 2.0 * 0.8)
    }

    /**
     * Signaux de fatigue (alerte de NOOP) : les 2 derniers jours contre une référence de ~28 jours qui s'arrête 3 jours
     * avant (pour ne pas s'y diluer). Pouls au repos ≥ +5 bpm, VFC ≤ −20 %, température cutanée ≥ +0,6 °C, respiration
     * ≥ +1,5 /min : au moins deux signaux ensemble, avec 14 jours de référence. [count] = nombre de signaux.
     */
    fun strain(i: Input): Insight? {
        fun split(m: Map<LocalDate, Double>): Pair<List<Double>, List<Double>> =
            days(i.today, 0, 1).mapNotNull { m[it] } to days(i.today, 3, 30).mapNotNull { m[it] }
        var n = 0; var tested = 0; var hrvDrop = 0.0
        split(i.restingHr).let { (r, b) -> if (r.isNotEmpty() && b.size >= 14) { tested++; if (r.average() >= b.average() + 5) n++ } }
        split(i.hrv).let { (r, b) -> if (r.isNotEmpty() && b.size >= 14) { tested++; val f = r.average() / b.average(); if (f <= 0.80) { n++; hrvDrop = (1 - f) * 100 } } }
        split(i.skinTemp).let { (r, b) -> if (r.isNotEmpty() && b.size >= 14) { tested++; if (r.average() - b.average() >= 0.6) n++ } }
        split(i.resp).let { (r, b) -> if (r.isNotEmpty() && b.size >= 14) { tested++; if (r.average() >= b.average() + 1.5) n++ } }
        if (n < 2) return null
        return Insight(Type.STRAIN_SIGNALS, hrvDrop, count = n, total = tested, score = 3.0 + n)
    }

    /** Jours de today−[from] à today−[to] (inclus). */
    private fun days(today: LocalDate, from: Int, to: Int) = (from..to).map { today.minusDays(it.toLong()) }

    /** t de Welch (moyennes de deux échantillons de variances différentes). */
    fun welchT(a: List<Double>, b: List<Double>): Double {
        if (a.size < 2 || b.size < 2) return 0.0
        val se = sqrt(variance(a) / a.size + variance(b) / b.size)
        val d = a.average() - b.average()
        return if (se == 0.0) (if (d == 0.0) 0.0 else Double.POSITIVE_INFINITY * kotlin.math.sign(d)) else d / se
    }

    /** Variance d'échantillon (n − 1). */
    fun variance(x: List<Double>): Double { val m = x.average(); return x.sumOf { (it - m) * (it - m) } / (x.size - 1) }
}

/** Corrélation de deux séries de jours (coefficient de Pearson sur les jours où les deux existent). */
object Correlation {
    /** [t] = r √((n − 2) / (1 − r²)) ; [significant] : au moins 7 jours communs et |t| ≥ 2 (≈ 95 %). */
    data class Result(val r: Double, val n: Int, val t: Double) {
        val significant: Boolean get() = n >= MIN_DAYS && abs(t) >= Insights.T_MIN
    }

    enum class Strength { NONE, WEAK, MODERATE, STRONG }

    const val MIN_DAYS = 7

    /** [lag] : décalage de b (1 = b du lendemain). Null sous 3 jours communs ou si une série est constante. */
    fun pearson(a: Map<LocalDate, Double>, b: Map<LocalDate, Double>, lag: Int = 0): Result? {
        val pairs = a.mapNotNull { (d, x) -> b[d.plusDays(lag.toLong())]?.let { x to it } }
        val n = pairs.size
        if (n < 3) return null
        val mx = pairs.sumOf { it.first } / n; val my = pairs.sumOf { it.second } / n
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for ((x, y) in pairs) { sxy += (x - mx) * (y - my); sxx += (x - mx) * (x - mx); syy += (y - my) * (y - my) }
        if (sxx == 0.0 || syy == 0.0) return null
        val r = (sxy / sqrt(sxx * syy)).coerceIn(-1.0, 1.0)
        val t = if (abs(r) >= 1.0) Double.POSITIVE_INFINITY * kotlin.math.sign(r) else r * sqrt((n - 2) / (1 - r * r))
        return Result(r, n, t)
    }

    /** Seuils usuels de Cohen : 0,1 / 0,3 / 0,5. */
    fun strength(r: Double): Strength = when (abs(r)) {
        in 0.0..<0.1 -> Strength.NONE
        in 0.1..<0.3 -> Strength.WEAK
        in 0.3..<0.5 -> Strength.MODERATE
        else -> Strength.STRONG
    }
}
