package app.superhealth.nutrition.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Âge et poids (règle SuperHealth, pas MacroFactor) : quatre tranches, de plus en plus prudentes. Chaque tranche fixe la
 * zone d'IMC visée, l'IMC à partir duquel une perte est proposée, le déficit maximal, la perte maximale par objectif,
 * l'IMC plancher de la cible et le plancher protéique.
 *
 * | tranche   | zone IMC   | perte dès | déficit max | perte max / objectif | cible ≥ IMC | protéines (poids ajusté) |
 * |-----------|------------|-----------|-------------|----------------------|-------------|--------------------------|
 * | < 60      | 18,5–24,9  | 18,5      | —           | (10 % suggérés)      | 18,5        | —                        |
 * | 60–64     | 20–24,9    | 25        | 750 kcal/j  | 10 %                 | 20          | 1,0 g/kg                 |
 * | 65–74     | 23–29,9    | 30        | 500 kcal/j  | 10 %                 | 23          | 1,0 (1,2 en perte)       |
 * | 75 et +   | 23–34,9    | 35        | 500 kcal/j  | 5 %                  | 23          | 1,0 (1,2 en perte)       |
 *
 * En perte à partir de 65 ans, l'apport ne descend pas sous 1 200 kcal/j.
 *
 * Sources :
 * - ESPEN 2022 (Volkert et al., Clin Nutr 41:958), recommandations 79–82 : en surpoids après 65 ans, éviter les régimes
 *   amaigrissants (le poids stable est souhaitable, la mortalité est la plus basse en surpoids) ; en obésité, seulement
 *   après pesée individuelle, restriction modérée ≈ 500 kcal/j sous les besoins, apport ≥ 1 000–1 200 kcal/j,
 *   0,25–1 kg/semaine, ≈ 5–10 % du poids initial en 6 mois ou plus, protéines ≥ 1 g/kg, avec de l'exercice. Les essais
 *   portent surtout sur les 60–70 ans ; les très âgés et fragiles demandent une prudence particulière.
 * - EASO 2025 (Di Vincenzo et al., Obes Facts, doi 10.1159/000549751) : ≈ 500 kcal/j, protéines 1,0–1,2 g/kg de poids
 *   ajusté (idéal + 25 % de l'excès), ≥ 25 g à chacun des trois repas.
 * - ASN/TOS 2005 (Villareal et al., Obes Res 13:1849) : 500–750 kcal/j chez l'adulte âgé obèse.
 * - PROT-AGE 2013 (Bauer et al., JAMDA 14:542) : 1,0–1,2 g/kg/j après 65 ans.
 * - Winter et al. 2014 (Am J Clin Nutr 99:875, 32 études, ≥ 65 ans) : mortalité plus haute sous un IMC de 23, plus basse
 *   vers 24–31.
 * - Flegal et al. 2013 (JAMA 309:71) et méta-analyse par âge (PMC12879887) : après 65 ans, mortalité minimale vers
 *   IMC 28 ; Global BMI Mortality Collaboration 2016 (Lancet 388:776) : le risque au-dessus de 25 s'atténue avec l'âge
 *   (HR 1,21 par 5 points à 70–89 ans contre 1,52 à 35–49 ans).
 * - Cheng et al. 2016 (Obesity 24:2232, âge moyen 74 ans) : surpoids et obésité de classe I (30–34,9) associés à une
 *   mortalité plus basse que l'IMC normal → à 75 ans et plus, perte proposée seulement dès l'IMC 35.
 * - GLIM 2019 (Cederholm et al., Clin Nutr 38:1) : IMC bas = < 20 avant 70 ans, < 22 à partir de 70 ans ; perte
 *   involontaire > 5 % en 6 mois = critère de dénutrition.
 * - OMS : zone 18,5–24,9 chez l'adulte ; NHLBI 1998 : premier objectif de perte = 10 % du poids de départ.
 *
 * Règles propres à SuperHealth (pas de recommandation dédiée) : la tranche 60–64 ans (perte dès le surpoids, déficit au
 * haut de la fourchette ASN/TOS, plancher IMC 20 de GLIM) et, à 75 ans et plus, l'entrée à IMC 35 et la perte limitée au
 * bas de la fourchette ESPEN (5 %).
 */
object Senior {
    enum class Band { ADULT, TRANSITION, OLDER, OLDEST }

    fun band(age: Int?): Band = when {
        age == null || age < 60 -> Band.ADULT
        age < 65 -> Band.TRANSITION
        age < 75 -> Band.OLDER
        else -> Band.OLDEST
    }

    /** Règles d'une tranche (voir le tableau ci-dessus). */
    data class Rules(
        val zone: ClosedFloatingPointRange<Double>,
        /** IMC à partir duquel une perte de poids est proposée. */
        val loseFromBmi: Double,
        /** Déficit maximal sous la dépense (kcal/j) ; null = pas de limite liée à l'âge. */
        val maxDeficit: Double?,
        /** Perte maximale par objectif (fraction du poids de départ) ; null = pas de limite. */
        val maxLoss: Double?,
        /** IMC sous lequel la cible ne descend pas. */
        val floorBmi: Double,
        /** Apport minimal en perte (kcal/j) ; null = plancher habituel. */
        val minIntake: Double?,
    )

    fun rules(band: Band): Rules = when (band) {
        Band.ADULT -> Rules(18.5..24.9, 18.5, null, null, 18.5, null)
        Band.TRANSITION -> Rules(20.0..24.9, 25.0, 750.0, 0.10, 20.0, null)
        Band.OLDER -> Rules(23.0..29.9, 30.0, 500.0, 0.10, 23.0, 1200.0)
        Band.OLDEST -> Rules(23.0..34.9, 35.0, 500.0, 0.05, 23.0, 1200.0)
    }
    fun rules(age: Int?) = rules(band(age))

    fun maxDeficit(band: Band): Double? = rules(band).maxDeficit

    /** Protéines par repas à 65 ans et plus (EASO 2025). */
    const val PROTEIN_PER_MEAL = 25.0
    /** Premier objectif de perte chez l'adulte (NHLBI 1998). */
    const val ADULT_FIRST_LOSS = 0.10

    fun bmi(kg: Double, heightCm: Double) = kg / (heightCm / 100).let { it * it }
    fun weightAtBmi(bmi: Double, heightCm: Double) = bmi * (heightCm / 100).let { it * it }

    /** Poids idéal de Devine (1974), puis poids ajusté = idéal + 25 % de l'excès (EASO 2025). */
    fun idealKg(heightCm: Double, female: Boolean) = (if (female) 45.5 else 50.0) + 0.91 * (heightCm - 152.4)
    fun adjustedKg(kg: Double, heightCm: Double, female: Boolean): Double {
        val ideal = idealKg(heightCm, female)
        return if (kg > ideal) ideal + 0.25 * (kg - ideal) else kg
    }

    /** Plancher protéique : 1,0 g/kg de poids ajusté dès 60 ans, 1,2 en perte de poids à partir de 65 ans. */
    fun proteinFloorG(age: Int?, kg: Double, heightCm: Double, female: Boolean, cutting: Boolean): Double = when (band(age)) {
        Band.ADULT -> 0.0
        Band.TRANSITION -> 1.0 * adjustedKg(kg, heightCm, female)
        Band.OLDER, Band.OLDEST -> (if (cutting) 1.2 else 1.0) * adjustedKg(kg, heightCm, female)
    }

    /** Pourquoi la cible a été retenue par l'âge (NOT_OBESE : IMC sous le seuil de perte de la tranche). */
    enum class Reason { CAPPED, NOT_OBESE }

    /**
     * Calories du jour en perte de poids, bornées par l'âge : [raw] = dépense + écart visé. Renvoie les calories retenues
     * et la raison (null si l'âge ne change rien).
     */
    fun limitCut(age: Int?, heightCm: Double, trendKg: Double, tdee: Double, raw: Double): Pair<Double, Reason?> {
        val b = band(age)
        if (raw >= tdee) return raw to null
        val r = rules(b)
        if (bmi(trendKg, heightCm) < r.loseFromBmi) return tdee to Reason.NOT_OBESE
        if (b == Band.ADULT) return raw to null
        var kcal = max(raw, tdee - r.maxDeficit!!)
        r.minIntake?.let { kcal = max(kcal, min(it, tdee)) }
        return kcal to (if (kcal > raw) Reason.CAPPED else null)
    }

    /** Rythme de perte maximal (% du poids / semaine) qu'autorise le déficit de la tranche ; null = pas de limite. */
    fun maxCutRatePct(age: Int?, trendKg: Double): Double? =
        maxDeficit(band(age))?.let { it * 7 / Targets.KCAL_PER_KG / trendKg * 100 }

    /** Perte de poids proposée ? (dès 60 ans : seulement au-dessus du seuil d'IMC de la tranche). */
    fun cutAllowed(age: Int?, trendKg: Double, heightCm: Double): Boolean = bmi(trendKg, heightCm) >= rules(age).loseFromBmi

    /** Poids cible minimal en perte : le plancher d'IMC de la tranche et, dès 60 ans, la perte maximale par objectif. */
    fun minTargetKg(age: Int?, heightCm: Double, startKg: Double): Double {
        val r = rules(age)
        return max(weightAtBmi(r.floorBmi, heightCm), r.maxLoss?.let { startKg * (1 - it) } ?: 0.0)
    }

    /**
     * Objectif conseillé selon l'IMC et l'âge : prise de poids sous un IMC bas (OMS 18,5 ; GLIM 20 avant 70 ans, 22 après,
     * dès 60 ans), maintien dès 60 ans quand la perte n'est pas proposée ; null = pas de conseil (au choix).
     */
    fun suggestedGoal(age: Int?, kg: Double, heightCm: Double): GoalType? {
        val b = bmi(kg, heightCm)
        val low = when { band(age) == Band.ADULT -> 18.5; age!! < 70 -> 20.0; else -> 22.0 }
        return when {
            b < low -> GoalType.BULK
            band(age) != Band.ADULT && !cutAllowed(age, kg, heightCm) -> GoalType.MAINTENANCE
            else -> null
        }
    }

    /**
     * Cible conseillée. Perte : la perte maximale de la tranche (10 % chez l'adulte, NHLBI), sans descendre sous le haut
     * de la zone d'IMC de l'adulte ni sous le bas de la zone des 65 ans et plus (la mortalité y est minimale vers 25–30) ;
     * sous la zone, 5 % sans passer le plancher. Prise : jusqu'au bas de la zone, sinon 5 kg. null = maintien.
     */
    fun suggestedTargetKg(age: Int?, kg: Double, heightCm: Double, goal: GoalType): Double? {
        val r = rules(age)
        return when (goal) {
            GoalType.MAINTENANCE -> null
            GoalType.BULK -> weightAtBmi(r.zone.start, heightCm).takeIf { it > kg + 1 } ?: (kg + 5)
            GoalType.CUT -> {
                val zoneEdge = weightAtBmi(if (band(age) >= Band.OLDER) 25.0 else r.zone.endInclusive, heightCm)
                val byLoss = kg * (1 - (r.maxLoss ?: ADULT_FIRST_LOSS))
                val t = max(byLoss, zoneEdge).takeIf { it < kg - 1 } ?: (kg * 0.95)
                max(t, minTargetKg(age, heightCm, kg))
            }
        }
    }

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
