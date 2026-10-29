package app.superhealth.nutrition.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Cibles caloriques et macros (MacroFactor-analyse/notes/15 §2).
 * Énergie : dépense retenue au check-in + taux visé × poids × 7700 / 7 (constantes du sélecteur de taux), bornée par le plancher.
 * Protéines : table observée (g/kg de masse maigre) selon objectif × entraînement × préférence.
 * Lipides / glucides : règles des 4 types de diète (0xaa24e0, 0xaa2700, 0xaa2920, 0xaa2b50).
 */
object Targets {

    data class DailyTarget(val macros: Macros, val tdeeUsed: Double, val limitedByFloor: Boolean, val checkInDay: Long?)

    const val KCAL_PER_KG = 7700.0
    /** Taux autorisés par le sélecteur (%/semaine). */
    const val MIN_RATE = 0.05
    const val MAX_RATE = 1.5

    private val PROTEIN_MAINT = mapOf(
        Training.NONE to doubleArrayOf(1.25, 1.6, 1.95, 2.3),
        Training.CARDIO to doubleArrayOf(1.5, 1.9, 2.3, 2.7),
        Training.STRENGTH to doubleArrayOf(1.75, 2.35, 2.75, 3.1),
        Training.BOTH to doubleArrayOf(1.75, 2.35, 2.75, 3.1),
    )
    private val PROTEIN_CUT = mapOf(
        Training.NONE to doubleArrayOf(1.4, 1.7, 2.0, 2.3),
        Training.CARDIO to doubleArrayOf(1.8, 2.1, 2.4, 2.7),
        Training.STRENGTH to doubleArrayOf(2.05, 2.5, 3.0, 3.5),
        Training.BOTH to doubleArrayOf(2.05, 2.5, 3.0, 3.5),
    )

    /** g de protéines par kg de masse maigre (table de 0xa7d224 ; la prise reprend les valeurs du maintien). */
    fun proteinPerKgLbm(goal: GoalType, training: Training, pref: ProteinPreference): Double =
        (if (goal == GoalType.CUT) PROTEIN_CUT else PROTEIN_MAINT).getValue(training)[pref.ordinal]

    /** Variation de poids visée en kg/semaine (négatif en perte). */
    fun weeklyRateKg(goal: Goal?, trendKg: Double): Double = when (goal?.type) {
        GoalType.CUT -> -goal.ratePctPerWeek / 100 * trendKg
        GoalType.BULK -> goal.ratePctPerWeek / 100 * trendKg
        GoalType.MAINTENANCE -> maintenanceCorrection(goal, trendKg)
        null -> 0.0
    }

    /** Demi-largeur de la plage de maintien : 1,5 lb (0xb56bbc, 0xaa7cc4). */
    const val MAINTENANCE_BAND_KG = 0.680389
    /** Taux de retour par défaut d'un objectif de maintien (%/sem, 0xaa7cc4 / 0x13c0bdc). */
    const val MAINTENANCE_RATE = 0.15

    /** Approche du maintien dynamique (enum MaintenanceApproach : loss / keep / gain, 0xb56aa4). */
    fun maintenanceApproach(goal: Goal, trendKg: Double): Int {
        val target = goal.targetWeightKg ?: return 0
        return when {
            trendKg > target + MAINTENANCE_BAND_KG -> -1
            target - MAINTENANCE_BAND_KG > trendKg -> 1
            else -> 0
        }
    }

    /**
     * Maintien dynamique (0xaa7cc4) : hors de la plage cible ± 0,68 kg, retour lent au taux de l'objectif
     * (0,15 %/sem par défaut) ; dans la plage, apport = dépense.
     */
    private fun maintenanceCorrection(goal: Goal, trendKg: Double): Double {
        val rate = if (goal.ratePctPerWeek == 0.0) MAINTENANCE_RATE else goal.ratePctPerWeek
        return maintenanceApproach(goal, trendKg) * rate / 100 * trendKg
    }

    fun dailyEnergyDelta(weeklyRateKg: Double) = weeklyRateKg * KCAL_PER_KG / 7.0

    /** Dernier jour de check-in ≤ date (inclus). */
    fun lastCheckIn(date: LocalDate, checkInDay: Int): LocalDate {
        val dow = DayOfWeek.of(checkInDay.coerceIn(1, 7))
        var d = date
        while (d.dayOfWeek != dow) d = d.minusDays(1)
        return d
    }

    // ----- Minimums et plancher dynamique (0x13c61b0) -----

    /** Arrondi Dart (demi-unité vers l'extérieur). */
    private fun dartRound(x: Double) = Math.signum(x) * floor(abs(x) + 0.5)

