package app.superhealth.nutrition.domain

import app.superhealth.nutrition.data.AiFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V4Test {
    @Test fun parsesPipeDelimitedAnalysis() {
        val raw = """
            ```text
            Œufs brouillés | 2 | œufs | 110 | generic | 165 | 11.5 | 12.4 | 1.2 | 0 | oeuf brouillé
            Riz basmati cuit | 1 | assiette | 180 | generic | 234 | 4,9 | 0.5 | 51 | 0.7 | riz basmati cuit
            | Poulet rôti, blanc | 1 | portion | 120 g | restaurant | 198 | 36 | 5.4 | 0 | 0 | poulet rôti |
            name | quantity | unit | weight_g | type | kcal | protein_g | fat_g | carbs_g | fiber_g | search_query
            ligne invalide
            ```
        """.trimIndent()
        val items = AiFood.parseAnalyzed(raw)
        assertEquals(3, items.size)
        assertEquals("Œufs brouillés", items[0].name)
        assertEquals("Riz basmati cuit", items[1].name)
        assertEquals(4.9, items[1].protein, 1e-9)
        assertEquals(120.0, items[2].grams, 1e-9)
        assertEquals("poulet rôti", items[2].query)
        assertTrue(AiFood.parseAnalyzed(AiFood.NOT_A_FOOD).isEmpty())
    }

    @Test fun parsesSelection() {
        val sel = AiFood.parseSelection("1 | 1.3\n2 | none\n3 | [3.1]\n4|2")
        assertEquals(mapOf(1 to 3, 3 to 1, 4 to 2), sel)
    }

    @Test fun streaks() {
        val days = setOf(1L, 2L, 3L, 5L, 6L, 7L, 8L, 9L)
        val s = Habits.streak(days, 10)        // aujourd'hui (10) vide : la série d'hier compte
        assertEquals(5, s.current); assertEquals(5, s.longest)
        assertEquals(0, Habits.streak(days, 11).current)
    }
}
