package app.superhealth.meals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortionsTest {
    private val chicken = Portions.Food(165.0, 31.0, 3.6, 0.0)
    private val rice = Portions.Food(130.0, 2.7, 0.3, 28.0)
    private val oil = Portions.Food(884.0, 0.0, 100.0, 0.0)

    @Test fun oneFoodHitsCalories() {
        val r = Portions.solve(listOf(rice), Portions.Goal(390.0, 10.0, 5.0, 80.0))
        assertEquals(300.0, r.grams[0], 0.1)
        assertTrue(r.reachable)
    }

    @Test fun mealLandsOnCaloriesAndMacros() {
        // 200 g poulet + 250 g riz + 10 g huile = 330 + 325 + 88 = 743 kcal ; P 68.8, L 17.5, G 70
        val goal = Portions.Goal(743.4, 68.75, 17.95, 70.0)
        val r = Portions.solve(listOf(chicken, rice, oil), goal)
        assertEquals(goal.kcal, r.kcal, 10.0)
        r.grams.forEach { assertEquals(0.0, it % 5.0, 1e-9) }
        // protéines servies d'abord : le poulet est la seule source sérieuse
        assertEquals(200.0, r.grams[0], 15.0)
        assertEquals(250.0, r.grams[1], 25.0)
    }

    @Test fun unreachableTargetGivesClosestPortions() {
        val r = Portions.solve(listOf(rice), Portions.Goal(2000.0, 0.0, 0.0, 0.0))
        assertFalse(r.reachable)
        assertEquals(Portions.MAX_G, r.grams[0], 1e-9)
    }
}
