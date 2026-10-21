package app.superhealth.nutrition.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BalanceHabitsTest {
    private fun day(d: Long, kcal: Double?, fasted: Boolean = false) = DayData(d, kcal, null, null, fasted)

    @Test fun balanceIsIntakeMinusExpenditure() {
        val t = 100L
        val days = listOf(day(t, 1800.0), day(t - 1, null, fasted = true), day(t - 2, 2600.0))
        val b = Balance.days(days, { 2200.0 }, t, 4)
        assertEquals(listOf(97L, 98L, 99L, 100L), b.map { it.first })
        assertNull(b[0].second)               // pas journalisé
        assertEquals(400.0, b[1].second!!, 1e-9) // surplus
        assertEquals(-2200.0, b[2].second!!, 1e-9) // jeûne = 0 kcal
        assertEquals(-400.0, b[3].second!!, 1e-9)
        assertEquals((400.0 - 2200 - 400) / 3, Balance.average(b)!!, 1e-9)
        assertNull(Balance.average(listOf(1L to null)))
    }

    @Test fun weekStartIsMonday() {
        for (k in 0L..13L) {
            val d = LocalDate.of(2026, 10, 12).plusDays(k)
            val ws = LocalDate.ofEpochDay(Habits.weekStart(d.toEpochDay()))
            assertEquals(java.time.DayOfWeek.MONDAY, ws.dayOfWeek)
            assertEquals(true, !ws.isAfter(d) && ws.plusDays(7).isAfter(d))
        }
    }

    @Test fun weekStreakCountsActiveWeeks() {
        val wed = LocalDate.of(2026, 10, 21).toEpochDay()
        // cette semaine vide : ne casse pas la série des 2 semaines précédentes
        assertEquals(2, Habits.weekStreak(setOf(wed - 7, wed - 14), wed))
        assertEquals(3, Habits.weekStreak(setOf(wed, wed - 7, wed - 13), wed))
        // trou d'une semaine
        assertEquals(1, Habits.weekStreak(setOf(wed, wed - 14), wed))
        assertEquals(0, Habits.weekStreak(emptySet(), wed))
        // un jour futur ne compte pas
        assertEquals(0, Habits.weekStreak(setOf(wed + 7), wed))
    }

    @Test fun dayStreakToleratesEmptyToday() {
        assertEquals(2, Habits.streak(setOf(9L, 8L), 10L).current)
        assertEquals(3, Habits.streak(setOf(10L, 9L, 8L, 5L), 10L).current)
        assertEquals(3, Habits.streak(setOf(10L, 9L, 8L, 5L), 10L).longest)
    }
}
