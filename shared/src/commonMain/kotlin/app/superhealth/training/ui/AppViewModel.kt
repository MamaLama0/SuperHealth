package app.superhealth.training.ui

import app.superhealth.platform.*

import app.superhealth.platform.Application
import app.superhealth.platform.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.superhealth.training.WorkoutApp
import app.superhealth.training.data.AppState
import app.superhealth.training.domain.CustomWorkout
import app.superhealth.training.domain.ExerciseDb
import app.superhealth.training.domain.Generator
import app.superhealth.training.domain.Gym
import app.superhealth.training.domain.PlanDiff
import app.superhealth.training.domain.Program
import app.superhealth.training.domain.ProgramDay
import app.superhealth.training.domain.Session
import app.superhealth.training.domain.SessionEngine
import app.superhealth.training.domain.SetType
import app.superhealth.training.domain.Settings
import app.superhealth.training.domain.Supersets
import app.superhealth.training.domain.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.superhealth.platform.UUID

/** Modèles de salle (GymTemplate observé : everything, commercial, warehouse, local, garage, home, blankSlate). */
enum class GymTemplate(val label: String, val flag: String?) {
    COMMERCIAL("Salle commerciale", "commercialGym"), WAREHOUSE("Grande salle (entrepôt)", "warehouseGym"),
    LOCAL("Salle de quartier", "localGym"), GARAGE("Garage", "garageGym"), HOME("Maison", "homeGym"),
    EVERYTHING("Tout l'équipement", null), BLANK("Vierge", "")
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = WorkoutApp.store
    val state: StateFlow<AppState> = store.state
    val db: StateFlow<ExerciseDb?> = WorkoutApp.db
    private var engineFor: ExerciseDb? = null
    private var engineCache: SessionEngine? = null
    private val engine: SessionEngine?
        get() = db.value?.let { d -> if (d === engineFor) engineCache else SessionEngine(d).also { engineFor = d; engineCache = it } }

    init {
        // programmes enregistrés avant le suivi des jours faits : on les retrouve dans l'historique
        update { s -> s.copy(programs = s.programs.map { SessionEngine.inferDone(it, s.history) }) }
    }

    /** Résultat de séance à afficher (écran « Workout Complete »). */
    val completed = MutableStateFlow<Workout?>(null)

    val settings get() = state.value.settings
    fun update(f: (AppState) -> AppState) = store.update(f)
    fun settings(f: (Settings) -> Settings) = store.update { it.copy(settings = f(it.settings)) }

    fun activeGym(): Gym? = state.value.let { s -> s.gyms.firstOrNull { it.id == s.settings.activeGymId } ?: s.gyms.firstOrNull() }
    fun gym(id: String?) = state.value.gyms.firstOrNull { it.id == id }
    fun activeProgram(): Program? = state.value.let { s -> s.programs.firstOrNull { it.id == s.settings.activeProgramId } }

    // ----- Salles -----

    fun gymFromTemplate(t: GymTemplate, name: String = t.label): Gym {
        val all = db.value?.equipment ?: emptyList()
        val ids = when (t.flag) { null -> all.map { it.id }; "" -> all.filter { it.category == "Body Weights" }.map { it.id }; else -> all.filter { t.flag in it.gyms }.map { it.id } }
        return Gym(UUID.randomUUID().toString(), name, t.ordinal, ids.toSet())
    }

    fun saveGym(g: Gym) = update { s ->
        val gyms = if (s.gyms.any { it.id == g.id }) s.gyms.map { if (it.id == g.id) g else it } else s.gyms + g
        s.copy(gyms = gyms, settings = if (s.settings.activeGymId == null) s.settings.copy(activeGymId = g.id) else s.settings)
    }

    fun deleteGym(id: String) = update { s -> s.copy(gyms = s.gyms.filter { it.id != id }, settings = s.settings.copy(activeGymId = s.settings.activeGymId.takeIf { it != id })) }

    // ----- Programmes -----

    fun generate(input: Generator.Input): Program? = db.value?.let { Generator.generate(it, input) }

    fun saveProgram(p: Program, activate: Boolean = false) = update { s ->
        val list = if (s.programs.any { it.id == p.id }) s.programs.map { if (it.id == p.id) p else it } else s.programs + p
        s.copy(programs = list, settings = if (activate) s.settings.copy(activeProgramId = p.id) else s.settings)
    }

