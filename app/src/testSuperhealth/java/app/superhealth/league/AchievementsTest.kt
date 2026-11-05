package app.superhealth.league

import org.junit.Assert.*
import org.junit.Test

class AchievementsTest {
    @Test fun thresholdsUseLifetimeTotalsAndWeeklyStreaks() {
        val p = Progression.Summary(records = 25, totals = mapOf("sessions" to 50, "foodDays" to 100, "mindMinutes" to 300, "distanceKm" to 260),
            weekly = listOf(Progression.WeekStreak("STEPS", 3, 10, true, 5, 4)))
        val earned = Achievements.unlocked(p)
        assertTrue(earned.containsAll(listOf("strength_50", "records_25", "food_100", "mind_300", "distance_250", "streak_STEPS_10")))
        assertFalse("streak_STEPS_20" in earned)
        assertFalse("strength_100" in earned)
        assertTrue("plus de badges de randonnée", Achievements.catalog.none { it.metric.startsWith("hike") || it.metric == "streak:HIKE" })
    }
    @Test fun rewardsGrowWithTiersAndStreakTiersPayMore() {
        assertEquals(25, Achievements.reward(Achievements.find("strength_10")!!))
        assertEquals(600, Achievements.reward(Achievements.find("distance_25000")!!))
        assertEquals(200, Achievements.reward(Achievements.find("streak_FOOD_10")!!))
        assertEquals(250, Achievements.reward(Achievements.find("streak_MIND_30")!!))
    }
    @Test fun weeklyStreaksAreLenient() {
        // Lundi 2 novembre 2026 = jour epoch 20759. Quatre jours sur sept suffisent ; trois cassent la série.
        val monday = java.time.LocalDate.of(2026, 11, 2).toEpochDay()
        val events = mutableMapOf<Long, Set<Progression.Track>>()
        fun days(week: Int, n: Int) = (0 until n).forEach { events[monday - 7L * week + it] = setOf(Progression.Track.FOOD) }
        days(3, 3); days(2, 4); days(1, 6); days(0, 4)
        val s = Progression.weekStreaks(events, monday + 4, listOf(Progression.Track.FOOD)) { _, _ -> 4 }.single()
        assertTrue(s.done); assertEquals(3, s.current); assertEquals(3, s.best)
        val early = Progression.weekStreaks(events, monday + 2, listOf(Progression.Track.FOOD)) { _, _ -> 4 }.single()
        assertFalse("semaine pas encore tenue : la série continue sans elle", early.done); assertEquals(2, early.current)
    }
    @Test fun oldProfilesStayValidWithoutBadgeHighlighting() {
        assertTrue(Achievements.validDecor("xp_1000", listOf("strength_50", "food_30")))
        assertFalse(Achievements.validDecor(null, listOf("food_30", "food_30")))
        assertFalse(Achievements.validDecor(null, listOf("food_7", "food_30", "food_100", "food_365")))
    }
}
