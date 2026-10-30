package app.superhealth

import app.superhealth.platform.*

import app.superhealth.platform.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Cartes du tableau de bord, dans l'ordre par défaut. [watch] : alimentée par la montre (ou Health Connect). */
@Serializable
enum class DashCard(val label: String, val section: Section, val watch: Boolean = false) {
    RINGS("Anneaux du jour", Section.HEALTH),
    FOCUS("À faire", Section.HEALTH),
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
    LIFTS("Progression des exercices", Section.TRAINING),
    INSIGHTS("Insights", Section.HEALTH),
    CORRELATION("Corrélations", Section.HEALTH),
    CHARGE("Charge", Section.HEALTH, watch = true),
    EFFORT("Effort", Section.TRAINING, watch = true),
    SLEEP_STAGES("Phases de sommeil", Section.HEALTH, watch = true),
    HRV_STATUS("Variabilité cardiaque", Section.HEALTH, watch = true),
    STRESS_DAY("Stress de la journée", Section.HEALTH, watch = true),
    HR_ZONES("Zones cardiaques", Section.TRAINING, watch = true),
    LOAD("Charge d'entraînement", Section.TRAINING, watch = true),
    DAY_TIMELINE("Ma journée", Section.HEALTH),
    /** Module Coach IA : le point du jour. */
    COACH("Coach", Section.HEALTH),
    /** Module Coach IA : le bilan des 7 derniers jours. */
    COACH_WEEK("Ma semaine", Section.HEALTH),
    /** Mesures corporelles et photos de progression (paliers de l'objectif). */
    BODY("Mesures et photos", Section.NUTRITION),
    /** iOS : signes vitaux de la nuit comparés à la plage habituelle (comme l'app Signes vitaux de l'Apple Watch). */
    /** 1.8 : pousser ou se reposer aujourd'hui (Charge, sommeil, charge d'entraînement et moral recoupés). */
    FORM("Forme du jour", Section.HEALTH),
    /** 1.8 : journal du moral et ce qui influence la récupération. */
    JOURNAL("Journal du moral", Section.HEALTH),
    /** 1.8 : coucher conseillé (besoin, dette, heure de réveil habituelle). */
    BEDTIME("Coucher conseillé", Section.HEALTH, watch = true),
    /** 1.8 : suivi du cycle. */
    CYCLE("Cycle", Section.HEALTH),
    /** 2.1 : coucher et lever des deux dernières semaines. */
    SLEEP_SCHEDULE("Horaires de sommeil", Section.HEALTH, watch = true),
    /** 2.1 : Repos et ses quatre parties. */
    SLEEP_QUALITY("Qualité du sommeil", Section.HEALTH, watch = true),
    /** 2.1 : pouls du coucher au lever et son point bas. */
    NIGHT_HEART("Cœur la nuit", Section.HEALTH, watch = true),
    /** 2.1 : signes vitaux de la nuit face à la plage habituelle. */
    NIGHT_VITALS("Signes vitaux de la nuit", Section.HEALTH, watch = true),
    /** 2.1 : constantes sur 30 jours avec leur plage habituelle. */
    VITALS_TRENDS("Tendances des constantes", Section.HEALTH, watch = true),
    /** 2.2 : Forme, Charge, Repos et Effort réunis en anneaux sur une carte sombre. */
    SCORES("Scores du jour", Section.HEALTH, watch = true);

    enum class Section(val label: String) { NUTRITION("Nutrition"), HEALTH("Santé et constantes"), TRAINING("Entraînement") }

    companion object {
        /** Cartes ajoutées avec la reprise des tableaux de bord MacroFactor et MacroFactor Workouts. */
        val newMacroFactorCards = listOf(NEXT_WORKOUT, HABITS, BALANCE, TRAINING_TRENDS, MUSCLE_SETS, LIFTS)

        /** Cartes « montre » de la 1.1 (graphiques des applis de montre, scores WHOOP), ajoutées une fois. */
        val watchCards11 = listOf(CHARGE, EFFORT, SLEEP_STAGES, DAY_TIMELINE, HRV_STATUS, STRESS_DAY, HR_ZONES, LOAD)

        /** Sélection de départ : l'essentiel de chaque module, sans surcharger l'écran. */
        val defaults = listOf(RINGS, VITALS, CALORIES, WEIGHT, EXPENDITURE, NEXT_WORKOUT, INTAKE, STEPS, HEART, SLEEP, ACTIVITIES, TRAINING_WEEK, HABITS)
    }
}

