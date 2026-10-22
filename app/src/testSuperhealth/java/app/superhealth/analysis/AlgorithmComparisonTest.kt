package app.superhealth.analysis

import app.superhealth.health.data.Scores
import app.superhealth.nutrition.domain.ExpenditureV3
import app.superhealth.nutrition.domain.WeightTrendV2
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/**
 * Comparaison chiffrée des algorithmes de SuperHealth et de ceux documentés par NOOP (WHOOP), sur des scénarios simulés
 * dont on connaît la vérité. Écrit build/algo-comparison.json (repris dans le rapport d'analyse).
 */
class AlgorithmComparisonTest {
    private data class Scenario(val name: String, val tdee: (Int) -> Double, val intake: (Int) -> Double, val weighEvery: Int, val logged: Double)

    /** Bilan énergétique de NOOP (AdaptiveExpenditureEngine) : 21 à 42 jours, pente des pesées × 7700, apports moyens. */
    private fun energyBalance(day: Int, weights: Map<Int, Double>, intake: Map<Int, Double>): Double? {
        for (window in 42 downTo 21) {
            val from = day - window + 1
            if (from < 0) continue
            val w = weights.filterKeys { it in from..day }
            val k = intake.filterKeys { it in from..day }
            if (w.size < 6 || k.size < 14 || k.size < 0.7 * window) continue
            val mx = w.keys.average(); val my = w.values.average()
            val slope = w.entries.sumOf { (x, y) -> (x - mx) * (y - my) } / w.keys.sumOf { (it - mx) * (it - mx) }
            return k.values.average() - slope * 7700
        }
        return null
    }

    private fun run(s: Scenario, seed: Int = 7): Map<String, Any> {
        val rnd = Random(seed)
        val days = 120
        var fat = 80.0
        val scale = HashMap<Int, Double>(); val logged = HashMap<Int, Double>()
        for (d in 0 until days) {
            fat += (s.intake(d) - s.tdee(d)) / 7700
            val water = 0.6 * kotlin.math.sin(d / 3.0) + rnd.nextDouble(-0.5, 0.5)
            if (d % s.weighEvery == 0) scale[d] = fat + water
            if (rnd.nextDouble() < s.logged) logged[d] = s.intake(d)
        }
        val trend = WeightTrendV2.compute(scale.mapKeys { it.key.toLong() }, days - 1L).associateBy { it.epochDay }
        val v3Days = (0 until days).map { d ->
            ExpenditureV3.Day(d.toLong(), trend[d.toLong()]?.tween ?: 80.0, scale[d], logged[d], false, false)
        }
        val mf = ExpenditureV3.compute(v3Days, initialTdee = s.tdee(0) + 300, goals = emptyList(), predictive = false).associate { it.epochDay.toInt() to it.tdee }
        val eb = (0 until days).associateWith { energyBalance(it, scale, logged) }
        fun mae(m: Map<Int, Double?>, from: Int) = (from until days).mapNotNull { d -> m[d]?.let { abs(it - s.tdee(d)) } }.average()
        fun coverage(m: Map<Int, Double?>) = (0 until days).count { m[it] != null }
        // délai pour revenir à moins de 100 kcal de la vraie dépense après un changement (jour 60)
        fun lag(m: Map<Int, Double?>) = (60 until days).firstOrNull { d -> (d until minOf(d + 7, days)).all { x -> m[x]?.let { abs(it - s.tdee(x)) < 100 } == true } }?.minus(60)
        // stabilité : variation moyenne d'un jour à l'autre de l'estimation (kcal) — une cible qui saute chaque jour est inutilisable
        fun vol(m: Map<Int, Double?>) = (31 until days).mapNotNull { d -> val a = m[d]; val b = m[d - 1]; if (a != null && b != null) abs(a - b) else null }.average()
        return mapOf(
            "mf_day_to_day_kcal" to vol(mf), "noop_day_to_day_kcal" to vol(eb),
            "scenario" to s.name,
            "mf_mae_30_120" to mae(mf, 30), "noop_mae_30_120" to mae(eb, 30),
            "mf_days_with_value" to coverage(mf), "noop_days_with_value" to coverage(eb),
            "mf_lag_after_change" to (lag(mf) ?: -1), "noop_lag_after_change" to (lag(eb) ?: -1),
        )
    }

    @Test fun compare() {
        val scenarios = listOf(
            Scenario("Déficit stable, pesée et journal quotidiens", { 2600.0 }, { 2100.0 }, 1, 1.0),
            Scenario("Adaptation : dépense −250 kcal au jour 60", { if (it < 60) 2600.0 else 2350.0 }, { 2100.0 }, 1, 1.0),
            Scenario("Données clairsemées : 3 pesées / sem., 80 % des jours journalisés", { if (it < 60) 2600.0 else 2350.0 }, { 2100.0 }, 2, 0.8),
            Scenario("Prise de masse : +300 kcal au jour 60", { 2600.0 }, { if (it < 60) 2600.0 else 2900.0 }, 1, 1.0),
        )
        val results = scenarios.map { run(it) }
        // dette de sommeil : récurrence de NOOP (0,55) contre la somme brute des manques sur 14 nuits
        val nights = List(10) { 390.0 } + List(4) { 540.0 }
        val naive = nights.sumOf { maxOf(0.0, 480 - it) } - nights.sumOf { maxOf(0.0, it - 480) }.coerceAtMost(0.0)
        val sleep = mapOf("nuits" to "10 × 6 h 30 puis 4 × 9 h (besoin 8 h)", "noop_debt_min" to Scores.sleepDebt(nights, 480.0),
            "naive_sum_min" to naive, "noop_debt_after_10_short" to Scores.sleepDebt(nights.take(10), 480.0))
        val json = buildString {
            append("{\"expenditure\":[")
            append(results.joinToString(",") { r -> r.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":" + (if (v is String) "\"$v\"" else v.toString()) } })
            append("],\"sleep_debt\":")
            append(sleep.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":" + (if (v is String) "\"$v\"" else v.toString()) })
            append("}")
        }
        File("build").mkdirs()
        File("build/algo-comparison.json").writeText(json)
        println(json)
        assertTrue(results.all { (it["mf_mae_30_120"] as Double).isFinite() })
    }
}