    fun activate(id: String?) = update { s ->
        // « Your active program will be moved to your Workout Library and its progress will be reset. »
        val programs = s.programs.map {
            when {
                it.id == s.settings.activeProgramId && it.id != id -> app.superhealth.training.domain.ProgramEdit.fixCursor(it.copy(cycleIndex = 0, dayIndex = 0, doneDayIds = emptyList()))
                it.id == id -> app.superhealth.training.domain.ProgramEdit.fixCursor(it)
                else -> it
            }
        }
        s.copy(programs = programs, settings = s.settings.copy(activeProgramId = id))
    }

    fun deleteProgram(id: String) = update { s ->
        s.copy(programs = s.programs.filter { it.id != id }, settings = s.settings.copy(activeProgramId = s.settings.activeProgramId.takeIf { it != id }))
    }

    fun saveCustom(w: CustomWorkout) = update { s ->
        s.copy(customWorkouts = if (s.customWorkouts.any { it.id == w.id }) s.customWorkouts.map { if (it.id == w.id) w else it } else s.customWorkouts + w)
    }

    fun saveCustomExercise(e: app.superhealth.training.domain.Exercise) = update { s ->
        s.copy(customExercises = if (s.customExercises.any { it.id == e.id }) s.customExercises.map { if (it.id == e.id) e else it } else s.customExercises + e)
    }

    /** Supprime un exercice personnel et le retire des programmes et modèles (l'historique garde ses séries). */
    fun deleteCustomExercise(id: String) = update { s ->
        s.copy(customExercises = s.customExercises.filter { it.id != id },
            programs = s.programs.map { p -> p.copy(days = p.days.map { d -> d.copy(exercises = d.exercises.filter { it.exerciseId != id }) }) },
            customWorkouts = s.customWorkouts.map { w -> w.copy(exercises = w.exercises.filter { it.exerciseId != id }) })
    }

    fun deleteCustom(id: String) = update { s -> s.copy(customWorkouts = s.customWorkouts.filter { it.id != id }) }

    // ----- Séance -----

    fun startProgramDay(p: Program, dayIndex: Int, cycle: Int) {
        val e = engine ?: return
        val day = p.days.getOrNull(dayIndex) ?: return
        val gym = gym(p.gymId) ?: activeGym()
        update { it.copy(session = e.fromProgram(p, day, cycle, it.history, it.settings, gym)) }
    }

    fun startCustom(w: CustomWorkout) {
        val e = engine ?: return
        update { it.copy(session = e.fromCustom(w, it.history, it.settings, gym(w.gymId) ?: activeGym())) }
    }

    fun startEmpty() { val e = engine ?: return; update { it.copy(session = e.empty(activeGym()).copy(name = tr("Entraînement libre"))) } }

    fun session(f: (Session) -> Session) = update { s -> s.session?.let { s.copy(session = f(it)) } ?: s }

    fun addExercises(ids: List<String>) {
        val e = engine ?: return
        update { s ->
            var sess = s.session ?: return@update s
            ids.forEach { sess = e.addExercise(sess, it, s.history, s.settings, gym(sess.gymId)) }
            s.copy(session = sess)
        }
    }

    /** Charge saisie sur une série : répétitions recalculées en direct. */
    fun setWeight(exIndex: Int, setIndex: Int, weight: Double?) {
        val e = engine ?: return
        update { s ->
            val sess = s.session ?: return@update s
            val ex = sess.exercises.getOrNull(exIndex) ?: return@update s
            val set = ex.sets.getOrNull(setIndex) ?: return@update s
            if (set.weight == weight) return@update s
            val sets = ex.sets.toMutableList().also { it[setIndex] = set.copy(weight = weight) }
            val next = sess.copy(exercises = sess.exercises.toMutableList().also { it[exIndex] = ex.copy(sets = sets) })
            s.copy(session = e.weightChanged(next, exIndex, setIndex, s.history, s.settings, gym(sess.gymId)))
        }
    }

