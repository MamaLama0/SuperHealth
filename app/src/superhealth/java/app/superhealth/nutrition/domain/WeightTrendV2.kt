package app.superhealth.nutrition.domain

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Tendance de poids V2 de MacroFactor : filtre de Kalman à 3 états (niveau, pente, transitoire) + lissage RTS.
 * Décodage : MacroFactor-analyse/notes/15 §1 (weight_trend_service, 0xaa9dcc, 0xab0fbc, 0xaaf868).
 *
 * Sorties par jour : trend = niveau, transient = eau/contenu digestif, tween = niveau + transitoire
 * (ce que la balance afficherait sans bruit), noise = pesée − tween.
 */
object WeightTrendV2 {

    data class Point(
        val epochDay: Long,
        val scale: Double?,
        val trend: Double,
        val tween: Double,
        val transient: Double,
        val slope: Double,
        /** Écart-type de la tendance lissée (kg), pour la bande d'incertitude. */
        val trendSd: Double,
    ) {
        val noise: Double? get() = scale?.let { it - tween }
    }

    private val LAMBDA_SLOPE = ln(2.0) / 30
    private val LAMBDA_TRANSIENT = ln(2.0) / 2
    private val E1 = exp(-LAMBDA_SLOPE)
    private val E2 = exp(-LAMBDA_TRANSIENT)
    private const val LEVEL_Q = 0.0025
    private const val HUBER = 2.0
    private val SQRT_HALF_PI = sqrt(Math.PI / 2)
    private val NOISE_EMA = 0.5.pow(1.0 / 30)

    private fun ref(w: Double) = max(abs(w), 25.0)
    fun obsSigma(w: Double) = (0.007 * ref(w)).coerceIn(0.45, 0.85)
    fun transientSigma(w: Double) = (0.01 * ref(w)).coerceIn(0.6, 1.4)
    fun slopeSigma(w: Double) = (0.0008 * ref(w)).coerceIn(0.04, 0.12)

    // Matrices 3×3 en tableaux de 9 (ligne par ligne), comme dans le code d'origine.
    private val F = doubleArrayOf(1.0, (1 - E1) / LAMBDA_SLOPE, 0.0, 0.0, E1, 0.0, 0.0, 0.0, E2)

