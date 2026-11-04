package app.superhealth.league

import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Note quotidienne commune (plan 3.5, moteur 3). Une seule définition de la « zone verte » de chaque domaine sert aux XP,
 * aux séries, aux quêtes et à l'indice : un jour validé pour les XP vaut toujours 100 dans l'indice.
 *
 * Hors zone, la note baisse linéairement avec l'écart à la zone, divisé par [Zone.slack] :
 * nutrition à 70 % d'une zone 80–115 % → 1 − 0,10 / 0,5 = 0,8.
 */
object DailyScore {
    data class Zone(val low: Double, val high: Double = Double.POSITIVE_INFINITY, val slack: Double) {
        fun contains(ratio: Double) = ratio >= low - 1e-7 && ratio <= high + 1e-7
    }

    val STEPS = Zone(1.0, slack = 1.0)
    val SLEEP = Zone(1.0, slack = 1.0)
    val MIND = Zone(1.0, slack = 1.0)
    val PROTEIN = Zone(0.9, slack = 0.9)
    fun food(range: Progression.NutritionRange) = Zone(range.minPercent / 100.0, range.maxPercent / 100.0, slack = 0.5)

    /** Note de 0 à 1 pour un rapport réalisé / cible ; null si la cible ou la mesure manque. */
    fun adherence(done: Double?, target: Double?, zone: Zone): Double? {
        if (done == null || target == null || !done.isFinite() || !target.isFinite() || target <= 0 || done < 0) return null
        val r = done / target
        if (zone.contains(r)) return 1.0
        val gap = if (r < zone.low) zone.low - r else r - zone.high
        return (1 - gap / zone.slack).coerceIn(0.0, 1.0)
    }

    /** Jour validé pour les XP et les séries : dans la zone verte. */
    fun validated(done: Double?, target: Double?, zone: Zone) = adherence(done, target, zone) == 1.0

    /** Nutrition : énergie dans la zone, protéines pour un quart quand elles sont connues. */
    fun food(kcal: Double?, target: Double?, range: Progression.NutritionRange, protein: Double? = null, proteinTarget: Double? = null): Double? {
        val energy = adherence(kcal, target, food(range)) ?: return null
        val p = adherence(protein, proteinTarget, PROTEIN) ?: return energy
        return 0.75 * energy + 0.25 * p
    }

    /** Régularité d'un domaine quotidien : moyenne des jours observés, au moins quatre. */
    fun regularity(days: List<Double?>): Double? = days.filterNotNull().takeIf { it.size >= 4 }?.average()

    /** Terme de progrès centré sur 0,5 : une pente doit dépasser deux fois son incertitude pour vraiment compter. */
    fun progress(slope: Double, slopeSd: Double): Double = 0.5 + 0.5 * tanh(slope / (2 * slopeSd.coerceAtLeast(1e-9)))

    /** Partie sur 100. Les domaines sportifs mêlent régularité (85 %) et progrès (15 %). */
    fun pillar(regularity: Double?, progress: Double? = null): Int? {
        regularity ?: return null
        val score = if (progress == null) regularity else 0.85 * regularity + 0.15 * progress
        return (score * 100).roundToInt().coerceIn(0, 100)
    }

    /** Couverture d'un domaine quotidien : part des sept jours observés, sous quatre jours le domaine est absent. */
    fun coverage(days: List<Double?>): Double = days.count { it != null } / 7.0
}
