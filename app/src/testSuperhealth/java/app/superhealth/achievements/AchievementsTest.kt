package app.superhealth.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AchievementsTest {
    private val d0 = LocalDate.of(2026, 10, 1)

    @Test fun levelsAndJourney() {
        assertEquals(Triple(1, 0, 100), Achievements.level(0))
        assertEquals(Triple(2, 150, 200), Achievements.level(250))
        val j = Achievements.journey(1600)
        assertEquals(Achievements.Leg.MOON, j.leg)
        assertEquals(100f / 2500, j.fraction, 1e-6f)
        assertEquals(listOf(Achievements.Leg.EARTH), j.done)
    }

    @Test fun dailyXpIsCapped() {
        val full = Achievements.Day(d0, steps = 20000.0, stepGoal = 8000, logged = true, kcal = 2000.0, kcalTarget = 2000.0, protein = 150.0,
            proteinTarget = 150.0, workouts = 1, sleepMin = 500.0, weighIn = true, records = 5, waterMl = 3000.0)
        assertEquals(100, Achievements.xp(full))
        assertEquals(10, Achievements.xp(Achievements.Day(d0, logged = true)))
    }

    @Test fun streaksBreakOnGaps() {
        val days = listOf(0, 1, 2, 4, 5).map { Achievements.Day(d0.plusDays(it.toLong()), logged = true) }
        assertEquals(3, Achievements.bestStreak(days) { it.logged })
        val s = Achievements.summarize(days, d0.plusDays(5))
        assertEquals(3, s.trophies.first { it.trophy == Achievements.Trophy.LOG_STREAK }.value)
        assertTrue(s.unlocked.isEmpty())
    }
}
