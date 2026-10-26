package app.superhealth.nutrition.domain

import app.superhealth.platform.*

import kotlinx.serialization.Serializable

// Enums alignés sur ceux observés dans MacroFactor (notes/09 §3, objs.txt de dae).

@Serializable enum class Sex { MALE, FEMALE }

/** Multiplicateurs observés : mostlySedentary 1.2, moderatelyActive 1.4, veryActive 1.6. */
@Serializable enum class Activity(val factor: Double, val label: String) {
    MOSTLY_SEDENTARY(1.2, "Plutôt sédentaire"),
    MODERATELY_ACTIVE(1.4, "Modérément actif"),
    VERY_ACTIVE(1.6, "Très actif"),
}

@Serializable enum class WeightChange(val label: String) {
    GAINING("En prise"), LOSING("En perte"), MAINTAINING("Stable"), UNKNOWN("Inconnu")
}

@Serializable enum class LifetimeWeight(val label: String) {
    HIGHER("J'ai déjà pesé nettement plus"), LOWER("Jamais plus lourd qu'aujourd'hui"), UNKNOWN("Je ne sais pas")
}

/**
 * Équation du métabolisme de base. MACROFACTOR_B = sélection automatique observée (0x12bee54) :
 * non-athlète → équation B, athlète « récréatif » → moyenne A/B, sportif/athlète → équation A.
 */
@Serializable enum class BmrEquation(val label: String) {
    MACROFACTOR_B("Automatique"),
    MACROFACTOR_A("Masse maigre seule"),
    CUNNINGHAM("Cunningham (1980)"),
}

/** Statut sportif (enum AthleteOptions observé). */
@Serializable enum class Athlete(val label: String) {
    NONE("Non"), RECREATIONAL("Loisir"), SPORTING("Sportif"), ATHLETE("Athlète")
}

/** Séances d'exercice par semaine (enum Exercise observé) ; bonus ajouté au facteur d'activité. */
@Serializable enum class ExerciseLevel(val bonus: Double, val label: String) {
    NONE(0.0, "Aucune"), LOW(0.1, "1-3 / sem."), MED(0.2, "4-6 / sem."), HIGH(0.3, "7+ / sem.")
}

/** Type d'entraînement pendant le programme (enum Training observé) ; module la cible de protéines. */
@Serializable enum class Training(val label: String) {
    NONE("Aucun"), CARDIO("Cardio"), STRENGTH("Musculation"), BOTH("Les deux")
}

/** Base de la prédiction de poids (WeightTrendPredictionStyle observé). */
@Serializable enum class PredictionStyle(val label: String, val description: String) {
    WEIGHT_DATA("Données de poids", "Basée sur votre rythme récent ; utilise le rythme visé si les données manquent"),
    BLENDED("Mixte", "Moyenne égale de vos données de poids et du rythme visé"),
    GOAL_RATE("Rythme visé", "Basée uniquement sur le rythme de votre objectif"),
}

@Serializable enum class TrendVersion(val label: String) { V2("V2 (Kalman, recommandée)"), V1("V1 (moyenne exponentielle)") }
@Serializable enum class ExpenditureVersion(val label: String) { V3("V3 (recommandée)"), V1("V1") }

/** ema9 : α = 2/9 ; ema20 : α = 0.1 (constantes observées). */
@Serializable enum class TrendAlpha(val alpha: Double, val label: String) {
    EMA9(2.0 / 9.0, "Réactive (EMA 9)"),
    EMA20(0.1, "Lissée (EMA 20)"),
}

@Serializable enum class GoalType(val label: String) { CUT("Perte"), MAINTENANCE("Maintien"), BULK("Prise") }

@Serializable enum class ProgramStyle(val label: String) { COACHED("Coaché (automatique)"), MANUAL("Manuel") }

@Serializable enum class TdeeMode(val label: String) { DYNAMIC("Dynamique"), STATIC("Valeur fixe") }

/**
 * Planchers (CalorieFloor observé). Valeur effective = plancher dynamique (Targets.dynamicFloor) :
 * standard = max(1200, 1,5 × kcal des minimums de macros) ; bas = max(800, kcal des minimums) ; aucun = ignoré.
 */
@Serializable enum class CalorieFloor(val kcal: Double, val label: String) {
    STANDARD(1200.0, "Standard"), LOW(800.0, "Bas"), NONE(0.0, "Ignoré")
}

/** Préférence protéines ; valeurs g/kg de masse maigre dans Targets.proteinTable (0xa7d224). */
@Serializable enum class ProteinPreference(val label: String) {
    LOW("Basse"), MODERATE("Modérée"), HIGH("Haute"), EXTRA("Très haute")
}

