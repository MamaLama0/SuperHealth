package app.superhealth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Cartes du tableau de bord, dans l'ordre par défaut. [watch] : alimentée par la montre (ou Health Connect). */
@Serializable
enum class DashCard(val label: String, val section: Section, val watch: Boolean = false) {
    CALORIES("Calories restantes", Section.NUTRITION),
    WEIGHT("Tendance du poids", Section.NUTRITION),
    INTAKE("Calories consommées", Section.NUTRITION),
    EXPENDITURE("Dépense énergétique", Section.NUTRITION),
    MACROS("Macros de la semaine", Section.NUTRITION),
    STEPS("Pas", Section.HEALTH),
    HEART("Rythme cardiaque", Section.HEALTH, watch = true),
    SLEEP("Sommeil", Section.HEALTH, watch = true),
    CARDIO("Minutes cardio", Section.HEALTH, watch = true),
    ENERGY("Énergie et stress", Section.HEALTH, watch = true),
    VITALS("Constantes", Section.HEALTH, watch = true),
    HYDRATION("Hydratation", Section.HEALTH),
    RUNNING("Course à pied", Section.TRAINING, watch = true),
    RECOVERY("Récupération", Section.TRAINING, watch = true),
    ACTIVITIES("Dernières activités", Section.TRAINING),
    TRAINING_WEEK("Semaine d'entraînement", Section.TRAINING),
    MUSCLES("Muscles travaillés", Section.TRAINING),
    RECORDS("Records récents", Section.TRAINING),
    STRATEGY("Programme nutrition", Section.NUTRITION);

    enum class Section(val label: String) { NUTRITION("Nutrition"), HEALTH("Santé et constantes"), TRAINING("Entraînement") }

    companion object {
        /** Sélection de départ : l'essentiel de chaque module, sans surcharger l'écran. */
        val defaults = listOf(CALORIES, WEIGHT, EXPENDITURE, INTAKE, STEPS, HEART, SLEEP, ACTIVITIES, TRAINING_WEEK, VITALS)
    }
}

/** Raccourcis proposés par le bouton « + », dans l'ordre par défaut. */
enum class PlusAction(val label: String, val section: DashCard.Section) {
    SEARCH("Rechercher", DashCard.Section.NUTRITION),
    SCAN("Code-barres", DashCard.Section.NUTRITION),
    QUICK("Ajout rapide", DashCard.Section.NUTRITION),
    AI("Décrire (IA)", DashCard.Section.NUTRITION),
    RECIPES("Recettes", DashCard.Section.NUTRITION),
    CUSTOM_FOOD("Aliment perso", DashCard.Section.NUTRITION),
    WORKOUT("Séance du programme", DashCard.Section.TRAINING),
    EMPTY("Séance vide", DashCard.Section.TRAINING),
    EXERCISES("Exercices", DashCard.Section.TRAINING),
    PROGRAM_NEW("Nouveau programme", DashCard.Section.TRAINING),
    WEIGHT("Pesée", DashCard.Section.HEALTH),
    WATER("Verre d'eau (250 ml)", DashCard.Section.HEALTH),
    VITALS("Hydratation et constantes", DashCard.Section.HEALTH),
    BODY("Mesures", DashCard.Section.HEALTH),
    SYNC("Synchroniser", DashCard.Section.HEALTH);

    companion object {
        val defaults = listOf(SEARCH, SCAN, QUICK, AI, RECIPES, CUSTOM_FOOD, WORKOUT, EMPTY, EXERCISES, WEIGHT, VITALS, BODY, SYNC)
    }
}

@Serializable
data class SuperSettings(
    /** Questionnaire d'accueil terminé. */
    val onboarded: Boolean = false,
    /** Module LocalHealth (moteur Gadgetbridge : montres et capteurs Bluetooth). Désactivable. */
    val watchModule: Boolean = true,
    /** Health Connect activé (lecture et écriture, voir HealthConnectHub). */
    val healthConnect: Boolean = false,
    /** Cartes visibles du tableau de bord, dans l'ordre. */
    val dashboard: List<DashCard> = DashCard.defaults,
    /** Raccourcis du « + » (noms de [PlusAction], gardés en texte : une valeur retirée plus tard n'efface pas les réglages). */
    val plus: List<String> = PlusAction.defaults.map { it.name },
    /** Dernière synchronisation Health Connect (ms) et son résumé. */
    val lastHcSync: Long = 0,
    val lastHcSummary: String? = null,
)

val SuperSettings.plusActions: List<PlusAction> get() = plus.mapNotNull { n -> PlusAction.entries.firstOrNull { it.name == n } }

/** Données lues dans Health Connect (autres apps) quand la montre n'est pas gérée par SuperHealth. */
@Serializable
data class HcCache(
    val heartRate: Map<Long, Double> = emptyMap(),
    val restingHr: Map<Long, Double> = emptyMap(),
    val sleepMin: Map<Long, Double> = emptyMap(),
    val hrv: Map<Long, Double> = emptyMap(),
    val spo2: Map<Long, Double> = emptyMap(),
    val sessions: List<HcSession> = emptyList(),
    /**
     * Pas des autres sources (podomètre du téléphone via Health Connect, autres apps) par heure : début de l'heure (ms) → pas.
     * Fusionnés heure par heure avec ceux de la montre (voir Bridge.steps).
     */
    val stepsHourly: Map<Long, Double> = emptyMap(),
)

/** Séance d'exercice enregistrée par une autre app (course, marche, vélo…). */
@Serializable
data class HcSession(val id: String, val type: Int, val title: String?, val start: Long, val end: Long, val app: String)

private val SuperJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

/** Réglages propres à SuperHealth (les modules gardent les leurs). */
object SuperPrefs {
    private lateinit var sp: android.content.SharedPreferences
    private val _settings = MutableStateFlow(SuperSettings())
    private val _hc = MutableStateFlow(HcCache())
    val settings: StateFlow<SuperSettings> = _settings
    val hc: StateFlow<HcCache> = _hc

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("superhealth", Context.MODE_PRIVATE)
        _settings.value = sp.getString("settings", null)?.let { runCatching { SuperJson.decodeFromString(SuperSettings.serializer(), it) }.getOrNull() }
            ?: SuperSettings()
        _hc.value = sp.getString("hc", null)?.let { runCatching { SuperJson.decodeFromString(HcCache.serializer(), it) }.getOrNull() } ?: HcCache()
    }


    @Synchronized fun update(f: (SuperSettings) -> SuperSettings) {
        val v = f(_settings.value)
        _settings.value = v
        sp.edit().putString("settings", SuperJson.encodeToString(SuperSettings.serializer(), v)).apply()
    }

    @Synchronized fun setHc(c: HcCache) {
        _hc.value = c
        sp.edit().putString("hc", SuperJson.encodeToString(HcCache.serializer(), c)).apply()
    }
}
