package app.superhealth.training.domain

import kotlinx.serialization.Serializable

// Modèle calqué sur MacroFactor Workouts 1.4.1 (Workouts-analyse/notes/02-modele-de-donnees.md).

/** ExerciseSetType observé : standard, warmUp, drop, myo, failure. */
@Serializable enum class SetType(val label: String, val short: String) {
    STANDARD("Normale", ""), WARMUP("Échauffement", "É"), DROP("Dégressive", "D"), MYO("Myo-reps", "M"), FAILURE("Échec", "F")
}

/** ProgramGoal observé. */
@Serializable enum class Goal(val label: String, val description: String) {
    HYPERTROPHY("Hypertrophie", "Gagner du muscle"),
    STRENGTH("Force", "Soulever plus lourd"),
    BOTH("Les deux", "Force et hypertrophie"),
}

/** ProgramPeriodization observé : plages fixes ou progression linéaire des répétitions d'un cycle à l'autre. */
@Serializable enum class Periodization(val label: String, val description: String) {
    STATIC("Fixes", "Mêmes plages de répétitions chaque semaine"),
    LINEAR("Linéaires", "Les plages évoluent d'une semaine à l'autre"),
}

/** WorkoutDeload observé (firstCycle, lastCycle) ; NONE = « Train continuously without scheduled reductions ». */
@Serializable enum class Deload(val label: String) { NONE("Aucune"), FIRST("Premier cycle"), LAST("Dernier cycle") }

/** WarmUpAutomation observé. */
@Serializable enum class WarmUpMode(val label: String, val description: String) {
    SMART("Intelligent", "Ajoutés selon la fraîcheur des muscles et la charge de travail"),
    SCHEME("Schéma", "Ajoutés depuis un schéma configuré"),
    NONE("Aucun", "Pas d'échauffement automatique"),
}

@Serializable enum class Experience(val label: String) { BEGINNER("Débutant"), INTERMEDIATE("Intermédiaire"), ADVANCED("Avancé") }

@Serializable enum class Sex { MALE, FEMALE }
@Serializable enum class WeightUnit(val label: String) { KG("kg"), LB("lb") }
@Serializable enum class ThemeMode(val label: String) { SYSTEM("Système"), LIGHT("Clair"), DARK("Sombre") }

/** ProgramSplit observé (nombre de jours d'entraînement par cycle selon la structure). */
@Serializable enum class Split(val label: String, val days: List<SplitDay>) {
    FULL_BODY("Corps entier", listOf(SplitDay.FULL)),
    UPPER_LOWER("Haut / Bas", listOf(SplitDay.UPPER, SplitDay.LOWER)),
    PUSH_PULL("Poussée / Tirage", listOf(SplitDay.PUSH, SplitDay.PULL_LEGS)),
    UPPER_LOWER_FULL("Haut / Bas / Complet", listOf(SplitDay.UPPER, SplitDay.LOWER, SplitDay.FULL)),
    PUSH_PULL_LEGS("Push / Pull / Legs", listOf(SplitDay.PUSH, SplitDay.PULL, SplitDay.LEGS)),
    UPPER_LOWER_X2("Haut / Bas ×2", listOf(SplitDay.UPPER, SplitDay.LOWER, SplitDay.UPPER, SplitDay.LOWER)),
    PUSH_PULL_HINGE_SQUAT("Poussée / Tirage / Charnière / Squat", listOf(SplitDay.PUSH, SplitDay.PULL, SplitDay.HINGE, SplitDay.SQUAT)),
    PPL_ACCESSORIES("PPL + accessoires", listOf(SplitDay.PUSH, SplitDay.PULL, SplitDay.LEGS, SplitDay.ACCESSORIES)),
    UPPER_LOWER_X2_UPPER("Haut / Bas ×2 + Haut", listOf(SplitDay.UPPER, SplitDay.LOWER, SplitDay.UPPER, SplitDay.LOWER, SplitDay.UPPER)),
    PPHS_ACCESSORIES("P / T / C / S + accessoires", listOf(SplitDay.PUSH, SplitDay.PULL, SplitDay.HINGE, SplitDay.SQUAT, SplitDay.ACCESSORIES)),
    PPL_UPPER_LOWER("PPL + Haut / Bas", listOf(SplitDay.PUSH, SplitDay.PULL, SplitDay.LEGS, SplitDay.UPPER, SplitDay.LOWER)),
    PPHS_UPPER_LOWER("P / T / C / S + Haut / Bas", listOf(SplitDay.PUSH, SplitDay.PULL, SplitDay.HINGE, SplitDay.SQUAT, SplitDay.UPPER, SplitDay.LOWER)),
    PPL_X2("Push / Pull / Legs ×2", listOf(SplitDay.PUSH, SplitDay.PULL, SplitDay.LEGS, SplitDay.PUSH, SplitDay.PULL, SplitDay.LEGS));

