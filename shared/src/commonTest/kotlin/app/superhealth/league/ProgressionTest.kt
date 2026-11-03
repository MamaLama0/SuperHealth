package app.superhealth.league

import app.superhealth.platform.*

import app.superhealth.league.Progression.Track
import kotlin.test.*
import kotlin.test.Test

class ProgressionTest {
    private fun day(date: String) = LocalDate.parse(date).toEpochDay()

    @Test fun dailyStreakRemainsAvailableUntilTheOpenDayEnds() {
        val thursday = day("2026-11-05")
        val completed = setOf(thursday - 3, thursday - 2, thursday - 1)
        val beforeToday = Progression.streak(Track.FOOD, completed, thursday)
        assertEquals(3, beforeToday.current)
        assertEquals(0, beforeToday.progress)
        assertEquals(4, Progression.streak(Track.FOOD, completed + thursday, thursday).current)
        val missedThursday = Progression.streak(Track.FOOD, completed, thursday + 1)
        assertEquals(0, missedThursday.current)
        assertEquals(3, missedThursday.best)
    }

    @Test fun dailyBreakPreservesPersonalBestAndStartsANewRun() {
        val today = day("2026-11-10")
        val days = setOf(today - 8, today - 7, today - 6, today - 3, today - 2, today - 1)
        val current = Progression.streak(Track.STEPS, days, today)
        assertEquals(3, current.current)
        assertEquals(3, current.best)
        val broken = Progression.streak(Track.STEPS, days, today + 1)
        assertEquals(0, broken.current)
        assertEquals(3, broken.best)
        assertEquals(1, Progression.streak(Track.STEPS, days + (today + 1), today + 1).current)
    }

    @Test fun weeklyStrengthAllowsRestDaysAndCountsDistinctTrainingDays() {
        val thursday = day("2026-11-05")
        val monday = Progression.week(thursday)
        val completed = listOf(monday - 7, monday - 3, monday, monday, thursday).toSet()
        val streak = Progression.streak(Track.STRENGTH, completed, thursday, target = 2)
        assertTrue(streak.weekly)
        assertEquals(2, streak.target)
        assertEquals(2, streak.current)
        assertEquals(2, streak.best)
        assertEquals(2, streak.progress)
        val singleDay = Progression.streak(Track.STRENGTH, setOf(monday), thursday, target = 2)
        assertEquals(0, singleDay.current)
        assertEquals(1, singleDay.progress)
    }

    @Test fun mondayStartsANewWeekWithoutBreakingTheLastCompletedOne() {
        val monday = day("2026-11-02")
        val completedLastWeek = setOf(monday - 7, monday - 4)
        assertEquals(1, Progression.streak(Track.RUN, completedLastWeek, monday, target = 2).current)
        val incompleteThisWeek = completedLastWeek + monday
        assertEquals(1, Progression.streak(Track.RUN, incompleteThisWeek, monday + 6, target = 2).current)
        val nextMonday = Progression.streak(Track.RUN, incompleteThisWeek, monday + 7, target = 2)
        assertEquals(0, nextMonday.current)
        assertEquals(1, nextMonday.best)
        assertEquals(monday, Progression.week(monday + 6))
        assertEquals(monday + 7, Progression.week(monday + 7))
    }

    @Test fun localCalendarDaysRemainConsecutiveAcrossDaylightSavingTime() {
        val paris = ZoneId.of("Europe/Paris")
        val before = LocalDate.parse("2026-10-25")
        val after = before.plusDays(1)
        assertEquals(25L, ChronoUnit.HOURS.between(before.atStartOfDay(paris).toInstant(), after.atStartOfDay(paris).toInstant()))
        val dates = setOf(before.minusDays(1).toEpochDay(), before.toEpochDay(), after.toEpochDay())
        assertEquals(3, Progression.streak(Track.SLEEP, dates, after.toEpochDay()).current)
        assertEquals(LocalDate.parse("2026-10-19").toEpochDay(), Progression.week(before.toEpochDay()))
        assertEquals(after.toEpochDay(), Progression.week(after.toEpochDay()))
    }

