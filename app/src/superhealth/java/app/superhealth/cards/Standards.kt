package app.superhealth.cards

import app.superhealth.nutrition.domain.Sex
import app.superhealth.training.domain.Exercise
import kotlin.math.pow

/**
 * Repères de force des cartes Athlète. Les tables publiques (Strength Level, Symmetric Strength, FitnessVolt) divisent
 * le 1RM par le poids de corps et placent ce rapport sur cinq niveaux, sans s'accorder sur les seuils. On garde la même
 * forme : un rapport de référence par niveau à 80 kg (hommes) et 60 kg (femmes), moyenne des seuils publiés, et une mise
 * à l'échelle allométrique (force ∝ poids^0,67, donc rapport ∝ poids^-0,33) pour les autres gabarits.
 *
 * Niveaux : débutant (≈ 5ᵉ centile), novice (20ᵉ), intermédiaire (50ᵉ), avancé (80ᵉ), élite (95ᵉ),
 * convertis en points de carte 40 / 55 / 70 / 83 / 93.
 */
/** Hors de l'objet : l'énumération [Standards.Lift] s'initialise sans dépendre de [Standards] (sinon initialisation circulaire). */
private fun d(vararg v: Double) = v

object Standards {
    enum class Lift(val male: DoubleArray, val female: DoubleArray, val bodyweightLoad: Boolean = false) {
        BENCH(d(.50, .75, 1.10, 1.45, 1.80), d(.28, .45, .68, .92, 1.15)),
        SQUAT(d(.75, 1.10, 1.50, 1.95, 2.45), d(.55, .85, 1.15, 1.50, 1.90)),
        DEADLIFT(d(1.00, 1.35, 1.80, 2.30, 2.80), d(.70, 1.00, 1.35, 1.75, 2.20)),
        OHP(d(.35, .50, .70, .90, 1.15), d(.20, .32, .45, .60, .78)),
        ROW(d(.45, .65, .90, 1.20, 1.50), d(.28, .42, .60, .80, 1.00)),
        /** Charge totale (poids de corps soulevé + lest) rapportée au poids de corps. */
        PULLUP(d(.85, 1.00, 1.25, 1.55, 1.85), d(.55, .75, .95, 1.20, 1.45), bodyweightLoad = true),
        DIP(d(.90, 1.10, 1.40, 1.70, 2.05), d(.60, .80, 1.05, 1.30, 1.60), bodyweightLoad = true),
        CURL(d(.25, .40, .55, .75, .95), d(.15, .25, .35, .48, .62)),
        TRICEPS(d(.20, .33, .48, .64, .82), d(.12, .20, .30, .40, .52)),
        HIP_THRUST(d(.75, 1.25, 1.75, 2.40, 3.00), d(.70, 1.10, 1.60, 2.20, 2.80)),
        LEG_PRESS(d(1.25, 2.00, 2.75, 3.60, 4.50), d(1.00, 1.60, 2.25, 3.00, 3.80)),
    }

    val levelPoints = doubleArrayOf(40.0, 55.0, 70.0, 83.0, 93.0)

    /** Exercice de la base → mouvement de référence et rapport de son 1RM à celui du mouvement. */
    data class Match(val lift: Lift, val coef: Double)

