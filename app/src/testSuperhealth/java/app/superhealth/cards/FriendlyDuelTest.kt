package app.superhealth.cards

import org.junit.Assert.*
import org.junit.Test

class FriendlyDuelTest {
    private fun athlete(vararg values: Int) = AthleteStats(values = Stat.entries.zip(values.toList()).associate { (s, v) -> s.code to v })

    @Test fun equalAthletesDrawWithoutSideAdvantage() {
        val s = athlete(80, 80, 80, 80, 80, 80)
        val battle = FriendlyDuel.battle(s, s, emptyMap(), emptyMap())
        assertEquals(6, battle.exchanges.size)
        assertEquals(0, battle.winner)
        assertEquals(2000, battle.myHealth)
        assertEquals(battle.myHealth, battle.theirHealth)
    }

    @Test fun swappingSidesSwapsDamageAndOutcome() {
        val a = athlete(90, 81, 65, 72, 95, 60)
        val b = athlete(70, 85, 90, 82, 62, 80)
        val ab = FriendlyDuel.battle(a, b, emptyMap(), emptyMap())
        val ba = FriendlyDuel.battle(b, a, emptyMap(), emptyMap())
        assertEquals(ab.myHealth, ba.theirHealth)
        assertEquals(ab.theirHealth, ba.myHealth)
        ab.exchanges.zip(ba.exchanges).forEach { (x, y) -> assertEquals(x.myDamage, y.theirDamage) }
    }

    @Test fun missingStatsAreSkippedAndHealthNeverGoesNegative() {
        val battle = FriendlyDuel.battle(athlete(99, 99), athlete(25, 25, 90), emptyMap(), emptyMap())
        assertTrue(battle.exchanges.all { it.stat in listOf(Stat.PEC, Stat.DOS) })
        assertTrue(battle.exchanges.all { it.myHealth >= 0 && it.theirHealth >= 0 })
        assertTrue(battle.winner > 0)
        assertEquals(0, battle.theirHealth)
    }

    @Test fun gearCountsOnlyWhenBothSidesShareItAndProgressHelps() {
        val base = athlete(80, 80, 80, 80, 80, 80)
        val bonus = mapOf(Stat.PEC.code to 120)
        assertEquals(0, FriendlyDuel.battle(base, base, bonus, null).winner)
        assertTrue(FriendlyDuel.battle(base, base, bonus, emptyMap()).winner > 0)
        val improved = FriendlyDuel.battle(athlete(85, 80, 80, 80, 80, 80), base, emptyMap(), emptyMap())
        assertTrue(improved.winner > 0)
        assertTrue(improved.myHealth > 2000)
    }

    @Test fun noCommonStatsHasNoInventedExchange() {
        val battle = FriendlyDuel.battle(athlete(80), AthleteStats(values = mapOf(Stat.CAR.code to 80)), emptyMap(), null)
        assertTrue(battle.exchanges.isEmpty())
        assertEquals(0, battle.winner)
    }
}