    /** Lipides minimum (g) selon la taille (0xab4a9c) : max(30, ½·(taille − 150) + 30). */
    fun minFatG(heightCm: Double) = max(30.0, dartRound((heightCm - 150) * 0.5 + 30))
    /** Glucides minimum (g) selon la taille (0xab490c) : 0,6·taille − 60. */
    fun minCarbsG(heightCm: Double) = dartRound(0.6 * heightCm - 60)
    /** Protéines minimum (g) (0xab4bf8) : 1,4 g/kg de masse maigre, masse maigre plafonnée à 80 kg. */
    fun minProteinG(lbmKg: Double) = dartRound(min(lbmKg, 80.0) * 1.4)
    /** Glucides du cétogène : glucides minimum, au plus 80 g (0xaa2c68). */
    fun ketoCarbsG(heightCm: Double) = min(max(minCarbsG(heightCm), 0.0), 80.0)

    fun minimumKcal(heightCm: Double, lbmKg: Double) = 9 * minFatG(heightCm) + 4 * minProteinG(lbmKg) + 4 * max(0.0, minCarbsG(heightCm))

    /**
     * Plancher dynamique (0x13c61b0) : standard = max(1200, 1,5 × kcal des minimums) ; bas = max(800, kcal des minimums) ;
     * aucun = pas de plancher sur la moyenne (la répartition par jour garde le plancher bas, 0x13c54cc).
     */
    fun dynamicFloor(floor: CalorieFloor, heightCm: Double, lbmKg: Double): Double = when (floor) {
        CalorieFloor.STANDARD -> max(1200.0, 1.5 * minimumKcal(heightCm, lbmKg))
        CalorieFloor.LOW -> max(800.0, minimumKcal(heightCm, lbmKg))
        CalorieFloor.NONE -> 0.0
    }

    /** Répartition lipides / glucides (kcal/jour) selon le type de diète ; minimums en grammes. */
    fun split(energy: Double, proteinKcal: Double, diet: DietPreference, minFatG: Double = 0.0, ketoCarbsG: Double = 45.0): Macros {
        val minFatKcal = minFatG * 9
        var p = proteinKcal
        val rest = energy - p
        val fat: Double; val carbs: Double
        when (diet) {
            DietPreference.BALANCED, DietPreference.LOW_FAT -> {
                val share = if (diet == DietPreference.BALANCED) 0.30 else 0.20
                fat = max(min(share * energy, rest), minFatKcal); carbs = rest - fat
            }
            DietPreference.LOW_CARB -> {
                var f = max(0.40 * energy, minFatKcal); var c = rest - f
                if (c > 5572) { f += c - 5572; c = 5572.0 }
                fat = f; carbs = c
            }
            DietPreference.KETO -> {
                val c = ketoCarbsG * 4
                var f = rest - c
                if (f < minFatKcal) { p -= (minFatKcal - f); f = minFatKcal }
                fat = f; carbs = c
            }
        }
        return Macros(energy, max(0.0, p) / 4, max(0.0, fat) / 9, max(0.0, carbs) / 4)
    }

    /**
     * Macros d'une journée. Quand protéines + lipides minimum ne laissent plus les glucides minimum, MacroFactor bascule en
     * cétogène (« we had to switch you over to a ketogenic program ») [INFÉRÉ : seuil exact de bascule].
     */
    fun macrosFor(kcal: Double, program: Program, goalType: GoalType, lbmKg: Double, heightCm: Double = 175.0, minProteinG: Double = 0.0): Macros {
        val proteinG = max(proteinPerKgLbm(goalType, program.training, program.proteinPreference) * lbmKg, minProteinG)
        val proteinKcal = min(proteinG * 4, kcal)
        val keto = ketoCarbsG(heightCm)
        val m = split(kcal, proteinKcal, program.dietPreference, minFatG(heightCm), keto)
        if (program.dietPreference != DietPreference.KETO && m.carbs < keto)
            return split(kcal, proteinKcal, DietPreference.KETO, minFatG(heightCm), keto)
        return m
    }

    // ----- Répartition des calories sur la semaine (CalorieDistributionStep, 0x13c54cc) -----

    data class Distribution(val kcal: List<Double>, val shifted: Boolean, val limitedByFloor: Boolean)

