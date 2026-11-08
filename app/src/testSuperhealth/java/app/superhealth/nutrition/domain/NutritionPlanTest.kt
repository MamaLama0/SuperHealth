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

    @Test fun blockProgressesWeekToWeekWithWaterAndSodium() {
        val weeks = NutritionPlan.block(NutritionPlan.Block(monday, weeks = 4, phase = PrepPhase.BULK, startKcal = 2800.0, stepKcal = 100.0,
            weightKg = 80.0, proteinPerKg = 2.0, fatPct = 25.0, waterMl = 3500.0, sodiumMg = 3000.0))
        assertEquals(4, weeks.size)
        assertEquals(listOf(2800.0, 2900.0, 3000.0, 3100.0), weeks.map { Math.round(it.days[0].target.kcal / 10.0) * 10.0 })
        assertTrue(weeks.all { it.phase == PrepPhase.BULK && it.waterMl == 3500.0 && it.sodiumMg == 3000.0 })
        assertEquals(160.0, weeks[0].days[0].target.protein, 0.2)
    }

    @Test fun cutBlockKeepsRefeedsAndFloor() {
        val weeks = NutritionPlan.block(NutritionPlan.Block(monday, weeks = 10, phase = PrepPhase.CUT, startKcal = 1800.0, stepKcal = -100.0,
            weightKg = 70.0, refeedDays = setOf(6), refeedKcal = 2400.0, floorKcal = 1500.0))
        assertTrue(weeks.last().days[0].target.kcal >= 1500.0 - 15)
        assertTrue(weeks[0].days[5].refeed && !weeks[0].days[0].refeed)
    }

    @Test fun dayValuesFallBackToWeek() {
        val w = NutritionPlan.weekFrom(monday, List(7) { DayTarget(2000.0, 150.0, 60.0, 200.0) }).let { w ->
            w.copy(waterMl = 3000.0, sodiumMg = 2500.0, addTraining = false,
                days = w.days.mapIndexed { i, d -> if (i == 2) d.copy(waterMl = 4000.0) else d })
        }
        val p = NutritionPlan.put(Program(), w)
        assertEquals(3000.0, NutritionPlan.waterAt(p, monday)!!, 0.01)
        assertEquals(4000.0, NutritionPlan.waterAt(p, monday + 2)!!, 0.01)
        assertEquals(2500.0, NutritionPlan.sodiumAt(p, monday + 6)!!, 0.01)
        assertTrue(!NutritionPlan.addsTraining(p, monday) && NutritionPlan.addsTraining(p, monday + 7))
        assertNull(NutritionPlan.waterAt(p, monday + 7))
    }

    @Test fun extendCopiesWithProgression() {
        // 150 g P + 60 g L + 215 g G = 2 000 kcal
        val w = NutritionPlan.weekFrom(monday, List(7) { DayTarget(2000.0, 150.0, 60.0, 215.0) })
        val next = NutritionPlan.extend(w, w.startDay, 3, -100.0)
        assertEquals(listOf(monday + 7, monday + 14, monday + 21), next.map { it.startDay })
        assertEquals(1700.0, next.last().days[0].target.kcal, 15.0)
        assertEquals(2, NutritionPlan.weekNumber(NutritionPlan.put(NutritionPlan.put(Program(), w), next[0]), next[0]))
    }
}
