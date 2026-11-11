package app.superhealth.cards

import app.superhealth.league.Progression
import app.superhealth.league.Progression.Track
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ArsenalGameTest {
    private val selected = setOf(Track.FOOD, Track.STEPS, Track.SLEEP, Track.STRENGTH)
    private val qualified = setOf(Track.FOOD, Track.STEPS)
    private val full = selected
    private fun day(date: Long, tracks: Set<Track>, history: Map<Long, Set<Track>> = emptyMap()) =
        ArsenalGame.quests(Progression.Result(Progression.Summary(throughDay = date), date,
            validatedToday = tracks, recentValidated = history), selected)

    @Test fun questsHaveThreeRanksAndAccumulateNonconsecutiveDays() {
        val quests = day(21003, full, mapOf(21001L to qualified, 21002L to emptySet()))
        assertEquals(3, quests.rank)
        assertEquals(listOf(1, 3), quests.days.map { it.rank })
        assertEquals(2, day(21003, full - Track.STRENGTH).rank)
    }

    @Test fun raidIsAvailableEveryWeekAndRotatesOnMonday() {
        fun week(date: String) = ArsenalGame.raidWeek(LocalDate.parse(date).toEpochDay())
        assertEquals(week("2026-11-02"), week("2026-11-08"))
        assertNotEquals(week("2026-11-08"), week("2026-11-09"))
        assertEquals(week("2026-11-09"), week("2026-11-15"))
        assertTrue(day(21003, full).fullToday)
        assertFalse(day(21003, qualified).fullToday)
    }

    @Test fun normalMonstersFallInOneHitAndElitesCarryHealthToTheNextDay() {
        val one = ArsenalGame.attack(ArsenalGame.Dungeon(), day(21000, qualified), 0)
        assertEquals(2, one.first.floor)
        assertEquals(15, one.second)
        val start = ArsenalGame.Dungeon(floor = 4)
        val mage = mapOf(DamageType.ARCANIST to 1.0)
        val neutral = ArsenalGame.attack(start, day(21000, qualified), 0).first
        val elite = ArsenalGame.attack(start, day(21000, qualified), 0, mage).first
        assertTrue(neutral.hp > elite.hp)
        assertEquals(46, elite.hp)
        assertEquals(elite, ArsenalGame.attack(elite, day(21000, qualified), 0, mage).first)
        assertEquals(5, ArsenalGame.attack(elite, day(21001, qualified, mapOf(21000L to qualified)), 0, mage).first.floor)
    }

    @Test fun fourMissesKnockOutAndAValidatedDayRestoresLife() {
        val start = ArsenalGame.Dungeon(settled = 21000)
        val now = day(21005, qualified)
        assertEquals(0, ArsenalGame.settle(start, now).health)
        val revived = ArsenalGame.attack(start, now, 0)
        assertEquals(100, revived.first.health)
        assertEquals(1, revived.first.floor)
        assertEquals(0, revived.second)
    }

    @Test fun recordedSportProtectsEvenWhenNoDailyTrackingGoalWasMet() {
        val start = ArsenalGame.Dungeon(settled = 21000)
        val quests = day(21003, emptySet()).copy(sportDays = setOf(21001, 21002))
        assertFalse(quests.gate.ready)
        assertEquals(100, ArsenalGame.settle(start, quests).health)
        assertEquals(0, ArsenalGame.attack(start, quests, 0).second)
    }

    @Test fun bodyPacksRotateAllPairsAndDrawBothAffinities() {
        assertEquals(6, (0L..5L).map { AffinityPair.forPack(it, EquipmentSlot.WEAPON) }.toSet().size)
        assertEquals(4, EquipmentSlot.entries.map { AffinityPair.forPack(21000L, it) }.toSet().size)
        val pair = AffinityPair(EquipmentSchool.PRIEST, EquipmentSchool.OCCULTIST)
        val pool = EquipmentSchool.entries.flatMap { school -> List(6) { index ->
            EquipmentCard("${school.name}-$index", "Test", "Test", "Test", index % 3, 20, 30, EquipmentSlot.WEAPON, school = school)
        } }
        val pack = EquipmentStore.draw(pool, 1L, true, kotlin.random.Random(3), pair)
        val schools = pack.map { piece -> pool.single { it.id == piece.id }.school.damageType }.toSet()
        assertEquals(setOf(pair.first.damageType, pair.second.damageType), schools)
        assertEquals(pack.size, pack.map { it.id }.toSet().size)
    }

    @Test fun aValidatedGymSessionAlsoUnlocksTheDailyAttack() {
        val quests = day(21000, setOf(Track.STRENGTH))
        assertTrue(quests.canAttack)
        assertEquals(setOf(21000L), quests.attackDays)
    }

    @Test fun freezingPreventsTheCounterattack() {
        val quests = day(21000, qualified)
        val normal = ArsenalGame.attack(ArsenalGame.Dungeon(floor=4), quests, 0).first
        val frozen = ArsenalGame.attack(ArsenalGame.Dungeon(floor=4,frozenDay=21000), quests, 0).first
        assertTrue(normal.received>0); assertEquals(0,frozen.received); assertEquals(100,frozen.health)
        assertEquals(normal.dealt,frozen.dealt)
    }

}
