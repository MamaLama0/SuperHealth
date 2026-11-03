package app.superhealth.league

import app.superhealth.platform.*

import kotlin.test.*
import kotlin.test.Test

class TrackingQualificationTest {
    private val monday = LocalDate.parse("2026-11-02").toEpochDay()

    @Test fun nutritionHasInclusiveBoundsAndOnlyRecoversAnUnderTargetDayAfterItEnds() {
        assertFalse(Progression.nutritionQualified(1799.9, 2000.0, true))
        assertTrue(Progression.nutritionQualified(1800.0, 2000.0, true))
        assertFalse(Progression.nutritionQualified(1800.0, 2000.0, false))
        assertTrue(Progression.nutritionQualified(2000.0, 2000.0, false))
        assertTrue(Progression.nutritionQualified(2200.0, 2000.0, true))
        assertFalse(Progression.nutritionQualified(2200.1, 2000.0, true))
        assertFalse(Progression.nutritionQualified(0.0, 0.0, true))
        assertFalse(Progression.nutritionQualified(Double.NaN, 2000.0, true))
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
