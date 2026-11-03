package app.superhealth.league

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrackingQualificationTest {
    private val monday = LocalDate.parse("2026-11-02").toEpochDay()

    @Test fun calorieRangesFollowTheGoalAndKeepInclusiveBounds() {
        val loss=app.superhealth.nutrition.domain.GoalType.CUT
        val gain=app.superhealth.nutrition.domain.GoalType.BULK
        val maintenance=app.superhealth.nutrition.domain.GoalType.MAINTENANCE
        assertTrue(Progression.nutritionQualified(1500.0,2000.0,loss))
        assertTrue(Progression.nutritionQualified(2200.0,2000.0,loss))
        assertFalse(Progression.nutritionQualified(1499.9,2000.0,loss))
        assertFalse(Progression.nutritionQualified(2200.1,2000.0,loss))
        assertTrue(Progression.nutritionQualified(1700.0,2000.0,gain))
        assertTrue(Progression.nutritionQualified(2500.0,2000.0,gain))
        assertFalse(Progression.nutritionQualified(1699.9,2000.0,gain))
        assertFalse(Progression.nutritionQualified(2500.1,2000.0,gain))
        assertTrue(Progression.nutritionQualified(1600.0,2000.0,maintenance))
        assertTrue(Progression.nutritionQualified(2300.0,2000.0,maintenance))
        assertFalse(Progression.nutritionQualified(1599.9,2000.0,maintenance))
        assertFalse(Progression.nutritionQualified(2300.1,2000.0,maintenance))
    }

    @Test fun manualTargetsUseNeutralBoundsAndInvalidValuesNeverQualify() {
        val loss=app.superhealth.nutrition.domain.GoalType.CUT
        assertFalse(Progression.nutritionQualified(1500.0,2000.0,loss,manual=true))
        assertTrue(Progression.nutritionQualified(1600.0,2000.0,loss,manual=true))
        assertTrue(Progression.nutritionQualified(2300.0,2000.0,loss,manual=true))
        assertFalse(Progression.nutritionQualified(2300.1,2000.0,loss,manual=true))
        assertFalse(Progression.nutritionQualified(0.0,0.0))
        assertFalse(Progression.nutritionQualified(Double.NaN,2000.0))
        assertFalse(Progression.nutritionQualified(2000.0,Double.POSITIVE_INFINITY))
    }

    @Test fun threeOfFourSessionsNeverCompletesTheOpenWeekButKeepsTheStreakNextMonday() {
        val sessions = setOf(monday, monday + 2, monday + 4)
        val open = Progression.streak(Progression.Track.STRENGTH, sessions, monday + 6, 4)
        assertEquals(3, open.progress)
        assertEquals(4, open.target)
        assertEquals(0, open.current)
        val closed = Progression.streak(Progression.Track.STRENGTH, sessions, monday + 7, 4)
        assertEquals(1, closed.current)
        assertEquals(0, closed.progress)
        assertEquals(4, closed.target)
    }

    @Test fun missingTwoSessionsStillBreaksTheStreakAndZeroSessionsNeverGetsAFreeWeek() {
        assertEquals(0, Progression.streak(Progression.Track.STRENGTH, setOf(monday, monday + 2), monday + 7, 4).current)
        assertEquals(0, Progression.streak(Progression.Track.STRENGTH, emptySet(), monday + 7, 1).current)
        assertEquals(1, Progression.streak(Progression.Track.STRENGTH, setOf(monday), monday + 7, 1).current)
    }

    @Test fun aStoredWeeklyPlanIsNotReplacedByTheNewProgramsSmallerGoal() {
        val s = Progression.streak(Progression.Track.STRENGTH, setOf(monday, monday + 2), monday + 7, 2,
            weeklyGoals = mapOf(monday to 4))
        assertEquals(0, s.current)
        assertEquals(2, s.target)
    }

    @Test fun strengthCountsCompletedSessionsInsteadOfOnlyDistinctDays() {
        val s = Progression.streak(Progression.Track.STRENGTH, setOf(monday), monday + 2, 2, dailyCounts = mapOf(monday to 2))
        assertEquals(2, s.progress)
        assertEquals(1, s.current)
        val run = Progression.streak(Progression.Track.RUN, setOf(monday), monday + 7, 2)
        assertEquals(0, run.current)
    }
}