    private val rules: List<Pair<Regex, Match>> = listOf(
        r("^(barbell|smith machine) bench press$") to Match(Lift.BENCH, 1.0),
        r("^barbell (floor|close.grip bench) press") to Match(Lift.BENCH, .90),
        r("^incline (barbell|smith machine) bench press") to Match(Lift.BENCH, .82),
        r("^(machine|seated machine) chest press") to Match(Lift.BENCH, .90),
        r("^dumbbell bench press$") to Match(Lift.BENCH, .40),
        r("^incline dumbbell (bench )?press") to Match(Lift.BENCH, .34),
        r("^(weighted )?(chest |triceps |ring )?dips?$") to Match(Lift.DIP, 1.0),
        r("^barbell (back )?squat( to a box)?$|^barbell box squat$|^smith machine squat") to Match(Lift.SQUAT, 1.0),
        r("^front squat$|^barbell front squat") to Match(Lift.SQUAT, .82),
        r("^hack squat") to Match(Lift.SQUAT, 1.10),
        r("^(45.degree )?leg press") to Match(Lift.LEG_PRESS, 1.0),
        r("^(conventional|sumo|trap bar|barbell) deadlift$") to Match(Lift.DEADLIFT, 1.0),
        r("^(barbell|trap bar) romanian deadlift$") to Match(Lift.DEADLIFT, .72),
        r("^barbell hip thrust$") to Match(Lift.HIP_THRUST, 1.0),
        r("^barbell (overhead|military) press$|^standing barbell overhead press") to Match(Lift.OHP, 1.0),
        r("^barbell push press$") to Match(Lift.OHP, 1.20),
        r("^(seated )?dumbbell (shoulder|overhead) press") to Match(Lift.OHP, .40),
        r("^machine shoulder press") to Match(Lift.OHP, .90),
        r("^(pendlay|barbell bent.over|barbell) row$|^chest-supported (neutral|overhand) grip t-bar row$") to Match(Lift.ROW, 1.0),
        r("^seated cable row") to Match(Lift.ROW, 1.0),
        r("^(one.arm |single.arm )?dumbbell row$") to Match(Lift.ROW, .45),
        r("^(weighted )?(pull-up|pull up|chin-up|chin up)s?$") to Match(Lift.PULLUP, 1.0),
        r("^lat pulldown") to Match(Lift.PULLUP, .82),
        r("^barbell biceps curl$|^barbell curl$") to Match(Lift.CURL, 1.0),
        r("^ez bar biceps curl$") to Match(Lift.CURL, .95),
        r("^(standing )?dumbbell (biceps )?curl$") to Match(Lift.CURL, .42),
        r("^(barbell|ez bar) skull crusher$") to Match(Lift.TRICEPS, 1.0),
        r("^cable triceps pushdown|^triceps pushdown") to Match(Lift.TRICEPS, 1.15),
    )

    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    fun match(exercise: Exercise): Match? = exercise.name.trim().let { n -> rules.firstOrNull { it.first.containsMatchIn(n) }?.second }

    /** Rapport attendu à chaque niveau pour ce poids de corps. */
    fun thresholds(lift: Lift, sex: Sex, bodyweight: Double): DoubleArray {
        val female = sex == Sex.FEMALE
        val ref = if (female) 60.0 else 80.0
        val scale = (bodyweight.coerceIn(40.0, 160.0) / ref).pow(-0.33)
        return (if (female) lift.female else lift.male).map { it * scale }.toDoubleArray()
    }

    /** Points de carte (25..99) d'un rapport 1RM / poids de corps. Au-delà de l'élite : +6 points par écart avancé → élite. */
    fun points(ratio: Double, levels: DoubleArray): Double {
        if (!ratio.isFinite() || ratio <= 0) return 25.0
        if (ratio < levels[0]) return 25.0 + 15.0 * ratio / levels[0]
        for (i in 0 until levels.size - 1) if (ratio < levels[i + 1]) {
            val f = (ratio - levels[i]) / (levels[i + 1] - levels[i])
            return levelPoints[i] + f * (levelPoints[i + 1] - levelPoints[i])
        }
        val gap = levels[4] - levels[3]
        return (93.0 + 6.0 * (ratio - levels[4]) / gap).coerceAtMost(99.0)
    }
}

/**
 * Normes de VO2max (mL/kg/min) de la Cooper Institute, reprises par les manuels Garmin : seuils des centiles 40, 60, 80
 * et 95 par décennie de 20 à 79 ans. Converties en points 63 / 73 / 83 / 93, 40 au 5ᵉ centile estimé.
 */
object Vo2Norms {
    private val men = arrayOf(
        d(41.7, 45.4, 51.1, 55.4), d(40.5, 44.0, 48.3, 54.0), d(38.5, 42.4, 46.4, 52.5),
        d(35.6, 39.2, 43.4, 48.9), d(32.3, 35.5, 39.5, 45.7), d(29.4, 32.3, 36.7, 42.1))
    private val women = arrayOf(
        d(36.1, 39.5, 43.9, 49.6), d(34.4, 37.8, 42.4, 47.4), d(33.0, 36.3, 39.7, 45.3),
        d(30.1, 33.0, 36.7, 41.1), d(27.5, 30.0, 33.0, 37.8), d(25.9, 28.1, 30.9, 36.7))
    private val points = doubleArrayOf(63.0, 73.0, 83.0, 93.0)
    private fun d(vararg v: Double) = v

    fun points(vo2: Double, sex: Sex, age: Int): Double {
        val row = (if (sex == Sex.FEMALE) women else men)[((age - 20) / 10).coerceIn(0, 5)]
        if (vo2 < row[0]) {
            val low = row[0] * 0.72
            return (40.0 + 23.0 * ((vo2 - low) / (row[0] - low))).coerceIn(25.0, 63.0)
        }
        for (i in 0 until 3) if (vo2 < row[i + 1]) return points[i] + (vo2 - row[i]) / (row[i + 1] - row[i]) * (points[i + 1] - points[i])
        return (93.0 + 6.0 * (vo2 - row[3]) / (row[3] - row[2])).coerceAtMost(99.0)
    }
}
