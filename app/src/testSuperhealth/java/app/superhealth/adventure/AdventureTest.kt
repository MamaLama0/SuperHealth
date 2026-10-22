package app.superhealth.adventure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdventureTest {
    private fun boss(id: String, hp: Long) = Adventure.Boss(id, Adventure.Txt(id), 0, hp)
    private val world = Adventure.World(bosses = listOf(boss("a", 1000), boss("b", 3000)), kgDamage = 1.0, kmDamage = 1000.0, loopFactor = 2.0,
        heroes = listOf(Adventure.Hero(1, 85, Adventure.Txt("v")), Adventure.Hero(5, 112, Adventure.Txt("r"))))
    private fun hit(t: Long, kg: Double = 0.0, km: Double = 0.0) = Adventure.Hit(t, "x", kg, km, Adventure.damage(world, kg, km))

    @Test fun damageCountsKilosAndKilometres() {
        assertEquals(2500, Adventure.damage(world, 500.0, 2.0))
    }

    @Test fun overflowCarriesToTheNextBossAndLoops() {
        // 1 200 kg : le premier boss tombe, 200 dégâts passent au second ; puis 4 km : le second tombe, nouveau tour (PV × 2), 1 200 dégâts au premier
        val b = Adventure.battle(world, listOf(hit(1, kg = 1200.0), hit(2, km = 4.0)))!!
        assertEquals(listOf("a", "b"), b.defeated.map { it.boss.id })
        assertEquals(1, b.round)
        assertEquals("a", b.boss.id)
        assertEquals(2000, b.maxHp)
        assertEquals(800, b.hp)
        assertEquals(5200, b.total)
    }

    @Test fun hitsAreAppliedInTimeOrder() {
        val b = Adventure.battle(world, listOf(hit(5, kg = 100.0), hit(1, kg = 950.0)))!!
        assertEquals(1, b.defeated.size)
        assertEquals(5L, b.defeated[0].time)
        assertEquals(3000 - 50L, b.hp)
    }

    @Test fun noBossesNoBattle() {
        assertNull(Adventure.battle(Adventure.World(), emptyList()))
    }

    @Test fun heroFollowsLevelUnlessAnUnlockedLookIsChosen() {
        assertEquals(85, Adventure.hero(world, 3)!!.sprite)
        assertEquals(112, Adventure.hero(world, 6)!!.sprite)
        assertEquals(85, Adventure.hero(world, 6, chosen = 85)!!.sprite)
        assertEquals(85, Adventure.hero(world, 3, chosen = 112)!!.sprite) // pas encore débloqué
    }

    @Test fun storyTriggersFollowTheFights() {
        val b = Adventure.battle(world, listOf(hit(1, kg = 1000.0)))!!
        assertEquals(listOf("start", "boss:a", "defeat:a", "boss:b", "level:5"), Adventure.triggers(b, 5))
    }
}