    companion object {
        /** Structure recommandée (« We'll choose a recommended split for you ») [INFÉRÉ]. */
        fun recommended(n: Int): Split = when (n) {
            in 1..3 -> FULL_BODY; 4 -> UPPER_LOWER_X2; 5 -> PPL_UPPER_LOWER; else -> PPL_X2
        }

        /** Structures proposées selon le nombre de séances par semaine. */
        fun forDays(n: Int): List<Split> = when (n) {
            1 -> listOf(FULL_BODY)
            2 -> listOf(FULL_BODY, UPPER_LOWER, PUSH_PULL)
            3 -> listOf(FULL_BODY, UPPER_LOWER_FULL, PUSH_PULL_LEGS)
            4 -> listOf(FULL_BODY, UPPER_LOWER_X2, PUSH_PULL_HINGE_SQUAT)
            5 -> listOf(FULL_BODY, UPPER_LOWER_X2_UPPER, PPL_UPPER_LOWER, PPL_ACCESSORIES, PPHS_ACCESSORIES)
            else -> listOf(FULL_BODY, PPL_X2, PPHS_UPPER_LOWER)
        }
    }
}

/** Jours-types de split (libellés observés : Upper, Lower, Push, Pull, Legs, Hinge, Squat, Full Body, Accessories). */
@Serializable enum class SplitDay(val label: String) {
    FULL("Corps entier"), UPPER("Haut du corps"), LOWER("Bas du corps"), PUSH("Poussée"), PULL("Tirage"),
    PULL_LEGS("Tirage et jambes"), LEGS("Jambes"), HINGE("Charnière"), SQUAT("Squat"), ACCESSORIES("Accessoires")
}

/** 23 groupes musculaires « feature » de la base (clés = id des tracés des silhouettes). */
enum class MuscleGroup(val key: String, val label: String, val upper: Boolean) {
    CHEST("chest", "Pectoraux", true), FRONT_DELTS("frontDelts", "Deltoïdes antérieurs", true),
    SIDE_DELTS("sideDelts", "Deltoïdes latéraux", true), REAR_DELTS("rearDelts", "Deltoïdes postérieurs", true),
    TRICEPS("triceps", "Triceps", true), BICEPS("biceps", "Biceps", true), FOREARMS("forearms", "Avant-bras", true),
    LATS("lats", "Grands dorsaux", true), UPPER_BACK("upperBack", "Haut du dos", true), UPPER_TRAPS("upperTraps", "Trapèzes supérieurs", true),
    NECK("neck", "Cou", true), SERRATUS("serratus", "Dentelés", true),
    ABS("abs", "Abdominaux", false), OBLIQUES("obliques", "Obliques", false), LOWER_BACK("lowerBack", "Lombaires", false),
    QUADS("quads", "Quadriceps", false), HAMSTRINGS("hamstrings", "Ischio-jambiers", false), GLUTES("glutes", "Fessiers", false),
    ADDUCTORS("adductors", "Adducteurs", false), ABDUCTORS("abductors", "Abducteurs", false), CALVES("calves", "Mollets", false),
    TIBS("tibs", "Tibiaux", false), HIP_FLEXORS("hipFlexors", "Fléchisseurs de hanche", false);

    companion object {
        private val byKey = entries.associateBy { it.key }
        fun of(key: String) = byKey[key]
    }
}

// ----- Cibles -----

/** ExerciseTargetLog observé : minFullReps, maxFullReps, rir, restTimer (+ durée / distance non repris). */
@Serializable
data class TargetSet(
    val type: SetType = SetType.STANDARD,
    val minReps: Int = 8,
    val maxReps: Int = 12,
    val rir: Int = 2,
    val restSec: Int = 120,
)

@Serializable
data class ProgramExercise(
    val id: String,
    val exerciseId: String,
    /** Cibles par cycle (periodizedTargets) ; une seule entrée si la périodisation est désactivée. */
    val cycles: List<List<TargetSet>>,
    /** Exercices consécutifs avec le même groupe forment un superset. */
    val supersetGroup: Int? = null,
)

@Serializable
data class ProgramDay(val id: String, val name: String, val exercises: List<ProgramExercise> = emptyList()) {
    val isRest get() = exercises.isEmpty()
}

