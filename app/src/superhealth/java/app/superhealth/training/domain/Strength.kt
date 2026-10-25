package app.superhealth.training.domain

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Modèle de force de MacroFactor Workouts (StrengthService, Workouts-analyse/notes/03).
 * Courbe charge ↔ répétitions jusqu'à l'échec (RTF = répétitions + RIR) :
 *   %1RM(n) = 0,8·b^(n−1) + 0,2, b = 0,965 (haut du corps) ou 0,975 (sinon)  — 0xa5c244 [OBSERVÉ]
 * Fatigue intra-séance : perte = 2,481·F^0,582 % pour F répétitions fatigantes cumulées — 0xa5f234 [OBSERVÉ]
 *   récupération : F' = (F^0,582 · e^(−0,327·n))^(1/0,582) — 0xa5f38c [OBSERVÉ] ; n = minutes de repos [INFÉRÉ]
 */
object Strength {
    const val B_UPPER = 0.965
    const val B_LOWER = 0.975
    const val FATIGUE_K = 2.481
    const val FATIGUE_EXP = 0.582
    const val RECOVERY = 0.327
    /** Progression attendue d'une séance à l'autre sans historique de progression (facteur 1,01, 0xa5eeec). */
    const val SESSION_PROGRESS = 1.01

    fun base(upper: Boolean) = if (upper) B_UPPER else B_LOWER

    /** Fraction du 1RM soulevable pour [rtf] répétitions jusqu'à l'échec. */
    fun pct(rtf: Double, upper: Boolean) = 0.8 * base(upper).pow(rtf - 1) + 0.2

    /** Bonus maximal de répétitions jusqu'à l'échec apporté par les partielles (toujours moins d'une demi-rep). */
    const val PARTIAL_MAX = 0.5
    /** Une partielle compte pour une demi-répétition fatigante (amplitude réduite). */
    const val PARTIAL_FATIGUE = 0.5

    /**
     * Répétitions jusqu'à l'échec d'une série. Des partielles prouvent l'échec (RIR 0) et que la répétition complète
     * suivante était impossible : le RTF est donc entre reps et reps + 1. Bonus borné, croissant et vite saturé :
     * 0,5·(1 − e^(−p/2)) → 1 partielle +0,20, 3 → +0,39, 5 → +0,46. Ce n'est pas un algorithme de MacroFactor : son
     * StrengthService ne lit pas les partielles dans le binaire analysé (Workouts-analyse/notes/03 §7).
     */
    fun rtf(reps: Int, rir: Int?, partials: Int = 0): Double =
        if (partials > 0) reps + PARTIAL_MAX * (1 - exp(-partials / 2.0)) else (reps + (rir ?: 0)).toDouble()

    /** Répétitions fatigantes d'une série, partielles comprises. */
    fun fatiguingReps(reps: Int, partials: Int = 0) = reps + PARTIAL_FATIGUE * partials

    /** 1RM estimé d'une série (charge, répétitions, RIR, partielles). */
    fun e1rm(weight: Double, reps: Int, rir: Int?, upper: Boolean, partials: Int = 0): Double =
        weight / pct(rtf(reps, rir, partials).coerceAtLeast(1.0), upper)

    /** Répétitions jusqu'à l'échec possibles à [weight] pour un 1RM donné (inverse de [pct]). */
    fun rtfAt(weight: Double, oneRm: Double, upper: Boolean): Double {
        if (oneRm <= 0) return 0.0
        val p = weight / oneRm
        if (p >= 1.0) return 1.0 - (p - 1.0) * 20
        val x = (p - 0.2) / 0.8
        if (x <= 0) return 60.0
        return min(60.0, 1 + ln(x) / ln(base(upper)))
    }

    fun weightFor(oneRm: Double, rtf: Double, upper: Boolean) = oneRm * pct(rtf, upper)

    // ----- Fatigue intra-séance -----

    /** Perte de force (fraction) après [fatiguingReps] répétitions fatigantes cumulées. */
    fun fatigueDrop(fatiguingReps: Double) = if (fatiguingReps <= 0) 0.0 else FATIGUE_K * fatiguingReps.pow(FATIGUE_EXP) / 100

