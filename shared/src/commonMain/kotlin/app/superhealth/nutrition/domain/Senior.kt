package app.superhealth.nutrition.domain

import app.superhealth.platform.*

import kotlin.math.max
import kotlin.math.min

/**
 * Âge et perte de poids (règle SuperHealth, pas MacroFactor). Après 60 ans, le déficit se resserre ; après 65 ans,
 * la perte de poids n'est proposée qu'en cas d'obésité et reste modérée, pour garder muscle, os et autonomie.
 *
 * Sources :
 * - ESPEN 2022 (Volkert et al., Clin Nutr 41:958) : en surpoids après 65 ans, les régimes amaigrissants sont à éviter ;
 *   en obésité, restriction seulement modérée pour une perte lente qui préserve le muscle ; protéines ≥ 1 g/kg/j.
 * - EASO 2025 (Di Vincenzo et al., Obes Facts, doi 10.1159/000549751) : ≈ 500 kcal/j sous les besoins, protéines
 *   1,0–1,2 g/kg de poids ajusté (idéal + 25 % de l'excès), ≥ 25 g à chacun des trois repas.
 * - ASN/TOS 2005 (Villareal et al., Obes Res 13:1849) : réduction de 500–750 kcal/j chez l'adulte âgé obèse.
 * - Apport minimal 1 000–1 200 kcal/j chez la personne âgée obèse (revue, Clin Interv Aging / PMC9602317) : 1 200 retenu.
 * - Winter et al. 2014 (Am J Clin Nutr 99:875, 32 études, ≥ 65 ans) : mortalité plus haute sous un IMC de 23.
 * - GLIM 2019 (Cederholm et al.) : perte involontaire > 5 % en 6 mois = critère de dénutrition.
 *
 * Tranche 60–64 ans : pas de recommandation gériatrique propre (elles commencent à 65 ans) ; on y applique le haut de la
 * fourchette ASN/TOS (750 kcal/j) comme palier vers les règles des 65 ans et plus.
 */
object Senior {
    enum class Band { ADULT, TRANSITION, OLDER }

    fun band(age: Int?): Band = when {
        age == null || age < 60 -> Band.ADULT
        age < 65 -> Band.TRANSITION
        else -> Band.OLDER
    }

    /** Déficit maximal sous la dépense mesurée (kcal/j) ; null = pas de limite liée à l'âge. */
    fun maxDeficit(band: Band): Double? = when (band) { Band.ADULT -> null; Band.TRANSITION -> 750.0; Band.OLDER -> 500.0 }

    /** IMC minimal pour proposer une perte de poids (65 ans et plus : obésité seulement, ESPEN 2022). */
    const val MIN_BMI_TO_LOSE = 30.0
    /** IMC sous lequel on ne fait plus perdre (65 ans et plus, Winter 2014). */
    const val FLOOR_BMI = 23.0
    /** Apport minimal en perte de poids à 65 ans et plus. */
    const val MIN_INTAKE = 1200.0
    /** Protéines par repas à 65 ans et plus (EASO 2025). */
    const val PROTEIN_PER_MEAL = 25.0

    fun bmi(kg: Double, heightCm: Double) = kg / (heightCm / 100).let { it * it }
    fun weightAtBmi(bmi: Double, heightCm: Double) = bmi * (heightCm / 100).let { it * it }

    /** Poids idéal de Devine (1974), puis poids ajusté = idéal + 25 % de l'excès (EASO 2025). */
    fun idealKg(heightCm: Double, female: Boolean) = (if (female) 45.5 else 50.0) + 0.91 * (heightCm - 152.4)
    fun adjustedKg(kg: Double, heightCm: Double, female: Boolean): Double {
        val ideal = idealKg(heightCm, female)
        return if (kg > ideal) ideal + 0.25 * (kg - ideal) else kg
    }

    /** Plancher protéique : 1,0 g/kg de poids ajusté de 60 à 64 ans et au maintien, 1,2 en perte de poids après 65 ans. */
    fun proteinFloorG(age: Int?, kg: Double, heightCm: Double, female: Boolean, cutting: Boolean): Double = when (band(age)) {
        Band.ADULT -> 0.0
        Band.TRANSITION -> 1.0 * adjustedKg(kg, heightCm, female)
        Band.OLDER -> (if (cutting) 1.2 else 1.0) * adjustedKg(kg, heightCm, female)
    }

    /** Pourquoi la cible a été retenue par l'âge. */
    enum class Reason { CAPPED, NOT_OBESE }

    /**
     * Calories du jour en perte de poids, bornées par l'âge : [raw] = dépense + écart visé. Renvoie les calories retenues
     * et la raison (null si l'âge ne change rien).
     */
    fun limitCut(age: Int?, heightCm: Double, trendKg: Double, tdee: Double, raw: Double): Pair<Double, Reason?> {
        val b = band(age)
        if (b == Band.ADULT || raw >= tdee) return raw to null
        if (b == Band.OLDER) {
            if (bmi(trendKg, heightCm) < MIN_BMI_TO_LOSE) return tdee to Reason.NOT_OBESE
        }
        val cap = maxDeficit(b)!!
        var kcal = max(raw, tdee - cap)
        if (b == Band.OLDER) kcal = max(kcal, min(MIN_INTAKE, tdee))
        return kcal to (if (kcal > raw) Reason.CAPPED else null)
    }

    /** Rythme de perte maximal (% du poids / semaine) qu'autorise le déficit de la tranche ; null = pas de limite. */
    fun maxCutRatePct(age: Int?, trendKg: Double): Double? =
        maxDeficit(band(age))?.let { it * 7 / Targets.KCAL_PER_KG / trendKg * 100 }

    /** Perte de poids proposée ? (65 ans et plus : seulement en obésité, et au-dessus d'un IMC de 23 visé). */
    fun cutAllowed(age: Int?, trendKg: Double, heightCm: Double): Boolean =
        band(age) != Band.OLDER || bmi(trendKg, heightCm) >= MIN_BMI_TO_LOSE

    /** Poids cible minimal en perte (65 ans et plus : IMC 23) ; null = pas de limite. */
    fun minTargetKg(age: Int?, heightCm: Double): Double? = if (band(age) == Band.OLDER) weightAtBmi(FLOOR_BMI, heightCm) else null

    /**
     * Perte de poids involontaire (GLIM 2019) : hors objectif de perte, tendance en baisse de plus de 5 % sur 6 mois.
     * Renvoie la perte en % ou null. Utile dès 60 ans : la dénutrition passe souvent inaperçue.
     */
    fun unintendedLossPct(age: Int?, goal: GoalType?, trendByDay: Map<Long, Double>, today: Long): Double? {
        if (band(age) == Band.ADULT || goal == GoalType.CUT) return null
        val now = trendByDay.filterKeys { it <= today }.maxByOrNull { it.key }?.value ?: return null
        val before = trendByDay.filterKeys { it <= today - 182 }.maxByOrNull { it.key }?.takeIf { it.key >= today - 200 }?.value ?: return null
        val pct = (before - now) / before * 100
        return pct.takeIf { it > 5.0 }
    }
}
