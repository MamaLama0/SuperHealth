package app.superhealth.nutrition.domain

import app.superhealth.nutrition.data.Repository
import app.superhealth.nutrition.data.WeighInEntity
import org.junit.Assert.*
import org.junit.Test

class ManualTargetsTest {
    @Test fun caloriesAndMacrosRoundTrip() {
        val t = ManualTargets.fromCalories(2400.0, 30.0, 25.0, 45.0)
        assertEquals(180.0, t.protein, 1e-9)
        assertEquals(2400.0, ManualTargets.fromMacros(t.protein, t.fat, t.carbs).kcal, 1e-9)
        assertEquals(2150.0, ManualTargets.fromMacros(150.0, 70.0, 230.0).kcal, 1e-9)
    }

    @Test fun invalidSharesAndNonFiniteInputsAreRejected() {
        for (f in listOf<() -> Unit>(
            { ManualTargets.fromCalories(2000.0, 30.0, 30.0, 30.0) },
            { ManualTargets.fromCalories(Double.NaN, 30.0, 30.0, 40.0) },
            { ManualTargets.fromMacros(-1.0, 70.0, 200.0) },
            { ManualTargets.fromMacros(150.0, Double.POSITIVE_INFINITY, 200.0) },
        )) assertTrue(runCatching(f).isFailure)
    }

    private val today = 20759L
    private val goal = Goal(startDay = today - 30, type = GoalType.CUT, startWeightKg = 80.0, targetWeightKg = 70.0)
    private fun analytics(p: Program, c: List<CheckIn> = emptyList(), goals: List<Goal> = listOf(goal)) =
        Repository.computeAnalytics(emptyList(), listOf(WeighInEntity(today, 80.0)), emptyList(), Profile(), p, Settings(), goals, c, today)

    @Test fun disabledWeightGoalDoesNotAffectFoodTrackingOrApplyOldDeficit() {
        val p = Program(weightGoalEnabled = false)
        val an = analytics(p)
        assertNull(an.currentGoal)
        val ci = Targets.checkIn(today, 2500.0, 80.0, 60.0, p, goal)
        assertEquals(2500.0, ci.kcal, 1e-9)
    }

    @Test fun closedGoalNeverReappearsAfterItsEnd() {
        assertNull(analytics(Program(), goals = listOf(goal.copy(closedDay = today - 1, status = GoalStatus.COMPLETED))).currentGoal)
    }

    @Test fun temporaryOverrideWinsThenOriginalProgramResumes() {
        val replacement = DayTarget(2400.0, 180.0, 80.0, 240.0)
        val p = Program(overrides = listOf(TargetOverride(today, today + 6, replacement)))
        val ci = Targets.checkIn(today - 7, 2500.0, 80.0, 60.0, p, goal)
        val an = analytics(p, listOf(ci))
        assertEquals(ci.kcal, an.targetFor(today - 1).macros.kcal, 1e-9)
        assertEquals(2400.0, an.targetFor(today).macros.kcal, 1e-9)
        assertEquals(2400.0, an.weekTarget(today + 6).macros.kcal, 1e-9)
        assertEquals(ci.kcal, an.targetFor(today + 7).macros.kcal, 1e-9)
    }

    @Test fun changingToManualPreservesHistoricalCheckInTargets() {
        val old = CheckIn(today - 7, 2500.0, 80.0, 2100.0, 150.0, 70.0, 217.5, GoalType.CUT, 0.5)
        val p = Program(style = ProgramStyle.MANUAL, manualKcal = 2600.0)
        val next = Targets.checkIn(today, 2500.0, 80.0, 60.0, p, goal)
        val an = analytics(p, listOf(old, next))
        assertEquals(2100.0, an.targetFor(today - 1).macros.kcal, 1e-9)
        assertEquals(2600.0, an.targetFor(today).macros.kcal, 1e-9)
    }

    @Test fun manualWeekRespectsEachDayAndCollaborativeKeepsRatios() {
        val days = (1..7).map { ManualTargets.fromCalories(1800.0 + it * 100, 30.0, 30.0, 40.0) }
        val manual = Program(style = ProgramStyle.MANUAL, manualDays = days)
        val ci = Targets.checkIn(today, 2500.0, 80.0, 60.0, manual, goal)
        (1..7).forEach { assertEquals(days[it - 1], ci.forDayOfWeek(it)) }
        val p = manual.copy(style = ProgramStyle.COLLABORATIVE, manualProtein = 150.0, manualFat = 70.0, manualCarbs = 230.0)
        val a = Targets.checkIn(today, 2500.0, 80.0, 60.0, p, null)
        val b = Targets.checkIn(today + 7, 2700.0, 80.0, 60.0, p, null)
        assertTrue(b.kcal > a.kcal)
        assertEquals(a.protein / a.fat, b.protein / b.fat, 1e-9)
        assertEquals(b.kcal, 4 * b.protein + 9 * b.fat + 4 * b.carbs, 1e-9)
    }
    @Test fun replacingAnOngoingOverridePreservesThePastAndDoesNotReactivateItLater() {
        val old = DayTarget(2800.0, 200.0, 80.0, 320.0)
        val next = DayTarget(2400.0, 180.0, 80.0, 240.0)
        val before = Program(overrides = listOf(TargetOverride(today - 10, null, old)))
        val after = ManualTargets.replaceOverride(before, TargetOverride(today, today + 6, next))
        assertEquals(old, ManualTargets.overrideAt(after, today - 1))
        assertEquals(next, ManualTargets.overrideAt(after, today))
        assertNull(ManualTargets.overrideAt(after, today + 7))
        val sameDay = ManualTargets.replaceOverride(after, TargetOverride(today, today + 2, old))
        assertEquals(2, sameDay.overrides.size)
        assertEquals(old, ManualTargets.overrideAt(sameDay, today + 2))
        assertNull(ManualTargets.overrideAt(sameDay, today + 3))
    }

}
