package app.superhealth.ui

import app.superhealth.league.League
import app.superhealth.league.Progression
import app.superhealth.ui.dashboard.dashboardPriorityStreak
import app.superhealth.ui.dashboard.dashboardScoreEntries
import app.superhealth.ui.dashboard.dashboardRank
import app.superhealth.ui.dashboard.dashboardQuest
import app.superhealth.league.WeeklyQuests
import kotlin.test.*
import kotlin.test.Test

class DashboardSocialTest {
    @Test fun questCardPrioritizesUnclaimedRewardsThenUnfinishedQuests() {
        val done = WeeklyQuests.Quest(WeeklyQuests.Metric.FOOD, 3, progress = 3, claimed = true)
        val side = WeeklyQuests.Quest(WeeklyQuests.Metric.STRENGTH, 2, progress = 1, secondary = true)
        assertEquals(side, dashboardQuest(listOf(done, side)))
        val reward = side.copy(progress = 2)
        assertEquals(reward, dashboardQuest(listOf(done.copy(claimed = false, progress = 1), reward)))
        assertEquals(done, dashboardQuest(listOf(done)))
        assertNull(dashboardQuest(emptyList()))
    }
    private val now = 1_000_000L
    private val mine = League.Score("2026-W45", 850, mapOf(League.Pillar.STRENGTH to 95,
        League.Pillar.FOOD to 100, League.Pillar.MIND to 0), ready = true, endDay = 20758)
    private fun friend(id: String, score: Int) = League.Friend(id, "key-$id", id, mine.week, now, score,
        ready = true, endDay = mine.endDay, model = app.superhealth.league.HealthIndex.MODEL, coverage = mine.coverage)

    @Test fun globalDashboardRankingUsesMatchingCoverageDateAndModelAndKeepsTies() {
        val entries = dashboardScoreEntries(mine, listOf(
            friend("leader", 875).copy(alias = "My alias"), friend("tie", 850),
            friend("other-day", 950).copy(endDay = mine.endDay - 1),
            friend("stale", 950).copy(time = now - 48 * 3600 - 1),
            friend("old-model", 950).copy(model = 2),
            friend("other-coverage", 950).copy(coverage = 7)
        ), now, "Me")
        assertEquals(listOf("My alias", "Me", "tie"), entries.map { it.name })
        assertEquals(listOf(1, 2, 2), entries.map { dashboardRank(entries, it) })
        assertTrue(dashboardScoreEntries(mine.copy(ready = false), emptyList(), now, "Me").isEmpty())
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
