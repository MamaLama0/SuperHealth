package app.superhealth.wellbeing

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Tests statistiques des constats personnels (journal, cycle) : on n'affiche un effet que s'il est net sur vos propres
 * jours, jamais une tendance tirée de trois valeurs.
 */
object Stats {
    /** Écart de moyennes entre deux groupes de jours et sa probabilité d'être dû au hasard (test de Welch, bilatéral). */
    data class Diff(val meanWith: Double, val meanWithout: Double, val nWith: Int, val nWithout: Int, val p: Double) {
        val delta: Double get() = meanWith - meanWithout
    }

    fun mean(xs: List<Double>) = xs.sum() / xs.size
    fun variance(xs: List<Double>): Double { val m = mean(xs); return xs.sumOf { (it - m) * (it - m) } / (xs.size - 1) }

    /** Test t de Welch ; null sous [minEach] jours dans un des groupes ou sans dispersion. */
    fun welch(with: List<Double>, without: List<Double>, minEach: Int = 5): Diff? {
        if (with.size < minEach || without.size < minEach) return null
        val va = variance(with) / with.size
        val vb = variance(without) / without.size
        val se = sqrt(va + vb)
        if (se <= 0 || se.isNaN()) return null
        val t = (mean(with) - mean(without)) / se
        val df = (va + vb) * (va + vb) / (va * va / (with.size - 1) + vb * vb / (without.size - 1))
        return Diff(mean(with), mean(without), with.size, without.size, twoSidedP(t, df))
    }

    /** Corrélation de Pearson et sa probabilité (t à n − 2 degrés de liberté) ; null sous [minN] paires. */
    fun pearson(xs: List<Double>, ys: List<Double>, minN: Int = 10): Pair<Double, Double>? {
        val n = minOf(xs.size, ys.size)
        if (n < minN) return null
        val mx = mean(xs.take(n)); val my = mean(ys.take(n))
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in 0 until n) { val dx = xs[i] - mx; val dy = ys[i] - my; sxy += dx * dy; sxx += dx * dx; syy += dy * dy }
        if (sxx <= 0 || syy <= 0) return null
        val r = (sxy / sqrt(sxx * syy)).coerceIn(-0.999999, 0.999999)
        val t = r * sqrt((n - 2) / (1 - r * r))
        return r to twoSidedP(t, (n - 2).toDouble())
    }

    /** P(|T| ≥ |t|) pour une loi de Student à [df] degrés de liberté : I_{df/(df+t²)}(df/2, 1/2). */
    fun twoSidedP(t: Double, df: Double): Double = incompleteBeta(df / (df + t * t), df / 2, 0.5).coerceIn(0.0, 1.0)

    /** Fonction bêta incomplète régularisée I_x(a, b) (fraction continue de Lentz, Numerical Recipes 6.4). */
    fun incompleteBeta(x: Double, a: Double, b: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        val lnFront = lnGamma(a + b) - lnGamma(a) - lnGamma(b) + a * ln(x) + b * ln(1 - x)
        return if (x < (a + 1) / (a + b + 2)) exp(lnFront) * betaCf(x, a, b) / a
        else 1 - exp(lnFront) * betaCf(1 - x, b, a) / b
    }

    private fun betaCf(x: Double, a: Double, b: Double): Double {
        val tiny = 1e-300
        var c = 1.0
        var d = 1 - (a + b) * x / (a + 1)
        if (abs(d) < tiny) d = tiny
        d = 1 / d
        var h = d
        for (m in 1..300) {
            val m2 = 2 * m
            var aa = m * (b - m) * x / ((a + m2 - 1) * (a + m2))
            d = 1 + aa * d; if (abs(d) < tiny) d = tiny
            c = 1 + aa / c; if (abs(c) < tiny) c = tiny
            d = 1 / d; h *= d * c
            aa = -(a + m) * (a + b + m) * x / ((a + m2) * (a + m2 + 1))
            d = 1 + aa * d; if (abs(d) < tiny) d = tiny
            c = 1 + aa / c; if (abs(c) < tiny) c = tiny
            d = 1 / d
            val del = d * c
            h *= del
            if (abs(del - 1) < 3e-14) break
        }
        return h
    }

    /** ln Γ(x) (approximation de Lanczos, g = 7, précision ~1e-15). */
    fun lnGamma(x: Double): Double {
        val g = doubleArrayOf(0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313, -176.61503916999185,
            12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7)
        if (x < 0.5) return ln(kotlin.math.PI / kotlin.math.sin(kotlin.math.PI * x)) - lnGamma(1 - x)
        val xx = x - 1
        var a = g[0]
        val t = xx + 7.5
        for (i in 1 until 9) a += g[i] / (xx + i)
        return 0.5 * ln(2 * kotlin.math.PI) + (xx + 0.5) * ln(t) - t + ln(a)
    }

    /** Seuil retenu : moins d'une chance sur vingt que l'écart soit dû au hasard. */
    const val ALPHA = 0.05

    /**
     * Benjamini-Hochberg : parmi plusieurs tests (une habitude × une mesure chacun), lesquels garder pour que la part de faux
     * constats reste sous [q]. Tester dix habitudes au seuil de 5 % en donnerait sinon un au hasard sur deux journaux.
     */
    fun significant(ps: List<Double>, q: Double = ALPHA): BooleanArray {
        val order = ps.indices.sortedBy { ps[it] }
        var cut = -1
        order.forEachIndexed { rank, i -> if (ps[i] <= q * (rank + 1) / ps.size) cut = rank }
        val keep = BooleanArray(ps.size)
        for (rank in 0..cut) keep[order[rank]] = true
        return keep
    }
}
