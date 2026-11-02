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
import app.superhealth.training.domain.Program
import app.superhealth.training.domain.Analytics
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

internal fun Program.leagueTarget(cycle: Int = cycleIndex): Int? =
    days.count { !it.isRestAt(cycle) }.takeIf { it > 0 }?.coerceAtMost(7)

object ProgressRepository {
    fun availableTracks(sh: app.superhealth.SuperSettings): Set<Progression.Track> = buildSet {
        if (sh.has(Feature.NUTRITION)) add(Progression.Track.FOOD)
        if (sh.has(Feature.TRAINING)) add(Progression.Track.STRENGTH)
        if (sh.has(Feature.HEALTH)) addAll(listOf(Progression.Track.RUN, Progression.Track.STEPS, Progression.Track.SLEEP))
        if (Meditation.available() || Stretch.available()) add(Progression.Track.MIND)
    }

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
        val fullGoal = active?.leagueTarget()
            ?: League.state.value.weeklyTargets["STRENGTH"] ?: 2
        val inputKey = listOf(day, calories.hashCode(), flags.hashCode(), repo.prefs.program.value.hashCode(), repo.prefs.checkIns.value.hashCode(),
            repo.prefs.profile.value.hashCode(), repo.prefs.settings.value.hashCode(), repo.prefs.goals.value.hashCode(), training.programs.hashCode(), training.settings.activeProgramId,
            app.superhealth.health.data.HealthRepository.revision, WorkoutApp.store.state.value.history.hashCode(),
            SuperPrefs.hc.value.hashCode(), sh.features.hashCode(), app.superhealth.modules.Modules.installed.value.hashCode(), Meditation.log.value.hashCode(), Stretch.log.value.hashCode(),
            League.state.value.questXp, League.state.value.questFocus.hashCode(), League.state.value.tracked.hashCode(), League.state.value.weeklyTargets.hashCode()).hashCode()
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
                    val goal = w.plannedSessions ?: saved?.leagueTarget(w.cycleIndex ?: saved.cycleIndex) ?: fullGoal
                    strengthGoals[Progression.week(d)] = goal.coerceIn(1, 7)
                }
            }
            w.exercises.forEach { e ->
                val exercise = db.exercise(e.exerciseId)
                val value = exercise?.let { Analytics.bestPerformance(it, Analytics.workingSets(e)) } ?: 0.0
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
        val savedHealth = League.state.value.observedHealthDays
        savedHealth.forEach { (track, days) ->
            Progression.Track.entries.firstOrNull { it.name == track }?.let { t -> days.filter { it < from }.forEach { add(it, t) } }
        }
        var hasRecentHealthData = false
        if (sh.has(Feature.HEALTH)) {
            Bridge.activities(LocalDate.ofEpochDay(from).atStartOfDay(zone).toInstant().toEpochMilli()).filter { it.kind == Bridge.Kind.RUN && it.minutes > 0 }.forEach {
                add(java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate().toEpochDay(), Progression.Track.RUN)
            }
            // Limité à 90 jours pour la première reprise montre ; le « + » signale une série qui atteint cette borne.
            for (d in from..day) {
                val date = LocalDate.ofEpochDay(d)
                val steps = Bridge.healthValue(ctx, Metric.STEPS, date)
                val sleep = Bridge.healthValue(ctx, Metric.SLEEP, date)
                if (d in (day - 14) until day && (steps != null || sleep != null)) hasRecentHealthData = true
                if ((steps ?: 0.0) >= nutrition.settings.stepGoalFor(date).takeIf { it > 0 }?.toDouble().let { it ?: Double.POSITIVE_INFINITY }) add(d, Progression.Track.STEPS)
                if ((sleep ?: 0.0) >= app.superhealth.health.data.Scores.sleepNeed()) add(d, Progression.Track.SLEEP)
            }
        }
        val observedHealth = if (sh.has(Feature.HEALTH)) listOf(Progression.Track.RUN, Progression.Track.STEPS, Progression.Track.SLEEP).associate { t ->
            t.name to (savedHealth[t.name].orEmpty() + events.filterValues { t in it }.keys).filter { it <= day }.sorted().takeLast(5000).toSet()
        } else savedHealth
        val healthSince = if (sh.has(Feature.HEALTH)) minOf(League.state.value.healthSince ?: from, from) else League.state.value.healthSince
        val defaults = availableTracks(sh)
        val tracks = if (League.state.value.tracked == null) defaults else League.state.value.tracked.orEmpty().mapNotNull { n -> Progression.Track.entries.firstOrNull { it.name == n } }.toSet().intersect(defaults)
        val weekStart = Progression.week(day)
        fun questCounts(first: Long, last: Long) = WeeklyQuests.Metric.entries.associateWith { metric ->
            when (metric) {
                WeeklyQuests.Metric.ACTIVE -> events.count { (d, ts) -> d in first..last && ts.any { it in tracks } }
                WeeklyQuests.Metric.STRENGTH -> sessions.filterKeys { it in first..last }.values.sum()
                WeeklyQuests.Metric.STRENGTH_REVIEW -> history.count { w -> Analytics.day(w.startTime).toEpochDay() in first..last && w.rpe != null && w.exercises.any { Analytics.workingSets(it).isNotEmpty() } }
                WeeklyQuests.Metric.FOOD -> calories.count { it.epochDay in first..last && it.kcal > 0 && it.epochDay !in partial }
                WeeklyQuests.Metric.MIND -> mindfulMinutes.count { (d, m) -> d in first..last && m >= 5 }
                else -> events.count { (d, ts) -> d in first..last && metric.track in ts }
            }
        }
        val comparisons = history.filter { Analytics.day(it.startTime).toEpochDay() in (day - 28)..day }
            .flatMap { w -> w.exercises.mapNotNull { e ->
                val exercise = db.exercise(e.exerciseId) ?: return@mapNotNull null
                if (!exercise.usesWeight) return@mapNotNull null
                val bestSet = Analytics.bestPerformance(exercise, Analytics.workingSets(e)) ?: return@mapNotNull null
                e.exerciseId to (Analytics.day(w.startTime).toEpochDay() to bestSet)
            } }.groupBy({ it.first }, { it.second })
        val stagnant = comparisons.values.count { samples -> samples.size >= 3 && samples.last().first - samples.first().first >= 14 && WeeklyQuests.plateau(samples.map { it.second }) } >= 2
        val selectedFocus = League.state.value.questFocus
        val questWeek = if (selectedFocus == null) null else WeeklyQuests.update(
            League.state.value.questWeeks.firstOrNull { it.start == weekStart }, day, defaults, questCounts(weekStart, day), selectedFocus,
            WeeklyQuests.Evidence(questCounts(day - 14, day - 1), fullGoal, League.state.value.weeklyTargets["RUN"] ?: 2, stagnant, hasRecentHealthData), League.state.value.questWeeks)
        val weeks = if (questWeek == null) League.state.value.questWeeks else
            (League.state.value.questWeeks.filterNot { it.start == weekStart } + questWeek).sortedBy { it.start }.takeLast(52)
        if (weeks != League.state.value.questWeeks) League.configure(ctx) { it.copy(questWeeks = weeks) }
        val bonus = League.state.value.questXp
        val weekBonus = weeks.firstOrNull { it.start == weekStart }?.quests.orEmpty().filter { it.claimed }.sumOf { it.reward }
        Progression.calculate(events, day, tracks, League.state.value.weeklyTargets + ("STRENGTH" to fullGoal), records, events.keys.minOrNull() ?: day,
            sessions, strengthGoals).let { raw ->
            val limited = raw.summary.streaks.map { s -> if (s.track in setOf("RUN", "STEPS", "SLEEP")) s.copy(capped = s.current > 0 && (if (s.weekly) s.current * 7 >= day - (healthSince ?: from) - 5 else s.current >= day - (healthSince ?: from))) else s.copy(capped = false, percent = percent.takeIf { s.track == "FOOD" }) }
            raw.copy(summary = raw.summary.copy(totalXp = raw.summary.totalXp + bonus, level = Progression.level(raw.summary.totalXp + bonus), weekXp = raw.summary.weekXp + weekBonus, streaks = limited, totals = mapOf(
                "quests" to League.state.value.questsCompleted,
                "sessions" to sessions.filterKeys { it <= day }.values.sum().toLong(),
                "foodDays" to events.count { it.key <= day && Progression.Track.FOOD in it.value }.toLong(),
                "mindMinutes" to mindfulMinutes.filterKeys { it <= day }.values.sum().toLong()
            ))).also { result ->
                current.value = result; lastKey = inputKey; lastAt = System.currentTimeMillis()
                val earned = League.state.value.earnedBadges + Achievements.unlocked(result.summary)
                if (earned != League.state.value.earnedBadges || observedHealth != League.state.value.observedHealthDays || healthSince != League.state.value.healthSince)
                    League.configure(ctx) { it.copy(earnedBadges = it.earnedBadges + earned, observedHealthDays = observedHealth, healthSince = healthSince) }
            }
        }
    } }
}
