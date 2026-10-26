package app.superhealth.health.data

import app.superhealth.platform.*

/**
 * Types d'activité et de phases de sommeil, mêmes noms que l'ActivityKind de Gadgetbridge sur Android
 * (les écrans et Bridge.kindOf les reconnaissent par leur nom).
 */
enum class ActivityKind(val code: Int) {
    NOT_MEASURED(-1), UNKNOWN(0), ACTIVITY(1), LIGHT_SLEEP(2), DEEP_SLEEP(4), NOT_WORN(8), RUNNING(16), WALKING(32),
    SWIMMING(64), CYCLING(128), TREADMILL(256), EXERCISE(512), SWIMMING_OPENWATER(1024), INDOOR_CYCLING(2048),
    ELLIPTICAL_TRAINER(4096), STAIRS(8192), ROWING_MACHINE(16384), INDOOR_TRACK(32768), HIKING(65536), CLIMBING(131072),
    REM_SLEEP(262144), SLEEP_ANY(524288), AWAKE_SLEEP(1048576), NAVIGATE(2097152), STRENGTH_TRAINING(4194304), YOGA(8388608),
    TRAIL_RUN(16777216), PILATES(33554432), DANCE(67108864), HIIT(134217728), SKIING(268435456), TENNIS(536870912);

