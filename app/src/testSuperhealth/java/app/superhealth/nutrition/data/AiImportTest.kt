package app.superhealth.nutrition.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AiImportTest {
    @Test fun label() {
        val l = AiFood.parseLabel("```\nYaourt grec 0 % | Fage | pot | 1 | 170 | 92 | 17 | 0.3 | 5.1 | 0 | 5.1 | 0.2 | 61\n```")
        assertNotNull(l)
        val f = l!!.toFood()
        assertEquals(170.0, f.servingGrams, 1e-9)
        assertEquals(92 / 1.7, f.kcal100, 1e-6)
        assertEquals(10.0, f.protein100, 1e-6)
        assertNull(AiFood.parseLabel("ai_search_not_a_food"))
    }

    @Test fun recipe() {
        val r = AiFood.parseRecipe("""
            Porridge | 2
            Flocons d'avoine | 80 | g | 80 | 300 | 10.8 | 5.6 | 47 | 8
            Lait demi-écrémé | 300 | ml | 300 | 138 | 9.9 | 4.8 | 14.4 | 0
        """.trimIndent())
        assertNotNull(r)
        assertEquals("Porridge", r!!.name)
        assertEquals(2.0, r.servings, 1e-9)
        assertEquals(2, r.items.size)
        assertEquals(438.0, r.items.sumOf { it.kcal }, 1e-9)
    }
}
