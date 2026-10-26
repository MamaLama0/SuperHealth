package app.superhealth.nutrition.domain

import app.superhealth.platform.*

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

    /** Masse maigre (0x13c4010) ; 65 kg sans aucun poids. */
    fun leanMassOrDefault(weightKg: Double?, bodyFatPct: Double?, sex: Sex) =
        if (weightKg == null) 65.0 else leanMass(weightKg, bodyFatPct ?: defaultBodyFat(sex))

    fun bmr(profile: Profile, weightKg: Double, bodyFatPct: Double?, ageYears: Int): Double {
        val bf = bodyFatPct ?: profile.bodyFatPct ?: defaultBodyFat(profile.sex)
        val lbm = leanMass(weightKg, bf)
        val factor = adaptationFactor(profile.weightChange, profile.lifetimeWeight)
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
