package app.superhealth.nutrition.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Métabolisme de base. Constantes et flot de calcul relevés dans libapp.so 5.8.9
 * (notes/09 §3.1 : corps 0x12bef4c et 0x12bf110 ; Cunningham dans l'initialisation de la dépense).
 */
object Bmr {

    fun leanMass(weightKg: Double, bodyFatPct: Double) = weightKg * (1 - bodyFatPct / 100.0)
    fun fatMass(weightKg: Double, bodyFatPct: Double) = weightKg * bodyFatPct / 100.0

    /** Facteur d'adaptation : 0.92 / 0.95 / 0.97 / 1 selon perte en cours et historique de poids. */
    fun adaptationFactor(change: WeightChange, lifetime: LifetimeWeight): Double {
        val losing = change == WeightChange.LOSING
        val higher = lifetime == LifetimeWeight.HIGHER
        return when {
            losing && higher -> 0.92
            losing -> 0.95
            higher -> 0.97
            else -> 1.0
        }
    }

    fun equationA(lbm: Double, factor: Double) = 40.4 * lbm.pow(0.932) * factor

    fun equationB(lbm: Double, fm: Double, ageYears: Int, factor: Double): Double {
        val lbmTerm = lbm.pow(0.7)
        val agePenalty = if (ageYears > 60) 2.75 * (ageYears - 60) else 0.0
        return (50.2 * lbmTerm + 40.5 * lbmTerm * max(fm, 0.0).pow(0.066) - 1.1 * ageYears - agePenalty) * factor
    }

    /** Cunningham ; masse maigre plafonnée à 100 kg (observé dans 0x12c0dac / 0x12c6584). */
    fun cunningham(lbm: Double) = 500 + 22 * min(lbm, 100.0)

    /** % masse grasse par défaut quand aucun n'est connu : 25 % (homme) / 35 % (femme) (fournisseur 0x13c41b0). */
    fun defaultBodyFat(sex: Sex) = if (sex == Sex.MALE) 25.0 else 35.0

    /**
     * CUN-BAE (Gómez-Ambrosi et al. 2012, Diabetes Care 35:383) : % de masse grasse estimé par l'IMC, l'âge et le sexe ;
     * validé contre la pléthysmographie chez 6 510 adultes de 18 à 80 ans, IMC normal à obésité sévère (r = 0,89).
     */
    fun cunBae(sex: Sex, ageYears: Int, bmi: Double): Double {
        val f = if (sex == Sex.FEMALE) 1.0 else 0.0
        val a = ageYears.toDouble()
        return -44.988 + 0.503 * a + 10.689 * f + 3.172 * bmi - 0.026 * bmi * bmi + 0.181 * bmi * f - 0.02 * bmi * a -
            0.005 * bmi * bmi * f + 0.00021 * bmi * bmi * a
    }

    /**
     * % masse grasse sans mesure (règle SuperHealth) : le défaut de MacroFactor (25 / 35 %), relevé par CUN-BAE quand
     * l'IMC le dit plus haut. En obésité sévère, 25 % surestimait la masse maigre de 30 à 40 kg, donc les protéines.
     */
    fun estimatedBodyFat(sex: Sex, ageYears: Int?, weightKg: Double?, heightCm: Double?): Double {
        if (ageYears == null || weightKg == null || heightCm == null || heightCm < 100) return defaultBodyFat(sex)
        val bmi = weightKg / (heightCm / 100).let { it * it }
        return max(defaultBodyFat(sex), min(cunBae(sex, ageYears.coerceIn(18, 90), bmi), 60.0))
    }

    /**
     * Mifflin-St Jeor (1990). L'équation la mieux validée en obésité, sévère comprise (revue systématique de Madden et al.
     * 2016, J Hum Nutr Diet 29:458 ; 4 247 patients d'IMC moyen 44 chez Bedogni et al. 2018, Front Endocrinol 9:367).
     */
    fun mifflin(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int) =
        10 * weightKg + 6.25 * heightCm - 5 * ageYears + if (sex == Sex.MALE) 5 else -161

    /** IMC à partir duquel, sans masse grasse mesurée, le métabolisme automatique passe à Mifflin-St Jeor. */
    const val MIFFLIN_BMI = 30.0

    /** Masse maigre (0x13c4010) ; 65 kg sans aucun poids. Sans masse grasse connue : [estimatedBodyFat]. */
    fun leanMassOrDefault(weightKg: Double?, bodyFatPct: Double?, sex: Sex, heightCm: Double? = null, ageYears: Int? = null) =
        if (weightKg == null) 65.0 else leanMass(weightKg, bodyFatPct ?: estimatedBodyFat(sex, ageYears, weightKg, heightCm))

    fun bmr(profile: Profile, weightKg: Double, bodyFatPct: Double?, ageYears: Int): Double {
        val measured = bodyFatPct ?: profile.bodyFatPct
        val bf = measured ?: estimatedBodyFat(profile.sex, ageYears, weightKg, profile.heightCm)
        val lbm = leanMass(weightKg, bf)
        val factor = adaptationFactor(profile.weightChange, profile.lifetimeWeight)
        // règle SuperHealth : en obésité sans masse grasse mesurée, les équations à masse maigre reposent sur une estimation ;
        // Mifflin-St Jeor n'en a pas besoin et reste la plus juste dans cette population
        val bmi = if (profile.heightCm >= 100) weightKg / (profile.heightCm / 100).let { it * it } else 0.0
        if (profile.bmrEquation == BmrEquation.MACROFACTOR_B && measured == null && bmi >= MIFFLIN_BMI)
            return mifflin(profile.sex, weightKg, profile.heightCm, ageYears) * factor
        val a = equationA(lbm, factor)
        val b = equationB(lbm, fatMass(weightKg, bf), ageYears, factor)
        return when (profile.bmrEquation) {
            BmrEquation.CUNNINGHAM -> cunningham(lbm)
            BmrEquation.MACROFACTOR_A -> a
            // Sélection observée (0x12bee54) selon AthleteOptions.
            BmrEquation.MACROFACTOR_B -> when (profile.athlete) {
                Athlete.NONE -> b
                Athlete.RECREATIONAL -> (a + b) / 2
                Athlete.SPORTING, Athlete.ATHLETE -> a
            }
        }
    }

    /** Dépense initiale (0x12c0dac) : BMR × (facteur d'activité + bonus d'exercice). */
    fun initialTdee(profile: Profile, weightKg: Double, bodyFatPct: Double?, ageYears: Int) =
        bmr(profile, weightKg, bodyFatPct, ageYears) * (profile.activity.factor + profile.exercise.bonus)
}
