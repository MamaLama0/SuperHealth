package app.superhealth.nutrition

import app.superhealth.nutrition.data.FoodEntryEntity
import app.superhealth.nutrition.ui.RecipeDraft
import org.junit.Assert.*
import org.junit.Test

class RecipeSelectionTest {
    private fun entry(id: Long, name: String, grams: Double?, kcal: Double) = FoodEntryEntity(
        id = id, epochDay = 1, meal = 0, foodId = id + 10, name = name, grams = grams,
        kcal = kcal, protein = 3.0, fat = 2.0, carbs = 10.0, fiber = 1.0, microsJson = "{\"iron\":1}")
    @Test fun milkshakeKeepsQuantitiesAndNutrientsWithoutChangingTheLog() {
        val selected = listOf(entry(1, "Milk", 300.0, 180.0), entry(2, "Banana", 100.0, 90.0), entry(3, "Honey", 10.0, 30.0))
        val draft = RecipeDraft.fromEntries(selected)!!
        assertEquals(410.0, draft.items.sumOf { it.grams }, 0.0)
        assertEquals(300.0, draft.items.sumOf { it.kcal }, 0.0)
        assertEquals(listOf(0, 1, 2), draft.items.map { it.position })
        assertEquals(selected.map { it.microsJson }, draft.items.map { it.microsJson })
        assertEquals(listOf(1L, 2L, 3L), selected.map { it.id })
        assertEquals(0L, draft.id)
    }
    @Test fun noUnknownOrInvalidWeightIsInvented() {
        listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertNull(RecipeDraft.fromEntries(listOf(entry(1, "Quick entry", it, 100.0))))
        }
        assertNull(RecipeDraft.fromEntries(emptyList()))
    }
}
