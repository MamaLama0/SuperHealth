package app.superhealth.training.domain

import app.superhealth.platform.*

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.superhealth.platform.Normalizer

/** Exercice de la base MacroFactor Workouts (assets/db/exercises.json, généré par tools/import_workout_assets.py). */
@Serializable
data class Exercise(
    val id: String,
    @SerialName("n") val name: String,
    @SerialName("alt") val alternatives: List<String> = emptyList(),
    /** compound / isolation / core */
    @SerialName("t") val type: String = "compound",
    /** upper / lower / core / full */
    @SerialName("rg") val region: String = "full",
    @SerialName("lat") val laterality: String = "bilateral",
    @SerialName("mp") val movementPatterns: List<String> = emptyList(),
    @SerialName("pf") val primaryGroups: List<String> = emptyList(),
    @SerialName("sf") val secondaryGroups: List<String> = emptyList(),
    @SerialName("pm") val primaryMuscles: List<String> = emptyList(),
    @SerialName("sm") val secondaryMuscles: List<String> = emptyList(),
    @SerialName("re") val resistanceGroups: List<String> = emptyList(),
    @SerialName("se") val supportGroups: List<String> = emptyList(),
    /** Niveaux de recommandation : force (composé principal, secondaire, accessoire) puis hypertrophie (idem). */
    @SerialName("rec") val recommendation: List<Double?> = emptyList(),
    @SerialName("cs") val strengthClass: List<String> = emptyList(),
    @SerialName("ch") val hypertrophyClass: List<String> = emptyList(),
    /** Métriques : w, r, d, wps, rps, aw… */
    @SerialName("m") val metrics: List<String> = emptyList(),
    val rom: Double? = null,
    @SerialName("st") val stability: Double? = null,
    /** Fraction du poids de corps soulevée (exercices au poids du corps). */
    @SerialName("bw") val bodyweight: Double? = null,
    @SerialName("img") val images: List<String> = emptyList(),
    @SerialName("pi") val preferredImage: String? = null,
    @SerialName("md") val hasGuide: Boolean = false,
    val notes: List<List<String>> = emptyList(),
    @SerialName("b") val searchBoost: Double? = null,
    @SerialName("grp") val groups: List<String> = emptyList(),
    @SerialName("ex") val exclusionGroups: List<String> = emptyList(),
) {
    val muscleGroups: List<MuscleGroup> get() = primaryGroups.mapNotNull { MuscleGroup.of(it) }
    val secondaryMuscleGroups: List<MuscleGroup> get() = secondaryGroups.mapNotNull { MuscleGroup.of(it) }
    val upperBody get() = region == "upper"
    val isCompound get() = type == "compound"
    val unilateral get() = laterality != "bilateral" || metrics.any { it.endsWith("ps") }
    val usesWeight get() = metrics.any { it.startsWith("w") || it == "aw" }
    val usesReps get() = metrics.any { it.startsWith("r") }
    val usesDuration get() = metrics.any { it.startsWith("d") && it != "ds" && it != "dl" }
    fun thumbnail(): String? = (preferredImage ?: images.firstOrNull())?.let { "ex/${id}_$it.webp" }
}

@Serializable
data class Equipment(
    val id: String,
    @SerialName("n") val name: String,
    @SerialName("cat") val category: String? = null,
    val kg: Double? = null,
    @SerialName("ikg") val incrementKg: Double? = null,
    @SerialName("skg") val selectKg: String? = null,
    @SerialName("pl") val pluralOf: String? = null,
    val gyms: List<String> = emptyList(),
    @SerialName("pcl") val plateLaterality: String? = null,
    val img: Boolean = false,
)

@Serializable
data class EquipmentGroup(@SerialName("n") val name: String, val eq: List<String> = emptyList(), @SerialName("s") val support: Boolean = false)

@Serializable
data class ExerciseDbFile(
    val v: String,
    val exercises: List<Exercise>,
    val equipment: List<Equipment>,
    val groups: Map<String, EquipmentGroup>,
    val categories: List<String> = emptyList(),
)

class ExerciseDb(private val file: ExerciseDbFile, val custom: List<Exercise> = emptyList()) {
    val version = file.v
    val exercises = file.exercises + custom
    val equipment = file.equipment
    val groups = file.groups
    val categories = file.categories
    private val byId = exercises.associateBy { it.id }
    private val equipmentById = equipment.associateBy { it.id }
    private val keys = exercises.associate { it.id to norm((listOf(it.name) + it.alternatives).joinToString(" ")) }
    /** Noms normalisés, calculés une fois : le tri de la recherche ne refait plus de regex à chaque comparaison. */
    private val names = exercises.associate { it.id to norm(it.name) }
    private val defaultOrder = compareByDescending<Exercise> { it.searchBoost ?: 0.0 }.thenBy { it.name }

    fun exercise(id: String) = byId[id]
    fun isCustom(id: String) = id.startsWith(CUSTOM_PREFIX)

    /** Même base avec une autre liste d'exercices personnels. */
    fun withCustom(list: List<Exercise>) = if (list == custom) this else ExerciseDb(file, list)
    fun equipment(id: String) = equipmentById[id]

    /** Une option de résistance / support est un groupe d'équipements ou un équipement seul (les deux existent dans la base). */
    fun option(id: String): List<String> = groups[id]?.eq ?: listOf(id)

    /** Équipements nécessaires à au moins une variante de résistance (et de support si l'exercice en exige). */
    fun isAvailable(e: Exercise, owned: Set<String>): Boolean {
        fun ok(ids: List<String>) = ids.isEmpty() || ids.any { opt -> option(opt).all { it in owned || isBasic(it) } }
        return ok(e.resistanceGroups) && ok(e.supportGroups)
    }

    /** Équipement toujours présent (poids du corps, sol…). */
    private fun isBasic(id: String) = equipmentById[id]?.category == "Body Weights"

    /** Groupe de résistance réellement utilisable (pour le calculateur de disques et l'arrondi des charges). */
    fun resistanceEquipment(e: Exercise, owned: Set<String>?): List<Equipment> {
        val opt = e.resistanceGroups.firstOrNull { o -> owned == null || option(o).all { it in owned || isBasic(it) } }
            ?: e.resistanceGroups.firstOrNull() ?: return emptyList()
        return option(opt).mapNotNull { equipmentById[it] }
    }

    /** Recherche : tous les mots présents ; tri par début de nom, boost de recherche, longueur. */
    fun search(query: String, pool: List<Exercise> = exercises): List<Exercise> {
        val words = norm(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return pool.sortedWith(defaultOrder)
        val q = words.joinToString(" ")
        return pool.filter { e -> words.all { keys.getValue(e.id).contains(it) } }
            .sortedWith(compareBy<Exercise>({ names[it.id]?.startsWith(q) != true }, { -(it.searchBoost ?: 0.0) }, { it.name.length }))
    }

    companion object {
        const val CUSTOM_PREFIX = "custom-"
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        fun parse(text: String) = ExerciseDb(json.decodeFromString(ExerciseDbFile.serializer(), text))
        fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]+"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
