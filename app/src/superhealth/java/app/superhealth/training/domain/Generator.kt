package app.superhealth.training.domain

import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Génération intelligente de programme (WorkoutProgramService / CreateWorkoutProgramModuleSection.createProgram).
 * [OBSERVÉ] questions, structures (ProgramSplit), types de modèles (primary / secondary / accessory), catégories
 * (upper/lower compound, isolation, core), périodisation static/linear (±1 répétition par cycle, 0xed55ec), décharge.
 * [INFÉRÉ] plages de répétitions, nombre de séries et repos par type de modèle et objectif (non extraits du binaire).
 */
object Generator {
    data class Input(
        val goal: Goal,
        val daysPerWeek: Int,
        val split: Split,
        val minutes: Int,
        val periodization: Periodization,
        val deload: Deload,
        val experience: Experience,
        /** Groupes à prioriser (+) ou déprioriser (−), 5 points maximum de chaque côté (help center 285). */
        val focus: Map<MuscleGroup, Int> = emptyMap(),
        val equipment: Set<String>,
        val doNotRecommend: Set<String> = emptySet(),
        val name: String = "Mon programme",
        val color: Int = 0,
        val gymId: String? = null,
        val numCycles: Int = 6,
    )

    enum class Template { PRIMARY, SECONDARY, ACCESSORY }

    /** Emplacement d'un jour : schémas de mouvement acceptés (noms de la base) ou groupe musculaire visé. */
    data class Slot(val template: Template, val patterns: List<String>, val group: MuscleGroup? = null)

    private const val H_PUSH = "Horizontal Push"; private const val H_PULL = "Horizontal Pull"
    private const val V_PUSH = "Vertical Push"; private const val V_PULL = "Vertical Pull"
    private const val SQUAT = "Squat"; private const val HINGE = "Hip Hinge"
    private const val LUNGE = "Lunge/Split Squat/Step Up/Single Leg Squat"

    private fun acc(group: MuscleGroup, vararg p: String) = Slot(Template.ACCESSORY, p.toList(), group)

