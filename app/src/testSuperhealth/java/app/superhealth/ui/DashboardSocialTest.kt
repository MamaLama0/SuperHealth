package app.superhealth.ui

import app.superhealth.league.League
import app.superhealth.league.Progression
import app.superhealth.ui.dashboard.dashboardPriorityStreak
import app.superhealth.ui.dashboard.dashboardXpEntries
import app.superhealth.ui.dashboard.dashboardXpRank
import org.junit.Assert.*
import org.junit.Test

class DashboardSocialTest {
    private val now = 1_000_000L
    private val mine = Progression.Summary(weekStart = 100L, weekXp = 100)
    private fun friend(id: String, xp: Int, week: Long = mine.weekStart, time: Long = now, alias: String? = null, model: Int = Progression.MODEL) =
        League.Friend(id, "key-$id", id, "2026-W45", time, 0, alias = alias,
            progress = Progression.Summary(model = model, weekStart = week, weekXp = xp))

    @Test fun weeklyDashboardRankingUsesRecentCardsFromTheSameWeekAndKeepsTies() {
        val entries = dashboardXpEntries(mine, listOf(
            friend("leader", 200, alias = "My alias"), friend("tie", 100),
            friend("previous-week", 900, week = mine.weekStart - 7),
            friend("stale", 1000, time = now - 48 * 3600 - 1),
            friend("different-model", 500, model = Progression.MODEL + 1),
        ), now, "Me")
        assertEquals(listOf("My alias", "Me", "tie"), entries.map { it.name })
        assertEquals(listOf(1, 2, 2), entries.map { dashboardXpRank(entries, it) })
        assertTrue(dashboardXpEntries(null, emptyList(), now, "Me").isEmpty())
    }

    @Test fun streakHeroComparesWeeksAsSevenDaysAndPrefersDailyWhenEqual() {
        val daily = Progression.Streak("FOOD", 20, 20, false, 1)
        val weekly = Progression.Streak("STRENGTH", 3, 3, true, 2)
        assertEquals(weekly, dashboardPriorityStreak(listOf(daily, weekly)))
        val twoWeeks = weekly.copy(current = 2)
        val fourteenDays = daily.copy(current = 14)
        assertEquals(fourteenDays, dashboardPriorityStreak(listOf(twoWeeks, fourteenDays)))
        assertEquals(daily, dashboardPriorityStreak(listOf(daily, daily.copy(track = "UNKNOWN", current = 100))))
    }
}
