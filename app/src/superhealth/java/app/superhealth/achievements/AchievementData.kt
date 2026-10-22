package app.superhealth.achievements

import android.content.Context
import app.superhealth.bridge.Bridge
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.ManualLogStore
import app.superhealth.health.data.Metric
import app.superhealth.health.data.Scores
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.data.Repository
import app.superhealth.nutrition.domain.stepGoalFor
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.Analytics as TrainingAnalytics
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Lecture des données de l'app pour les succès, et mémoire des trophées déjà vus (pour annoncer les nouveaux). */
object AchievementData {
    /** Au plus deux ans d'historique ; le sommeil, plus coûteux à relire, sur les 120 derniers jours. */
    private const val MAX_DAYS = 730L
    private const val SLEEP_DAYS = 120L

    private var cached: Pair<Long, Achievements.Summary>? = null

    fun invalidate() { cached = null }

    suspend fun summary(ctx: Context, force: Boolean = false): Achievements.Summary {
        val now = System.currentTimeMillis()
        cached?.takeIf { !force && now - it.first < 10 * 60_000L }?.let { return it.second }
        return Achievements.summarize(days(ctx)).also { cached = now to it }
    }

    suspend fun days(ctx: Context, today: LocalDate = LocalDate.now()): List<Achievements.Day> = coroutineScope {
        val repo = NutriApp.repository
        val an = repo.analytics().first()
        val settings = repo.prefs.settings.value
        val steps = repo.body.metrics().filter { it.metric == Repository.STEPS }.associate { it.epochDay to it.value }
        val gym = WorkoutApp.store.state.value.history.sortedBy { it.startTime }
        val first = listOfNotNull(an.days.minOfOrNull { it.epochDay }, steps.keys.minOrNull(), gym.firstOrNull()?.let { TrainingAnalytics.day(it.startTime).toEpochDay() })
            .minOrNull()?.let { LocalDate.ofEpochDay(it) } ?: today
        val from = maxOf(first, today.minusDays(MAX_DAYS))
        val zone = ZoneId.systemDefault()
        val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()

        val protein = repo.entries.rangeFlow(from.toEpochDay(), today.toEpochDay()).first().groupBy { it.epochDay }.mapValues { e -> e.value.sumOf { it.protein } }
        val byDay = an.days.associateBy { it.epochDay }
        val db = withTimeoutOrNull(3000) { WorkoutApp.db.filterNotNull().first() }
        val records = HashMap<LocalDate, Int>()
        if (db != null) gym.forEachIndexed { i, w -> TrainingAnalytics.newRecords(w, gym.take(i), db).size.takeIf { it > 0 }?.let { records.merge(TrainingAnalytics.day(w.startTime), it, Int::plus) } }
        val acts = runCatching { Bridge.activities(fromMs) }.getOrDefault(emptyList())
        // une séance de musculation suivie aussi par la montre ne compte qu'une fois
        val gymActs = acts.filter { it.source == Bridge.Source.GYM }
        val workouts = acts.filter { it.minutes >= 15 }.filter { a -> a.source == Bridge.Source.GYM || gymActs.none { it.start < a.end && a.start < it.end } }
            .groupingBy { TrainingAnalytics.day(it.start) }.eachCount()
        val runKm = acts.filter { it.kind == Bridge.Kind.RUN }.groupBy { TrainingAnalytics.day(it.start) }
            .mapValues { e -> e.value.sumOf { (it.watch?.distanceM ?: 0.0) / 1000 } }
        val water = ManualLogStore.get(ctx).query(Metric.HYDRATION, fromMs, Long.MAX_VALUE).groupBy { TrainingAnalytics.day(it.timestampMs) }
            .mapValues { e -> e.value.sumOf { it.value } }
        val waterGoal = HealthRepository.goal(Metric.HYDRATION) ?: 2000.0
        val need = Scores.sleepNeed()
        val sleepFrom = maxOf(from, today.minusDays(SLEEP_DAYS))
        val sleep = if (Bridge.hasSource(Metric.SLEEP)) generateSequence(sleepFrom) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
            .map { d -> async { d to runCatching { Bridge.healthValue(ctx, Metric.SLEEP, d) }.getOrNull() } }.awaitAll().toMap()
        else emptyMap()

        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.map { d ->
            val e = d.toEpochDay()
            val day = byDay[e]
            val target = an.targetFor(e).macros
            Achievements.Day(d, steps[e], settings.stepGoalFor(d), day?.intakeKcal != null, day?.intakeKcal, target.kcal.takeIf { it > 0 },
                protein[e], target.protein.takeIf { it > 0 }, workouts[d] ?: 0, runKm[d] ?: 0.0, sleep[d], need, day?.weightKg != null,
                records[d] ?: 0, water[d], waterGoal)
        }.toList()
    }

    // ---- Trophées nouvellement obtenus --------------------------------------------------

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("superhealth_achievements", Context.MODE_PRIVATE)

    /** Paliers obtenus depuis la dernière visite (le tout premier calcul ne marque rien comme nouveau). */
    fun newlyUnlocked(ctx: Context, s: Achievements.Summary): Set<String> {
        val p = prefs(ctx)
        val seen = p.getStringSet("seen", null)
        val now = s.unlocked
        if (seen == null) { p.edit().putStringSet("seen", now).apply(); return emptySet() }
        return now - seen
    }

    fun markSeen(ctx: Context, keys: Set<String>) {
        val p = prefs(ctx)
        p.edit().putStringSet("seen", (p.getStringSet("seen", emptySet()).orEmpty() + keys)).apply()
    }
}
