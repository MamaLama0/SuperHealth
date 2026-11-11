package app.superhealth.ui.cards

import app.superhealth.cards.EnemyAffinity
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DungeonBestiaryTest {
    @Test fun importedCreaturesAreReachableAndMatchTheCombatRules() {
        val assets = File("src/superhealth/assets/arsenal")
        val catalogue = JSONObject(File(assets, "bestiary/catalogue.json").readText())
        val reached = mutableSetOf<String>()
        for (floor in 1..120) {
            val creature = DungeonCreature.floor(floor)
            val id = creature.modelId ?: continue
            val source = catalogue.getJSONObject(id)
            val affinity = EnemyAffinity.floor(floor)
            assertEquals("Weakness at floor $floor", source.getString("weak"), affinity.weak.name)
            assertEquals("Resistance at floor $floor", source.getString("resist"), affinity.resist.name)
            assertEquals("Ward at floor $floor", source.getBoolean("ward"), affinity.ward)
            assertTrue(File(assets, "bestiary/$id.glb").length() > 0)
            assertTrue(File(assets, "creature-$id.webp").length() > 0)
            reached += id
        }
        assertEquals(catalogue.keys().asSequence().toSet(), reached)
    }
}
