package app.superhealth.nutrition.data

import app.superhealth.nutrition.domain.Goal
import app.superhealth.nutrition.domain.GoalType
import app.superhealth.nutrition.domain.Profile
import app.superhealth.nutrition.domain.Program
import app.superhealth.nutrition.domain.Settings
import app.superhealth.nutrition.domain.Targets
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekTargetsTest {
    /** Inscription un dimanche : les jours précédents de la semaine ne doivent pas avoir une cible différente. */
    @Test fun daysBeforeFirstCheckInUseIt() {
        val sunday = LocalDate.of(2026, 10, 18).toEpochDay()
        val goal = Goal(startDay = sunday, type = GoalType.CUT, startWeightKg = 82.0, targetWeightKg = 76.0, ratePctPerWeek = 0.5)
        val program = Program(startEpochDay = sunday)
        val weigh = listOf(WeighInEntity(sunday, 82.0))
        val ci = Targets.checkIn(sunday, 2600.0, 82.0, 61.5, program, goal, 180.0)
        val an = Repository.computeAnalytics(emptyList(), weigh, emptyList(), Profile(heightCm = 180.0), program, Settings(onboarded = true),
            listOf(goal), listOf(ci), sunday)
        for (d in sunday - 6..sunday) assertEquals(ci.kcal, an.targetFor(d).macros.kcal, 1e-9)
        for (d in sunday - 6..sunday) assertEquals(ci.kcal, an.weekTarget(d).macros.kcal, 1e-9)
    }
}
