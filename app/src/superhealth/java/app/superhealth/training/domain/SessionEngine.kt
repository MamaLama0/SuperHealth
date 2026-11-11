package app.superhealth.training.domain

import java.util.UUID
import kotlin.math.round

/** Construction et déroulement d'une séance (WorkoutSession) : pré-remplissage, échauffements, progression en séance. */
class SessionEngine(private val db: ExerciseDb, private val exerciseConfig: Map<String, ExerciseConfig> = emptyMap()) {

    private fun uid() = UUID.randomUUID().toString()

    // ----- Charges réalisables -----

    fun ladderFor(exerciseId: String, gym: Gym?): LoadLadder {
        val e = db.exercise(exerciseId) ?: return LoadLadder.step(2.5)
        val eq = db.resistanceEquipment(e, gym?.equipmentIds)
        val cats = eq.mapNotNull { it.category }
        val plates = gym?.plates ?: listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
        return when {
            cats.contains("Loadable Bars") -> {
                val bar = eq.firstOrNull { it.category == "Loadable Bars" }
                val w = bar?.kg?.takeIf { it > 0 } ?: gym?.barWeight ?: 20.0
                LoadLadder.barbell(w, plates)
            }
            cats.contains("Plate Loaded Machines") -> LoadLadder.barbell(0.0, plates)
            cats.contains("Free Weights") || cats.contains("Fixed Weight Bars") -> {
                val step = eq.firstNotNullOfOrNull { it.incrementKg?.takeIf { s -> s > 0 } } ?: gym?.dumbbellStep ?: 2.0
                LoadLadder.step(step, step, 80.0)
            }
            cats.contains("Pin Loaded Machines") || cats.contains("Cable Machines") -> LoadLadder.step(gym?.machineStep ?: 5.0, 0.0, 300.0)
            else -> LoadLadder.step(1.25, 0.0, 200.0)
        }
    }

    /** Pas plus grossier pour les échauffements (« Simplify Warm-Up Plates »). */
    private fun simplify(w: Double, ladder: LoadLadder): Double {
        val coarse = ladder.loads.filter { Math.round(it * 100) % 500 == 0L }
        return if (coarse.isEmpty()) ladder.nearest(w) else LoadLadder(coarse).nearest(w)
    }

    // ----- Historique -----

    /** Séries de travail de la dernière séance contenant l'exercice. */
    fun lastSets(exerciseId: String, history: List<Workout>): List<PastSet> {
        val w = history.filter { h -> h.exercises.any { it.exerciseId == exerciseId && Analytics.workingSets(it).isNotEmpty() } }
            .maxByOrNull { it.startTime } ?: return emptyList()
        return pastSets(exerciseId, w)
    }

    private fun pastSets(exerciseId: String, w: Workout): List<PastSet> =
        w.exercises.filter { it.exerciseId == exerciseId }.flatMap { Analytics.workingSets(it) }
            .filter { (it.weight ?: 0.0) >= 0 && (it.reps ?: 0) > 0 }
            .map { PastSet(it.weight ?: 0.0, it.reps!!, it.rir, it.completedAt ?: w.startTime, it.partialReps) }

    private fun Workout.matchesGym(gym: Gym?) = if (gymId != null) gymId == gym?.id else gymName == gym?.name

    private data class Context(val key: Triple<Int, String?, Experience>, val bias: Double, val trust: Double)
    @Volatile private var context: Context? = null

    /** Biais de RIR et confiance du filtre : calculés une fois par état de l'historique. */
    private fun context(history: List<Workout>, experience: Experience): Context {
        val key = Triple(history.size, history.maxByOrNull { it.startTime }?.id, experience)
        context?.takeIf { it.key == key }?.let { return it }
        val bias = StrengthModel.rirBias(history, db)
        return Context(key, bias, StrengthModel.trust(history, db, experience, bias)).also { context = it }
    }

    /** Biais de RIR appris (RIR annoncé − RIR réel), pour les écrans et le coach. */
    fun rirBias(history: List<Workout>, experience: Experience) = context(history, experience).bias