    /** Ordre des emplacements par jour-type (les premiers sont gardés quand le temps manque). */
    fun slots(day: SplitDay, variant: Int): List<Slot> = when (day) {
        SplitDay.FULL -> listOf(
            Slot(Template.PRIMARY, listOf(if (variant % 2 == 0) SQUAT else HINGE)),
            Slot(Template.PRIMARY, listOf(if (variant % 2 == 0) H_PUSH else V_PUSH)),
            Slot(Template.SECONDARY, listOf(if (variant % 2 == 0) V_PULL else H_PULL)),
            Slot(Template.SECONDARY, listOf(if (variant % 2 == 0) HINGE else LUNGE)),
            acc(MuscleGroup.SIDE_DELTS, "Lateral Delt Accessory"),
            acc(if (variant % 2 == 0) MuscleGroup.BICEPS else MuscleGroup.TRICEPS, if (variant % 2 == 0) "Biceps Accessory" else "Triceps Accessory"),
            acc(MuscleGroup.ABS, "Ab Accessory"),
            acc(MuscleGroup.CALVES, "Calf Accessory"),
        )
        SplitDay.UPPER -> listOf(
            Slot(Template.PRIMARY, listOf(if (variant % 2 == 0) H_PUSH else V_PUSH)),
            Slot(Template.PRIMARY, listOf(if (variant % 2 == 0) V_PULL else H_PULL)),
            Slot(Template.SECONDARY, listOf(if (variant % 2 == 0) V_PUSH else H_PUSH)),
            Slot(Template.SECONDARY, listOf(if (variant % 2 == 0) H_PULL else V_PULL)),
            acc(MuscleGroup.SIDE_DELTS, "Lateral Delt Accessory"),
            acc(MuscleGroup.BICEPS, "Biceps Accessory"),
            acc(MuscleGroup.TRICEPS, "Triceps Accessory"),
            acc(MuscleGroup.REAR_DELTS, "Rear Delt Accessory"),
        )
        SplitDay.LOWER -> listOf(
            Slot(Template.PRIMARY, listOf(if (variant % 2 == 0) SQUAT else HINGE)),
            Slot(Template.SECONDARY, listOf(if (variant % 2 == 0) HINGE else SQUAT)),
            Slot(Template.SECONDARY, listOf(LUNGE)),
            acc(MuscleGroup.HAMSTRINGS, "Hamstring Accessory"),
            acc(MuscleGroup.QUADS, "Quad Accessory"),
            acc(MuscleGroup.CALVES, "Calf Accessory"),
            acc(MuscleGroup.ABS, "Ab Accessory"),
            acc(MuscleGroup.GLUTES, "Glute Max Accessory"),
        )
        SplitDay.PUSH -> listOf(
            Slot(Template.PRIMARY, listOf(H_PUSH)),
            Slot(Template.SECONDARY, listOf(V_PUSH)),
            Slot(Template.SECONDARY, listOf(H_PUSH)),
            acc(MuscleGroup.CHEST, "Chest Accessory"),
            acc(MuscleGroup.SIDE_DELTS, "Lateral Delt Accessory"),
            acc(MuscleGroup.TRICEPS, "Triceps Accessory"),
            acc(MuscleGroup.TRICEPS, "Triceps Accessory"),
            acc(MuscleGroup.FRONT_DELTS, "Front Delt Accessory"),
        )
        SplitDay.PULL -> listOf(
            Slot(Template.PRIMARY, listOf(V_PULL)),
            Slot(Template.SECONDARY, listOf(H_PULL)),
            acc(MuscleGroup.LATS, "Lat Accessory"),
            acc(MuscleGroup.REAR_DELTS, "Rear Delt Accessory"),
            acc(MuscleGroup.BICEPS, "Biceps Accessory"),
            acc(MuscleGroup.UPPER_BACK, "Upper Back Accessory"),
            acc(MuscleGroup.BICEPS, "Biceps Accessory"),
            acc(MuscleGroup.UPPER_TRAPS, "Upper Trap Accessory"),
        )
        SplitDay.PULL_LEGS -> listOf(
            Slot(Template.PRIMARY, listOf(SQUAT)),
            Slot(Template.PRIMARY, listOf(V_PULL)),
            Slot(Template.SECONDARY, listOf(HINGE)),
            Slot(Template.SECONDARY, listOf(H_PULL)),
            acc(MuscleGroup.BICEPS, "Biceps Accessory"),
            acc(MuscleGroup.HAMSTRINGS, "Hamstring Accessory"),
            acc(MuscleGroup.CALVES, "Calf Accessory"),
            acc(MuscleGroup.REAR_DELTS, "Rear Delt Accessory"),
        )
        SplitDay.LEGS -> listOf(
            Slot(Template.PRIMARY, listOf(SQUAT)),
            Slot(Template.SECONDARY, listOf(HINGE)),
            Slot(Template.SECONDARY, listOf(LUNGE)),
            acc(MuscleGroup.QUADS, "Quad Accessory"),
            acc(MuscleGroup.HAMSTRINGS, "Hamstring Accessory"),
            acc(MuscleGroup.CALVES, "Calf Accessory"),
            acc(MuscleGroup.ABS, "Ab Accessory"),
            acc(MuscleGroup.ADDUCTORS, "Hip Adductor Accessory"),
        )
        SplitDay.HINGE -> listOf(
            Slot(Template.PRIMARY, listOf(HINGE)),
            Slot(Template.SECONDARY, listOf(HINGE)),
            acc(MuscleGroup.HAMSTRINGS, "Hamstring Accessory"),
            acc(MuscleGroup.GLUTES, "Glute Max Accessory"),
            acc(MuscleGroup.LOWER_BACK, "Lower Back Accessory"),
            acc(MuscleGroup.ABS, "Ab Accessory"),
            acc(MuscleGroup.OBLIQUES, "Oblique Acessory"),
            acc(MuscleGroup.CALVES, "Calf Accessory"),
        )
        SplitDay.SQUAT -> listOf(
            Slot(Template.PRIMARY, listOf(SQUAT)),
            Slot(Template.SECONDARY, listOf(LUNGE)),
            acc(MuscleGroup.QUADS, "Quad Accessory"),
            acc(MuscleGroup.ADDUCTORS, "Hip Adductor Accessory"),
            acc(MuscleGroup.CALVES, "Calf Accessory"),
            acc(MuscleGroup.ABS, "Ab Accessory"),
            acc(MuscleGroup.ABDUCTORS, "Hip Abductor Accessory"),
            acc(MuscleGroup.TIBS, "Tibialis Anterior Accessory"),
        )
        SplitDay.ACCESSORIES -> listOf(
            acc(MuscleGroup.SIDE_DELTS, "Lateral Delt Accessory"),
            acc(MuscleGroup.BICEPS, "Biceps Accessory"),
            acc(MuscleGroup.TRICEPS, "Triceps Accessory"),
            acc(MuscleGroup.REAR_DELTS, "Rear Delt Accessory"),
            acc(MuscleGroup.CHEST, "Chest Accessory"),
            acc(MuscleGroup.ABS, "Ab Accessory"),
            acc(MuscleGroup.CALVES, "Calf Accessory"),
            acc(MuscleGroup.FOREARMS, "Forearm Accessory", "Anterior Forearm Accessory", "Posterior Forearm Accessory"),
        )
    }

