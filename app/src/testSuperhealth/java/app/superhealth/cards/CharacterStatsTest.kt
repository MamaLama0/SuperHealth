package app.superhealth.cards

import app.superhealth.league.Progression
import app.superhealth.league.Progression.Track
import org.junit.Assert.*
import org.junit.Test

class CharacterStatsTest {
    private val today = 21000L
    private fun progress(history: Map<Long, Set<Track>>) = Progression.Result(
        Progression.Summary(throughDay = today), today - 100,
        validatedToday = history[today].orEmpty(), recentValidated = history)

    @Test fun missingTrackingDoesNotInventStats() {
        assertEquals(CharacterStats(), CharacterStats.from(progress(emptyMap()), Track.entries.toSet()))
        assertEquals(-1, CharacterStats.modifier(9))
        assertEquals(5, CharacterStats.modifier(20))
    }
    @Test fun enabledTracksRenormalizeAndAreCappedAtTheirGoals() {
        val all = (today - 27..today).associateWith { Track.entries.toSet() }
        val full = CharacterStats.from(progress(all), Track.entries.toSet())
        assertEquals(CharacterStats(20,20,20,28), full)
        val sleep = CharacterStats.from(progress(all), setOf(Track.SLEEP))
        assertEquals(CharacterStats(8,8,20,28), sleep)
        assertEquals(CharacterStats(8,8,14,14), CharacterStats.from(progress((today-13..today).associateWith { setOf(Track.SLEEP) }), setOf(Track.SLEEP)))
    }
    @Test fun futureAndOldDaysDoNotCountAndFirstDayDoesNotMaxStats() {
        val history = mapOf(today-28 to Track.entries.toSet(), today+1 to Track.entries.toSet(), today to Track.entries.toSet())
        val stats = CharacterStats.from(progress(history), Track.entries.toSet())
        assertTrue(stats.valid()); assertEquals(1, stats.days)
        assertTrue(stats.strength < 12); assertTrue(stats.constitution < 12)
    }
    @Test fun combatIsSymmetricalAndUsesThreeStatsWithoutStances() {
        val a = CharacterStats(16,12,18,20); val b = CharacterStats(10,20,13,15)
        val ab = CharacterCombat.battle(a,b); val ba = CharacterCombat.battle(b,a)
        assertEquals(3, ab.exchanges.size)
        assertEquals(ab.myHealth,ba.theirHealth); assertEquals(ab.theirHealth,ba.myHealth)
        assertEquals(0, CharacterCombat.battle(a,a).winner)
        assertTrue(ab.exchanges.all { it.myHealth in 0..10000 && it.theirHealth in 0..10000 })
    }
    @Test fun crossPlatformBattleVector() {
        val battle = CharacterCombat.battle(CharacterStats(16,12,18,20), CharacterStats(10,20,13,15))
        assertEquals(listOf(3200,1950,3019), battle.exchanges.map { it.myDamage })
        assertEquals(2570, battle.myHealth); assertEquals(1831, battle.theirHealth)
    }
    @Test fun actualPerformancesAndTrackingContributeWithDisplayedWeights() {
        val athlete = AthleteStats(values = mapOf("PEC" to 50,"DOS" to 60,"BRA" to 70,"JAM" to 80,"CAR" to 80,"RÉG" to 90))
        val history = (0..27).associate { i -> today-i to buildSet {
            if (i < 6) add(Track.STRENGTH)
            if (i < 14) { add(Track.STEPS); add(Track.SLEEP) }
            if (i < 21) add(Track.FOOD)
            if (i < 7) add(Track.MIND)
        } }
        val result = CharacterStats.from(progress(history), Track.entries.toSet(), athlete)
        assertEquals(15,result.strength); assertEquals(16,result.dexterity); assertEquals(16,result.constitution)
        val calculation = CharacterStats.breakdown(progress(history), Track.entries.toSet(), athlete)
        assertEquals(.62,calculation[0].ratio,.000001)
        assertEquals(.6575,calculation[1].ratio,.000001)
        assertEquals(.6825,calculation[2].ratio,.000001)
    }
    @Test fun equipmentMapsBonusesAndCursesToThreeAttributesWithoutChangingDamageTypes() {
        val weapon=EquipmentCard("a","Sword","Sword","Steel",0,25,45,EquipmentSlot.WEAPON,
            penalty=Stat.DOS,penaltyPoints=45,damageTypes=listOf(DamageType.MELEE,DamageType.ARCANIST))
        val boots=EquipmentCard("b","Boots","Boots","Leather",0,25,45,EquipmentSlot.LEGS)
        val pieces=listOf(EquipmentPiece(1,"a",EquipmentFinish.NORMAL,30,100,balanceVersion=2),
            EquipmentPiece(2,"b",EquipmentFinish.NORMAL,25,100,balanceVersion=2))
        val saved=EquipmentStore.State(owned=pieces,equipped=mapOf(EquipmentSlot.WEAPON to 1,EquipmentSlot.LEGS to 2))
        val bonuses=EquipmentStore.characterBonuses(saved,mapOf("a" to weapon,"b" to boots))
        assertEquals(42,bonuses[CharacterAttribute.FOR]);assertEquals(25,bonuses[CharacterAttribute.DEX]);assertEquals(-45,bonuses[CharacterAttribute.CON])
        assertEquals(listOf(DamageType.MELEE,DamageType.ARCANIST),weapon.affinities)
        assertTrue(CharacterStats().equipped(bonuses).valid())
    }
    @Test fun boostersPartitionEveryEquipmentSlot() {
        assertEquals(2, EquipmentBoosters.slots.size)
        EquipmentSlot.entries.forEach { item -> assertEquals(1, EquipmentBoosters.slots.count { EquipmentBoosters.contains(it,item) }) }
    }
}
