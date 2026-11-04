package app.superhealth.adaptive

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Filtre de Kalman niveau + pente à pas irréguliers, avec résidu robuste (Huber). Partagé par la force par exercice et la
 * vitesse de course ; le poids garde son filtre à trois états (WeightTrendV2), qui ajoute un transitoire.
 *
 * Les valeurs sont dans l'unité de la série (souvent un logarithme) et le temps en jours. Seule la valeur filtrée à date est
 * exposée : un score passé ne change pas quand une mesure arrive ensuite.
 */
class LevelTrend(
    /** Variance ajoutée au niveau par jour. */
    private val qLevel: Double,
    /** Variance ajoutée à la pente par jour. */
    private val qSlope: Double,
    /** Écart-type initial de la pente. */
    private val slopeSd0: Double,
    /** Seuil de Huber, en écarts-types d'innovation. */
    private val huber: Double = 2.5,
    /** Bornes de la pente (par jour), pour éviter une extrapolation absurde après deux mesures. */
    private val slopeMin: Double = Double.NEGATIVE_INFINITY,
    private val slopeMax: Double = Double.POSITIVE_INFINITY,
) {
    data class State(
        val day: Double, val level: Double, val slope: Double,
        val pLL: Double, val pLS: Double, val pSS: Double,
        val count: Int = 1,
    ) {
        val levelSd get() = sqrt(max(pLL, 0.0))
        val slopeSd get() = sqrt(max(pSS, 0.0))
    }

    /** Première mesure : niveau mesuré, pente a priori. */
    fun start(day: Double, z: Double, r: Double, slope0: Double = 0.0) =
        State(day, z, slope0.coerceIn(slopeMin, slopeMax), r, 0.0, slopeSd0 * slopeSd0)

    /** État prédit à [day] (≥ jour de l'état). */
    fun predict(s: State, day: Double): State {
        val dt = (day - s.day).coerceAtLeast(0.0)
        if (dt == 0.0) return s
        val level = s.level + s.slope * dt
        val pLL = s.pLL + 2 * dt * s.pLS + dt * dt * s.pSS + qLevel * dt
        val pLS = s.pLS + dt * s.pSS
        val pSS = s.pSS + qSlope * dt
        return s.copy(day = day, level = level, pLL = pLL, pLS = pLS, pSS = pSS)
    }

    /** Innovation normalisée d'une mesure, avant mise à jour (pour juger une séance atypique). */
    fun surprise(s: State, day: Double, z: Double, r: Double): Double {
        val p = predict(s, day)
        return (z - p.level) / sqrt(p.pLL + r)
    }

    /** Mise à jour par une mesure [z] de variance [r]. */
    fun update(s: State, day: Double, z: Double, r: Double): State {
        val p = predict(s, day)
        val nu = z - p.level
        val sNu = p.pLL + r
        val zScore = abs(nu) / sqrt(sNu)
        val rEff = if (zScore <= huber) r else r * (zScore / huber) * (zScore / huber)
        val sEff = p.pLL + rEff
        val kL = p.pLL / sEff
        val kS = p.pLS / sEff
        val slope = (p.slope + kS * nu).coerceIn(slopeMin, slopeMax)
        return State(
            day = p.day, level = p.level + kL * nu, slope = slope,
            pLL = max((1 - kL) * p.pLL, 1e-12), pLS = (1 - kL) * p.pLS, pSS = max(p.pSS - kS * p.pLS, 1e-14),
            count = s.count + 1,
        )
    }
}