    /**
     * Force attendue pour la séance qui commence, progression comprise ([StrengthModel]). Les exercices assistés ou sans
     * répétitions gardent la règle d'origine : meilleure série de la dernière séance, + 1 %.
     */
    fun openingStrength(exercise: Exercise, history: List<Workout>, settings: Settings, gym: Gym?,
                        last: List<PastSet> = lastSets(exercise.id, history), now: Long = System.currentTimeMillis()): Double? {
        if (!exercise.usesReps || "aw" in exercise.metrics)
            return Progression.currentOneRm(last, exercise.upperBody)?.times(Strength.SESSION_PROGRESS)
        return estimate(exercise, history, settings, gym, now)?.oneRm
    }

    fun estimate(exercise: Exercise, history: List<Workout>, settings: Settings, gym: Gym?, now: Long = System.currentTimeMillis()): StrengthModel.Estimate? {
        if (!exercise.usesReps || "aw" in exercise.metrics) return null
        val c = context(history, settings.experience)
        return StrengthModel.estimate(exercise, history, gym, settings.experience, c.bias, c.trust, now)
    }

    /** 1RM frais mesuré sur les séries déjà faites, fusionné avec l'estimation d'ouverture selon leurs incertitudes. */
    fun inSessionStrength(exercise: Exercise, done: List<SetLog>, restSec: Int, history: List<Workout>, settings: Settings, gym: Gym?): Double? {
        val c = context(history, settings.experience)
        val m = StrengthModel.measure(listOf(SessionExercise("now", exercise.id, done, restSec = restSec)), exercise.upperBody, c.bias, 0) ?: return null
        val opening = if (history.isEmpty()) null else estimate(exercise, history, settings, gym)
        return kotlin.math.exp(StrengthModel.combine(opening?.oneRm?.let { kotlin.math.ln(it) }, (opening?.sd ?: 1.0).let { it * it + 0.03 * 0.03 }, m))
    }

    // ----- Construction -----

    fun fromProgram(program: Program, day: ProgramDay, cycle: Int, history: List<Workout>, settings: Settings, gym: Gym?): Session {
        val worked = mutableSetOf<MuscleGroup>()
        val exercises = day.exercises.filter { cycle !in it.skippedCycles }.map { pe ->
            val ex = buildExercise(pe.exerciseId, Generator.targetsFor(pe, cycle), history, settings, gym, worked)
            db.exercise(pe.exerciseId)?.muscleGroups?.let(worked::addAll)
            ex.copy(supersetGroup = pe.supersetGroup)
        }
        val groups = Supersets.normalize(exercises.map { it.supersetGroup })
        return Session(uid(), day.name, System.currentTimeMillis(), exercises.mapIndexed { i, e -> e.copy(supersetGroup = groups[i]) }, gym?.id, program.id, cycle, day.id)
    }

    fun fromCustom(w: CustomWorkout, history: List<Workout>, settings: Settings, gym: Gym?): Session {
        val worked = mutableSetOf<MuscleGroup>()
        val exercises = w.exercises.map { pe ->
            val ex = buildExercise(pe.exerciseId, pe.cycles.first(), history, settings, gym, worked).copy(supersetGroup = pe.supersetGroup)
            db.exercise(pe.exerciseId)?.muscleGroups?.let(worked::addAll)
            ex
        }
        return Session(uid(), w.name, System.currentTimeMillis(), exercises, gym?.id, freeForm = false)
    }

    fun empty(gym: Gym?) = Session(uid(), "Entraînement libre", System.currentTimeMillis(), emptyList(), gym?.id, freeForm = true)

    /** Ajoute un exercice en cours de séance (3 séries de 8-12 à RIR 2 par défaut). */
    fun addExercise(s: Session, exerciseId: String, history: List<Workout>, settings: Settings, gym: Gym?): Session {
        val worked = s.exercises.flatMap { db.exercise(it.exerciseId)?.muscleGroups ?: emptyList() }.toMutableSet()
        val targets = List(3) { TargetSet(restSec = settings.restDefaultSec) }
        return s.copy(exercises = s.exercises + buildExercise(exerciseId, targets, history, settings, gym, worked))
    }