/** Type de diète (enum ProgramType observé) ; règles de répartition dans Targets.split. */
@Serializable enum class DietPreference(val label: String, val description: String) {
    BALANCED("Équilibrée", "Répartition standard des glucides et des lipides"),
    LOW_FAT("Pauvre en lipides", "Moins de lipides, priorité aux protéines et aux glucides"),
    LOW_CARB("Pauvre en glucides", "Moins de glucides, priorité aux protéines et aux lipides"),
    KETO("Cétogène", "Glucides très restreints, davantage de lipides"),
}

@Serializable enum class ThemeMode(val label: String) { SYSTEM("Système"), LIGHT("Clair"), DARK("Sombre") }

/** Taille du texte de toute l'app, multipliée à celle choisie dans les réglages du téléphone. */
@Serializable enum class TextSize(val label: String, val scale: Float) { SMALL("Petite", 0.9f), NORMAL("Normale", 1f), LARGE("Grande", 1.12f), XLARGE("Très grande", 1.25f) }

/** Couleurs d'accent proposées par MacroFactor (enum ThemeColor observé : pink, blue, green, yellow, purple, orange, gray). */
@Serializable enum class AccentColor(val label: String) {
    BLUE("Bleu"), PINK("Rose"), GREEN("Vert"), YELLOW("Jaune"), PURPLE("Violet"), ORANGE("Orange"), GRAY("Gris")
}

@Serializable enum class WeightUnit(val label: String) { KG("kg"), LB("lb") }
@Serializable enum class EnergyUnit(val label: String) { KCAL("kcal"), KJ("kJ") }

@Serializable
data class Profile(
    val sex: Sex = Sex.MALE,
    val birthYear: Int = 1990,
    val heightCm: Double = 175.0,
    val activity: Activity = Activity.MODERATELY_ACTIVE,
    /** % masse grasse de référence si aucune pesée n'en fournit. Null = estimation. */
    val bodyFatPct: Double? = null,
    val weightChange: WeightChange = WeightChange.UNKNOWN,
    val lifetimeWeight: LifetimeWeight = LifetimeWeight.UNKNOWN,
    val bmrEquation: BmrEquation = BmrEquation.MACROFACTOR_B,
    val athlete: Athlete = Athlete.NONE,
    val exercise: ExerciseLevel = ExerciseLevel.NONE,
)

@Serializable
data class Program(
    val style: ProgramStyle = ProgramStyle.COACHED,
    val goalType: GoalType = GoalType.MAINTENANCE,
    /** % du poids corporel par semaine (valeur absolue). */
    val ratePctPerWeek: Double = 0.5,
    val targetWeightKg: Double? = null,
    val proteinPreference: ProteinPreference = ProteinPreference.MODERATE,
    val training: Training = Training.NONE,
    val dietPreference: DietPreference = DietPreference.BALANCED,
    val calorieFloor: CalorieFloor = CalorieFloor.STANDARD,
    val tdeeMode: TdeeMode = TdeeMode.DYNAMIC,
    val staticTdee: Double = 2200.0,
    /** Dépense initiale saisie à la main ; null = calcul BMR × activité. */
    val initialTdeeOverride: Double? = null,
    /** 1 = lundi … 7 = dimanche (lundi par défaut, comme MacroFactor). */
    val checkInDay: Int = 1,
    /** Cibles manuelles (style MANUAL). */
    val manualKcal: Double = 2000.0,
    val manualProtein: Double = 150.0,
    val manualFat: Double = 70.0,
    val manualCarbs: Double = 200.0,
    /** Premier jour du programme (epochDay) ; null = première donnée. */
    val startEpochDay: Long? = null,
    /** Jours à calories plus élevées (1 = lundi … 7 = dimanche) ; vide = cibles identiques toute la semaine. */
    val highDays: Set<Int> = emptySet(),
)

@Serializable
data class Settings(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val energyUnit: EnergyUnit = EnergyUnit.KCAL,
    val trendAlpha: TrendAlpha = TrendAlpha.EMA9,
    val openFoodFactsEnabled: Boolean = true,
    /** Base suisse des valeurs nutritives (embarquée). */
    val swissDbEnabled: Boolean = true,
    /** Fonctions IA (description / photo) visibles. */
    val aiEnabled: Boolean = true,
    val onboarded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val trendVersion: TrendVersion = TrendVersion.V2,
    val expenditureVersion: ExpenditureVersion = ExpenditureVersion.V3,
    /** « Predictive Goal Adjustment » de la dépense V3. */
    val predictiveGoalAdjustment: Boolean = true,
    /** Langue de l'interface. */
    val language: Language = Language.SYSTEM,
    /** Health Connect : synchronisation activée (pas, poids, nutrition). */
    val healthConnect: Boolean = false,
    /** Utiliser les pas dans la dépense V3. */
    val useSteps: Boolean = true,
    /** Objectif de pas quotidien (0 = aucun). */
    val stepGoal: Int = 8000,
    /** Objectifs par jour de la semaine (lundi → dimanche) ; vide ou 0 : l'objectif quotidien. */
    val stepGoalDays: List<Int> = emptyList(),
    val predictionStyle: PredictionStyle = PredictionStyle.WEIGHT_DATA,
    val accent: AccentColor = AccentColor.BLUE,
    val textSize: TextSize = TextSize.NORMAL,
    /** Cibles de micronutriments personnalisées (MicroGoalType.custom) ; absent = référence automatique. */
    val microTargets: Map<String, Double> = emptyMap(),
)