    /** Cible de base par objectif et type de modèle [INFÉRÉ]. */
    fun baseTarget(goal: Goal, t: Template): TargetSet = when (goal) {
        Goal.STRENGTH -> when (t) {
            Template.PRIMARY -> TargetSet(minReps = 3, maxReps = 6, rir = 2, restSec = 210)
            Template.SECONDARY -> TargetSet(minReps = 5, maxReps = 8, rir = 2, restSec = 180)
            Template.ACCESSORY -> TargetSet(minReps = 8, maxReps = 12, rir = 1, restSec = 90)
        }
        Goal.HYPERTROPHY -> when (t) {
            Template.PRIMARY -> TargetSet(minReps = 6, maxReps = 10, rir = 2, restSec = 180)
            Template.SECONDARY -> TargetSet(minReps = 8, maxReps = 12, rir = 2, restSec = 150)
            Template.ACCESSORY -> TargetSet(minReps = 10, maxReps = 15, rir = 1, restSec = 90)
        }
        Goal.BOTH -> when (t) {
            Template.PRIMARY -> TargetSet(minReps = 4, maxReps = 8, rir = 2, restSec = 180)
            Template.SECONDARY -> TargetSet(minReps = 6, maxReps = 10, rir = 2, restSec = 150)
            Template.ACCESSORY -> TargetSet(minReps = 10, maxReps = 15, rir = 1, restSec = 90)
        }
    }

    fun baseSets(t: Template, exp: Experience) = when (t) {
        Template.PRIMARY -> 3
        Template.SECONDARY -> 3
        Template.ACCESSORY -> if (exp == Experience.BEGINNER) 2 else 3
    }

    /** Minutes estimées d'un exercice : séries × (repos + ~45 s d'effort) + 2 min de mise en place. */
    fun minutes(sets: Int, rest: Int) = sets * (rest + 45) / 60.0 + 2

    /**
     * Niveau de recommandation de la base pour l'objectif et le rôle (1 = le plus recommandé ; null = inadapté à ce rôle) :
     * champs strength/hypertrophy × PrimaryCompound / SecondaryCompound / Accessory.
     */
    fun level(e: Exercise, goal: Goal, t: Template): Double? {
        fun lvl(i: Int) = e.recommendation.getOrNull(i)
        val i = when (t) { Template.PRIMARY -> 0; Template.SECONDARY -> 1; Template.ACCESSORY -> 2 }
        val s = lvl(i); val h = lvl(i + 3)
        return when (goal) {
            Goal.STRENGTH -> s ?: h?.plus(1)
            Goal.HYPERTROPHY -> h ?: s?.plus(1)
            Goal.BOTH -> listOfNotNull(s, h).minOrNull()
        }
    }

    /** Score : meilleur niveau, puis boost de recherche (popularité) ; les variantes moins adaptées servent de repli. */
    fun score(e: Exercise, goal: Goal, t: Template): Double {
        val l = level(e, goal, t) ?: (level(e, goal, Template.SECONDARY)?.plus(3)) ?: 12.0
        return 10 - l + (e.searchBoost ?: 0.0) * 0.05
    }

    fun generate(db: ExerciseDb, input: Input): Program {
        val available = db.exercises.filter { db.isAvailable(it, input.equipment) && it.id !in input.doNotRecommend && it.usesReps }
        val used = mutableMapOf<String, Int>()
        val dayCounters = mutableMapOf<SplitDay, Int>()
        val trainingDays = mutableListOf<ProgramDay>()
        // autant de journées distinctes que de séances par semaine (variantes A, B, C… d'un même jour-type)
        val dayTypes = List(max(input.daysPerWeek, input.split.days.size)) { input.split.days[it % input.split.days.size] }
        for ((idx, sd) in dayTypes.withIndex()) {
            val variant = dayCounters.getOrDefault(sd, 0); dayCounters[sd] = variant + 1
            val exercises = mutableListOf<ProgramExercise>()
            var budget = input.minutes.toDouble()
            val chosenToday = mutableSetOf<String>()
            for (slot in slots(sd, variant)) {
                val cands = available.filter { e ->
                    e.id !in chosenToday && e.exclusionGroups.none { g -> chosenToday.any { c -> db.exercise(c)?.exclusionGroups?.contains(g) == true } } &&
                        (e.movementPatterns.any { it in slot.patterns } ||
                            (slot.template == Template.ACCESSORY && slot.group != null && slot.group.key in e.primaryGroups && !e.isCompound)) &&
                        (slot.template == Template.ACCESSORY || e.isCompound)
                }
                val pool = cands.filter { level(it, input.goal, slot.template) != null }.ifEmpty { cands }
                val pick = pool.maxByOrNull { score(it, input.goal, slot.template) - used.getOrDefault(it.id, 0) * 1.5 } ?: continue
                val base = baseTarget(input.goal, slot.template)
                val focus = pick.muscleGroups.sumOf { input.focus[it] ?: 0 }.coerceIn(-1, 2)
                val sets = (baseSets(slot.template, input.experience) + focus).coerceIn(1, 5)
                val cost = minutes(sets, base.restSec)
                if (cost > budget && exercises.size >= 3) break
                budget -= cost
                chosenToday += pick.id
                used[pick.id] = used.getOrDefault(pick.id, 0) + 1
                exercises += ProgramExercise(UUID.randomUUID().toString(), pick.id, cycles(input, base, sets))
            }
            trainingDays += ProgramDay(UUID.randomUUID().toString(), dayName(sd, dayTypes, idx), exercises)
        }
        return Program(
            id = UUID.randomUUID().toString(), name = input.name, color = input.color,
            days = withRestDays(trainingDays, input.daysPerWeek), numCycles = input.numCycles, deload = input.deload,
            periodization = input.periodization, goal = input.goal, gymId = input.gymId,
        )
    }

