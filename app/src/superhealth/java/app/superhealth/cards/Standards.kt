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
        // Classiques des salles, aux ordres de grandeur des mêmes tables (charge de la machine, ou d'un haltère).
        PULLDOWN(d(.50, .75, 1.00, 1.30, 1.60), d(.30, .45, .65, .85, 1.10)),
        LEG_EXTENSION(d(.50, .80, 1.15, 1.55, 2.00), d(.35, .55, .85, 1.15, 1.50)),
        LEG_CURL(d(.35, .55, .80, 1.10, 1.40), d(.25, .40, .60, .85, 1.10)),
        /** Un haltère. */
        LATERAL_RAISE(d(.08, .15, .24, .35, .47), d(.05, .09, .15, .22, .30)),
        SHRUG(d(.70, 1.05, 1.50, 2.00, 2.55), d(.40, .65, .95, 1.30, 1.70)),
        CALF_RAISE(d(.60, 1.00, 1.50, 2.10, 2.75), d(.40, .70, 1.10, 1.55, 2.05)),
        /** Écarté à la machine (pec deck). */
        FLY(d(.30, .55, .85, 1.20, 1.55), d(.15, .30, .50, .75, 1.00)),
        /** Fente ou split squat à la barre. */
        LUNGE(d(.50, .75, 1.05, 1.40, 1.75), d(.35, .55, .80, 1.05, 1.35)),
    }

    val levelPoints = doubleArrayOf(40.0, 55.0, 70.0, 83.0, 93.0)

    /**
     * Exercice de la base → mouvement de référence et rapport de son 1RM à celui du mouvement. [confidence] : 1 pour un
     * mouvement à repères publiés ou une variante proche, moins pour la charte de compatibilité (estimation grossière).
     */
    data class Match(val lift: Lift, val coef: Double, val confidence: Double = 1.0)

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
        r("^lat pulldown") to Match(Lift.PULLDOWN, 1.0),
        r("^barbell biceps curl$|^barbell curl$") to Match(Lift.CURL, 1.0),
        r("^ez bar biceps curl$") to Match(Lift.CURL, .95),
        r("^(standing )?dumbbell (biceps )?curl$") to Match(Lift.CURL, .42),
        r("^(barbell|ez bar) skull crusher$") to Match(Lift.TRICEPS, 1.0),
        r("^cable triceps pushdown|^triceps pushdown") to Match(Lift.TRICEPS, 1.15),
    )

    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val memo = java.util.concurrent.ConcurrentHashMap<String, Any>()
    fun match(exercise: Exercise): Match? = memo.getOrPut(exercise.name) {
        exercise.name.trim().let { n -> rules.firstOrNull { it.first.containsMatchIn(n) }?.second ?: generic(n) ?: compatible(n) } ?: Unit
    } as? Match

    /**
     * Les variantes de la base (« Overhand grip pull-up », « 45° leg press », « Wide grip cable lat pulldown »…) : on lit
     * le mouvement dans le nom, puis le matériel. Un haltère se note à la charge d'une main, un exercice à un bras ou à
     * une jambe sur machine ou poulie à la moitié. Les variantes trop éloignées d'un mouvement de référence sont ignorées.
     */
    private fun generic(n: String): Match? {
        val dumbbell = n.has("dumbbell|kettlebell")
        val machine = n.has("machine|leverage|pin-loaded|plate-loaded")
        val cable = n.has("cable") && !n.has("dual cable bar")
        fun kit(bar: Double, db: Double, mach: Double = bar * .9, cab: Double = bar * .85) = when { dumbbell -> db; machine -> mach; cable -> cab; else -> bar }
        val half = if (!dumbbell && n.has("single arm|one arm|single-arm|one-arm|single leg")) .5 else 1.0
        fun m(lift: Lift, coef: Double) = Match(lift, coef * half)
        // Lestés ou au poids du corps : la charge totale compte, sans aide.
        if (n.has("\\bdips?\\b") && !n.has("bench dip|assisted|machine|band")) return m(Lift.DIP, 1.0)
        if (n.has("pull-?ups?\\b|chin-?ups?\\b") && !n.has("assisted|band|negative|kipping|scapular|dead hang")) return m(Lift.PULLUP, 1.0)
        // Tirage vertical : la charge de la machine seule, ce n'est pas une traction.
        if (n.has("pulldown") && !n.has("straight arm|scapular|band")) return m(Lift.PULLDOWN, 1.0)
        if (n.has("band|assisted|cheat|calf|jefferson|nordic|wrist|upright|inverted|renegade|straight arm|sissy|pistol|skater|split squat|lunge|step-|carry|walk|" +
                "bodyweight|push-up|isometric|hold|thruster|overhead squat|good morning|pullover|landmine|reverse curl|leg curl|kickback|\\bfly\\b|raise|shrug|goblet|sumo squat|jm press")) return null
        return when {
            n.has("hack squat") -> m(Lift.SQUAT, 1.10)
            n.has("leg extension") -> m(Lift.LEG_EXTENSION, 1.0)
            n.has("belt squat") -> m(Lift.SQUAT, .90)
            n.has("front squat") -> m(Lift.SQUAT, kit(.82, .30))
            n.has("squat") && !dumbbell -> m(Lift.SQUAT, kit(1.0, 1.0, .95))
            n.has("leg press") -> m(Lift.LEG_PRESS, 1.0)
            n.has("romanian deadlift|straight-leg deadlift|stiff") -> m(Lift.DEADLIFT, kit(.72, .33, .65, .60))
            n.has("rack pull|block pull") -> m(Lift.DEADLIFT, 1.10)
            n.has("deadlift") -> m(Lift.DEADLIFT, kit(1.0, .40, .85, .70))
            n.has("hip thrust") -> m(Lift.HIP_THRUST, kit(1.0, .50, 1.0))
            n.has("push press") -> m(Lift.OHP, kit(1.20, .45))
            n.has("overhead press|military press|strict press|shoulder press|z press|arnold") -> m(Lift.OHP, kit(1.0, .40, .90, .80))
            n.has("incline") && n.has("press") -> m(Lift.BENCH, kit(.82, .34, .80, .45))
            n.has("decline") && n.has("press") -> m(Lift.BENCH, kit(.95, .40, .90, .50))
            n.has("chest press") -> m(Lift.BENCH, kit(.90, .38, .90, .50))
            n.has("floor press") -> m(Lift.BENCH, kit(.90, .36))
            n.has("bench press|close grip press|wide grip press") -> m(Lift.BENCH, kit(1.0, .40, .90, .50))
            n.has("t-bar row") -> m(Lift.ROW, .90)
            n.has("\\brow\\b") -> m(Lift.ROW, kit(1.0, .45, .95, 1.0))
            n.has("skull crusher|lying .*triceps extension") -> m(Lift.TRICEPS, kit(1.0, .40, 1.0, .90))
            n.has("pushdown") -> m(Lift.TRICEPS, 1.15)
            n.has("triceps extension") -> m(Lift.TRICEPS, kit(.90, .45, 1.0, 1.0))
            n.has("curl") -> m(Lift.CURL, kit(if (n.has("ez bar")) .95 else 1.0, .42, .85, .85) * if (n.has("preacher|scott|spider|concentration")) .90 else 1.0)
            else -> null
        }
    }

    /**
     * Charte de compatibilité : les exercices sans repères publiés (isolation, unilatéraux, accessoires) sont rattachés
     * au mouvement de référence le plus proche avec un rapport de charge typique. L'estimation est grossière : elle ne
     * pèse qu'une fraction d'un exercice à repères (0,3 à 0,6), pour compléter un muscle sans jamais le dominer.
     */
    private fun compatible(n: String): Match? {
        if (n.has("band|assisted|bodyweight|push-up|isometric|hold|static|carry|walk|plank|crunch|sit-up|jump|sled|stretch")) return null
        val dumbbell = n.has("dumbbell|kettlebell")
        val machine = n.has("machine|leverage|pin-loaded|plate-loaded|smith")
        val cable = n.has("cable")
        fun kit(bar: Double, db: Double, mach: Double = bar, cab: Double = bar) = when { dumbbell -> db; machine -> mach; cable -> cab; else -> bar }
        val half = if (!dumbbell && n.has("single arm|one arm|single-arm|one-arm|single leg")) .5 else 1.0
        fun m(lift: Lift, coef: Double, confidence: Double) = Match(lift, coef * half, confidence)
        // Classiques des salles, avec leurs propres repères : poids plein.
        if (!n.has("front raise|rear delt|reverse fly")) when {
            n.has("lateral raise") -> return m(Lift.LATERAL_RAISE, kit(1.0, 1.0, 2.2, .70), 1.0)
            n.has("leg curl") && !n.has("nordic") -> return m(Lift.LEG_CURL, 1.0, 1.0)
            n.has("shrug") && !n.has("row|romanian|kelso|shrug-in") -> return m(Lift.SHRUG, kit(1.0, .45, 1.0, .80), 1.0)
            n.has("calf") -> return m(Lift.CALF_RAISE, (if (n.has("leg press")) 1.3 else if (n.has("seated")) .7 else 1.0) * (if (dumbbell) .35 else 1.0), 1.0)
            n.has("pec deck|machine fly|cable fly|dumbbell fly|crossover") -> return m(Lift.FLY, kit(1.0, .35, 1.0, .70), 1.0)
            n.has("lunge|split squat|step-up") -> return m(Lift.LUNGE, kit(1.0, .45, 1.0, .80) * (if (n.has("step-up")) .85 else 1.0), 1.0)
        }
        return when {
            n.has("y-raise|lu raise") -> m(Lift.LATERAL_RAISE, kit(1.0, .85, 2.0, .65), .4)
            n.has("front raise") -> m(Lift.OHP, kit(.40, .22, .40, .20), .35)
            n.has("rear delt|reverse fly|face pull") -> m(Lift.ROW, kit(.35, .15, .45, .35), .35)
            n.has("upright row") -> m(Lift.OHP, kit(.80, .35, .80, .70), .4)
            n.has("\\bfly\\b|press-around") -> m(Lift.FLY, kit(1.0, .35, 1.0, .70), .45)
            n.has("jm press") -> m(Lift.BENCH, .65, .5)
            n.has("squeeze press") -> m(Lift.BENCH, .30, .4)
            n.has("landmine") && n.has("press") -> m(Lift.OHP, .55, .4)
            n.has("thruster") -> m(Lift.OHP, kit(.90, .38), .4)
            n.has("pullover|straight arm") -> m(Lift.ROW, kit(.45, .30, .55, .45), .4)
            n.has("shrug") -> m(Lift.SHRUG, kit(1.0, .45, 1.0, .80), .4)
            n.has("good morning") -> m(Lift.DEADLIFT, .45, .45)
            n.has("goblet|sumo squat|squat") -> m(Lift.SQUAT, kit(.70, .45, .80, .60), .45)
            n.has("hip abduction|hip adduction|abductor|adductor") -> m(Lift.HIP_THRUST, .40, .35)
            n.has("kickback") && !n.has("triceps") -> m(Lift.HIP_THRUST, .20, .3)
            n.has("glute bridge") -> m(Lift.HIP_THRUST, kit(.85, .45, .90), .5)
            n.has("back extension|hyperextension") -> m(Lift.DEADLIFT, .25, .3)
            n.has("kickback") -> m(Lift.TRICEPS, kit(.30, .18, .30, .30), .35)
            n.has("hammer curl|reverse curl|cheat curl|drag curl") -> m(Lift.CURL, kit(.85, .42, .80, .80), .5)
            n.has("\\brow\\b") -> m(Lift.ROW, kit(.80, .40, .85, .85), .5)
            n.has("\\bpress\\b") && n.has("shoulder|overhead") -> m(Lift.OHP, kit(.85, .38, .85, .75), .5)
            n.has("\\bpress\\b") -> m(Lift.BENCH, kit(.80, .35, .85, .45), .45)
            else -> null
        }
    }

    private fun String.has(p: String) = Regex(p, RegexOption.IGNORE_CASE).containsMatchIn(this)

    /** Rapport attendu à chaque niveau pour ce poids de corps. */
    fun thresholds(lift: Lift, sex: Sex, bodyweight: Double): DoubleArray {
        val female = sex == Sex.FEMALE
        val ref = if (female) 60.0 else 80.0
        val scale = (bodyweight.coerceIn(40.0, 160.0) / ref).pow(-0.33)
        return (if (female) lift.female else lift.male).map { it * scale }.toDoubleArray()
    }

    /** Shared by the muscle scores and the reference table in the profile. */
    fun athleteThresholds(lift: Lift, sex: Sex, bodyweight: Double): DoubleArray =
        thresholds(lift, sex, bodyweight).apply { indices.forEach { i -> this[i] *= 1.10 } }

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

