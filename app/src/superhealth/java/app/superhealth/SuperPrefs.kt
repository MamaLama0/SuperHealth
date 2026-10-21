package app.superhealth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Cartes du tableau de bord, dans l'ordre par défaut. [watch] : alimentée par la montre (ou Health Connect). */
@Serializable
enum class DashCard(val label: String, val section: Section, val watch: Boolean = false) {
    RINGS("Anneaux du jour", Section.HEALTH),
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
    STRATEGY("Programme nutrition", Section.NUTRITION),
    HABITS("Habitudes", Section.NUTRITION),
    BALANCE("Bilan énergétique", Section.NUTRITION),
    NEXT_WORKOUT("Prochaine séance", Section.TRAINING),
    TRAINING_TRENDS("Tendances d'entraînement", Section.TRAINING),
    MUSCLE_SETS("Séries par muscle", Section.TRAINING),
    LIFTS("Progression des exercices", Section.TRAINING);

    enum class Section(val label: String) { NUTRITION("Nutrition"), HEALTH("Santé et constantes"), TRAINING("Entraînement") }

    companion object {
        /** Cartes ajoutées avec la reprise des tableaux de bord MacroFactor et MacroFactor Workouts. */
        val newMacroFactorCards = listOf(NEXT_WORKOUT, HABITS, BALANCE, TRAINING_TRENDS, MUSCLE_SETS, LIFTS)

        /** Sélection de départ : l'essentiel de chaque module, sans surcharger l'écran. */
        val defaults = listOf(RINGS, VITALS, CALORIES, WEIGHT, EXPENDITURE, NEXT_WORKOUT, INTAKE, STEPS, HEART, SLEEP, ACTIVITIES, TRAINING_WEEK, HABITS)
    }
}

/**
 * Taille d'une carte dans la grille de 6 colonnes : S (bulle ronde, 3 par ligne), M (demi-largeur), L (pleine largeur).
 * Les cartes se rangent dans l'ordre, ligne par ligne, tant qu'elles tiennent.
 */
@Serializable
enum class CardSize(val span: Int, val label: String) { S(2, "Bulle"), M(3, "Demi"), L(6, "Pleine") }

/** Cartes qui ont une valeur principale : disponibles en bulle (S) et en tuile (M). */
val COMPACT = setOf(DashCard.RINGS, DashCard.CALORIES, DashCard.WEIGHT, DashCard.EXPENDITURE, DashCard.INTAKE, DashCard.MACROS,
    DashCard.STEPS, DashCard.HEART, DashCard.SLEEP, DashCard.CARDIO, DashCard.ENERGY, DashCard.HYDRATION, DashCard.RUNNING,
    DashCard.RECOVERY, DashCard.TRAINING_WEEK, DashCard.HABITS, DashCard.BALANCE)

/** Cartes conçues en demi-largeur. */
val HALF = setOf(DashCard.WEIGHT, DashCard.EXPENDITURE, DashCard.STEPS, DashCard.SLEEP, DashCard.CARDIO, DashCard.ENERGY, DashCard.HYDRATION,
    DashCard.RECOVERY, DashCard.HEART)

val DashCard.sizes: List<CardSize> get() = when {
    this in COMPACT -> CardSize.entries
    else -> listOf(CardSize.L)
}

val DashCard.defaultSize: CardSize get() = if (this in HALF) CardSize.M else CardSize.L

/** Taille choisie (ou par défaut) d'une carte, toujours parmi celles qu'elle accepte. */
fun SuperSettings.sizeOf(c: DashCard): CardSize =
    dashSizes[c.name]?.let { n -> CardSize.entries.firstOrNull { it.name == n } }?.takeIf { it in c.sizes } ?: c.defaultSize

/** Rangées de la grille : chaque rangée tient dans 6 colonnes. */
fun <T> packRows(items: List<T>, span: (T) -> Int): List<List<T>> {
    val rows = mutableListOf<MutableList<T>>()
    var used = 6
    for (it in items) {
        val w = span(it).coerceIn(1, 6)
        if (used + w > 6) { rows += mutableListOf<T>(); used = 0 }
        rows.last() += it; used += w
    }
    return rows
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

/** Liste de cartes gardée en noms : une carte retirée ou ajoutée par une autre version ne fait pas perdre les réglages. */
object DashCardList : kotlinx.serialization.KSerializer<List<DashCard>> {
    private val names = kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>())
    override val descriptor = names.descriptor
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: List<DashCard>) = names.serialize(encoder, value.map { it.name })
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): List<DashCard> =
        names.deserialize(decoder).mapNotNull { n -> DashCard.entries.firstOrNull { it.name == n } }.distinct()
}