    private fun dayName(sd: SplitDay, days: List<SplitDay>, idx: Int): String {
        val same = days.count { it == sd }
        val n = days.take(idx + 1).count { it == sd }
        // Nom créé dans la langue de l'interface (il reste ensuite modifiable comme tout nom de séance).
        val label = app.superhealth.training.ui.tr(sd.label)
        return if (same > 1) "$label ${'A' + (n - 1)}" else label
    }

    /** Répartit les jours d'entraînement sur un cycle de 7 jours (jours de repos intercalés). */
    fun withRestDays(training: List<ProgramDay>, perWeek: Int): List<ProgramDay> {
        val n = training.size
        if (n == 0) return training
        val pattern = when (perWeek.coerceIn(1, 7)) {
            1 -> listOf(true, false, false, false, false, false, false)
            2 -> listOf(true, false, false, true, false, false, false)
            3 -> listOf(true, false, true, false, true, false, false)
            4 -> listOf(true, true, false, true, true, false, false)
            5 -> listOf(true, true, true, false, true, true, false)
            6 -> listOf(true, true, true, true, true, true, false)
            else -> List(7) { true }
        }
        val out = mutableListOf<ProgramDay>()
        var t = 0
        for (train in pattern) {
            if (train) { out += training[t % n]; t++ } else out += ProgramDay(UUID.randomUUID().toString(), "Repos")
        }
        // un split plus long que les jours d'entraînement de la semaine : jours restants ajoutés à la suite
        while (t < n) { out += training[t]; t++ }
        return out
    }

    /**
     * Cibles par cycle. Linéaire : plage décalée d'une répétition par cycle (0xed55ec) et RIR décroissant vers 0-1 ;
     * décharge : moitié des séries, RIR +2.
     */
    fun cycles(input: Input, base: TargetSet, sets: Int): List<List<TargetSet>> {
        val n = input.numCycles.coerceAtLeast(1)
        if (input.periodization == Periodization.STATIC && input.deload == Deload.NONE) return listOf(List(sets) { base })
        return (0 until n).map { c ->
            val isDeload = (input.deload == Deload.FIRST && c == 0) || (input.deload == Deload.LAST && c == n - 1)
            val work = (0 until n).filter { !((input.deload == Deload.FIRST && it == 0) || (input.deload == Deload.LAST && it == n - 1)) }
            if (isDeload) List(max(1, (sets + 1) / 2)) { base.copy(rir = min(5, base.rir + 2)) }
            else {
                val k = work.indexOf(c)
                val t = if (input.periodization == Periodization.LINEAR && work.size > 1) {
                    val shift = (work.size - 1 - k).coerceAtMost(2) - 1  // de +1 à −1 répétition au fil du bloc
                    val rir = (base.rir + 1 - (k * (base.rir + 1) / (work.size - 1))).coerceIn(0, base.rir + 1)
                    base.copy(minReps = max(1, base.minReps + shift), maxReps = max(2, base.maxReps + shift), rir = rir)
                } else base
                List(sets) { t }
            }
        }
    }

    /** Index du cycle de cibles pour un programme (périodisation désactivée = cycle unique). */
    fun targetsFor(pe: ProgramExercise, cycle: Int): List<TargetSet> = pe.cycles[cycle.coerceIn(0, pe.cycles.lastIndex)]

    /** Durée estimée d'un jour (minutes). */
    fun estimateMinutes(day: ProgramDay, cycle: Int) =
        day.exercises.sumOf { pe -> targetsFor(pe, cycle).let { s -> minutes(s.size, s.firstOrNull()?.restSec ?: 120) } }.roundToInt()
}
