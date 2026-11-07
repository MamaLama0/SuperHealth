package app.superhealth.nutrition.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionPlanTest {
    private val monday = 20_766L // lundi 9 novembre 2026

    @Test fun generatesCutBreaksPeakAndReverse() {
        val weeks = NutritionPlan.generate(NutritionPlan.Prep(monday, weeks = 6, startKcal = 2400.0, stepKcal = 50.0, maintenanceKcal = 2800.0,
            weightKg = 80.0, leanKg = 68.0, refeedDays = setOf(6), dietBreakEvery = 3, peakWeek = true, reverseWeeks = 2))
        // 6 semaines de sèche + 1 pause (après la 3e) + pic + 2 reverse
        assertEquals(10, weeks.size)
        assertEquals(PrepPhase.DIET_BREAK, weeks[3].phase)
        assertEquals(PrepPhase.PEAK_WEEK, weeks[7].phase)
        assertTrue(weeks.zipWithNext().all { (a, b) -> b.startDay == a.startDay + 7 })
        val first = weeks[0]
        assertEquals(2400.0, first.days[0].target.kcal, 15.0)
        assertTrue(first.days[5].refeed && first.days[5].target.kcal > first.days[0].target.kcal)
        assertEquals(68 * 2.6, first.days[0].target.protein, 0.2)
        // la semaine de pic garde des lipides bas et charge les glucides les trois derniers jours
        assertTrue(weeks[7].days[6].target.carbs >= 80 * 8 - 1)
    }

    @Test fun futureWeekOnlyAppliesFromItsMonday() {
        val p = NutritionPlan.put(Program(), NutritionPlan.weekFrom(monday + 7, List(7) { DayTarget(2000.0, 150.0, 60.0, 200.0) }))
        assertNull(NutritionPlan.dayAt(p, monday + 6))
        assertEquals(2000.0, NutritionPlan.dayAt(p, monday + 7)!!.target.kcal, 0.01)
        assertNull(NutritionPlan.dayAt(p, monday + 14))
    }
}