@Serializable
data class SuperSettings(
    /** Questionnaire d'accueil terminé. */
    val onboarded: Boolean = false,
    /** Module LocalHealth (moteur Gadgetbridge : montres et capteurs Bluetooth). Désactivable. */
    val watchModule: Boolean = true,
    /** Health Connect activé (lecture et écriture, voir HealthConnectHub). */
    val healthConnect: Boolean = false,
    /** Cartes visibles du tableau de bord, dans l'ordre (une carte inconnue, d'une autre version, est ignorée). */
    @Serializable(with = DashCardList::class)
    val dashboard: List<DashCard> = DashCard.defaults,
    /** Raccourcis du « + » (noms de [PlusAction], gardés en texte : une valeur retirée plus tard n'efface pas les réglages). */
    val plus: List<String> = PlusAction.defaults.map { it.name },
    /** Mesures de la carte Constantes (pièce maîtresse du tableau de bord), noms de [app.superhealth.health.data.Metric]. */
    val vitals: List<String> = listOf("RESTING_HR", "RESPIRATORY_RATE", "BODY_ENERGY"),
    /** Constantes déjà placées en tête du tableau de bord (migration unique). */
    val vitalsHero: Boolean = false,
    /** Taille de chaque carte (nom de [DashCard] → nom de [CardSize]) ; absente : taille par défaut. */
    val dashSizes: Map<String, String> = emptyMap(),
    /** Mesures des anneaux du jour, de l'extérieur vers l'intérieur (noms de Rings.Kind). */
    val rings: List<String> = listOf("STEPS", "ENERGY", "KCAL"),
    /** Anneaux du jour déjà placés au-dessus des constantes (migration unique). */
    val ringsAdded: Boolean = false,
    /** Cartes reprises de MacroFactor (habitudes, bilan, prochaine séance…) déjà proposées (migration unique). */
    val mfCardsAdded: Boolean = false,
    /** Fonds de carte OpenStreetMap des activités (seule connexion liée à vos parcours ; sans eux, le tracé seul). */
    val onlineMaps: Boolean = true,
    /** Dernière synchronisation Health Connect (ms) et son résumé. */
    val lastHcSync: Long = 0,
    val lastHcSummary: String? = null,
)

/** Mesures que la carte Constantes peut afficher (montre ou Health Connect). */
val VITAL_CHOICES = listOf("RESTING_HR", "HEART_RATE", "RESPIRATORY_RATE", "BODY_ENERGY", "STRESS", "SPO2", "HRV", "SLEEP", "SLEEP_SCORE",
    "READINESS", "VO2MAX", "TEMPERATURE", "STEPS")

val SuperSettings.vitalMetrics: List<app.superhealth.health.data.Metric>
    get() = vitals.mapNotNull { n -> app.superhealth.health.data.Metric.entries.firstOrNull { it.name == n } }

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

/** [cards] avec les anneaux juste avant les constantes (en tête sans constantes) ; inchangé s'ils y sont déjà. */
fun withRings(cards: List<DashCard>): List<DashCard> {
    if (DashCard.RINGS in cards) return cards
    val i = cards.indexOf(DashCard.VITALS).coerceAtLeast(0)
    return cards.toMutableList().also { it.add(i, DashCard.RINGS) }
}

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
        // Constantes devenues la pièce maîtresse : placées une fois en tête (l'utilisateur peut ensuite les déplacer ou les retirer).
        _settings.value.let { s ->
            if (!s.vitalsHero) update { it.copy(dashboard = listOf(DashCard.VITALS) + (it.dashboard - DashCard.VITALS), vitalsHero = true) }
        }
        // Anneaux du jour : ajoutés une fois, juste au-dessus des constantes.
        if (!_settings.value.ringsAdded) update { it.copy(dashboard = withRings(it.dashboard), ringsAdded = true) }
        // Nouvelles cartes MacroFactor : ajoutées une fois en fin de tableau de bord (retirables dans Personnaliser).
        if (!_settings.value.mfCardsAdded) update { it.copy(dashboard = (it.dashboard + DashCard.newMacroFactorCards).distinct(), mfCardsAdded = true) }
        _hc.value = sp.getString("hc", null)?.let { runCatching { SuperJson.decodeFromString(HcCache.serializer(), it) }.getOrNull() } ?: HcCache()
    }


    @Synchronized fun update(f: (SuperSettings) -> SuperSettings) {
        val v = f(_settings.value)
        _settings.value = v
        sp.edit().putString("settings", SuperJson.encodeToString(SuperSettings.serializer(), v)).apply()
    }

    /** Réglages SuperHealth en JSON (sauvegarde complète). */
    fun exportSettings(): String = SuperJson.encodeToString(SuperSettings.serializer(), _settings.value)

    fun importSettings(json: String) {
        val s = runCatching { SuperJson.decodeFromString(SuperSettings.serializer(), json) }.getOrNull() ?: return
        update { s.copy(onboarded = true) }
    }

    @Synchronized fun setHc(c: HcCache) {
        _hc.value = c
        sp.edit().putString("hc", SuperJson.encodeToString(HcCache.serializer(), c)).apply()
    }
}
