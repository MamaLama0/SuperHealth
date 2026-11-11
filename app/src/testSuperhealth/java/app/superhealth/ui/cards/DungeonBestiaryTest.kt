package app.superhealth.ui.cards

import app.superhealth.cards.EnemyAffinity
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DungeonBestiaryTest {
    @Test fun largeMonstersAreReservedForSoloAndTeamBosses() {
        val bosses = setOf(DungeonCreature.SKELETONGOLEM, DungeonCreature.TROLL_BRISEFER, DungeonCreature.NECROMANCER)
        assertEquals(bosses, (4..32 step 4).map(DungeonCreature::floor).toSet())
        assertEquals(bosses, (0L..28L step 7L).map(DungeonCreature::weekly).toSet())
        val ordinary = (1..120).filter { it % 4 != 0 }.map(DungeonCreature::floor).toSet()
        assertEquals(emptySet<DungeonCreature>(), ordinary.intersect(bosses))
        assertEquals(14, ordinary.size)
        assertTrue(ordinary.all { creature ->
            listOf("GOBELIN_", "SKELETON", "SLIME_").any { creature.name.startsWith(it) }
        })
    }

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
            assertFalse(File(assets, "bestiary/$id.glb").exists())
            assertFalse(File(assets, "creature-$id.webp").exists())
            reached += id
        }
        assertEquals(catalogue.keys().asSequence().toSet(), reached)
        val plugins = JSONObject(File(assets.parentFile, "plugins/index.json").readText()).getJSONArray("modules")
        val group = (0 until plugins.length()).map(plugins::getJSONObject).single { it.getString("id") == "league" }
        assertTrue(group.getLong("size") in 1L..(24L * 1024 * 1024))
        assertTrue(group.getString("sha256").matches(Regex("[0-9a-f]{64}")))
    }
}