/**
 * Taille d'une carte dans la grille de [GRID] blocs : petit (carré, 1 bloc), moyen (2 blocs), bandeau (toute la largeur,
 * une ligne) et grand (toute la largeur, version détaillée). Petit, moyen et bandeau sont les versions à regarder d'un coup
 * d'œil (symbole et valeur, peu ou pas de texte) ; grand donne le détail.
 * Les cartes se rangent dans l'ordre ; un trou en fin de rangée est comblé par la prochaine carte qui y tient.
 */
@Serializable
enum class CardSize(val span: Int, val label: String, private val en: String) {
    S(1, "Petit", "Small"), M(2, "Moyen", "Medium"), W(3, "Bandeau", "Strip"), L(3, "Grand", "Large");

    /** Nom affiché ; « Moyen » a son propre anglais (taille, pas humeur). */
    val title: String get() = if (app.superhealth.ui.Lang.english) en else label
}

const val GRID = 3

/** Cartes qui ont une valeur principale : disponibles en bulle (S) et en tuile (M). */
val COMPACT = setOf(DashCard.RINGS, DashCard.CALORIES, DashCard.WEIGHT, DashCard.EXPENDITURE, DashCard.INTAKE, DashCard.MACROS,
    DashCard.STEPS, DashCard.HEART, DashCard.SLEEP, DashCard.CARDIO, DashCard.ENERGY, DashCard.HYDRATION, DashCard.RUNNING,
    DashCard.RECOVERY, DashCard.TRAINING_WEEK, DashCard.HABITS, DashCard.BALANCE, DashCard.CHARGE, DashCard.EFFORT, DashCard.HRV_STATUS,
    DashCard.STRESS_DAY, DashCard.LOAD, DashCard.SLEEP_STAGES, DashCard.HR_ZONES, DashCard.DAY_TIMELINE,
    DashCard.COACH, DashCard.COACH_WEEK, DashCard.FORM, DashCard.JOURNAL, DashCard.BEDTIME, DashCard.CYCLE,
    DashCard.SLEEP_SCHEDULE, DashCard.SLEEP_QUALITY, DashCard.NIGHT_HEART, DashCard.NIGHT_VITALS, DashCard.VITALS_TRENDS)

/** Cartes conçues en demi-largeur. */
val HALF = setOf(DashCard.WEIGHT, DashCard.EXPENDITURE, DashCard.STEPS, DashCard.SLEEP, DashCard.CARDIO, DashCard.ENERGY, DashCard.HYDRATION,
    DashCard.RECOVERY, DashCard.HEART, DashCard.CHARGE, DashCard.EFFORT, DashCard.HRV_STATUS, DashCard.STRESS_DAY, DashCard.LOAD,
    DashCard.SLEEP_QUALITY, DashCard.NIGHT_HEART)

/** Module téléchargeable dont dépend la carte (null : toujours disponible). */
val DashCard.module: String? get() = when (this) {
    DashCard.COACH, DashCard.COACH_WEEK -> app.superhealth.modules.Modules.COACH
    else -> null
}

/** Carte affichable : son module (s'il en faut un) est installé et activé. */
val DashCard.available: Boolean get() = module?.let { app.superhealth.modules.Modules.isActive(it) } ?: true

/** Toutes les cartes ont les quatre tailles, sauf les phrases (Insights) qui n'ont pas de version petite. */
val DashCard.sizes: List<CardSize> get() = if (this == DashCard.INSIGHTS) listOf(CardSize.M, CardSize.W, CardSize.L) else CardSize.entries

val DashCard.defaultSize: CardSize get() = if (this in HALF) CardSize.M else CardSize.L

/** Taille choisie (ou par défaut) d'une carte, toujours parmi celles qu'elle accepte. */
fun SuperSettings.sizeOf(c: DashCard): CardSize =
    dashSizes[c.name]?.let { n -> CardSize.entries.firstOrNull { it.name == n } }?.takeIf { it in c.sizes } ?: c.defaultSize

/**
 * Rangées de la grille : dans l'ordre, chaque rangée prend la première carte restante puis, tant qu'il reste de la place,
 * les cartes suivantes qui y tiennent (une bulle remonte combler le trou laissé à côté d'une demi-carte).
 */