    /**
     * Calories par jour (lundi → dimanche) pour une moyenne [avg] et des jours « hauts » [highDays] (1 = lundi).
     * Poids d'un jour haut : 115 (1 jour), 110 (2 jours), 105 (3 jours et plus) ; jour normal = 100 ; moyenne conservée.
     * Si le jour normal passe sous le plancher, l'excédent au-dessus du plancher va aux jours hauts (≤ 1,1 × moyenne).
     * Pas de décalage si aucun jour choisi ou si le budget au-dessus du plancher ≤ 10 kcal/semaine.
     */
    fun distribute(avg: Double, floorKcal: Double, highDays: Set<Int>): Distribution {
        val a = dartRound(avg); val f = dartRound(floorKcal)
        val budget = (a - f) * 7
        if (budget <= 0) return Distribution(List(7) { max(f, a) }, shifted = false, limitedByFloor = true)
        val n = highDays.count { it in 1..7 }; val m = 7 - n
        if (n == 0 || budget <= 10) return Distribution(List(7) { a }, shifted = false, limitedByFloor = false)
        val w = when (n) { 1 -> 115.0; 2 -> 110.0; else -> 105.0 }
        val base = a / (w * n + 100.0 * m) * 7
        var normal = dartRound(base * 100); var high = dartRound(base * w)
        if (normal < f) {
            high = min(dartRound(a * 1.1), f + dartRound(budget / n))
            normal = if (m == 0) dartRound(high / 1.1) else dartRound((a * 7 - high * n) / m)
        }
        val lo = max(f, normal); val hi = max(f, high)
        return Distribution((1..7).map { if (it in highDays) hi else lo }, shifted = true, limitedByFloor = false)
    }

    /** Calcule un check-in (mise à jour hebdomadaire du programme coaché). */
    fun checkIn(day: Long, tdee: Double, trendKg: Double, lbmKg: Double, program: Program, goal: Goal?, heightCm: Double = 175.0,
                ageYears: Int? = null, female: Boolean = false): CheckIn {
        // à partir de 60 ans : protéines sur le poids ajusté, déficit borné (Senior)
        val minP = Senior.proteinFloorG(ageYears, trendKg, heightCm, female, goal?.type == GoalType.CUT)
        val type = goal?.type ?: GoalType.MAINTENANCE
        if (program.style == ProgramStyle.MANUAL) {
            return CheckIn(day, tdee, trendKg, program.manualKcal, program.manualProtein, program.manualFat, program.manualCarbs,
                type, goal?.ratePctPerWeek ?: 0.0, manual = true)
        }
        val wanted = tdee + dailyEnergyDelta(weeklyRateKg(goal, trendKg))
        val (raw, ageLimit) = if (type == GoalType.CUT) Senior.limitCut(ageYears, heightCm, trendKg, tdee, wanted) else wanted to null
        val floorKcal = dynamicFloor(program.calorieFloor, heightCm, lbmKg)
        val kcal = max(raw, floorKcal)
        val m = macrosFor(kcal, program, type, lbmKg, heightCm, minP)
        val dist = distribute(kcal, max(floorKcal, dynamicFloor(CalorieFloor.LOW, heightCm, lbmKg)), program.highDays)
        val days = if (!dist.shifted) emptyList() else dist.kcal.map { k ->
            macrosFor(k, program, type, lbmKg, heightCm, minP).let { DayTarget(k, it.protein, it.fat, it.carbs) }
        }
        return CheckIn(day, tdee, trendKg, kcal, m.protein, m.fat, m.carbs, type, goal?.ratePctPerWeek ?: 0.0, raw < floorKcal,
            days = days, floorKcal = floorKcal, ageLimit = ageLimit?.name)
    }

    // ----- Objectifs : lien taux ↔ date de fin -----

    /** Semaines nécessaires pour aller de [fromKg] à [toKg] au taux donné (% du poids courant / semaine, composé). */
    fun weeksToGoal(fromKg: Double, toKg: Double, ratePct: Double): Double? {
        if (ratePct <= 0 || fromKg <= 0 || toKg <= 0) return null
        val r = ratePct / 100
        val ratio = toKg / fromKg
        if (abs(ratio - 1) < 1e-9) return 0.0
        val weekly = if (toKg < fromKg) 1 - r else 1 + r
        val w = Math.log(ratio) / Math.log(weekly)
        return if (w.isFinite() && w >= 0) w else null
    }

    fun endDayFromRate(startDay: Long, fromKg: Double, toKg: Double, ratePct: Double): Long? =
        weeksToGoal(fromKg, toKg, ratePct)?.let { startDay + ceil(it * 7).toLong() }

    /** Taux (%/sem) nécessaire pour atteindre [toKg] à [endDay] ; borné à [MIN_RATE, MAX_RATE]. */
    fun rateFromEndDay(today: Long, fromKg: Double, toKg: Double, endDay: Long): Double? {
        val weeks = (endDay - today) / 7.0
        if (weeks <= 0 || fromKg <= 0 || toKg <= 0) return null
        val r = Math.abs(Math.pow(toKg / fromKg, 1 / weeks) - 1) * 100
        return r
    }
}
