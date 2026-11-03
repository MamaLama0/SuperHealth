package app.superhealth.stretch

import android.content.Context
import app.superhealth.modules.Modules
import app.superhealth.training.domain.MuscleGroup
import java.io.File
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Plugin Étirements. Le moteur est ici ; le plugin apporte le catalogue (exercices, consignes, indicateurs avant / après séance
 * et quotidien, zones) et les vignettes. Les vidéos sont lues en flux depuis les adresses données par le catalogue.
 *
 * Le choix des exercices d'une routine ([routine]) est propre à SuperHealth : GOWOD génère ses protocoles sur son serveur.
 */
object Stretch {
    @Serializable
    data class Ex(
        val id: String, val name: String, val type: String, val area: String? = null,
        val zones: List<String> = emptyList(), val gears: List<String> = emptyList(),
        val uni: Boolean = false, val daily: Boolean = false, val pre: Boolean = false, val post: Boolean = false,
        val recup: String? = null, val like: String? = null, val backup: String? = null,
        /** Durée de référence en minutes (par côté pour un exercice unilatéral). */
        val min: Int = 1, val thumb: Boolean = false,
        val secondsPerSide: Int? = null,
        val fr: List<String> = emptyList(), val frFeel: String? = null,
        val en: List<String> = emptyList(), val enFeel: String? = null,
    ) {
        val steps get() = if (app.superhealth.ui.Lang.english) en else fr
        val feel get() = if (app.superhealth.ui.Lang.english) enFeel else frFeel
        /** Temps total en secondes, les deux côtés compris. */
        val sideSeconds get() = (secondsPerSide ?: min * 60).coerceIn(15, 600)
        val seconds get() = sideSeconds * if (uni) 2 else 1
    }

    @Serializable data class Catalog(val video: String = "", val poster: String = "", val exercises: List<Ex> = emptyList())

    enum class Kind(val label: String) { DAILY("Quotidien"), PRE("Avant séance"), POST("Après séance") }

    /** Zones du catalogue regroupées pour le choix. */
    enum class Group(val label: String, val zones: Set<String>) {
        SHOULDERS("Épaules", setOf("Shoulders", "Rotators", "Traps")),
        CHEST("Pectoraux", setOf("Pecs")),
        BACK("Dos", setOf("Lats", "LumbarMuscles")),
        CORE("Tronc", setOf("Abs", "Obliques")),
        ARMS("Bras", setOf("Biceps", "Triceps", "Flexors", "Extensors")),
        HIPS("Hanches", setOf("Hips", "Glutes", "Adductors")),
        THIGHS("Cuisses", setOf("Quads", "Hamstrings", "ItBands")),
        LOWER_LEGS("Mollets et chevilles", setOf("Calves", "Ankles", "Feet", "Shins", "PostShins")),
    }

    enum class Gear(val key: String, val label: String) {
        BAND("Band", "Élastique"), ROLLER("MassageRoller", "Rouleau"), BALL("Lacrosse", "Balle"), PIPE("Pipe", "Tube"), BOX("Box", "Banc"),
        BARBELL("Barbell", "Barre"), PLATE("Plate", "Disque"),
    }

    @Serializable data class Routine(val name: String, val kind: Kind, val items: List<Ex>, val id: String? = null,
        val focusGroups: Set<Group> = emptySet(), val focusZones: Set<String> = emptySet(),
        val plannedMinutes: Int? = null, val defaultSeconds: Int? = null) {
        val seconds get() = items.sumOf { it.seconds }
    }

