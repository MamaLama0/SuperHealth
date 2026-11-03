package app.superhealth.nutrition.data

import org.junit.Assert.*
import org.junit.Test

class AiEstimationTest {
    @Test fun portionAssumptionsAreVisibleWithoutBreakingOlderModelResponses() {
        val line = "Riz cuit | 1 | bol | 180 | generic | 234 | 4.9 | 0.5 | 51 | 0.7 | riz cuit"
        val old = AiFood.parseAnalyzed(line).single()
        assertEquals("", old.assumption)
        val detailed = AiFood.parseAnalyzed(line + " | Bol estimé à 180 g").single()
        assertEquals("Bol estimé à 180 g", detailed.assumption)
        assertEquals(old.grams, detailed.grams, 0.0)
        assertEquals(old.kcal, detailed.kcal, 0.0)
    }
    @Test fun rawAndCookedPastaCannotBeMatchedDespiteASimilarEnergyDensity() {
        val item = AiFood.Item("Pâtes cuites", 100.0, "g", 100.0, "generic", 150.0, 5.0, 1.0, 30.0, 1.0, "pates cuites")
        val dry = FoodEntity(name = "Pâtes sèches crues", kcal100 = 350.0, protein100 = 12.0, fat100 = 2.0, carbs100 = 70.0)
        assertFalse(AiFood.plausible(item, dry))
        assertTrue(AiFood.plausible(item, dry.copy(name = "Pâtes cuites", kcal100 = 155.0)))
    }
    @Test fun negativeNutrientsAndImpossibleDensitiesAreRejectedButZeroCalorieDrinksRemain() {
        assertTrue(AiFood.parseAnalyzed("Food | 1 | g | 10 | generic | 1000 | 1 | 1 | 1 | 0 | food").isEmpty())
        assertTrue(AiFood.parseAnalyzed("Food | 1 | g | 100 | generic | 100 | -2 | 1 | 1 | 0 | food").isEmpty())
        assertEquals(0.0, AiFood.parseAnalyzed("Water | 250 | ml | 250 | generic | 0 | 0 | 0 | 0 | 0 | eau").single().kcal, 0.0)
    }
}