/** Objectif de pas du jour [date] : celui de son jour de la semaine s'il est fixé, sinon l'objectif quotidien. */
fun Settings.stepGoalFor(date: LocalDate): Int = stepGoalDays.getOrNull(date.dayOfWeek.value - 1)?.takeIf { it > 0 } ?: stepGoal

data class Macros(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, fat + o.fat, carbs + o.carbs)
    companion object { val ZERO = Macros(0.0, 0.0, 0.0, 0.0) }
}

/** Données d'une journée pour le moteur. */
data class DayData(
    val epochDay: Long,
    /** Somme des kcal journalisées ; null si rien n'est journalisé. */
    val intakeKcal: Double?,
    val weightKg: Double?,
    val bodyFatPct: Double?,
    val fasted: Boolean = false,
    val partial: Boolean = false,
    /** Pas du jour (Health Connect). */
    val steps: Double? = null,
)

@Serializable enum class Language(val label: String) { SYSTEM("Système"), FR("Français"), EN("English") }

@Serializable enum class GoalStatus(val label: String) { IN_PROGRESS("En cours"), ACHIEVED("Atteint"), COMPLETED("Terminé"), FAILED("Abandonné") }

/**
 * Objectif (modèle GoalEntry observé : startTime, endTime, type, weightStart, goalWeight, goalRatePerWeek, goalStatus…).
 * Le taux et la date de fin sont liés : saisir l'un recalcule l'autre.
 */
@Serializable
data class Goal(
    val id: Long = System.currentTimeMillis(),
    val startDay: Long,
    val type: GoalType,
    val startWeightKg: Double,
    val targetWeightKg: Double? = null,
    /** % du poids par semaine (valeur absolue). */
    val ratePctPerWeek: Double = 0.5,
    /** Date de fin visée (epochDay) ; null = calculée depuis le taux. */
    val endDay: Long? = null,
    val status: GoalStatus = GoalStatus.IN_PROGRESS,
    val closedDay: Long? = null,
)

/** Résultat d'un check-in hebdomadaire : les cibles s'appliquent à partir de [day] jusqu'au check-in suivant. */
@Serializable
data class CheckIn(
    val day: Long,
    val tdee: Double,
    val trendKg: Double?,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val goalType: GoalType,
    val ratePctPerWeek: Double,
    val limitedByFloor: Boolean = false,
    val manual: Boolean = false,
    /** Cibles par jour de la semaine (lundi → dimanche) quand des jours hauts sont choisis ; vide = uniforme. */
    val days: List<DayTarget> = emptyList(),
    /** Plancher effectif (dynamique) au moment du check-in. */
    val floorKcal: Double = 0.0,
) {
    /** Cible du jour de semaine [dow] (1 = lundi). */
    fun forDayOfWeek(dow: Int): DayTarget = days.getOrNull(dow - 1) ?: DayTarget(kcal, protein, fat, carbs)
}

/** Cible d'un jour (modèle DayTarget observé). */
@Serializable
data class DayTarget(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)

/** Mesures corporelles (enum BodyMetric observé, codes courts de MacroFactor), en cm. */
enum class BodyMetric(val code: String, val label: String, val bilateral: Boolean = false) {
    NECK("n", "Cou"), SHOULDERS("s", "Épaules"), CHEST("c", "Poitrine"), BUST("b", "Buste"),
    WAIST("w", "Taille"), HIPS("h", "Hanches"),
    LEFT_BICEP("lb", "Biceps gauche"), RIGHT_BICEP("rb", "Biceps droit"),
    LEFT_FOREARM("lf", "Avant-bras gauche"), RIGHT_FOREARM("rf", "Avant-bras droit"),
    LEFT_WRIST("lw", "Poignet gauche"), RIGHT_WRIST("rw", "Poignet droit"),
    LEFT_THIGH("lt", "Cuisse gauche"), RIGHT_THIGH("rt", "Cuisse droite"),
    LEFT_CALF("lc", "Mollet gauche"), RIGHT_CALF("rc", "Mollet droit"),
    LEFT_ANKLE("la", "Cheville gauche"), RIGHT_ANKLE("ra", "Cheville droite");

    companion object { fun of(code: String) = entries.firstOrNull { it.code == code } }
}

/** Poses des photos de progression (frontPhoto / sidePhoto / backPhoto). */
enum class PhotoPose(val code: String, val label: String) {
    FRONT("fp", "Face"), SIDE("sp", "Profil"), BACK("bp", "Dos");
    companion object { fun of(code: String) = entries.firstOrNull { it.code == code } ?: FRONT }
}