    @Serializable data class Done(val day: Long, val at: Long, val minutes: Int, val kind: String, val zones: List<String> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var cache: Catalog? = null

    fun available() = Modules.isActive(Modules.STRETCH)
    fun catalog(): Catalog = cache ?: runCatching {
        json.decodeFromString(Catalog.serializer(), Modules.file(Modules.STRETCH, "exercises.json")!!.readText())
    }.getOrDefault(Catalog()).also { if (it.exercises.isNotEmpty()) cache = it }
    fun invalidate() { cache = null; mobilityCache = null }

    val customExercises = MutableStateFlow<List<Ex>>(emptyList())
    val saved = MutableStateFlow<List<Routine>>(emptyList())
    val draft = MutableStateFlow<Routine?>(null)
    fun exercises() = catalog().exercises + customExercises.value
    fun exercise(id: String) = exercises().firstOrNull { it.id == id }
    fun prepare(routine: Routine? = null) { draft.value = routine ?: Routine("", Kind.DAILY, emptyList()) }
    fun save(ctx: Context, routine: Routine): Routine {
        require(routine.name.isNotBlank() && routine.items.isNotEmpty() && routine.items.size <= 64)
        val next = routine.copy(id = routine.id ?: java.util.UUID.randomUUID().toString(), name = routine.name.trim().take(80))
        saved.value = (saved.value.filterNot { it.id == next.id } + next).takeLast(64)
        writePersonal(ctx)
        return next
    }
    fun removeSaved(ctx: Context, id: String) { saved.value = saved.value.filterNot { it.id == id }; writePersonal(ctx) }
    fun saveExercise(ctx: Context, ex: Ex) {
        require(ex.id.startsWith("custom-") && ex.name.isNotBlank() && ex.zones.isNotEmpty())
        customExercises.value = (customExercises.value.filterNot { it.id == ex.id } + ex).takeLast(200)
        writePersonal(ctx)
    }
    @Serializable private data class Personal(val routines: List<Routine> = emptyList(), val exercises: List<Ex> = emptyList())
    private fun writePersonal(ctx: Context) {
        val atomic = android.util.AtomicFile(File(ctx.filesDir, "stretch_personal.json"))
        val stream = atomic.startWrite()
        try { stream.write(json.encodeToString(Personal.serializer(), Personal(saved.value, customExercises.value)).toByteArray()); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
    fun alternatives(ex: Ex, pool: List<Ex>, gears: Set<String>, sameType: Boolean = true): List<Ex> = pool.filter { e ->
        e.id != ex.id && e.gears.all { it in gears } && (!sameType || e.type == ex.type) && e.zones.any { it in ex.zones }
    }.sortedWith(compareByDescending<Ex> { e -> e.zones.count { it in ex.zones } }.thenBy { it.name })
    fun replace(routine: Routine, index: Int, replacement: Ex): Routine {
        require(index in routine.items.indices)
        val original = routine.items[index]
        return routine.copy(items = routine.items.toMutableList().also { it[index] = replacement.copy(secondsPerSide = original.sideSeconds) })
    }
    /** Display mapping shared with the workout anatomy preview. */
    fun musclesFor(zones: Iterable<String>): Set<MuscleGroup> {
        val keys = zones.flatMap { zone -> when (zone) {
            "Shoulders", "Rotators" -> listOf("frontDelts", "sideDelts", "rearDelts")
            "Traps" -> listOf("upperTraps"); "Pecs" -> listOf("chest"); "Lats" -> listOf("lats")
            "LumbarMuscles" -> listOf("lowerBack"); "Abs" -> listOf("abs"); "Obliques" -> listOf("obliques")
            "Biceps" -> listOf("biceps"); "Triceps" -> listOf("triceps"); "Flexors", "Extensors" -> listOf("forearms")
            "Hips" -> listOf("hipFlexors"); "Glutes" -> listOf("glutes"); "Adductors" -> listOf("adductors")
            "Quads" -> listOf("quads"); "Hamstrings" -> listOf("hamstrings"); "ItBands" -> listOf("abductors")
            "Calves", "Ankles", "Feet" -> listOf("calves"); "Shins", "PostShins" -> listOf("tibs")
            else -> emptyList()
        } }.toSet()
        return MuscleGroup.entries.filter { it.key in keys }.toSet()
    }
    fun videoUrl(id: String) = catalog().video.replace("{id}", id)
    fun posterUrl(id: String) = catalog().poster.replace("{id}", id)
    fun thumb(id: String): File? = Modules.file(Modules.STRETCH, "thumbs/$id.jpg")

    /** Routine à jouer (écran de lecture), posée par l'écran de préparation ou le coach. */
    val current = MutableStateFlow<Routine?>(null)

    /**
     * Routine de [minutes] sur les [groups] (toutes zones si vide). Filtre : indicateur du catalogue pour ce moment, matériel possédé.
     * Choix glouton : à chaque tour, l'exercice qui couvre le mieux les zones les moins travaillées, la zone principale (première) comptant
     * double. Le tirage du jour départage (une routine différente chaque jour, identique dans la journée).
     */
    fun routine(kind: Kind, groups: Set<Group>, minutes: Int, gears: Set<String>, day: Long = LocalDate.now().toEpochDay(), zones: Set<String> = emptySet(), favorites: Set<String> = emptySet(), secondsPerSide: Int? = null, availableExercises: List<Ex> = exercises(), disliked: Set<String> = emptySet(), preferredZones: Set<String> = emptySet()): Routine {
        val pool = availableExercises.filter { e ->
            e.id !in disliked && when (kind) { Kind.DAILY -> e.daily; Kind.PRE -> e.pre; Kind.POST -> e.post } &&
                e.gears.all { it in gears } && (e.type != "Massage" || e.gears.isNotEmpty() || e.id.startsWith("custom-"))
        }
        val target = zones.ifEmpty { groups.flatMap { it.zones }.toSet() }.ifEmpty { Group.entries.flatMap { it.zones }.toSet() }
        val cover = HashMap<String, Int>()
        val rnd = Random(day * 31 + kind.ordinal + groups.hashCode())
        val left = pool.filter { e -> e.zones.any { it in target } }.map { if (secondsPerSide == null) it else it.copy(secondsPerSide = secondsPerSide.coerceIn(15, 600)) }.toMutableList()
        val picked = ArrayList<Ex>()
        var budget = minutes.coerceIn(1, 60) * 60
        while (left.isNotEmpty()) {
            val best = left.maxByOrNull { e ->
                e.zones.withIndex().sumOf { (i, z) -> if (z in target) ((if (i == 0) 2.0 else 1.0) + if (z in preferredZones) 0.35 else 0.0) / (1 + (cover[z] ?: 0)) else 0.0 } + (if (e.id in favorites) 0.3 else 0.0) + rnd.nextDouble() * 0.4
            }!!
            left.remove(best)
            val item = if (best.seconds <= budget) best else {
                val perSide = budget / if (best.uni) 2 else 1
                if (perSide < 15) break
                best.copy(secondsPerSide = perSide.coerceAtMost(best.sideSeconds))
            }
            picked += item
            budget -= item.seconds
            best.zones.forEach { cover[it] = (cover[it] ?: 0) + 1 }
        }
        return Routine(if (groups.size == 1) groups.first().label else kind.label, kind, ordered(picked, kind),
            focusGroups = groups, focusZones = zones, plannedMinutes = minutes.coerceIn(1, 60), defaultSeconds = secondsPerSide)
    }

    /** Ordre de passage : le moins de changements de position possible ; avant séance on finit debout, après séance au sol. */
    private fun ordered(list: List<Ex>, kind: Kind): List<Ex> {
        val rank = listOf("Floor", "FloorWall", "FloorRack", "StandWall", "StandRack", "Stand")
        val up = list.sortedBy { rank.indexOf(it.area).let { r -> if (r < 0) 0 else r } }
        return if (kind == Kind.POST) up.reversed() else up
    }

    /** Zones à étirer après une séance : les groupes des muscles travaillés. */
    fun groupsFor(muscles: Collection<MuscleGroup>): Set<Group> = muscles.mapNotNull {
        when (it) {
            MuscleGroup.CHEST -> Group.CHEST
            MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS, MuscleGroup.REAR_DELTS, MuscleGroup.UPPER_TRAPS, MuscleGroup.NECK, MuscleGroup.SERRATUS -> Group.SHOULDERS
            MuscleGroup.TRICEPS, MuscleGroup.BICEPS, MuscleGroup.FOREARMS -> Group.ARMS
            MuscleGroup.LATS, MuscleGroup.UPPER_BACK, MuscleGroup.LOWER_BACK -> Group.BACK
            MuscleGroup.ABS, MuscleGroup.OBLIQUES -> Group.CORE
            MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS -> Group.THIGHS
            MuscleGroup.GLUTES, MuscleGroup.ADDUCTORS, MuscleGroup.ABDUCTORS, MuscleGroup.HIP_FLEXORS -> Group.HIPS
            MuscleGroup.CALVES, MuscleGroup.TIBS -> Group.LOWER_LEGS
            else -> null
        }
    }.toSet()

    // ---- Test de mobilité (tests, niveaux et points de GOWOD) -----------------------------------------------

    @Serializable data class MTest(val id: String, val focus: String, val coeff: Double = 1.0, val max: Double = 1.0, val uni: Boolean = false, val pts: List<Double> = emptyList(),
                                   val images: Map<String, List<String>> = emptyMap(), val fr: List<String> = emptyList(), val en: List<String> = emptyList(),
                                   val frAnat: String? = null, val enAnat: String? = null) {
        val steps get() = if (app.superhealth.ui.Lang.english) en else fr
        val anatomy get() = (if (app.superhealth.ui.Lang.english) enAnat else frAnat) ?: id
        fun images(side: String): List<String> = images[side] ?: images["both"].orEmpty()
        /** Taux d'un niveau : points du niveau / points maximum (getScoreByMobilityTest de GOWOD). */
        fun pct(level: Int) = ((pts.getOrElse(level.coerceIn(0, pts.lastIndex)) { 0.0 } / max) * 100).coerceIn(0.0, 100.0)
    }

    @Serializable data class Mobility(val image: String = "", val tests: List<MTest> = emptyList(), val averages: List<List<Double>> = emptyList())

    /** Résultat : pour chaque test, les niveaux choisis (un par côté). */
    @Serializable data class MResult(val at: Long, val levels: Map<String, List<Int>>)

    val FOCI = listOf("Shoulders", "Overhead", "Hips", "Postchain", "Ankles")
    fun focusLabel(f: String) = when (f) { "Shoulders" -> "Épaules"; "Overhead" -> "Bras au-dessus de la tête"; "Hips" -> "Hanches"; "Postchain" -> "Chaîne postérieure"; "Ankles" -> "Chevilles"; else -> f }

    private var mobilityCache: Mobility? = null
    fun mobility(): Mobility = mobilityCache ?: runCatching {
        json.decodeFromString(Mobility.serializer(), Modules.file(Modules.STRETCH, "mobility.json")!!.readText())
    }.getOrDefault(Mobility()).also { if (it.tests.isNotEmpty()) mobilityCache = it }
    fun imageUrl(name: String) = mobility().image.replace("{name}", name)

    val results = MutableStateFlow<List<MResult>>(emptyList())
    private fun resultsFile(ctx: Context) = File(ctx.filesDir, "stretch_mobility.json")
    fun saveResult(ctx: Context, r: MResult) {
        results.value = (results.value + r).takeLast(60)
        runCatching { resultsFile(ctx).writeText(json.encodeToString(ListSerializer(MResult.serializer()), results.value)) }
    }

    private fun testPct(t: MTest, r: MResult) = r.levels[t.id]?.takeIf { it.isNotEmpty() }?.map { t.pct(it) }?.average()

    /** Score d'une partie du corps : moyenne de ses tests pondérée par leur coefficient. */
    fun focusScore(f: String, r: MResult): Int? {
        val l = mobility().tests.filter { it.focus == f }.mapNotNull { t -> testPct(t, r)?.let { it to t.coeff } }
        return if (l.isEmpty()) null else (l.sumOf { it.first * it.second } / l.sumOf { it.second }).toInt()
    }

    /**
     * Score global sur 100 : moyenne de tous les tests pondérée par leur coefficient. Reconstitution : l'app GOWOD reçoit ce total de son
     * serveur (le calcul n'est pas dans son binaire), seuls les points, coefficients et le taux par test en viennent.
     */
    fun globalScore(r: MResult): Int? {
        val l = mobility().tests.mapNotNull { t -> testPct(t, r)?.let { it to t.coeff } }
        return if (l.isEmpty()) null else (l.sumOf { it.first * it.second } / l.sumOf { it.second }).toInt()
    }

    /** Moyenne publiée par GOWOD pour cet âge et ce sexe (colonne 0 : global ; ensuite épaules, overhead, hanches, chaîne post., chevilles, thorax). */
    fun average(age: Int, male: Boolean): List<Double>? {
        val rows = mobility().averages.takeIf { it.size == 13 } ?: return null
        val range = when (age) { in 18..33 -> 0; in 34..45 -> 1; in 46..55 -> 2; in 56..65 -> 3; else -> return rows[12] }
        return rows[range * 3 + if (male) 0 else 1]
    }

    /** Les zones à travailler : les deux parties les plus faibles du dernier test. */
    fun weakGroups(r: MResult, history: List<MResult> = emptyList()): Set<Group> = FOCI.mapNotNull { f ->
        focusScore(f, r)?.let { last ->
            val previous = history.filter { it.at in (r.at - 90L * 86_400_000) until r.at }.sortedByDescending { it.at }
                .mapNotNull { focusScore(f, it) }.take(2)
            f to (previous.sum() + 2.0 * last) / (2 + previous.size)
        }
    }.sortedBy { it.second }.take(2).flatMap { (f, _) ->
        when (f) {
            "Shoulders" -> listOf(Group.SHOULDERS)
            "Overhead" -> listOf(Group.SHOULDERS, Group.BACK)
            "Hips" -> listOf(Group.HIPS)
            "Postchain" -> listOf(Group.THIGHS, Group.BACK)
            else -> listOf(Group.LOWER_LEGS)
        }
    }.toSet()

    // ---- Journal : alimente la carte, le coach et le classement ----------------------------------------

    val log = MutableStateFlow<List<Done>>(emptyList())
    private fun file(ctx: Context) = File(ctx.filesDir, "stretch_log.json")

    fun load(ctx: Context) {
        val personal = runCatching { json.decodeFromString(Personal.serializer(), android.util.AtomicFile(File(ctx.filesDir, "stretch_personal.json")).openRead().bufferedReader().use { it.readText() }) }.getOrDefault(Personal())
        saved.value = personal.routines.takeLast(64); customExercises.value = personal.exercises.takeLast(200)
        log.value = runCatching { json.decodeFromString(ListSerializer(Done.serializer()), file(ctx).readText()) }.getOrDefault(emptyList())
        results.value = runCatching { json.decodeFromString(ListSerializer(MResult.serializer()), resultsFile(ctx).readText()) }.getOrDefault(emptyList())
    }

    fun record(ctx: Context, r: Routine, seconds: Int) {
        if (seconds < 30) return
        val d = Done(LocalDate.now().toEpochDay(), System.currentTimeMillis(), (seconds + 30) / 60, r.kind.name, r.items.flatMap { it.zones }.distinct())
        log.value = (log.value + d).takeLast(1000)
        runCatching { file(ctx).writeText(json.encodeToString(ListSerializer(Done.serializer()), log.value)) }
    }

    fun minutes(from: Long, to: Long = Long.MAX_VALUE) = log.value.filter { it.day in from..to }.sumOf { it.minutes }
}