    companion object {
        fun fromCode(code: Int): ActivityKind = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** Appareil qui a enregistré les données (« Apple Santé » : Apple Watch, iPhone ou autre app). */
data class SourceDevice(val address: String, val name: String, val model: String? = null) {
    val aliasOrName: String get() = name
}

/**
 * Accès aux données de santé de la plateforme. Sur iOS : HealthKit (Apple Watch, iPhone, apps qui écrivent dans Santé) ;
 * sur l'aperçu ordinateur : des données de démonstration. Le moteur Gadgetbridge et Health Connect d'Android sont remplacés
 * par cette source unique.
 */
interface HealthSource {
    /** Nom affiché (« Apple Santé »). */
    val name: String

    /** Données de santé disponibles sur cet appareil (HealthKit absent sur certains iPad). */
    val available: Boolean

    /** Demande les autorisations de lecture et d'écriture ; vrai si la demande a pu être faite. */
    suspend fun requestAuthorization(): Boolean

    /** La source sait fournir cette mesure. */
    fun supports(metric: Metric): Boolean

    /** Valeur du jour : total (pas, distance, calories, minutes actives), moyenne (pouls au repos, VFC…) ou dernière (VO2 max, poids). */
    suspend fun daily(metric: Metric, date: LocalDate): Double?

    /** Mesures brutes horodatées entre [fromMs] et [toMs] (pouls, pas, SpO2…). */
    suspend fun samples(metric: Metric, fromMs: Long, toMs: Long): List<Point>

    /** Phases de sommeil qui se terminent entre [fromMs] et [toMs]. */
    suspend fun sleep(fromMs: Long, toMs: Long): List<HealthRepository.SleepPhase>

    suspend fun workouts(fromMs: Long, toMs: Long): List<Workout>

    suspend fun workoutDetail(id: Long): WorkoutDetail?

    /** Pesées enregistrées par d'autres apps (balance connectée…) : horodatage, kg, masse grasse %. */
    suspend fun weights(fromMs: Long, toMs: Long): List<Triple<Long, Double, Double?>>

    /** Anneaux d'activité du jour (Apple : Bouger, M'entraîner, Me lever) avec les objectifs réglés sur la montre. */
    suspend fun activitySummary(date: LocalDate): ActivitySummary? = null

    /** Alertes cardiaques de la montre (pouls élevé ou bas, rythme irrégulier) et ECG enregistrés. */
    suspend fun heartEvents(fromMs: Long, toMs: Long): List<HeartEvent> = emptyList()

    /** Nouvelles mesures de pouls dès qu'elles arrivent (null : pas de suivi en direct). */
    fun liveHeart(): kotlinx.coroutines.flow.Flow<Point>? = null

    // Écriture vers la plateforme (une donnée réécrite remplace la précédente : même identifiant de synchro).
    suspend fun writeWeight(epochDay: Long, kg: Double, fatPct: Double?) = Unit
    suspend fun writeNutrition(epochDay: Long, kcal: Double, protein: Double, fat: Double, carbs: Double, fiber: Double) = Unit
    suspend fun deleteNutrition(epochDay: Long) = Unit
    suspend fun writeWorkout(id: String, startMs: Long, endMs: Long, title: String, kcal: Double?) = Unit
    suspend fun writeWater(id: Long, timestampMs: Long, ml: Double) = Unit
}

/** Anneaux Apple : calories actives (Bouger), minutes d'exercice (M'entraîner), heures debout (Me lever). */
data class ActivitySummary(
    val move: Double, val moveGoal: Double,
    val exercise: Double, val exerciseGoal: Double,
    val stand: Double, val standGoal: Double,
)

enum class HeartEventKind(val label: String) {
    HIGH("Pouls élevé"), LOW("Pouls bas"), IRREGULAR("Rythme irrégulier"), ECG("ECG"),
}

/** Alerte ou ECG : [detail] = classement de l'ECG (rythme sinusal, fibrillation auriculaire, non concluant…). */
data class HeartEvent(val timeMs: Long, val kind: HeartEventKind, val bpm: Int? = null, val detail: String? = null, val alert: Boolean = false)

object HealthSources {
    var current: HealthSource = NoHealthSource
}

object NoHealthSource : HealthSource {
    override val name = "—"
    override val available = false
    override suspend fun requestAuthorization() = false
    override fun supports(metric: Metric) = false
    override suspend fun daily(metric: Metric, date: LocalDate): Double? = null
    override suspend fun samples(metric: Metric, fromMs: Long, toMs: Long): List<Point> = emptyList()
    override suspend fun sleep(fromMs: Long, toMs: Long): List<HealthRepository.SleepPhase> = emptyList()
    override suspend fun workouts(fromMs: Long, toMs: Long): List<Workout> = emptyList()
    override suspend fun workoutDetail(id: Long): WorkoutDetail? = null
    override suspend fun weights(fromMs: Long, toMs: Long): List<Triple<Long, Double, Double?>> = emptyList()
}

/**
 * Profil d'activité (ex-ActivityUser de Gadgetbridge) : prénom, âge et objectifs quotidiens, recopiés par Bridge depuis le
 * profil nutrition. Mêmes valeurs par défaut que Gadgetbridge.
 */
class ActivityUser {
    private val sp get() = appContext.getSharedPreferences("activity_user", Context.MODE_PRIVATE)

    val name: String? get() = sp.getString(PREF_USER_NAME, null)
    val age: Int get() = LocalDate.now().year - (sp.getString(PREF_USER_DATE_OF_BIRTH, null)?.take(4)?.toIntOrNull() ?: 1990)
    val stepsGoal: Int get() = sp.getString(PREF_USER_STEPS_GOAL, null)?.toIntOrNull() ?: 8000
    val caloriesBurntGoal: Int get() = sp.getString(PREF_USER_CALORIES_BURNT, null)?.toIntOrNull() ?: 350
    val distanceGoalMeters: Int get() = sp.getString(PREF_USER_DISTANCE_METERS, null)?.toIntOrNull() ?: 5000
    val activeTimeGoalMinutes: Int get() = sp.getString(PREF_USER_ACTIVETIME_MINUTES, null)?.toIntOrNull() ?: 60
    /** Objectif de sommeil en minutes (Gadgetbridge : 7 h). */
    val sleepDurationGoal: Int get() = (sp.getString(PREF_USER_SLEEP_DURATION, null)?.toIntOrNull() ?: 7) * 60
    val hydrationGoalMl: Int get() = 0

    companion object {
        const val PREF_USER_NAME = "mi_user_alias"
        const val PREF_USER_DATE_OF_BIRTH = "activity_user_date_of_birth"
        const val PREF_USER_GENDER = "activity_user_gender"
        const val PREF_USER_HEIGHT_CM = "activity_user_height_cm"
        const val PREF_USER_WEIGHT_KG = "activity_user_weight_kg"
        const val PREF_USER_STEPS_GOAL = "fitness_goal"
        const val PREF_USER_SLEEP_DURATION = "activity_user_sleep_duration"
        const val PREF_USER_CALORIES_BURNT = "activity_user_calories_burnt"
        const val PREF_USER_DISTANCE_METERS = "activity_user_distance_meters"
        const val PREF_USER_ACTIVETIME_MINUTES = "activity_user_activetime_minutes"
        const val GENDER_FEMALE = 0
        const val GENDER_MALE = 1

        fun edit(): SharedPreferences.Editor = appContext.getSharedPreferences("activity_user", Context.MODE_PRIVATE).edit()
    }
}