@Serializable
data class Program(
    val id: String,
    val name: String,
    val color: Int = 0,
    val days: List<ProgramDay>,
    val numCycles: Int = 6,
    val deload: Deload = Deload.LAST,
    val periodization: Periodization = Periodization.LINEAR,
    val goal: Goal = Goal.HYPERTROPHY,
    val gymId: String? = null,
    val runIndefinitely: Boolean = false,
    /** Position courante : cycle et jour suivants à faire. */
    val cycleIndex: Int = 0,
    val dayIndex: Int = 0,
    /** Jours faits dans le cycle en cours : on peut les faire dans le désordre sans valider ceux qu'on a sautés. */
    val doneDayIds: List<String> = emptyList(),
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Entraînement autonome de la bibliothèque (CustomWorkout). */
@Serializable
data class CustomWorkout(val id: String, val name: String, val exercises: List<ProgramExercise>, val gymId: String? = null)

// ----- Séance en cours et historique -----

/** WorkoutSessionLog / WorkoutLog observés. weight en kg (charge totale, barre comprise). */
@Serializable
data class SetLog(
    val id: String,
    val type: SetType = SetType.STANDARD,
    val weight: Double? = null,
    val reps: Int? = null,
    val partialReps: Int = 0,
    val rir: Int? = null,
    val done: Boolean = false,
    val target: TargetSet? = null,
    /** Valeurs proposées par la progression intelligente (AutoTarget : weight, reps, rir). */
    val autoWeight: Double? = null,
    val autoReps: Int? = null,
    val completedAt: Long? = null,
)

@Serializable
data class SessionExercise(
    val id: String,
    val exerciseId: String,
    val sets: List<SetLog>,
    val note: String = "",
    val supersetGroup: Int? = null,
    val restSec: Int = 120,
)

@Serializable
data class Session(
    val id: String,
    val name: String,
    val startTime: Long,
    val exercises: List<SessionExercise>,
    val gymId: String? = null,
    val programId: String? = null,
    val cycleIndex: Int? = null,
    val dayId: String? = null,
    /** Fin du repos en cours (epoch ms) et sa durée. */
    val restEndsAt: Long? = null,
    val restDuration: Int = 0,
    val pausedMs: Long = 0,
    val note: String = "",
    /** Pause en cours (epoch ms), null si la séance tourne. */
    val pausedAt: Long? = null,
    /** Exercice affiché (page du PageView de séance). */
    val current: Int = 0,
    /** Forme du jour appliquée : +1 un cran plus lourd (pousser) ou −1 une série de moins (alléger) ; le programme ne change pas. */
    val adjust: Int = 0,
) {
    /** Durée effective en secondes, pauses déduites. */
    fun elapsedSec(now: Long): Long = ((now - startTime - pausedMs - (pausedAt?.let { now - it } ?: 0)) / 1000).coerceAtLeast(0)
}

@Serializable
data class Workout(
    val id: String,
    val name: String,
    val startTime: Long,
    val durationSec: Long,
    val exercises: List<SessionExercise>,
    val gymName: String? = null,
    val programId: String? = null,
    val programName: String? = null,
    val cycleIndex: Int? = null,
    val note: String = "",
    val dayId: String? = null,
    /** Effort ressenti de la séance (échelle CR-10, 1–10) : sa charge ressentie entre dans l'Effort du jour. */
    val rpe: Int? = null,
)

// ----- Salles et réglages -----

/** Gym observé : équipements sélectionnés, configuration de poids, options du calculateur de disques. */
@Serializable
data class Gym(
    val id: String,
    val name: String,
    val icon: Int = 0,
    val equipmentIds: Set<String>,
    /** Disques disponibles (kg) et nombre de paires. */
    val plates: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
    /** Pas des haltères et des machines (kg). */
    val dumbbellStep: Double = 2.0,
    val machineStep: Double = 5.0,
    val barWeight: Double = 20.0,
    val unit: WeightUnit = WeightUnit.KG,
)

/** UserExerciseConfig (sous-ensemble). */
@Serializable
data class ExerciseConfig(
    val note: String = "",
    val doNotRecommend: Boolean = false,
    val restSec: Int? = null,
    val warmUp: WarmUpMode? = null,
)

@Serializable enum class Language(val label: String) { SYSTEM("Système"), FR("Français"), EN("English") }

@Serializable
data class WarmUpStep(val percent: Double, val reps: Int)

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    /** Langue de l'interface. */
    val language: Language = Language.SYSTEM,
    /** Health Connect : écrire les séances, lire le poids de corps. */
    val healthConnect: Boolean = false,
    /** Sexe, poids de corps et expérience saisis à l'accueil. */
    val experience: Experience = Experience.INTERMEDIATE,
    val unit: WeightUnit = WeightUnit.KG,
    val sex: Sex = Sex.MALE,
    val bodyweightKg: Double = 75.0,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val smartProgression: Boolean = true,
    /** « Initial log fill » : valeurs de la progression intelligente (true) ou précédentes (false). */
    val fillWithSmart: Boolean = true,
    val expandRepRange: Boolean = true,
    val weightMatch: Boolean = true,
    val warmUp: WarmUpMode = WarmUpMode.SMART,
    /** Schéma par défaut (exemple du centre d'aide : 40 %, 60 %, 80 % ×3). */
    val warmUpScheme: List<WarmUpStep> = listOf(WarmUpStep(0.4, 8), WarmUpStep(0.6, 5), WarmUpStep(0.8, 3)),
    val hideCompletedWarmUps: Boolean = false,
    val simplifyWarmUpPlates: Boolean = true,
    val restDefaultSec: Int = 120,
    val restBetweenSidesSec: Int = 15,
    val restAfterWarmUpSec: Int = 60,
    val restSound: Boolean = true,
    val activeGymId: String? = null,
    val activeProgramId: String? = null,
)
