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
        if (app.superhealth.modules.Modules.isActive(app.superhealth.modules.Modules.HIKING)) add(Progression.Track.HIKE)
        if (Meditation.available() || Stretch.available()) add(Progression.Track.MIND)
    }

    fun selectedTracks(sh: app.superhealth.SuperSettings): Set<Progression.Track> {
        val available = availableTracks(sh)
        return League.state.value.tracked?.mapNotNull { name -> Progression.Track.entries.firstOrNull { it.name == name } }
            ?.toSet()?.intersect(available) ?: available.filter { track -> when (track) {
                Progression.Track.STRENGTH -> !sh.activityAsked || "STRENGTH" in sh.activitySections
                Progression.Track.RUN -> !sh.activityAsked || "RUN" in sh.activitySections
                Progression.Track.HIKE -> "HIKING" in sh.activitySections || HikingProgress.outings.value.isNotEmpty()
                Progression.Track.MIND -> sh.activitySections.any { it in setOf("MEDITATION", "STRETCH") } || Meditation.log.value.isNotEmpty() || Stretch.log.value.isNotEmpty()
                else -> true
            } }.toSet()
    }

    private val lock = Mutex()
    private var lastKey = 0
    private var lastAt = 0L
    val current = MutableStateFlow<Progression.Result?>(null)
    val foodRange = MutableStateFlow(Progression.nutritionRange())
    internal fun foodRangeAt(nutrition: app.superhealth.nutrition.data.Analytics, day: Long): Progression.NutritionRange {
        val checkIn = nutrition.checkInFor(day) ?: nutrition.checkIns.firstOrNull()
        val type = nutrition.goalAt(day)?.type ?: checkIn?.goalType ?: nutrition.program.goalType
        val manual = nutrition.program.style == app.superhealth.nutrition.domain.ProgramStyle.MANUAL || checkIn?.manual == true
        val overridden = app.superhealth.nutrition.domain.ManualTargets.overrideAt(nutrition.program, day) != null
        return Progression.nutritionRange(type, manual || overridden)
    }
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
            SuperPrefs.hc.value.hashCode(), app.superhealth.hiking.Hiking.library.value.completed.hashCode(), HikingProgress.outings.value.hashCode(), sh.features.hashCode(), sh.activityAsked, sh.activitySections.hashCode(), app.superhealth.modules.Modules.installed.value.hashCode(), Meditation.log.value.hashCode(), Stretch.log.value.hashCode(),
            League.state.value.questXp, League.state.value.questFocus.hashCode(), League.state.value.tracked.hashCode(), League.state.value.weeklyTargets.hashCode()).hashCode()
        current.value?.takeIf { inputKey == lastKey && System.currentTimeMillis() - lastAt < 300000 }?.let { return@withContext it }
        val nutrition = repo.analytics().first()
        foodRange.value = foodRangeAt(nutrition, day)
        calories.filter { it.epochDay !in partial && it.epochDay <= day }.forEach { entry ->
            if (Progression.nutritionQualified(entry.kcal, nutrition.targetFor(entry.epochDay).macros.kcal, foodRangeAt(nutrition, entry.epochDay))) add(entry.epochDay, Progression.Track.FOOD)
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
        mindfulMinutes.filter { (_, m) -> DailyScore.validated(m.toDouble(), 10.0, DailyScore.MIND) }.keys.forEach { add(it, Progression.Track.MIND) }
        val hiking = HikingProgress.collect(ctx,LocalDate.ofEpochDay(from).atStartOfDay(zone).toInstant().toEpochMilli())
        hiking.forEach { add(HikingProgress.day(it,zone),Progression.Track.HIKE) }
        val savedHealth = League.state.value.observedHealthDays
        savedHealth.forEach { (track, days) ->
            Progression.Track.entries.firstOrNull { it.name == track }?.let { t -> days.filter { it < from }.forEach { add(it, t) } }
        }
        val sportDays = events.filterValues { it.any { track -> track in setOf(Progression.Track.STRENGTH, Progression.Track.HIKE) } }
            .keys.filter { it >= day - 34 }.toMutableSet()
        sportDays += Stretch.log.value.filter { it.minutes > 0 && it.day in day - 34..day }.map { it.day }
        var hasRecentHealthData = false
        // Distance parcourue chaque jour (mètres) : celle du téléphone ou de la montre, sinon les pas × la foulée.
        val distance = League.state.value.distanceDays.toMutableMap()
        val stride = (nutrition.profile.heightCm.takeIf { it in 120.0..230.0 } ?: 170.0) * 0.415 / 100
        if (sh.has(Feature.HEALTH)) {
            Bridge.activityFacts(LocalDate.ofEpochDay(from).atStartOfDay(zone).toInstant().toEpochMilli()).filter { it.minutes > 0 }.forEach {
                val date = java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate().toEpochDay()
                if (date in day - 34..day) sportDays += date
                if (it.kind == Bridge.Kind.RUN) add(date, Progression.Track.RUN)
            }
            // Limité à 90 jours pour la première reprise montre ; le « + » signale une série qui atteint cette borne.
            for (d in from..day) {
                val date = LocalDate.ofEpochDay(d)
                val steps = Bridge.healthValue(ctx, Metric.STEPS, date)
                val sleep = Bridge.healthValue(ctx, Metric.SLEEP, date)
                if (d in (day - 14) until day && (steps != null || sleep != null)) hasRecentHealthData = true
                (Bridge.healthValue(ctx, Metric.DISTANCE, date)?.takeIf { it > 0 }?.let { it * 1000 } ?: steps?.let { it * stride })
                    ?.takeIf { it.isFinite() && it < 200_000 }?.let { distance[d] = it.toInt() }
                if (DailyScore.validated(steps, nutrition.settings.stepGoalFor(date).takeIf { it > 0 }?.toDouble(), DailyScore.STEPS)) add(d, Progression.Track.STEPS)
                if (DailyScore.validated(sleep, app.superhealth.health.data.Scores.sleepNeed(), DailyScore.SLEEP)) add(d, Progression.Track.SLEEP)
            }
        }
        val observedHealth = if (sh.has(Feature.HEALTH)) listOf(Progression.Track.RUN, Progression.Track.STEPS, Progression.Track.SLEEP).associate { t ->
            t.name to (savedHealth[t.name].orEmpty() + events.filterValues { t in it }.keys).filter { it <= day }.sorted().takeLast(5000).toSet()
        } else savedHealth
        val healthSince = if (sh.has(Feature.HEALTH)) minOf(League.state.value.healthSince ?: from, from) else League.state.value.healthSince
        val defaults = availableTracks(sh)
        val tracks = selectedTracks(sh)
        val weekStart = Progression.week(day)
        fun questCounts(first: Long, last: Long) = WeeklyQuests.Metric.entries.associateWith { metric ->
            when (metric) {
                WeeklyQuests.Metric.ACTIVE -> events.count { (d, ts) -> d in first..last && ts.any { it in tracks } }
                WeeklyQuests.Metric.STRENGTH -> sessions.filterKeys { it in first..last }.values.sum()
                WeeklyQuests.Metric.STRENGTH_REVIEW -> history.count { w -> Analytics.day(w.startTime).toEpochDay() in first..last && w.rpe != null && w.exercises.any { Analytics.workingSets(it).isNotEmpty() } }
                WeeklyQuests.Metric.FOOD -> events.count { (d, ts) -> d in first..last && Progression.Track.FOOD in ts }
                WeeklyQuests.Metric.HIKE -> hiking.count { HikingProgress.day(it,zone) in first..last }
                WeeklyQuests.Metric.MIND -> mindfulMinutes.count { (d, m) -> d in first..last && m >= 10 }
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
        // Plateau : au moins deux exercices dont la tendance de force est nulle et bien mesurée ([StrengthModel]).
        val experience = training.settings.experience
        val bias = app.superhealth.training.domain.StrengthModel.rirBias(history, db)
        val stagnant = comparisons.keys.count { id ->
            val e = db.exercise(id) ?: return@count false
            app.superhealth.training.domain.StrengthModel.trend(e, history, experience, bias, Long.MAX_VALUE)
                ?.let { (slope, sd) -> slope < 0.0005 && sd < 0.003 } ?: (comparisons[id]!!.let { s -> s.size >= 3 && s.last().first - s.first().first >= 14 && WeeklyQuests.plateau(s.map { it.second }) })
        } >= 2
        val previousWeek = League.state.value.questWeeks.firstOrNull { it.start == weekStart }
        val questWeek: WeeklyQuests.Week? = WeeklyQuests.update(
            previousWeek, day, tracks, questCounts(weekStart, day), WeeklyQuests.AUTO,
            WeeklyQuests.Evidence(questCounts(day - 14, day - 1), fullGoal, League.state.value.weeklyTargets["RUN"] ?: 2, stagnant, hasRecentHealthData), League.state.value.questWeeks)
            // Plus de défi miroir : une quête partagée avec des amis devient un défi interactif (course ou ensemble, voir Economy).
            .let { w -> w.copy(mirror = null) }
        if (questWeek?.mirror != null && previousWeek?.mirror == null)
            SocialNotifications.mirrorChallenge(ctx, questWeek.mirror.name, questWeek.mirror.metric)
        val weeks = if (questWeek == null) League.state.value.questWeeks else
            (League.state.value.questWeeks.filterNot { it.start == weekStart } + questWeek).sortedBy { it.start }.takeLast(52)
        if (weeks != League.state.value.questWeeks) League.configure(ctx) { it.copy(questWeeks = weeks) }
        val bonus = League.state.value.questXp
        val weekBonus = weeks.firstOrNull { it.start == weekStart }?.quests.orEmpty().filter { it.claimed }.sumOf { it.reward }
        Progression.calculate(events, day, tracks, League.state.value.weeklyTargets + ("STRENGTH" to fullGoal) + ("HIKE" to (League.state.value.weeklyTargets["HIKE"] ?: 1)), records, events.keys.minOrNull() ?: day,
            sessions, strengthGoals).let { raw ->
            val limited = raw.summary.streaks.map { s -> if (s.track in setOf("RUN", "STEPS", "SLEEP")) s.copy(capped = s.current > 0 && (if (s.weekly) s.current * 7 >= day - (healthSince ?: from) - 5 else s.current >= day - (healthSince ?: from))) else s.copy(capped = false, percent = percent.takeIf { s.track == "FOOD" }) }
            // Séries à la semaine (sans la randonnée) : 3 jours sur 7, ou l'objectif de séances et de sorties, avec une
            // séance de marge sur les semaines passées (comme les séries au jour près).
            val thisWeek = Progression.week(day)
            fun soft(goal: Int, w: Long) = if (w < thisWeek) (goal - 1).coerceAtLeast(1) else goal
            val weekly = Progression.weekStreaks(events, day, tracks.filter { it != Progression.Track.HIKE }) { t, w -> when (t) {
                Progression.Track.STRENGTH -> soft(strengthGoals[w] ?: fullGoal, w)
                Progression.Track.RUN -> soft(League.state.value.weeklyTargets["RUN"] ?: 1, w)
                else -> 3 } }
            raw.copy(validatedToday = events[day].orEmpty().intersect(tracks), sportDays = sportDays,
                recentValidated = events.filterKeys { it in day - 34..day }.mapValues { (_, values) -> values.intersect(tracks) }, summary = raw.summary.copy(weekly = weekly, questsDone = weeks.firstOrNull { it.start == weekStart }?.quests.orEmpty().filter { it.complete }.map { it.metric.name }, totalXp = raw.summary.totalXp + bonus, level = Progression.level(raw.summary.totalXp + bonus), weekXp = raw.summary.weekXp + weekBonus, streaks = limited, totals = mapOf(
                "quests" to League.state.value.questsCompleted,
                "sessions" to sessions.filterKeys { it <= day }.values.sum().toLong(),
                "foodDays" to events.count { it.key <= day && Progression.Track.FOOD in it.value }.toLong(),
                "mindMinutes" to mindfulMinutes.filterKeys { it <= day }.values.sum().toLong(),
                "distanceKm" to distance.filterKeys { it <= day }.values.sumOf { it.toLong() } / 1000
            ) + HikingProgress.totals(hiking))).also { result ->
                if (distance != League.state.value.distanceDays) League.configure(ctx) { it.copy(distanceDays = distance.toSortedMap().entries.toList().takeLast(4000).associate { e -> e.key to e.value }) }
                current.value = result; lastKey = inputKey; lastAt = System.currentTimeMillis()
                val previous = League.state.value
                val earned = previous.earnedBadges + Achievements.unlocked(result.summary)
                val newBadges = earned - previous.earnedBadges
                if (previous.progressNotificationBaseline && (newBadges.isNotEmpty() || result.summary.level > previous.progressNotificationLevel))
                    SocialNotifications.progressEarned(ctx, newBadges.size, result.summary.level.takeIf { it > previous.progressNotificationLevel })
                if (!previous.progressNotificationBaseline || previous.progressNotificationLevel != result.summary.level)
                    League.configure(ctx) { it.copy(progressNotificationBaseline = true, progressNotificationLevel = result.summary.level) }
                if (earned != League.state.value.earnedBadges || observedHealth != League.state.value.observedHealthDays || healthSince != League.state.value.healthSince)
                    League.configure(ctx) { it.copy(earnedBadges = it.earnedBadges + earned, observedHealthDays = observedHealth, healthSince = healthSince) }
                // Départ de la mise à jour Dopamine, dès que les badges déjà obtenus sont connus.
                runCatching { app.superhealth.cards.CardStore.start(ctx, earned) }
            }
        }
    } }
}