    /** Répétitions fatigantes résiduelles après [restMin] minutes de repos. */
    fun recover(fatiguingReps: Double, restMin: Double): Double =
        if (fatiguingReps <= 0) 0.0 else (fatiguingReps.pow(FATIGUE_EXP) * exp(-RECOVERY * restMin)).pow(1 / FATIGUE_EXP)

    // ----- Échauffements intelligents (getSmartWarmUpRatios 0xa6ae5c) -----

    private val WARMUP_BASE = listOf(0.4, 0.6, 0.8, 0.95)

    /** Ratios du 1RM des séries d'échauffement pour une charge de travail à [workRatio] du 1RM. */
    fun smartWarmUpRatios(workRatio: Double): List<Double> {
        val out = mutableListOf<Double>()
        for (r in WARMUP_BASE) {
            if (r >= workRatio) break
            out += if (out.isEmpty()) r else out.last() + 0.6 * (workRatio - out.last())
        }
        return out
    }

    /** Répétitions d'échauffement (0xa6aa80) : un tiers des répétitions possibles, arrondi à 5 au-delà de 10 ; null si < 1. */
    fun warmUpReps(ratio: Double): Int? {
        val r = (1 - 32.8 * ln(ratio)) / 3
        if (r < 1) return null
        return if (r > 10) ((r / 5).roundToInt() * 5) else r.roundToInt()
    }
}

/** Échelle des charges réalisables avec l'équipement (arrondi « Weight Match » et calculateur de disques). */
class LoadLadder(val loads: List<Double>) {
    fun nearest(w: Double) = loads.minByOrNull { abs(it - w) } ?: w
    fun floor(w: Double) = loads.lastOrNull { it <= w + 1e-6 } ?: loads.firstOrNull() ?: w
    fun above(w: Double) = loads.firstOrNull { it > w + 1e-6 }
    fun below(w: Double) = loads.lastOrNull { it < w - 1e-6 }

    companion object {
        fun step(step: Double, from: Double = 0.0, to: Double = 400.0) =
            LoadLadder(generateSequence(from) { it + step }.takeWhile { it <= to + 1e-9 }.map { Math.round(it * 100) / 100.0 }.toList())

        /** Barre + disques par paires : toutes les sommes atteignables (jusqu'à 6 disques identiques par côté). */
        fun barbell(bar: Double, plates: List<Double>, maxTotal: Double = 400.0): LoadLadder {
            var sides = setOf(0.0)
            for (p in plates.distinct()) {
                val next = sides.toMutableSet()
                for (s in sides) for (k in 1..6) { val v = s + p * k; if (bar + 2 * v <= maxTotal) next += v }
                sides = next
            }
            return LoadLadder(sides.map { Math.round((bar + 2 * it) * 100) / 100.0 }.distinct().sorted())
        }
    }
}

/** Calculateur de disques (BarPlateArrangement : recherche en profondeur du chargement exact par côté). */
object Plates {
    /** Disques par côté pour atteindre [total] avec [bar] ; null si impossible exactement. */
    fun perSide(total: Double, bar: Double, plates: List<Double>): List<Double>? {
        val side = (total - bar) / 2
        if (side < -1e-6) return null
        if (abs(side) < 1e-6) return emptyList()
        val sorted = plates.distinct().sortedDescending()
        fun dfs(rem: Double, from: Int, acc: MutableList<Double>): List<Double>? {
            if (abs(rem) < 1e-6) return acc.toList()
            for (i in from until sorted.size) {
                val p = sorted[i]
                if (p <= rem + 1e-6) {
                    acc += p
                    dfs(rem - p, i, acc)?.let { return it }
                    acc.removeAt(acc.lastIndex)
                }
            }
            return null
        }
        return dfs(side, 0, mutableListOf())
    }
}

/** Série passée utilisée pour estimer la force. */
data class PastSet(val weight: Double, val reps: Int, val rir: Int?, val time: Long, val partials: Int = 0)