    private fun mul(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { k ->
        val i = k / 3; val j = k % 3
        a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
    }
    private fun t(a: DoubleArray) = DoubleArray(9) { k -> a[(k % 3) * 3 + k / 3] }
    private fun mulV(a: DoubleArray, v: DoubleArray) = DoubleArray(3) { i -> a[i * 3] * v[0] + a[i * 3 + 1] * v[1] + a[i * 3 + 2] * v[2] }
    private fun floorDiag(p: DoubleArray) { for (i in 0..2) p[i * 4] = max(p[i * 4], 1e-12) }

    /** Inverse 3×3 (pour le gain du lisseur). */
    private fun inv(m: DoubleArray): DoubleArray {
        val a = m
        val c00 = a[4] * a[8] - a[5] * a[7]; val c01 = a[5] * a[6] - a[3] * a[8]; val c02 = a[3] * a[7] - a[4] * a[6]
        val det = a[0] * c00 + a[1] * c01 + a[2] * c02
        val d = if (abs(det) < 1e-18) 1e-18 else det
        return doubleArrayOf(
            c00 / d, (a[2] * a[7] - a[1] * a[8]) / d, (a[1] * a[5] - a[2] * a[4]) / d,
            c01 / d, (a[0] * a[8] - a[2] * a[6]) / d, (a[2] * a[3] - a[0] * a[5]) / d,
            c02 / d, (a[1] * a[6] - a[0] * a[7]) / d, (a[0] * a[4] - a[1] * a[3]) / d,
        )
    }

    /**
     * @param weighIns pesées par epochDay (kg).
     * @param toDay dernier jour à produire (les jours après la dernière pesée sont des prédictions).
     */
    fun compute(weighIns: Map<Long, Double>, toDay: Long): List<Point> {
        val valid = weighIns.filterValues { it >= 25.0 }
        if (valid.isEmpty()) return emptyList()
        val first = valid.keys.min()
        val w0 = valid.getValue(first)
        val sp = slopeSigma(w0); val st = transientSigma(w0)
        val q = doubleArrayOf(LEVEL_Q, 0.0, 0.0, 0.0, sp * sp * (1 - E1 * E1), 0.0, 0.0, 0.0, st * st * (1 - E2 * E2))
        val sigma = obsSigma(w0)
        var noiseScale = 1.0

        val n = (toDay - first + 1).toInt().coerceAtLeast(1)
        val xPred = Array(n) { DoubleArray(3) }; val pPred = Array(n) { DoubleArray(9) }
        val xFilt = Array(n) { DoubleArray(3) }; val pFilt = Array(n) { DoubleArray(9) }

        var x = doubleArrayOf(w0, 0.0, 0.0)
        var p = doubleArrayOf(4.0, 0.0, 0.0, 0.0, sp * sp, 0.0, 0.0, 0.0, st * st)
        for (i in 0 until n) {
            if (i > 0) {
                x = mulV(F, x)
                p = mul(mul(F, p), t(F)).also { m -> for (k in 0..8) m[k] += q[k] }
                floorDiag(p)
            }
            xPred[i] = x.copyOf(); pPred[i] = p.copyOf()
            val y = valid[first + i]
            if (y != null) {
                val s0 = p[0] + p[2] + p[6] + p[8]
                val r = (sigma * noiseScale).let { it * it }
                val nu = y - (x[0] + x[2])
                val z = nu / sqrt(s0 + r)
                val wgt = if (abs(z) <= HUBER) 1.0 else HUBER / abs(z)
                val rEff = r / max(wgt, 1e-9)
                val s = s0 + rEff
                val ph = doubleArrayOf(p[0] + p[2], p[3] + p[5], p[6] + p[8])
                val k = DoubleArray(3) { ph[it] / s }
                x = DoubleArray(3) { x[it] + k[it] * nu }
                // P = P − K (H P)  avec  H P = [P0+P6, P1+P7, P2+P8]
                val hp = doubleArrayOf(p[0] + p[6], p[1] + p[7], p[2] + p[8])
                p = DoubleArray(9) { idx -> p[idx] - k[idx / 3] * hp[idx % 3] }
                floorDiag(p)
                // Échelle de bruit d'observation : moyenne exponentielle des résidus normalisés.
                val clipped = min(abs(nu), 2 * sqrt(s))
                val ratio = (clipped * SQRT_HALF_PI).coerceIn(0.45, 0.85) / sigma
                noiseScale += (1 - NOISE_EMA) * (ratio - noiseScale)
            }
            xFilt[i] = x.copyOf(); pFilt[i] = p.copyOf()
        }

        // Lissage RTS jusqu'à la dernière pesée ; au-delà, la prédiction est conservée.
        val lastObs = (valid.keys.max() - first).toInt().coerceAtMost(n - 1)
        val xs = Array(n) { xFilt[it].copyOf() }; val ps = Array(n) { pFilt[it].copyOf() }
        for (i in lastObs - 1 downTo 0) {
            val g = mul(mul(pFilt[i], t(F)), inv(pPred[i + 1]))
            val dx = DoubleArray(3) { xs[i + 1][it] - xPred[i + 1][it] }
            val gdx = mulV(g, dx)
            xs[i] = DoubleArray(3) { xFilt[i][it] + gdx[it] }
            val dp = DoubleArray(9) { ps[i + 1][it] - pPred[i + 1][it] }
            val corr = mul(mul(g, dp), t(g))
            ps[i] = DoubleArray(9) { pFilt[i][it] + corr[it] }
            floorDiag(ps[i])
        }
        return (0 until n).map { i ->
            val st0 = xs[i]
            Point(first + i, valid[first + i], st0[0], st0[0] + st0[2], st0[2], st0[1], sqrt(max(ps[i][0], 0.0)))
        }
    }
}
