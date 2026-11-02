package app.superhealth.league

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/** Progression de l'app : la régularité des suivis, distincte de l'indice de santé. */
object Progression {
    const val MODEL = 2
    enum class Track(val label: String, val xp: Int, val weekly: Boolean = false) {
        FOOD("Nutrition", 20), STRENGTH("Musculation", 40, true), RUN("Course", 30, true),
        STEPS("Pas", 10), SLEEP("Sommeil", 10), MIND("Esprit et mobilité", 20)
    }
    @Serializable data class Streak(val track: String, val current: Int, val best: Int, val weekly: Boolean, val target: Int,
        val progress: Int = 0, val capped: Boolean = false, val percent: Int? = null)
    @Serializable data class DayXp(val day: Long, val xp: Int)
    @Serializable data class Summary(val model: Int = MODEL, val totalXp: Long = 0, val weekXp: Int = 0, val level: Int = 1,
        val weekStart: Long = 0, val throughDay: Long = 0, val days: List<DayXp> = emptyList(), val streaks: List<Streak> = emptyList(), val records: Int = 0)
    data class Result(val summary: Summary, val since: Long) {
        val intoLevel get() = summary.totalXp - threshold(summary.level)
        val nextLevel get() = threshold(summary.level + 1) - threshold(summary.level)
    }
    fun threshold(level: Int): Long { val n = (level - 1).coerceAtLeast(0).toLong(); return n * (100 + 25 * n) }
    fun level(xp: Long): Int { var l = 1; while (l < 10000 && threshold(l + 1) <= xp.coerceAtLeast(0)) l++; return l }
    fun week(day: Long): Long = LocalDate.ofEpochDay(day).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
    /** Tolérance de rattrapage seulement pour une journée terminée ; l'objectif affiché reste 100 %. */
    fun nutritionQualified(kcal: Double, goal: Double, closedDay: Boolean): Boolean = kcal.isFinite() && goal.isFinite() && goal > 0 &&
        kcal >= goal * (if (closedDay) 0.9 else 1.0) - 1e-7 && kcal <= goal * 1.1 + 1e-7

    fun streak(track: Track, days: Set<Long>, today: Long, target: Int = if (track.weekly) 2 else 1, since: Long = days.minOrNull() ?: today,
        dailyCounts: Map<Long, Int>? = null, weeklyGoals: Map<Long, Int> = emptyMap()): Streak {
        val required = if (track.weekly) target.coerceIn(1, 7) else 1
        val counts = (dailyCounts ?: days.associateWith { 1 }).filterKeys { it in since..today }.entries
            .groupBy { if (track.weekly) week(it.key) else it.key }.mapValues { (_, rows) -> rows.sumOf { it.value.coerceAtLeast(0) } }
        val step = if (track.weekly) 7L else 1L
        val now = if (track.weekly) week(today) else today
        val first = if (track.weekly) week(since) else since
        fun goal(period: Long) = if (track == Track.STRENGTH) (weeklyGoals[period] ?: required).coerceIn(1, 7) else required
        // La séance de marge n'allume jamais une coche dans la semaine ouverte.
        val complete = counts.filter { (period, count) -> count >= if (track == Track.STRENGTH && period < now) (goal(period) - 1).coerceAtLeast(1) else goal(period) }.keys
        var cursor = if (now in complete) now else now - step
        var current = 0
        while (cursor >= first && cursor in complete) { current++; cursor -= step }
        var best = 0; var run = 0; var previous: Long? = null
        complete.sorted().forEach { p -> run = if (previous != null && p == previous!! + step) run + 1 else 1; best = maxOf(best, run); previous = p }
        // La période ouverte n'est jamais déclarée perdue avant sa fin.
        return Streak(track.name, current, best, track.weekly, goal(now), counts[now] ?: 0, current > 0 && cursor < first)
    }
    fun calculate(events: Map<Long, Set<Track>>, today: Long, tracked: Set<Track>, goals: Map<String, Int> = emptyMap(), records: Map<Long, Int> = emptyMap(), since: Long = events.keys.minOrNull() ?: today,
        sessions: Map<Long, Int>? = null, strengthGoals: Map<Long, Int> = emptyMap()): Result {
        val valid = events.filterKeys { it in since..today }
        // Une catégorie n'accorde ses points qu'une fois/jour ; les PR sont plafonnés à 20 XP/jour.
        val xp = valid.mapValues { (d, tracks) -> (tracks.sumOf { it.xp } + (records[d] ?: 0).coerceIn(0, 2) * 10).coerceAtMost(120) }
        val start = week(today)
        val total = xp.values.sumOf { it.toLong() }
        val summary = Summary(totalXp = total, weekXp = xp.filterKeys { it >= start }.values.sum(), level = level(total), weekStart = start, throughDay = today,
            days = (start..start + 6).map { DayXp(it, xp[it] ?: 0) },
            streaks = Track.entries.filter { it in tracked }.map { t -> streak(t, valid.filterValues { t in it }.keys, today, goals[t.name] ?: if (t.weekly) 2 else 1, since,
                dailyCounts = sessions.takeIf { t == Track.STRENGTH }, weeklyGoals = strengthGoals.takeIf { t == Track.STRENGTH }.orEmpty()) },
            records = records.filterKeys { it <= today }.values.sum())
        return Result(summary, since)
    }
}
