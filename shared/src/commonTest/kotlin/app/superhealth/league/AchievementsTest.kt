package app.superhealth.league

import app.superhealth.platform.*

import kotlin.test.*
import kotlin.test.Test

class AchievementsTest {
    @Test fun thresholdsUseLifetimeTotalsAndBestStreaks() {
        val p = Progression.Summary(totalXp = 1000, records = 10, totals = mapOf("sessions" to 50, "foodDays" to 30, "mindMinutes" to 300),
            streaks = listOf(Progression.Streak("STEPS", 0, 30, false, 1)))
        val earned = Achievements.unlocked(p)
        assertTrue(earned.containsAll(listOf("xp_1000", "strength_50", "records_10", "food_30", "mind_300", "streak_STEPS_30")))
        assertFalse("streak_STEPS_60" in earned)
        assertFalse("strength_100" in earned)
    }
    @Test fun publicDecorIsBoundedAndUsesKnownIdentifiers() {
        assertTrue(Achievements.validDecor("xp_1000", listOf("strength_50", "food_30")))
        assertFalse(Achievements.validDecor("arbitrary title", emptyList()))
        assertFalse(Achievements.validDecor("strength_50", emptyList()))
        assertFalse(Achievements.validDecor(null, listOf("food_30", "food_30")))
        assertFalse(Achievements.validDecor(null, listOf("food_7", "food_30", "food_100", "food_365")))
    }
}
