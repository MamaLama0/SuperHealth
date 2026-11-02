package app.superhealth.league

import android.content.Context
import app.superhealth.SuperPrefs
import app.superhealth.Feature
import app.superhealth.has
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.Metric
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.domain.stepGoalFor
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.Analytics
import app.superhealth.training.domain.Strength
import app.superhealth.meditation.Meditation
import app.superhealth.stretch.Stretch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.ZoneId

object ProgressRepository {
    private val lock = Mutex()
    private var lastKey = 0
    private var lastAt = 0L
    val current = MutableStateFlow<Progression.Result?>(null)
    suspend fun compute(ctx: Context, today: LocalDate = LocalDate.now()): Progression.Result = lock.withLock { withContext(Dispatchers.IO) {
        val day = today.toEpochDay(); val from = day - 89; val zone = ZoneId.systemDefault()
        val events = mutableMapOf<Long, MutableSet<Progression.Track>>()
        fun add(d: Long, t: Progression.Track) { if (d <= day) events.getOrPut(d) { mutableSetOf() }.add(t) }
        val sh = SuperPrefs.settings.value
        val repo = NutriApp.repository
        val calories = repo.entries.dailyCalories()
        val flags = repo.dayFlags.all()
        val partial = flags.filter { it.partial }.map { it.epochDay }.toSet()
        val db = WorkoutApp.db.first { it != null }!!
        val training = WorkoutApp.store.state.value
        val active = training.programs.firstOrNull { it.id == training.settings.activeProgramId }
        val fullGoal = active?.days?.count { !it.isRestAt(active.cycleIndex) }?.takeIf { it > 0 }?.coerceAtMost(7)
            ?: League.state.value.weeklyTargets["STRENGTH"] ?: 2
        val inputKey = listOf(day, calories.hashCode(), flags.hashCode(), repo.prefs.program.value.hashCode(), repo.prefs.checkIns.value.hashCode(),
            repo.prefs.profile.value.hashCode(), repo.prefs.settings.value.hashCode(), repo.prefs.goals.value.hashCode(), training.programs.hashCode(), training.settings.activeProgramId,
            app.superhealth.health.data.HealthRepository.revision, WorkoutApp.store.state.value.history.hashCode(),
            SuperPrefs.hc.value.hashCode(), sh.features.hashCode(), Meditation.log.value.hashCode(), Stretch.log.value.hashCode(),
            League.state.value.tracked.hashCode(), League.state.value.weeklyTargets.hashCode()).hashCode()
        current.value?.takeIf { inputKey == lastKey && System.currentTimeMillis() - lastAt < 300000 }?.let { return@withContext it }
        val nutrition = repo.analytics().first()
        calories.filter { it.epochDay !in partial && it.epochDay <= day }.forEach { entry ->
            if (Progression.nutritionQualified(entry.kcal, nutrition.targetFor(entry.epochDay).macros.kcal, entry.epochDay < day)) add(entry.epochDay, Progression.Track.FOOD)
        }
        val todayCalories = calories.firstOrNull { it.epochDay == day }?.kcal ?: 0.0
        val todayTarget = nutrition.targetFor(day).macros.kcal
        val percent = if (todayTarget > 0 && todayCalories.isFinite()) ((todayCalories / todayTarget) * 100).toInt().coerceIn(0, 1000) else null
        val history = training.history.distinctBy { it.id }.sortedBy { it.startTime }
        val sessions = mutableMapOf<Long, Int>()
        val strengthGoals = mutableMapOf(Progression.week(day) to fullGoal)
        val records = mutableMapOf<Long, Int>(); val best = mutableMapOf<String, Double>()
        history.forEach { w ->
            val d = Analytics.day(w.startTime).toEpochDay()
            if (w.exercises.any { Analytics.workingSets(it).isNotEmpty() }) {
                add(d, Progression.Track.STRENGTH)
                sessions[d] = (sessions[d] ?: 0) + 1
                if (Progression.week(d) < Progression.week(day)) {
                    val saved = training.programs.firstOrNull { it.id == w.programId }
                    val goal = w.plannedSessions ?: saved?.days?.count { !it.isRestAt(w.cycleIndex ?: saved.cycleIndex) }?.takeIf { it > 0 } ?: fullGoal
                    strengthGoals[Progression.week(d)] = goal.coerceIn(1, 7)
                }
            }
            w.exercises.forEach { e ->
                val exercise = db?.exercise(e.exerciseId)
                val value = Analytics.workingSets(e).filter { (it.reps ?: 0) > 0 }.maxOfOrNull { s ->
                    if (exercise?.usesWeight == true && (s.weight ?: 0.0) > 0 && s.reps!! <= 12)
                        Strength.e1rm(s.weight!!, s.reps!!, s.rir, exercise.upperBody, s.partialReps)
                    else if (exercise?.usesWeight == false) s.reps!!.toDouble() else 0.0
                } ?: 0.0
                val old = best[e.exerciseId]
                if (value > 0) {
                    if (old != null && value > old) records[d] = (records[d] ?: 0) + 1
                    best[e.exerciseId] = maxOf(old ?: 0.0, value)
                }
            }
        }
        val mindfulMinutes = (Meditation.log.value.map { it.day to it.minutes } + Stretch.log.value.map { it.day to it.minutes })
            .groupBy { it.first }.mapValues { (_, rows) -> rows.sumOf { it.second } }
        mindfulMinutes.filterValues { it >= 10 }.keys.forEach { add(it, Progression.Track.MIND) }
        if (sh.has(Feature.HEALTH)) {
            Bridge.activities(LocalDate.ofEpochDay(from).atStartOfDay(zone).toInstant().toEpochMilli()).filter { it.kind == Bridge.Kind.RUN && it.minutes > 0 }.forEach {
                add(java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate().toEpochDay(), Progression.Track.RUN)
            }
            // Limité à 90 jours pour la première reprise montre ; le « + » signale une série qui atteint cette borne.
            for (d in from..day) {
                val date = LocalDate.ofEpochDay(d)
                if ((Bridge.healthValue(ctx, Metric.STEPS, date) ?: 0.0) >= nutrition.settings.stepGoalFor(date).takeIf { it > 0 }?.toDouble().let { it ?: Double.POSITIVE_INFINITY }) add(d, Progression.Track.STEPS)
                if ((Bridge.healthValue(ctx, Metric.SLEEP, date) ?: 0.0) >= app.superhealth.health.data.Scores.sleepNeed()) add(d, Progression.Track.SLEEP)
            }
        }
        val defaults = buildSet {
            if (sh.has(Feature.NUTRITION)) add(Progression.Track.FOOD)
            if (sh.has(Feature.TRAINING)) add(Progression.Track.STRENGTH)
            if (sh.has(Feature.HEALTH)) addAll(listOf(Progression.Track.RUN, Progression.Track.STEPS, Progression.Track.SLEEP))
            if (Meditation.available() || Stretch.available()) add(Progression.Track.MIND)
        }
        val tracks = if (League.state.value.tracked == null) defaults else League.state.value.tracked.orEmpty().mapNotNull { n -> Progression.Track.entries.firstOrNull { it.name == n } }.toSet()
        Progression.calculate(events, day, tracks, League.state.value.weeklyTargets + ("STRENGTH" to fullGoal), records, events.keys.minOrNull() ?: day,
            sessions, strengthGoals).let { raw ->
            val limited = raw.summary.streaks.map { s -> if (s.track in setOf("RUN", "STEPS", "SLEEP")) s.copy(capped = s.current > 0 && (if (s.weekly) s.current * 7 >= 84 else s.current >= 89)) else s.copy(capped = false, percent = percent.takeIf { s.track == "FOOD" }) }
            raw.copy(summary = raw.summary.copy(streaks = limited)).also { current.value = it; lastKey = inputKey; lastAt = System.currentTimeMillis() }
        }
    } }
}