    fun buildExercise(
        exerciseId: String, targets: List<TargetSet>, history: List<Workout>, settings: Settings, gym: Gym?, worked: Set<MuscleGroup>,
    ): SessionExercise {
        val e = db.exercise(exerciseId)
        val upper = e?.upperBody ?: true
        val compatible = if (settings.smartProgression && settings.fillWithSmart) history.filter { it.matchesGym(gym) }.ifEmpty { history } else history
        val last = lastSets(exerciseId, compatible)
        val ladder = ladderFor(exerciseId, gym)
        val oneRm = if (e?.usesWeight == true) openingStrength(e, history, settings, gym, last) else null
        val mode = SessionWarmUps.mode(settings, exerciseConfig[exerciseId])
        val planned = if (mode == WarmUpMode.NONE) targets.filter { it.type != SetType.WARMUP } else targets
        val suggestions = if (oneRm != null && oneRm > 0) Progression.suggest(
            oneRm, planned, upper, ladder, last.firstOrNull()?.weight, settings.weightMatch, settings.expandRepRange,
            progress = 1.0,
        ) else null
        val work = planned.mapIndexed { i, t ->
            val sg = suggestions?.getOrNull(i)
            val prev = last.getOrNull(i) ?: last.lastOrNull()
            val (w, r) = when {
                settings.smartProgression && settings.fillWithSmart && sg != null -> sg.weight to sg.reps
                prev != null -> prev.weight to prev.reps
                else -> null to null
            }
            SetLog(uid(), t.type, weight = w, reps = r, rir = t.rir, target = t, autoWeight = sg?.weight, autoReps = sg?.reps)
        }
        val warmUps = if (work.any { it.type == SetType.WARMUP }) emptyList() else
            warmUps(e, work.firstOrNull()?.weight, oneRm, settings.copy(warmUp = mode), ladder, worked)
        val rest = targets.firstOrNull()?.restSec ?: settings.restDefaultSec
        return SessionExercise(uid(), exerciseId, warmUps + work, restSec = rest)
    }

    /**
     * Échauffements. Intelligent : ratios de getSmartWarmUpRatios sur le 1RM estimé, seulement si les muscles principaux
     * n'ont pas déjà travaillé dans la séance (« Detect when a muscle group has not been worked »), et pas d'échauffement
     * sans historique (« Smart warm-ups will appear after you've completed a workout that includes this exercise »).
     * Schéma : pourcentages de la première charge de travail.
     */
    fun warmUps(e: Exercise?, workWeight: Double?, oneRm: Double?, settings: Settings, ladder: LoadLadder, worked: Set<MuscleGroup>): List<SetLog> {
        if (e == null || workWeight == null || workWeight <= 0 || !e.usesWeight) return emptyList()
        return when (settings.warmUp) {
            WarmUpMode.NONE -> emptyList()
            WarmUpMode.SCHEME -> settings.warmUpScheme.map {
                val w = ladder.nearest(workWeight * it.percent).let { x -> if (settings.simplifyWarmUpPlates) simplify(x, ladder) else x }
                SetLog(uid(), SetType.WARMUP, weight = w, reps = it.reps)
            }.filter { (it.weight ?: 0.0) > 0 && (it.weight ?: 0.0) < workWeight }.distinctBy { it.weight }
            WarmUpMode.SMART -> {
                if (oneRm == null || oneRm <= 0) return emptyList()
                if (e.muscleGroups.isNotEmpty() && worked.containsAll(e.muscleGroups)) return emptyList()
                Strength.smartWarmUpRatios(workWeight / oneRm).mapNotNull { ratio ->
                    val reps = Strength.warmUpReps(ratio) ?: return@mapNotNull null
                    val raw = ladder.nearest(oneRm * ratio)
                    val w = if (settings.simplifyWarmUpPlates) simplify(raw, ladder) else raw
                    if (w >= workWeight || w <= 0) null else SetLog(uid(), SetType.WARMUP, weight = w, reps = reps)
                }.distinctBy { it.weight }
            }
        }
    }

    // ----- En séance -----

    /**
     * Après une série validée : recalcule les séries suivantes (applySmartProgressionInSession).
     * Le 1RM est ré-estimé depuis la série réalisée en tenant compte de la fatigue accumulée ; une charge changée à la main
     * est gardée et seules ses répétitions sont recalculées.
     */
    fun afterSetCompleted(s: Session, exIndex: Int, settings: Settings, gym: Gym?, history: List<Workout> = emptyList()): Session {
        if (!settings.smartProgression) return s
        val ex = s.exercises[exIndex]
        val forced = ex.sets.withIndex().filter { (_, v) -> v.isPlanned && v.weight != null && (v.weightEdited || v.weight != v.autoWeight) }
            .associate { it.index to it.value.weight!! }
        return replan(s, exIndex, history, settings, gym, forced, fromIndex = 0)
    }