fun <T> packRows(items: List<T>, width: Int = GRID, span: (T) -> Int): List<List<T>> {
    val left = items.toMutableList()
    val rows = mutableListOf<List<T>>()
    while (left.isNotEmpty()) {
        val row = mutableListOf(left.removeAt(0))
        var used = span(row[0]).coerceIn(1, width)
        var k = 0
        while (used < width && k < left.size) {
            val w = span(left[k]).coerceIn(1, width)
            if (used + w <= width) { row += left.removeAt(k); used += w } else k++
        }
        rows += row
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
    SYNC("Synchroniser", DashCard.Section.HEALTH),
    /** Module Coach IA. */
    COACH("Demander au coach", DashCard.Section.HEALTH),
    /** 1.8 */
    MOOD("Noter mon moral", DashCard.Section.HEALTH),
    PERIOD("Règles", DashCard.Section.HEALTH),
    BREATHE("Respirer", DashCard.Section.HEALTH);

    companion object {
        val defaults = listOf(SEARCH, SCAN, QUICK, AI, RECIPES, CUSTOM_FOOD, WORKOUT, EMPTY, EXERCISES, WEIGHT, VITALS, BODY, SYNC, COACH, MOOD, PERIOD, BREATHE)
    }
}

/**
 * Grandes parties de l'app, chacune désactivable (questionnaire d'accueil, Réglages > Fonctions) : une partie coupée quitte
 * les onglets, le tableau de bord et le « + », sans effacer ses données.
 */
enum class Feature(val label: String, val summary: String) {
    NUTRITION("Nutrition", "Journal, calories adaptatives, tendance du poids"),
    TRAINING("Musculation", "Programmes, séances guidées, progression"),
    HEALTH("Activités et montre", "Course, pas, cœur, sommeil, Apple Santé"),
    JOURNAL("Journal du moral", "Humeur du jour, habitudes et leur effet sur la récupération"),
    CYCLE("Cycle menstruel", "Règles, phases et prochaines dates estimées"),
}

fun SuperSettings.has(f: Feature) = f.name in features

/** Partie de l'app d'une carte ; null : toujours proposée (anneaux, à faire, constats, coach…). */
fun DashCard.feature(): Feature? = when (this) {
    DashCard.CALORIES, DashCard.WEIGHT, DashCard.INTAKE, DashCard.EXPENDITURE, DashCard.MACROS, DashCard.STRATEGY,
    DashCard.HABITS, DashCard.BALANCE -> Feature.NUTRITION
    DashCard.TRAINING_WEEK, DashCard.MUSCLES, DashCard.RECORDS, DashCard.NEXT_WORKOUT, DashCard.TRAINING_TRENDS,
    DashCard.MUSCLE_SETS, DashCard.LIFTS -> Feature.TRAINING
    DashCard.STEPS, DashCard.RUNNING, DashCard.RECOVERY, DashCard.ACTIVITIES, DashCard.DAY_TIMELINE, DashCard.FORM -> Feature.HEALTH
    DashCard.JOURNAL -> Feature.JOURNAL
    DashCard.CYCLE -> Feature.CYCLE
    else -> if (watch) Feature.HEALTH else null
}

fun PlusAction.feature(): Feature? = if (this == PlusAction.MOOD) Feature.JOURNAL else if (this == PlusAction.PERIOD) Feature.CYCLE else when (section) {
    DashCard.Section.NUTRITION -> Feature.NUTRITION
    DashCard.Section.TRAINING -> Feature.TRAINING
    else -> if (this == PlusAction.SYNC) Feature.HEALTH else null
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
    /** 2.31 : verrouillage de l'app (empreinte ou code du téléphone), délai avant de redemander (s), écran masqué dans les Récents. */
    val appLock: Boolean = false,
    val lockDelaySec: Int = 0,
    val secureScreen: Boolean = false,
    /** Module LocalHealth (moteur Gadgetbridge : montres et capteurs Bluetooth). Désactivable. */
    val watchModule: Boolean = true,
    /** Health Connect activé (lecture et écriture, voir HealthConnectHub). */
    val healthConnect: Boolean = false,
    /** Cartes visibles du tableau de bord, dans l'ordre (une carte inconnue, d'une autre version, est ignorée). */
    @Serializable(with = DashCardList::class)
    val dashboard: List<DashCard> = DashCard.defaults,
    /** Raccourcis du « + » (noms de [PlusAction], gardés en texte : une valeur retirée plus tard n'efface pas les réglages). */
    val plus: List<String> = PlusAction.defaults.map { it.name },
    /** Place du « + » dans la barre du bas : LEFT (gaucher), CENTER ou RIGHT (sous le pouce droit). */
    val plusSide: String = "RIGHT",
    /** 1.8 : rappels (minute du jour, −1 : coupé) et coucher conseillé. */
    val remindWeigh: Int = -1,
    val remindLog: Int = -1,
    val remindJournal: Int = -1,
    val remindBedtime: Boolean = false,
    /** Ressenti du matin demandé avant d'afficher Charge, Forme et sommeil. */
    val morningCheck: Boolean = false,
    /** 2.0 : sommeil, chacun désactivable — fenêtre d'endormissement (Coucher conseillé), nuit comparée à vos habitudes. */
    val sleepGate: Boolean = true,
    val sleepCompare: Boolean = true,
    /** 2.0 : appareil de la VFC de chaque jour (epochDay → nom), pour ne comparer la VFC qu'aux nuits du même appareil. */
    val hrvSources: Map<Long, String> = emptyMap(),
    /** 2.1 : cartes du sommeil et des constantes déjà proposées (migration unique). */
    val nightCardsAdded: Boolean = false,
    /** 2.2 : carte Scores du jour ajoutée une fois, en tête. */
    val scoresAdded: Boolean = false,
    /** 1.8 : journal du moral, forme du jour et coucher conseillé déjà proposés (migration unique). */
    val wellbeingAdded: Boolean = false,
    /** Règles lues dans Apple Santé (autorisation à part, demandée depuis l'écran Cycle). */
    val cycleSync: Boolean = false,
    /** Mesures de la carte Constantes (pièce maîtresse du tableau de bord), noms de [app.superhealth.health.data.Metric]. */
    val vitals: List<String> = listOf("RESTING_HR", "RESPIRATORY_RATE", "HRV"),
    /** Constantes déjà placées en tête du tableau de bord (migration unique). */
    val vitalsHero: Boolean = false,
    /** Taille de chaque carte (nom de [DashCard] → nom de [CardSize]) ; absente : taille par défaut. */
    val dashSizes: Map<String, String> = emptyMap(),
    /** Mesures des anneaux du jour, de l'extérieur vers l'intérieur (noms de Rings.Kind). */
    val rings: List<String> = listOf("MOVE", "EXERCISE", "KCAL"),
    /** Anneaux du jour déjà placés au-dessus des constantes (migration unique). */
    val ringsAdded: Boolean = false,
    /** Cartes reprises de MacroFactor (habitudes, bilan, prochaine séance…) déjà proposées (migration unique). */
    val mfCardsAdded: Boolean = false,
    /** Cartes montre 1.1 et Progression déjà ajoutées (migration unique). */
    val watchCardsAdded: Boolean = false,
    /** Insights et corrélations déjà ajoutés au tableau de bord (migration unique). */
    val insightsAdded: Boolean = false,
    /** 1.6 : carte « Ma semaine » du coach déjà placée sous la carte Coach (migration unique). */
    val coachWeekAdded: Boolean = false,
    /** Les deux séries superposées de la carte Corrélations (noms de InsightData.Source) et la période en jours. */
    val corr: List<String> = listOf("SLEEP", "VOLUME"),
    val corrDays: Int = 30,
    /** Demande de pesée à l'ouverture tant qu'aucune pesée n'est saisie aujourd'hui ; [weighSkipDay] : « pas aujourd'hui ». */
    val weighPrompt: Boolean = true,
    val weighSkipDay: Long = 0,
    /** Fonds de carte OpenStreetMap des activités (seule connexion liée à vos parcours ; sans eux, le tracé seul). */
    val onlineMaps: Boolean = true,
    /** Sauvegarde automatique chiffrée : destination (nom de backup.Remote.Kind), serveur WebDAV, dossier (arbre SAF), fréquence en jours (0 : jamais). */
    val backupDest: String = "NONE",
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val backupFolder: String? = null,
    val backupEvery: Int = 0,
    val lastBackup: Long = 0,
    val lastBackupError: String? = null,
    /** Mises à jour depuis les versions GitHub : vérification quotidienne, confirmation par notification, dépôt « propriétaire/nom ». */
    val updateCheck: Boolean = true,
    val updateConfirm: Boolean = true,
    val updateRepo: String = "Ouinon0/SuperHealth",
    val lastUpdateCheck: Long = 0,
    val updateSkip: String? = null,
    /** Modules : sources ajoutées à la main (non vérifiées), mises à jour automatiques. */
    val moduleSources: List<String> = emptyList(),
    val modulesAuto: Boolean = true,
    /** Coach IA : ton (nom de coach.Coach.Tone), ce qu'il doit retenir (écrit ou validé par l'utilisateur), modèle dédié (vide : celui de l'IA). */
    val coachTone: String = "WARM",
    val coachMemory: List<String> = emptyList(),
    /** 2.0 : catégories que le coach ne reçoit pas (noms de coach.Coach.Share). */
    val coachHidden: Set<String> = emptySet(),
    /** Catégories sensibles (moral, cycle) envoyées au coach seulement si l'utilisateur les active. */
    val coachShared: Set<String> = emptySet(),
    val coachModel: String = "",
    val coachBrief: Boolean = true,
    /** Planificateur de repas : aliments à éviter (texte libre, transmis au fournisseur). */
    val mealsExclude: String = "",
    /** Mesures corporelles suivies (codes de BodyMetric), comme le choix des mesures visibles de MacroFactor. */
    val bodyMetrics: List<String> = listOf("w", "h", "c", "b", "lb", "rb", "lt", "rt"),
    /** Photos de progression : paliers de l'objectif déjà photographiés ou écartés (0, 25, 50, 75, 100 %), pour l'objectif commencé le [photoGoalStart]. */
    val photoMilestones: List<Int> = emptyList(),
    val photoGoalStart: Long = -1,
    /** Carte « Corps » déjà ajoutée au tableau de bord (migration unique). */
    val bodyCardAdded: Boolean = false,
    /** iOS : carte « Signes vitaux de la nuit » déjà placée sous les constantes (migration unique). */
    val appleCardsAdded: Boolean = false,
    /** Parties de l'app activées (noms de [Feature]). */
    val features: Set<String> = Feature.entries.filter { it != Feature.CYCLE }.map { it.name }.toSet(),
    /** Labo de style (version test) : cartes du tableau de bord en clair et en sombre, séance de musculation. Noms des enums de ui.theme.StyleLab. */
    val styleCardsLight: String = "NEUTRAL",
    val styleCardsDark: String = "NEUTRAL",
    val styleWorkoutColor: String = "BLUE",
    val styleHero: String = "LARGE",
    val styleSets: String = "CARDS",
    /** Dernière synchronisation Health Connect (ms) et son résumé. */
    val lastHcSync: Long = 0,
    val lastHcSummary: String? = null,
)

/** Mesures que la carte Constantes peut afficher (montre ou Health Connect). */
val VITAL_CHOICES = listOf("RESTING_HR", "HEART_RATE", "RESPIRATORY_RATE", "BODY_ENERGY", "STRESS", "SPO2", "HRV", "SLEEP", "SLEEP_SCORE", "FITNESS_AGE",
    "WALKING_HR", "HR_RECOVERY", "BREATHING_DISTURBANCES", "DAYLIGHT", "STAND_HOURS", "NOISE",
    "READINESS", "VO2MAX", "TEMPERATURE", "STEPS")

val SuperSettings.vitalMetrics: List<app.superhealth.health.data.Metric>
    get() = vitals.mapNotNull { n -> app.superhealth.health.data.Metric.entries.firstOrNull { it.name == n } }

val SuperSettings.plusActions: List<PlusAction> get() = plus.mapNotNull { n -> PlusAction.entries.firstOrNull { it.name == n } }
    .filter { a -> a.feature()?.let { has(it) } ?: true }

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
    /** Source de la donnée la plus récente de chaque mesure (nom de [app.superhealth.health.data.Metric]) : appli et type d'appareil. */
    val origins: Map<String, app.superhealth.health.data.DataSource> = emptyMap(),
    /** Nuit de chaque jour (jour du réveil → phases) écrite par l'appli retenue pour ce jour-là. */
    val nights: Map<Long, List<HcPhase>> = emptyMap(),
)