    /** Charge suivante ou précédente réalisable avec le matériel de la salle (disques, pas des haltères ou de la machine). */
    fun nudgeWeight(exIndex: Int, setIndex: Int, up: Boolean) {
        val e = engine ?: return
        val sess = state.value.session ?: return
        val ex = sess.exercises.getOrNull(exIndex) ?: return
        val set = ex.sets.getOrNull(setIndex) ?: return
        val cur = set.weight ?: set.autoWeight ?: 0.0
        val ladder = e.ladderFor(ex.exerciseId, gym(sess.gymId))
        val next = (if (up) ladder.above(cur) else ladder.below(cur)) ?: return
        setWeight(exIndex, setIndex, next)
    }

    /**
     * Échauffements demandés en séance : ceux du réglage (intelligents, sinon le schéma en % de la charge de travail),
     * à la place des échauffements pas encore faits ; une série d'échauffement vide quand aucune charge n'est connue.
     */
    fun addWarmUps(exIndex: Int) {
        val e = engine ?: return
        update { s ->
            val sess = s.session ?: return@update s
            val ex = sess.exercises.getOrNull(exIndex) ?: return@update s
            val exercise = db.value?.exercise(ex.exerciseId)
            val work = ex.sets.firstOrNull { it.type != SetType.WARMUP }
            val weight = work?.weight ?: work?.autoWeight
            val ladder = e.ladderFor(ex.exerciseId, gym(sess.gymId))
            val oneRm = if (exercise?.usesWeight == true) app.superhealth.training.domain.Progression.currentOneRm(e.lastSets(ex.exerciseId, s.history), exercise.upperBody) else null
            val smart = s.settings.warmUp == app.superhealth.training.domain.WarmUpMode.SMART
            // demandés à la main : les muscles déjà travaillés ne les suppriment pas
            var warm = if (smart) e.warmUps(exercise, weight, oneRm, s.settings, ladder, emptySet()) else emptyList()
            if (warm.isEmpty()) warm = e.warmUps(exercise, weight, oneRm, s.settings.copy(warmUp = app.superhealth.training.domain.WarmUpMode.SCHEME), ladder, emptySet())
            if (warm.isEmpty()) warm = listOf(app.superhealth.training.domain.SetLog(UUID.randomUUID().toString(), SetType.WARMUP))
            val kept = ex.sets.filter { it.done || it.type != SetType.WARMUP }
            val doneWarm = kept.takeWhile { it.type == SetType.WARMUP }
            val sets = doneWarm + warm + kept.drop(doneWarm.size)
            s.copy(session = sess.copy(exercises = sess.exercises.toMutableList().also { it[exIndex] = ex.copy(sets = sets) }))
        }
    }

    /** Valide / dévalide une série ; lance le minuteur de repos et la progression en séance. */
    fun toggleSet(exIndex: Int, setIndex: Int) {
        val e = engine ?: return
        update { s ->
            val sess = s.session ?: return@update s
            val ex = sess.exercises[exIndex]
            val set = ex.sets[setIndex]
            val done = !set.done
            val sets = ex.sets.toMutableList().also {
                it[setIndex] = set.copy(done = done, completedAt = if (done) System.currentTimeMillis() else null,
                    weight = set.weight ?: set.autoWeight, reps = set.reps ?: set.autoReps ?: set.target?.minReps)
            }
            var next = sess.copy(exercises = sess.exercises.toMutableList().also { it[exIndex] = ex.copy(sets = sets) })
            if (done) {
                val lastWarm = set.type == SetType.WARMUP && sets.drop(setIndex + 1).none { it.type == SetType.WARMUP }
                val rest = when {
                    set.type == SetType.WARMUP && !lastWarm -> 0
                    lastWarm -> s.settings.restAfterWarmUpSec
                    // repos de l'exercice dans la séance (initialisé depuis la cible, modifiable en séance)
                    else -> ex.restSec
                }
                val inSuperset = ex.supersetGroup != null && sess.exercises.getOrNull(exIndex + 1)?.supersetGroup == ex.supersetGroup
                val r = if (inSuperset) 0 else rest
                next = next.copy(restEndsAt = if (r > 0) System.currentTimeMillis() + r * 1000L else null, restDuration = r)
                if (r > 0) RestTimer.schedule(getApplication(), next.restEndsAt!!, s.settings.restSound) else RestTimer.cancel(getApplication())
                next = e.afterSetCompleted(next, exIndex, s.settings, gym(sess.gymId))
            }
            s.copy(session = next)
        }
    }