    /**
     * Charge changée sur une série à venir : ses répétitions et celles des séries automatiques qui suivent sont recalculées
     * en direct, avec le même modèle que [afterSetCompleted] (ou que la suggestion d'ouverture si rien n'est encore validé).
     */
    fun weightChanged(s: Session, exIndex: Int, setIndex: Int, history: List<Workout>, settings: Settings, gym: Gym?): Session {
        if (!settings.smartProgression) return s
        val ex = s.exercises[exIndex]
        val w = ex.sets.getOrNull(setIndex)?.weight ?: return s
        if (w <= 0 || !ex.sets[setIndex].isPlanned) return s
        // séries à venir avant celle-ci : gardées telles quelles
        val forced = ex.sets.withIndex().filter { (i, v) -> v.isPlanned && v.weight != null && (i <= setIndex || v.weightEdited || v.weight != v.autoWeight) }
            .associate { it.index to it.value.weight!! }
        return replan(s, exIndex, history, settings, gym, forced, fromIndex = setIndex)
    }

    private val SetLog.isPlanned get() = !done && type != SetType.WARMUP && target != null

    /** Recalcule les séries à venir à partir de [fromIndex] ; [forced] = charges imposées par index de série. */
    private fun replan(s: Session, exIndex: Int, history: List<Workout>, settings: Settings, gym: Gym?, forced: Map<Int, Double>, fromIndex: Int): Session {
        val ex = s.exercises[exIndex]
        val e = db.exercise(ex.exerciseId) ?: return s
        if (!e.usesWeight) return s
        val done = ex.sets.filter { it.done && it.type != SetType.WARMUP && (it.weight ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
        val oneRm: Double
        val prevWeight: Double?
        val progress: Double
        var recentStrength: Double? = null
        if (done.isNotEmpty()) {
            val useStrengthModel = e.usesReps && "aw" !in e.metrics
            val rm = (if (useStrengthModel) inSessionStrength(e, done, ex.restSec, history, settings, gym) else {
                val actual = done.map { PastSet(it.weight!!, it.reps!!, it.rir, 0, it.partialReps) }
                Progression.currentOneRm(actual, e.upperBody, ex.restSec / 60.0)
            }) ?: return s
            var fatigue = 0.0
            var lastFreshStrength: Double? = null
            done.forEachIndexed { i, set ->
                if (useStrengthModel && i == done.lastIndex && set.weight!!.isFinite() && set.reps!! <= StrengthModel.MAX_REPS) {
                    val remaining = 1 - Strength.fatigueDrop(fatigue)
                    if (remaining > 0.5) {
                        val bias = context(history, settings.experience).bias
                        lastFreshStrength = StrengthModel.observedOneRm(set, e.upperBody, bias) / remaining
                    }
                }
                fatigue = Strength.recover(fatigue + Strength.fatiguingReps(set.reps!!, set.partialReps), Progression.restMinutes(set, done.getOrNull(i + 1), ex.restSec))
            }
            val remaining = 1 - Strength.fatigueDrop(fatigue)
            recentStrength = lastFreshStrength?.times(remaining)
            oneRm = recentStrength?.let { 0.60 * it + 0.40 * rm * remaining } ?: (rm * remaining)
            prevWeight = done.last().weight
            progress = 1.0
        } else {
            val last = lastSets(ex.exerciseId, history.filter { it.matchesGym(gym) }.ifEmpty { history })
            oneRm = openingStrength(e, history, settings, gym, last) ?: return s
            prevWeight = last.firstOrNull()?.weight
            progress = 1.0
        }
        if (oneRm <= 0) return s
        val pending = ex.sets.withIndex().filter { it.value.isPlanned }
        if (pending.isEmpty()) return s
        val sugg = Progression.suggest(
            oneRm, pending.map { it.value.target!! }, e.upperBody, ladderFor(ex.exerciseId, gym),
            prevWeight, settings.weightMatch, settings.expandRepRange, progress,
            manualReps = pending.mapIndexedNotNull { k, iv -> iv.value.reps?.takeIf { it > 0 && iv.value.repsEdited }?.let { k to it } }.toMap(),
            forced = pending.withIndex().mapNotNull { (k, iv) -> forced[iv.index]?.let { k to it } }.toMap(),
            recentStrength = recentStrength,
            recentReps = done.lastOrNull()?.reps,
        )
        val sets = ex.sets.toMutableList()
        pending.forEachIndexed { k, iv ->
            if (iv.index < fromIndex) return@forEachIndexed
            val g = sugg[k]
            val set = iv.value
            val reps = if (set.repsEdited) set.reps else g.reps
            sets[iv.index] = if (iv.index in forced || set.weightEdited) set.copy(reps = reps, autoReps = g.reps)
            else set.copy(weight = g.weight, reps = reps, autoWeight = g.weight, autoReps = g.reps)
        }
        return s.copy(exercises = s.exercises.toMutableList().also { it[exIndex] = ex.copy(sets = sets) })
    }

    // ----- Fin de séance -----

    /**
     * Séance → historique. [logAll] (« Log All Sets ») enregistre aussi les séries non cochées qui ont une charge ou des
     * répétitions ; sinon seules les séries faites sont gardées (« Log Only Completed Sets »).
     */
    fun finish(s: Session, now: Long, gym: Gym?, programName: String?, logAll: Boolean = false): Workout {
        val exercises = s.exercises.map { ex ->
            ex.copy(sets = ex.sets.mapNotNull { set ->
                when {
                    set.done -> set
                    logAll && (set.reps ?: 0) > 0 -> set.copy(done = true, completedAt = now)
                    else -> null
                }
            })
        }.filter { it.sets.isNotEmpty() }
        return Workout(s.id, s.name, s.startTime, s.elapsedSec(now), exercises, gym?.name,
            s.programId, programName, s.cycleIndex, s.note, s.dayId, gymId = s.gymId)
    }

    /**
     * Jour [doneDayId] fait : il est marqué fait dans le cycle, le prochain jour proposé est le suivant pas encore fait
     * (en revenant au début si besoin). Les jours sautés restent à faire ; le cycle change quand tous sont faits.
     */
    fun advance(p: Program, doneDayId: String?, completedCycle: Int = p.cycleIndex): Program {
        // Une séance commencée avant un saut manuel reste dans son cycle d'origine.
        if (completedCycle != p.cycleIndex) return p
        val idx = p.days.indexOfFirst { it.id == doneDayId }.takeIf { it >= 0 } ?: p.dayIndex
        val day = p.days.getOrNull(idx) ?: return p
        val done = (p.doneDayIds + day.id).distinct()
        val training = p.days.indices.filter { !p.days[it].isRestAt(p.cycleIndex) }
        if (training.isEmpty()) return p
        val next = ((idx + 1 until p.days.size) + (0..idx)).firstOrNull { it in training && p.days[it].id !in done }
        if (next != null) return p.copy(dayIndex = next, doneDayIds = done)
        var c = p.cycleIndex + 1
        if (c >= p.numCycles && p.runIndefinitely) c = 0
        // A block can deliberately skip entire days in later cycles.
        var attempts = 0
        while (c < p.numCycles && p.days.all { it.isRestAt(c) } && attempts++ < p.numCycles) {
            c++
            if (c >= p.numCycles && p.runIndefinitely) c = 0
        }
        return ProgramEdit.fixCursor(p.copy(dayIndex = 0, cycleIndex = c, doneDayIds = emptyList()))
    }

    companion object {
        fun roundKg(v: Double) = round(v * 100) / 100

        /**
         * Jours faits du cycle en cours retrouvés dans l'historique (programmes enregistrés avant le suivi des jours faits) :
         * séances du programme et du cycle, reconnues par nom.
         */
        fun inferDone(p: Program, history: List<Workout>): Program {
            if (p.doneDayIds.isNotEmpty()) return p
            // seulement les séances enregistrées avant le suivi (sans dayId) : les nouvelles passent par advance()
            val ws = history.filter { it.programId == p.id && it.cycleIndex == p.cycleIndex && it.dayId == null }
            if (ws.isEmpty() || history.any { it.programId == p.id && it.dayId != null }) return p
            val ids = ws.mapNotNull { w -> p.days.firstOrNull { it.name == w.name }?.id }.distinct()
            if (ids.isEmpty()) return p
            val training = p.days.filter { !it.isRestAt(p.cycleIndex) }
            val next = training.firstOrNull { it.id !in ids } ?: return p
            return p.copy(doneDayIds = ids, dayIndex = p.days.indexOf(next))
        }
    }
}