/**
 * Progression intelligente (Smart Progression) — reconstruction [INFÉRÉ] fondée sur la courbe et la fatigue décodées et
 * sur le comportement documenté (help center 305) : vise le RIR cible dans la plage de répétitions, +1 % par séance,
 * charge arrondie à l'équipement ; « Weight Match » garde la charge précédente si une cible valable existe ;
 * « Expand Rep Range » élargit la plage quand aucun changement de charge ne convient.
 */
object Progression {
    data class Suggestion(val weight: Double, val reps: Int, val rir: Int, val outOfRange: Boolean = false, val widened: Boolean = false)

    /** 1RM courant estimé : meilleure série de la dernière séance, corrigée de la fatigue au moment de la série. */
    fun currentOneRm(lastSession: List<PastSet>, upper: Boolean, restMin: Double = 2.0): Double? {
        var fatigue = 0.0
        var best: Double? = null
        for (s in lastSession) {
            val raw = Strength.e1rm(s.weight, s.reps, s.rir ?: 2, upper, s.partials)
            val fresh = raw / (1 - Strength.fatigueDrop(fatigue))
            if (best == null || fresh > best) best = fresh
            fatigue = Strength.recover(fatigue + Strength.fatiguingReps(s.reps, s.partials), restMin)
        }
        return best
    }

    /**
     * Cibles des séries de travail pour la prochaine séance.
     * [prevWeight] = charge de la dernière séance (Weight Match), [ladder] = charges possibles.
     */
    fun suggest(
        oneRm: Double, targets: List<TargetSet>, upper: Boolean, ladder: LoadLadder,
        prevWeight: Double?, weightMatch: Boolean, expandRange: Boolean, progress: Double = Strength.SESSION_PROGRESS,
        forced: Map<Int, Double> = emptyMap(),
    ): List<Suggestion> {
        val strength = oneRm * progress
        var fatigue = 0.0
        val out = mutableListOf<Suggestion>()
        var previousSetWeight: Double? = null
        for ((k, t) in targets.withIndex()) {
            val effective = strength * (1 - Strength.fatigueDrop(fatigue))
            fun repsAt(w: Double) = floor(Strength.rtfAt(w, effective, upper) - t.rir + 1e-9).toInt()
            // charge choisie à la main : seules les répétitions sont calculées (au RIR cible, plafonnées comme les suggestions)
            forced[k]?.let { w ->
                var reps = repsAt(w)
                val widened = reps > t.maxReps && expandRange && reps <= t.maxReps + 2
                if (reps > t.maxReps && !widened) reps = t.maxReps
                reps = max(1, reps)
                out += Suggestion(w, reps, t.rir, outOfRange = reps < t.minReps, widened = widened)
                previousSetWeight = w
                fatigue = Strength.recover(fatigue + reps, t.restSec / 60.0)
                continue
            }
            val keep = previousSetWeight ?: if (weightMatch) prevWeight else null
            val ideal = Strength.weightFor(effective, (t.maxReps + t.rir).toDouble(), upper)
            // charge la plus lourde qui garde au moins le minimum de répétitions
            var w = ladder.floor(Strength.weightFor(effective, (t.minReps + t.rir).toDouble(), upper))
            if (repsAt(w) > t.maxReps) w = ladder.nearest(ideal)
            if (keep != null) {
                val r = repsAt(keep)
                if (r in t.minReps..t.maxReps || (expandRange && r in t.minReps..t.maxReps + 2)) w = keep
            }
            var reps = repsAt(w)
            var widened = false
            var outOfRange = false
            if (reps > t.maxReps) {
                if (expandRange && reps <= t.maxReps + 2) widened = true else { reps = t.maxReps }
            }
            if (reps < t.minReps) {
                val lighter = ladder.below(w)
                if (lighter != null && repsAt(lighter) >= t.minReps) { w = lighter; reps = min(repsAt(w), t.maxReps) }
                else if (expandRange && reps >= t.minReps - 2 && reps >= 1) widened = true
                else outOfRange = true
            }
            reps = max(1, reps)
            out += Suggestion(w, reps, t.rir, outOfRange, widened)
            previousSetWeight = w
            fatigue = Strength.recover(fatigue + reps, t.restSec / 60.0)
        }
        return out
    }
}
