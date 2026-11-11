package app.superhealth.meals
import app.superhealth.nutrition.data.FoodEntryEntity
import app.superhealth.nutrition.domain.Macros
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
class MealPlanTest {
    private val meal = MealPlanner.Meal(1, "Lunch", logged = true, entryIds = listOf(10, 11))
    private val plan = MealPlanner.Plan(100, "foods", listOf(meal))
    private fun row(id: Long, day: Long = 100) = FoodEntryEntity(id, day, 1, name = "Food", kcal = 100.0, protein = 5.0, fat = 2.0, carbs = 10.0)
    @Test fun futureMealCompositionStartsAtBreakfastEvenLateInTheEvening() {
        val slots = listOf(480, 720, 1140).map { MealPlanner.Slot(MealPlanner.Txt("Meal"), .33, it) }
        assertEquals(0, app.superhealth.ui.meals.defaultSlot(101, slots, emptyList(), today = 100, minute = 1200))
        assertEquals(2, app.superhealth.ui.meals.defaultSlot(100, slots, emptyList(), today = 100, minute = 1200))
    }
    @Test fun journalDeletionAndUndoDriveMealStatus() {
        val partial = MealPlanner.reconciled(plan, listOf(row(10))).meals.single()
        assertTrue(partial.logged); assertTrue(partial.partiallyLogged)
        val deleted = MealPlanner.reconciled(plan, emptyList()).meals.single()
        assertFalse(deleted.logged); assertFalse(deleted.partiallyLogged)
        assertEquals(listOf(10L, 11L), deleted.entryIds)
        val restored = MealPlanner.reconciled(plan, listOf(row(10), row(11))).meals.single()
        assertTrue(restored.logged); assertFalse(restored.partiallyLogged)
        assertFalse(MealPlanner.reconciled(plan, listOf(row(10, 101))).meals.single().logged)
    }
    @Test fun reservedMealsAreSubtractedBeforeNewPortionsAreComputed() {
        val c = MealPlanner.Content(system = "", user = "", slots = List(2) { MealPlanner.Slot(MealPlanner.Txt("Meal"), .5, 720) })
        val kept = MealPlanner.Meal(1, "Dinner", perServing = listOf(600.0, 30.0, 20.0, 75.0))
        val goal = MealPlanner.slotGoal(Macros(2000.0, 100.0, 60.0, 250.0), listOf(row(1)), 0, c, listOf(kept))!!
        assertEquals(1300.0, goal.kcal, .001)
        assertEquals(65.0, goal.protein, .001)
    }
    @Test fun movingAPlanSwapsOccupiedWindowsAndPreservesItsContents() {
        val first = meal.copy(slot = 0, logged = false, entryIds = emptyList(), manual = true)
        val second = first.copy(slot = 3, title = "Snack")
        assertEquals(listOf(second.copy(slot = 0), first.copy(slot = 3)), MealPlanner.moved(listOf(first, second), 0, 3))
        assertEquals(listOf(first, meal), MealPlanner.moved(listOf(first, meal), 0, 1))
    }

    @Test fun manualConfirmationUsesTheConfiguredWindowEvenWithoutSuggestions() {
        val ends = listOf(720, 745, 840)
        assertEquals(744, MealPlanner.scheduledMinute(1, ends))
        assertEquals(839, MealPlanner.scheduledMinute(3, ends))
        assertEquals(1170, MealPlanner.scheduledMinute(2, ends))
    }

    @Test fun legacyPlansDecodeAndMicronutrientsSurviveSerialization() {
        val legacy = Json.decodeFromString<MealPlanner.Plan>("""{"day":100,"provider":"foods","meals":[{"slot":1,"title":"Lunch"}]}""")
        assertNull(legacy.meals.single().ingredients.firstOrNull()?.microsJson)
        val ingredient = MealPlanner.Ingredient("Food", 100.0, 80.0, 5.0, 2.0, 10.0, microsJson = "{\"iron\":2.5}")
        val encoded = Json.encodeToString(plan.copy(meals = listOf(meal.copy(ingredients = listOf(ingredient)))))
        assertEquals(ingredient, Json.decodeFromString<MealPlanner.Plan>(encoded).meals.single().ingredients.single())
    }
}