    @Test fun xpIsDeduplicatedByCategoryAndCappedAcrossCategoriesAndRecords() {
        val today = day("2026-11-04")
        val events = mapOf(today - 1 to listOf(Track.FOOD, Track.FOOD).toSet(), today to Track.entries.toSet())
        val result = Progression.calculate(events, today, Track.entries.toSet(), records = mapOf(today to 100))
        assertEquals(140L, result.summary.totalXp)
        assertEquals(140, result.summary.weekXp)
        assertEquals(20, result.summary.days.first { it.day == today - 1 }.xp)
        assertEquals(120, result.summary.days.first { it.day == today }.xp)
        val few = Progression.calculate(mapOf(today to setOf(Track.FOOD)), today, setOf(Track.FOOD), records = mapOf(today to 100))
        assertEquals(40L, few.summary.totalXp)
    }

    @Test fun futureDatesNeverAwardXpRecordsOrStreakProgress() {
        val today = day("2026-11-04")
        val result = Progression.calculate(mapOf(today to setOf(Track.FOOD), today + 1 to Track.entries.toSet()), today,
            Track.entries.toSet(), records = mapOf(today to 1, today + 1 to 100))
        assertEquals(30L, result.summary.totalXp)
        assertEquals(1, result.summary.records)
        assertEquals(0, result.summary.days.first { it.day == today + 1 }.xp)
        assertEquals(0, result.summary.streaks.first { it.track == Track.STRENGTH.name }.progress)
    }

    @Test fun totalXpAndWeeklyXpUseDifferentCalendarWindows() {
        val monday = day("2026-11-02")
        val result = Progression.calculate(mapOf(monday - 1 to setOf(Track.FOOD), monday to setOf(Track.RUN)), monday, Track.entries.toSet())
        assertEquals(50L, result.summary.totalXp)
        assertEquals(30, result.summary.weekXp)
        assertEquals(monday, result.summary.weekStart)
        assertEquals(7, result.summary.days.size)
    }

    @Test fun changingWhichStreaksAreDisplayedDoesNotCreateOrRemoveEarnedXp() {
        val today = day("2026-11-04")
        val events = mapOf(today to setOf(Track.FOOD, Track.SLEEP))
        val all = Progression.calculate(events, today, setOf(Track.FOOD, Track.SLEEP)).summary
        val onlyFood = Progression.calculate(events, today, setOf(Track.FOOD)).summary
        assertEquals(all.totalXp, onlyFood.totalXp)
        assertEquals(30L, onlyFood.totalXp)
        assertEquals(listOf(Track.FOOD.name), onlyFood.streaks.map { it.track })
    }

    @Test fun levelBoundariesAndRemaindersAreConsistent() {
        assertEquals(1, Progression.level(-100))
        assertEquals(1, Progression.level(124))
        assertEquals(2, Progression.level(125))
        assertEquals(2, Progression.level(299))
        assertEquals(3, Progression.level(300))
        assertEquals(0L, Progression.threshold(1))
        assertEquals(125L, Progression.threshold(2))
        assertEquals(300L, Progression.threshold(3))
        val today = day("2026-11-04")
        val result = Progression.calculate((today - 6..today).associateWith { setOf(Track.FOOD) }, today, setOf(Track.FOOD))
        assertEquals(2, result.summary.level)
        assertEquals(15L, result.intoLevel)
        assertEquals(175L, result.nextLevel)
    }

    @Test fun explicitHistoryBoundaryExcludesOlderEvents() {
        val today = day("2026-11-04")
        val result = Progression.calculate((today - 10..today).associateWith { setOf(Track.FOOD) }, today, setOf(Track.FOOD), since = today - 2)
        assertEquals(60L, result.summary.totalXp)
        assertEquals(3, result.summary.streaks.single().current)
        assertEquals(3, result.summary.streaks.single().best)
        assertTrue(result.summary.streaks.single().capped)
    }
}
