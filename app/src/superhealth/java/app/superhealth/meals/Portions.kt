package app.superhealth.meals

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Grammes des aliments choisis pour un repas : les calories du repas tombent sur sa cible, puis protéines, lipides et
 * glucides s'en approchent au mieux (moindres carrés, écarts comptés en kcal : 4 / 9 / 4 kcal par gramme).
 *
 * Règle propre à SuperHealth (pas de MacroFactor) : l'écart de protéines pèse double, comme dans la consigne du
 * planificateur IA (« les protéines d'abord »). Chaque aliment choisi garde au moins [MIN_G] g et au plus [MAX_G] g.
 *
 * Problème convexe (quadratique, une égalité, bornes) résolu par gradient projeté : la projection sur
 * {bornes ∩ calories = cible} est exacte (recherche par dichotomie du multiplicateur, la fonction est monotone).
 * Résultat arrondi à 5 g, puis ajusté par pas de 5 g pour rester au plus près de la cible de calories.
 */
object Portions {
    const val MIN_G = 10.0
    const val MAX_G = 500.0
    private const val STEP = 5.0

    /** Aliment pour 100 g. */
    data class Food(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)

    data class Goal(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)

    /** [grams] dans l'ordre des aliments ; [reachable] faux si la cible de calories sort des bornes (portions au plus près). */
    data class Result(val grams: List<Double>, val kcal: Double, val reachable: Boolean)

    private val W = doubleArrayOf(2.0, 1.0, 1.0)

    fun solve(foods: List<Food>, goal: Goal): Result {
        val n = foods.size
        require(n > 0)
        val k = DoubleArray(n) { foods[it].kcal / 100 }
        // lignes : protéines, lipides, glucides, en kcal par gramme d'aliment
        val a = arrayOf(DoubleArray(n) { foods[it].protein * 4 / 100 }, DoubleArray(n) { foods[it].fat * 9 / 100 }, DoubleArray(n) { foods[it].carbs * 4 / 100 })
        val t = doubleArrayOf(goal.protein * 4, goal.fat * 9, goal.carbs * 4)
        val lo = DoubleArray(n) { MIN_G }; val hi = DoubleArray(n) { MAX_G }
        val minK = (0 until n).sumOf { k[it] * lo[it] }; val maxK = (0 until n).sumOf { k[it] * hi[it] }
        val target = goal.kcal.coerceIn(minK, maxK)
        val reachable = goal.kcal in minK..maxK

        // pas sûr : 1 / constante de Lipschitz du gradient (2 Σ w‖aᵢ‖² majore la plus grande valeur propre de 2 AᵀWA)
        val lip = 2 * (0 until 3).sumOf { r -> W[r] * a[r].sumOf { it * it } }
        val step = if (lip > 0) 1 / lip else 0.0
        var x = project(DoubleArray(n) { target / n / k[it].coerceAtLeast(1e-6) }, k, lo, hi, target)
        if (step > 0) for (iter in 0 until 4000) {
            val g = DoubleArray(n)
            for (r in 0 until 3) {
                val res = (0 until n).sumOf { a[r][it] * x[it] } - t[r]
                for (i in 0 until n) g[i] += 2 * W[r] * res * a[r][i]
            }
            val y = DoubleArray(n) { x[it] - step * g[it] }
            val nx = project(y, k, lo, hi, target)
            val moved = (0 until n).maxOf { abs(nx[it] - x[it]) }
            x = nx
            if (moved < 1e-7) break
        }
        val grams = round(x, k, lo, hi, target)
        return Result(grams.toList(), (0 until n).sumOf { k[it] * grams[it] }, reachable)
    }

    /** Point le plus proche de [y] dans {lo ≤ x ≤ hi, k·x = cible} : x = clip(y − λk), λ trouvé par dichotomie. */
    private fun project(y: DoubleArray, k: DoubleArray, lo: DoubleArray, hi: DoubleArray, target: Double): DoubleArray {
        fun at(l: Double) = DoubleArray(y.size) { (y[it] - l * k[it]).coerceIn(lo[it], hi[it]) }
        fun kc(x: DoubleArray) = x.indices.sumOf { k[it] * x[it] }
        var a = -1e6; var b = 1e6
        repeat(200) {
            val m = (a + b) / 2
            if (kc(at(m)) > target) a = m else b = m
        }
        return at((a + b) / 2)
    }

    /** Arrondi à 5 g puis, tant que ça rapproche des calories visées, ±5 g sur l'aliment qui corrige le plus sans dépasser. */
    private fun round(x: DoubleArray, k: DoubleArray, lo: DoubleArray, hi: DoubleArray, target: Double): DoubleArray {
        val g = DoubleArray(x.size) { ((x[it] / STEP).roundToInt() * STEP).coerceIn(lo[it], hi[it]) }
        repeat(40) {
            val err = g.indices.sumOf { k[it] * g[it] } - target
            var best = -1; var bestErr = abs(err); var dir = 0.0
            for (i in g.indices) for (d in doubleArrayOf(-STEP, STEP)) {
                val v = g[i] + d
                if (v < lo[i] || v > hi[i]) continue
                val e = abs(err + k[i] * d)
                if (e < bestErr - 1e-9) { bestErr = e; best = i; dir = d }
            }
            if (best < 0) return g
            g[best] += dir
        }
        return g
    }
}