    /** Ouverture automatique de la séance en cours déjà faite pour ce processus. */
    var resumedOnLaunch = false

    /** Route demandée par le widget (consommée par WorkoutRoot). */
    val pendingRoute = MutableStateFlow<String?>(null)


    /** Démarre le prochain jour du programme actif (raccourci du widget). */
    fun startNext(): Boolean {
        if (state.value.session != null) return true
        val p = activeProgram() ?: return false
        if (p.days.getOrNull(p.dayIndex)?.isRest != false) return false
        startProgramDay(p, p.dayIndex, p.cycleIndex)
        return true
    }

    fun setCurrent(i: Int) = session { if (it.current == i) it else it.copy(current = i) }

    fun togglePause() = session { s ->
        val now = System.currentTimeMillis()
        if (s.pausedAt != null) s.copy(pausedMs = s.pausedMs + (now - s.pausedAt), pausedAt = null)
        else { RestTimer.cancel(getApplication()); s.copy(pausedAt = now, restEndsAt = null) }
    }

    fun removeExercise(i: Int) = session { s ->
        val list = s.exercises.filterIndexed { j, _ -> j != i }
        s.copy(exercises = list, current = s.current.coerceAtMost((list.size - 1).coerceAtLeast(0)))
    }

    fun moveExercise(from: Int, to: Int) = session { s ->
        if (to !in s.exercises.indices) return@session s
        val list = s.exercises.toMutableList(); val e = list.removeAt(from); list.add(to, e)
        val ng = Supersets.normalize(list.map { it.supersetGroup })
        s.copy(exercises = list.mapIndexed { k, x -> x.copy(supersetGroup = ng[k]) }, current = to)
    }

    /** Relie l'exercice au suivant (superset, puis triset…) ; le dernier exercice se relie au précédent. */
    fun linkNext(i: Int) = session { s ->
        val g = s.exercises.map { it.supersetGroup }
        val ng = if (i + 1 < g.size) Supersets.link(g, i) else Supersets.link(g, i - 1)
        s.copy(exercises = s.exercises.mapIndexed { k, e -> e.copy(supersetGroup = ng[k]) })
    }

    /** Sort l'exercice de son superset / triset. */
    fun unlink(i: Int) = session { s ->
        val ng = Supersets.unlink(s.exercises.map { it.supersetGroup }, i)
        s.copy(exercises = s.exercises.mapIndexed { k, e -> e.copy(supersetGroup = ng[k]) })
    }

    /**
     * Exercice à afficher après une série validée en [i] : partenaire de superset ayant encore des séries,
     * sinon l'exercice suivant non terminé ; null = rester sur place.
     */
    fun nextFocus(s: Session, i: Int): Int? {
        val ex = s.exercises.getOrNull(i) ?: return null
        fun pending(k: Int) = s.exercises[k].sets.any { !it.done }
        if (ex.supersetGroup != null) {
            val group = s.exercises.indices.filter { s.exercises[it].supersetGroup == ex.supersetGroup }
            val order = group.dropWhile { it != i }.drop(1) + group.takeWhile { it != i }
            order.firstOrNull { pending(it) }?.let { return it }
        }
        if (pending(i)) return null
        return ((i + 1 until s.exercises.size) + (0 until i)).firstOrNull { pending(it) }
    }

    fun adjustRest(deltaSec: Int) = session { s ->
        val end = s.restEndsAt ?: return@session s
        val ne = end + deltaSec * 1000L
        if (ne <= System.currentTimeMillis()) { RestTimer.cancel(getApplication()); s.copy(restEndsAt = null) }
        else { RestTimer.schedule(getApplication(), ne, settings.restSound); s.copy(restEndsAt = ne, restDuration = s.restDuration + deltaSec) }
    }

    /** Nouveau repos pour l'exercice [i] ; un repos en cours est recalé sur la nouvelle durée. */
    fun setRest(i: Int, sec: Int) = session { s ->
        val list = s.exercises.toMutableList().also { if (i in it.indices) it[i] = it[i].copy(restSec = sec) }
        val end = s.restEndsAt
        val now = System.currentTimeMillis()
        if (end == null || end <= now || s.restDuration <= 0) return@session s.copy(exercises = list)
        val ne = end - s.restDuration * 1000L + sec * 1000L
        if (ne <= now) { RestTimer.cancel(getApplication()); s.copy(exercises = list, restEndsAt = null) }
        else { RestTimer.schedule(getApplication(), ne, settings.restSound); s.copy(exercises = list, restEndsAt = ne, restDuration = sec) }
    }