/**
 * Repères du cardio. Chaque table relie une mesure à des points (≈ 70 = la moyenne des pratiquants, 90+ = très bon) ;
 * entre deux repères on interpole. Sources : ACSM (3 à 5 séances par semaine), OMS (150 à 300 min), Tudor-Locke pour les pas, relevés Garmin et
 * Strava pour la distance hebdomadaire et l'allure moyenne, Daniels-Gilbert, Uth (2004) et Jackson (1990) pour la VO₂max.
 */
object CardioNorms {
    private fun interp(x: Double, xs: DoubleArray, ys: DoubleArray): Double {
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        val i = (0 until xs.size - 1).first { x < xs[it + 1] }
        return ys[i] + (x - xs[i]) / (xs[i + 1] - xs[i]) * (ys[i + 1] - ys[i])
    }

    /** Séances par semaine. */
    fun sessions(perWeek: Double) = interp(perWeek, doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.5), doubleArrayOf(30.0, 50.0, 63.0, 75.0, 85.0, 93.0, 99.0))
    /** Pas par jour, paliers de Tudor-Locke : sédentaire sous 5 000, actif à 10 000, très actif dès 12 500. */
    fun steps(perDay: Double) = interp(perDay, doubleArrayOf(0.0, 3000.0, 5000.0, 7500.0, 10000.0, 12500.0, 15000.0, 20000.0), doubleArrayOf(30.0, 38.0, 50.0, 63.0, 75.0, 86.0, 93.0, 99.0))
    /** Meilleure course ramenée au 5 km (Riegel adouci), sur une grille par âge et sexe : 35 min ≈ 40 pts, 19 min ≈ 93 pts. */
    fun best5k(runs: List<AthleteStatsEngine.Run>, sex: Sex, age: Int): Double? {
        val best = runs.filter { it.distanceM >= 1000 && it.seconds > 0 }.maxOfOrNull { (it.distanceM / it.seconds) * (it.distanceM / 5000.0).pow(0.06) } ?: return null
        val seconds = 5000 / best
        val factor = (if (sex == Sex.FEMALE) 1.12 else 1.0) * (1 + 0.008 * (age - 30).coerceAtLeast(0))
        val levels = doubleArrayOf(2100.0, 1800.0, 1560.0, 1320.0, 1140.0).map { it * factor }
        val pts = doubleArrayOf(40.0, 55.0, 70.0, 83.0, 93.0)
        return when {
            seconds >= levels[0] -> 40.0 - (seconds - levels[0]) / 60.0
            seconds <= levels[4] -> 93.0 + 6.0 * (levels[4] - seconds) / (levels[3] - levels[4])
            else -> (0 until 4).first { seconds >= levels[it + 1] }.let { i -> pts[i] + (levels[i] - seconds) / (levels[i] - levels[i + 1]) * (pts[i + 1] - pts[i]) }
        }.coerceIn(25.0, 99.0)
    }
    /** Kilomètres par semaine en équivalent course. */
    fun distance(km: Double) = interp(km, doubleArrayOf(0.0, 5.0, 12.0, 25.0, 40.0, 55.0, 70.0), doubleArrayOf(30.0, 45.0, 62.0, 75.0, 85.0, 92.0, 99.0))

    /** Allure moyenne (s/km), ramenée à celle d'un homme de 20 à 35 ans (+12 % chez les femmes, +0,8 % par an après 35 ans). */
    fun pace(secondsPerKm: Double, sex: Sex, age: Int): Double {
        val factor = (if (sex == Sex.FEMALE) 1.12 else 1.0) * (1 + 0.008 * (age - 35).coerceAtLeast(0))
        val s = secondsPerKm / factor
        // Plus l'allure est rapide (secondes faibles), plus il y a de points : on lit la table à l'envers.
        return interp(-s, doubleArrayOf(-480.0, -420.0, -390.0, -350.0, -315.0, -280.0, -240.0, -210.0), doubleArrayOf(35.0, 50.0, 60.0, 70.0, 80.0, 88.0, 95.0, 99.0))
    }

    /** VDOT de Daniels-Gilbert sur la meilleure course de 1,5 à 50 km : coût en oxygène de la vitesse, divisé par la part de VO₂max tenue sur la durée. */
    fun vdot(runs: List<AthleteStatsEngine.Run>): Double? = runs.filter { it.distanceM in 1500.0..50_000.0 && it.seconds > 0 }.maxOfOrNull { r ->
        val t = r.seconds / 60; val v = r.distanceM / t
        val cost = -4.6 + 0.182258 * v + 0.000104 * v * v
        val share = 0.8 + 0.1894393 * kotlin.math.exp(-0.012778 * t) + 0.2989558 * kotlin.math.exp(-0.1932605 * t)
        cost / share
    }?.takeIf { it in 20.0..85.0 }

    /** Uth et al. (2004) : 15,3 × FCmax / FC au repos, FCmax de Tanaka (208 − 0,7 × âge). */
    fun uth(age: Int, resting: Double) = 15.3 * (208 - 0.7 * age) / resting

    /** Jackson et al. (1990), sans effort : âge, sexe, IMC et activité (échelle 0 à 7, ici d'après les minutes par semaine). */
    fun jackson(age: Int, sex: Sex, bmi: Double, weeklyMinutes: Double): Double {
        val activity = (weeklyMinutes / 45).coerceIn(0.0, 7.0)
        return 56.363 + 1.921 * activity - 0.381 * age - 0.754 * bmi + if (sex == Sex.FEMALE) 0.0 else 10.987
    }
}
