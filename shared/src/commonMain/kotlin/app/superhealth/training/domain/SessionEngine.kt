package app.superhealth.training.domain

import app.superhealth.platform.*

import app.superhealth.platform.UUID
import kotlin.math.max
import kotlin.math.round

/** Construction et déroulement d'une séance (WorkoutSession) : pré-remplissage, échauffements, progression en séance. */
class SessionEngine(private val db: ExerciseDb) {

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
        return w.exercises.filter { it.exerciseId == exerciseId }.flatMap { Analytics.workingSets(it) }
            .filter { (it.weight ?: 0.0) >= 0 && (it.reps ?: 0) > 0 }
            .map { PastSet(it.weight ?: 0.0, it.reps!!, it.rir, it.completedAt ?: w.startTime, it.partialReps) }
    }

    // ----- Construction -----

    fun fromProgram(program: Program, day: ProgramDay, cycle: Int, history: List<Workout>, settings: Settings, gym: Gym?): Session {
        val worked = mutableSetOf<MuscleGroup>()
        val exercises = day.exercises.map { pe ->
            val ex = buildExercise(pe.exerciseId, Generator.targetsFor(pe, cycle), history, settings, gym, worked)
            db.exercise(pe.exerciseId)?.muscleGroups?.let(worked::addAll)
            ex.copy(supersetGroup = pe.supersetGroup)
        }
        return Session(uid(), day.name, System.currentTimeMillis(), exercises, gym?.id, program.id, cycle, day.id)
    }

    fun fromCustom(w: CustomWorkout, history: List<Workout>, settings: Settings, gym: Gym?): Session {
        val worked = mutableSetOf<MuscleGroup>()
        val exercises = w.exercises.map { pe ->
            val ex = buildExercise(pe.exerciseId, pe.cycles.first(), history, settings, gym, worked)
            db.exercise(pe.exerciseId)?.muscleGroups?.let(worked::addAll)
            ex
        }
        return Session(uid(), w.name, System.currentTimeMillis(), exercises, gym?.id)
    }

    fun empty(gym: Gym?) = Session(uid(), "Entraînement libre", System.currentTimeMillis(), emptyList(), gym?.id)

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
        val last = lastSets(exerciseId, history)
        val ladder = ladderFor(exerciseId, gym)
        val oneRm = if (e?.usesWeight == true) Progression.currentOneRm(last, upper) else null
        val suggestions = if (oneRm != null && oneRm > 0) Progression.suggest(
            oneRm, targets, upper, ladder, last.firstOrNull()?.weight, settings.weightMatch, settings.expandRepRange,
        ) else null
        val work = targets.mapIndexed { i, t ->
            val sg = suggestions?.getOrNull(i)
            val prev = last.getOrNull(i) ?: last.lastOrNull()
            val (w, r) = when {
                settings.smartProgression && settings.fillWithSmart && sg != null -> sg.weight to sg.reps
                prev != null -> prev.weight to prev.reps
                else -> null to null
            }
            SetLog(uid(), t.type, weight = w, reps = r, rir = t.rir, target = t, autoWeight = sg?.weight, autoReps = sg?.reps)
        }
        val warmUps = warmUps(e, work.firstOrNull()?.weight, oneRm, settings, ladder, worked)
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
            }.filter { (it.weight ?: 0.0) < workWeight }
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
    fun afterSetCompleted(s: Session, exIndex: Int, settings: Settings, gym: Gym?): Session {
        if (!settings.smartProgression) return s
        val ex = s.exercises[exIndex]
        val forced = ex.sets.withIndex().filter { (_, v) -> v.isPlanned && v.weight != null && v.weight != v.autoWeight }
            .associate { it.index to it.value.weight!! }
        return replan(s, exIndex, emptyList(), settings, gym, forced, fromIndex = 0)
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
        val forced = ex.sets.withIndex().filter { (i, v) -> v.isPlanned && v.weight != null && (i <= setIndex || v.weight != v.autoWeight) }
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
        if (done.isNotEmpty()) {
            val rm = Progression.currentOneRm(done.map { PastSet(it.weight!!, it.reps!!, it.rir, 0, it.partialReps) }, e.upperBody, ex.restSec / 60.0) ?: return s
            var fatigue = 0.0
            done.forEach { fatigue = Strength.recover(fatigue + Strength.fatiguingReps(it.reps!!, it.partialReps), ex.restSec / 60.0) }
            oneRm = rm * (1 - Strength.fatigueDrop(fatigue)); prevWeight = done.last().weight; progress = 1.0
        } else {
            val last = lastSets(ex.exerciseId, history)
            oneRm = Progression.currentOneRm(last, e.upperBody) ?: return s
            prevWeight = last.firstOrNull()?.weight; progress = Strength.SESSION_PROGRESS
        }
        if (oneRm <= 0) return s
        val pending = ex.sets.withIndex().filter { it.value.isPlanned }
        if (pending.isEmpty()) return s
        val sugg = Progression.suggest(
            oneRm, pending.map { it.value.target!! }, e.upperBody, ladderFor(ex.exerciseId, gym),
            prevWeight, settings.weightMatch, settings.expandRepRange, progress,
            forced = pending.withIndex().mapNotNull { (k, iv) -> forced[iv.index]?.let { k to it } }.toMap(),
        )
        val sets = ex.sets.toMutableList()
        pending.forEachIndexed { k, iv ->
            if (iv.index < fromIndex) return@forEachIndexed
            val g = sugg[k]
            sets[iv.index] = if (iv.index in forced) iv.value.copy(reps = g.reps, autoReps = g.reps)
            else iv.value.copy(weight = g.weight, reps = g.reps, autoWeight = g.weight, autoReps = g.reps)
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
            s.programId, programName, s.cycleIndex, s.note, s.dayId)
    }

    /**
     * Jour [doneDayId] fait : il est marqué fait dans le cycle, le prochain jour proposé est le suivant pas encore fait
     * (en revenant au début si besoin). Les jours sautés restent à faire ; le cycle change quand tous sont faits.
     */
    fun advance(p: Program, doneDayId: String?): Program {
        val idx = p.days.indexOfFirst { it.id == doneDayId }.takeIf { it >= 0 } ?: p.dayIndex
        val day = p.days.getOrNull(idx) ?: return p
        val done = (p.doneDayIds + day.id).distinct()
        val training = p.days.indices.filter { !p.days[it].isRest }
        if (training.isEmpty()) return p
        val next = ((idx + 1 until p.days.size) + (0..idx)).firstOrNull { it in training && p.days[it].id !in done }
        if (next != null) return p.copy(dayIndex = next, doneDayIds = done)
        var c = p.cycleIndex + 1
        if (c >= p.numCycles && p.runIndefinitely) c = 0
        return p.copy(dayIndex = training.first(), cycleIndex = c, doneDayIds = emptyList())
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
            val training = p.days.filter { !it.isRest }
            val next = training.firstOrNull { it.id !in ids } ?: return p
            return p.copy(doneDayIds = ids, dayIndex = p.days.indexOf(next))
        }
    }
}