/** Phase de sommeil lue dans Health Connect : type (nom d'ActivityKind : DEEP_SLEEP, SLEEP_ANY…), début et fin en ms. */
@Serializable
data class HcPhase(val kind: String, val start: Long, val end: Long)

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
    private lateinit var sp: SharedPreferences
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
        // 1.7.5 : mesures et photos de nouveau visibles, en carte du tableau de bord (après « À faire »)
        if (!_settings.value.bodyCardAdded) update { s ->
            val i = s.dashboard.indexOf(DashCard.FOCUS)
            s.copy(dashboard = if (DashCard.BODY in s.dashboard) s.dashboard else s.dashboard.toMutableList().apply { add(if (i >= 0) i + 1 else size, DashCard.BODY) },
                bodyCardAdded = true)
        }
        if (!_settings.value.mfCardsAdded) update { it.copy(dashboard = (it.dashboard + DashCard.newMacroFactorCards).distinct(), mfCardsAdded = true) }
        // Insights sous les anneaux (ou en tête), corrélations en fin de tableau de bord.
        if (!_settings.value.insightsAdded) update { s ->
            val cards = s.dashboard.toMutableList()
            if (DashCard.INSIGHTS !in cards) cards.add((cards.indexOf(DashCard.RINGS) + 1).coerceAtLeast(0), DashCard.INSIGHTS)
            if (DashCard.CORRELATION !in cards) cards.add(DashCard.CORRELATION)
            s.copy(dashboard = cards, insightsAdded = true)
        }
        // 1.1 : graphiques de montre après les constantes (masqués sans montre qui mesure)
        if (!_settings.value.watchCardsAdded) update { s ->
            val cards = s.dashboard.toMutableList()
            val at = (cards.indexOf(DashCard.VITALS) + 1).takeIf { it > 0 } ?: cards.size
            cards.addAll(at, DashCard.watchCards11.filter { it !in cards })
            s.copy(dashboard = cards, watchCardsAdded = true)
        }
        // 1.6 : « Ma semaine » juste sous la carte Coach (sans Coach au tableau de bord, elle reste à ajouter à la main)
        if (!_settings.value.coachWeekAdded) update { s ->
            val cards = s.dashboard.toMutableList()
            val at = cards.indexOf(DashCard.COACH)
            if (at >= 0 && DashCard.COACH_WEEK !in cards) cards.add(at + 1, DashCard.COACH_WEEK)
            s.copy(dashboard = cards, coachWeekAdded = true)
        }
        // iOS : signes vitaux de la nuit juste sous les constantes
        if (!_settings.value.appleCardsAdded) update { s ->
            val cards = s.dashboard.toMutableList()
            if (DashCard.NIGHT_VITALS !in cards) cards.add((cards.indexOf(DashCard.VITALS) + 1).takeIf { it > 0 } ?: cards.size, DashCard.NIGHT_VITALS)
            s.copy(dashboard = cards, appleCardsAdded = true)
        }
        // 1.8 : journal du moral activé une fois ; forme du jour en tête, journal et coucher conseillé juste après
        if (!_settings.value.wellbeingAdded) update { s ->
            val cards = s.dashboard.toMutableList()
            val at = (cards.indexOf(DashCard.RINGS) + 1).coerceAtLeast(0)
            listOf(DashCard.FORM, DashCard.JOURNAL, DashCard.BEDTIME).filter { it !in cards }.reversed().forEach { cards.add(at, it) }
            s.copy(dashboard = cards, features = s.features + Feature.JOURNAL.name, wellbeingAdded = true,
                plus = (s.plus + listOf(PlusAction.MOOD.name, PlusAction.BREATHE.name)).distinct())
        }
        // 2.1 : nouvelles cartes du sommeil après la carte Sommeil, celles des constantes après Constantes (masquées sans montre)
        if (!_settings.value.nightCardsAdded) update { s ->
            val cards = s.dashboard.toMutableList()
            val sleep = listOf(DashCard.SLEEP_QUALITY, DashCard.SLEEP_SCHEDULE, DashCard.NIGHT_HEART).filter { it !in cards }
            val at = maxOf(cards.indexOf(DashCard.SLEEP), cards.indexOf(DashCard.SLEEP_STAGES)).let { if (it >= 0) it + 1 else cards.size }
            cards.addAll(at, sleep)
            val vitals = listOf(DashCard.NIGHT_VITALS, DashCard.VITALS_TRENDS).filter { it !in cards }
            cards.addAll((cards.indexOf(DashCard.VITALS) + 1).takeIf { it > 0 } ?: cards.size, vitals)
            s.copy(dashboard = cards, nightCardsAdded = true)
        }
        // 2.2 : Scores du jour en tête du tableau de bord (masquée sans montre)
        if (!_settings.value.scoresAdded) update { s ->
            s.copy(dashboard = if (DashCard.SCORES in s.dashboard) s.dashboard else listOf(DashCard.SCORES) + s.dashboard, scoresAdded = true)
        }
        _hc.value = sp.getString("hc", null)?.let { runCatching { SuperJson.decodeFromString(HcCache.serializer(), it) }.getOrNull() } ?: HcCache()
    }


    fun update(f: (SuperSettings) -> SuperSettings) {
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

    fun setHc(c: HcCache) {
        _hc.value = c
        sp.edit().putString("hc", SuperJson.encodeToString(HcCache.serializer(), c)).apply()
    }
}