    fun skipRest() { RestTimer.cancel(getApplication()); session { it.copy(restEndsAt = null) } }

    /** Écarts entre la séance terminée et son jour de programme, proposés sur l'écran de fin (« Workout Changes »). */
    data class PlanChange(val programId: String, val day: ProgramDay, val session: Session, val diff: PlanDiff)
    val planChange = MutableStateFlow<PlanChange?>(null)

    fun applyPlanChange(sel: PlanDiff.Selection) {
        val c = planChange.value ?: return
        planChange.value = null
        val day = PlanDiff.apply(c.day, c.session, c.diff, sel)
        update { s -> s.copy(programs = s.programs.map { p -> if (p.id != c.programId) p else p.copy(days = p.days.map { if (it.id == day.id) day else it }) }) }
    }

    /** « Removes this session from this week's schedule » : la séance proposée est passée, la suivante devient la prochaine. */
    fun skipNext() {
        val e = engine ?: return
        update { s -> s.copy(programs = s.programs.map { if (it.id == s.settings.activeProgramId) e.advance(it, it.days.getOrNull(it.dayIndex)?.id) else it }) }
    }

    fun finishSession(logAll: Boolean = false) {
        val e = engine ?: return
        RestTimer.cancel(getApplication())
        val s0 = state.value
        val sess = s0.session ?: return
        val program = s0.programs.firstOrNull { it.id == sess.programId }
        val day = program?.days?.firstOrNull { it.id == sess.dayId }
        planChange.value = if (program != null && day != null && sess.adjust == 0) PlanDiff.compute(day, sess).takeUnless { it.isEmpty }?.let { PlanChange(program.id, day, sess, it) } else null
        val w = e.finish(sess, System.currentTimeMillis(), gym(sess.gymId), program?.name, logAll)
        update { s ->
            val programs = if (program != null) s.programs.map { if (it.id == program.id) e.advance(it, sess.dayId) else it } else s.programs
            s.copy(session = null, history = if (w.exercises.isEmpty()) s.history else s.history + w, programs = programs)
        }
        if (w.exercises.isNotEmpty()) completed.value = w
    }

    fun discardSession() { RestTimer.cancel(getApplication()); update { it.copy(session = null) } }

    fun deleteWorkout(id: String) = update { s -> s.copy(history = s.history.filter { it.id != id }) }

    /** Édition d'une séance passée (WorkoutHistoryEditView). */
    fun saveWorkout(w: Workout) = update { s -> s.copy(history = s.history.map { if (it.id == w.id) w else it }.sortedBy { it.startTime }) }

    /** « Save to Library » : la séance devient un entraînement de la bibliothèque, cibles reprises des séries faites. */
    fun saveToLibrary(w: Workout) = saveCustom(asCustom(w))

    /** Refaire une séance passée : mêmes exercices, séries et supersets ; charges proposées d'après l'historique. */
    fun repeatWorkout(w: Workout) { if (state.value.session == null) startCustom(asCustom(w)) }

    private fun asCustom(w: Workout) = CustomWorkout(UUID.randomUUID().toString(), w.name, w.exercises.map { ex ->
        val work = ex.sets.filter { it.type != SetType.WARMUP }
        val targets = work.map { set -> set.target ?: (set.reps ?: 10).let { r -> app.superhealth.training.domain.TargetSet(set.type, (r - 2).coerceAtLeast(1), r + 2, set.rir ?: 2, ex.restSec) } }
        app.superhealth.training.domain.ProgramExercise(UUID.randomUUID().toString(), ex.exerciseId, listOf(targets.ifEmpty { listOf(app.superhealth.training.domain.TargetSet()) }), ex.supersetGroup)
    }, state.value.gyms.firstOrNull { it.name == w.gymName }?.id)

    // ----- Sauvegarde -----

    fun exportJson() = WorkoutApp.store.export()
    fun importJson(text: String) = runCatching { WorkoutApp.store.import(text) }
}
