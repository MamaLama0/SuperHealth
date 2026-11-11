package app.superhealth.cards

import app.superhealth.league.Progression.Track
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class EquipmentStoreTest {
    @Test fun legacyRollKeepsItsPercentileAndDoesNotDoubleUpgrade() {
        val ranges = listOf(25..45, 50..80, 90..130, 150..210, 240..330)
        val old = listOf(20..40, 32..62, 50..90, 78..140, 105..185)
        ranges.forEachIndexed { tier, range ->
            val card = EquipmentCard("old", "Old", "Sword", "Iron", tier, range.first, range.last, EquipmentSlot.WEAPON)
            for (value in listOf(old[tier].first, old[tier].last)) {
                val legacy = EquipmentPiece(1, "old", EquipmentFinish.NORMAL, value, 100)
                val expected = if (value == old[tier].first) range.first else range.last
                assertEquals(expected, legacy.baseBonus(card))
                assertEquals(expected, legacy.copy(level = 2).baseBonus(card))
                assertEquals(expected, legacy.copy(base = expected, balanceVersion = 2).baseBonus(card))
            }
        }
        val piece = kotlinx.serialization.json.Json.decodeFromString<EquipmentPiece>("""{"serial":1,"id":"old","finish":"NORMAL","base":145,"multiplier":100}""")
        assertEquals(1, piece.balanceVersion)
        val remote = kotlinx.serialization.json.Json.decodeFromString<ArsenalGame.Piece>("""{"key":"sample","id":"old","finish":"NORMAL","base":285,"multiplier":100,"balanceVersion":2}""")
        assertEquals(2, remote.local(4).balanceVersion)
        val legendary = EquipmentCard("old", "Old", "Sword", "Iron", 4, 240, 330, EquipmentSlot.WEAPON)
        assertEquals(285, piece.baseBonus(legendary))
        assertEquals(285, remote.local(4).baseBonus(legendary))
        assertTrue(FriendlyDuel.validBonuses(mapOf(Stat.BRA.code to 330)))
    }

    @Test fun eachDrawGetsItsOwnPersistentRollWithinTheRarityRange() {
        val ranges = listOf(25..45, 50..80, 90..130, 150..210, 240..330)
        ranges.forEachIndexed { tier, range ->
            val pool = (0..8).map { EquipmentCard("$tier-$it", "Test", "Sword", "Iron", tier, range.first, range.last, EquipmentSlot.WEAPON) }
            val draws = (0..29).flatMap { seed -> EquipmentStore.draw(pool, seed * 10L, true, Random(seed)) }
            assertTrue(draws.all { it.base in range && it.balanceVersion == 2 })
            assertTrue(draws.map { it.base }.distinct().size > 5)
            val sameItem = draws.groupBy { it.id }.values.first()
            assertTrue(sameItem.map { it.base }.distinct().size > 1)
            val encoded = kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(EquipmentPiece.serializer()), draws)
            val restored = kotlinx.serialization.json.Json.decodeFromString<List<EquipmentPiece>>(encoded)
            assertEquals(draws, restored)
            draws.zip(restored).forEach { (before, after) ->
                val card = pool.first { it.id == before.id }
                assertEquals(before.boost(card), after.boost(card))
                assertEquals(before.base, before.copy(level = 2).base)
            }
        }
    }

    @Test fun dualAffinitySharesOneItemWeightAndRespectsPackTypes() {
        val card = EquipmentCard("dual", "Sword", "Runic sword", "Steel", 3, 150, 210, EquipmentSlot.WEAPON,
            school = EquipmentSchool.WARRIOR, damageTypes = listOf(DamageType.MELEE, DamageType.ARCANIST))
        val guard = card.copy(id = "guard", slot = EquipmentSlot.GUARD, damageTypes = listOf(DamageType.DIVINE))
        val pieces = listOf(EquipmentPiece(1, card.id, EquipmentFinish.NORMAL, 180, 100, balanceVersion = 2),
            EquipmentPiece(2, guard.id, EquipmentFinish.NORMAL, 190, 100, balanceVersion = 2))
        val state = EquipmentStore.State(owned = pieces, equipped = mapOf(EquipmentSlot.WEAPON to 1L, EquipmentSlot.GUARD to 2L))
        val mix = EquipmentStore.damageMix(state, listOf(card, guard).associateBy { it.id })
        assertEquals(mapOf(DamageType.MELEE to .5, DamageType.ARCANIST to .5, DamageType.DIVINE to 1.0), mix)
        assertEquals(2.0, mix.values.sum(), .001)
        assertEquals(37, EnemyAffinity.floor(1).hit(48, mapOf(DamageType.MELEE to .5, DamageType.ARCANIST to .5)))
        assertTrue(card in AffinityPair(EquipmentSchool.WARRIOR, EquipmentSchool.MAGE))
        assertFalse(card in AffinityPair(EquipmentSchool.WARRIOR, EquipmentSchool.PRIEST))
        assertEquals(180, pieces[0].boost(card))
    }

    @Test fun majorityCountsOnlySelectedDailyTracking() {
        val selected = setOf(Track.FOOD, Track.STEPS, Track.SLEEP, Track.STRENGTH, Track.HIKE)
        val partial = PackProgress.from(10, selected, setOf(Track.FOOD, Track.STRENGTH, Track.HIKE))
        assertEquals(2, partial.required)
        assertEquals(1, partial.completed)
        assertFalse(partial.ready)
        assertTrue(PackProgress.from(10, selected, setOf(Track.FOOD, Track.STEPS)).ready)
        assertFalse(PackProgress.from(10, selected + Track.MIND, setOf(Track.FOOD, Track.STEPS)).ready)
    }

    @Test fun weeklyTrackingFallbackUsesTodayAndEmptyTrackingStaysLocked() {
        val selected = setOf(Track.STRENGTH, Track.RUN)
        val gate = PackProgress.from(10, selected, setOf(Track.RUN))
        assertTrue(gate.canOpen(9))
        assertFalse(gate.canOpen(10))
        assertFalse(gate.canOpen(11))
        assertFalse(PackProgress.from(10, selected, setOf(Track.FOOD)).ready)
        assertFalse(PackProgress.from(10, emptySet(), setOf(Track.RUN)).ready)
    }

    @Test fun drawSupportsFourOrFiveUniqueCardsAndGoldAlwaysHasFive() {
        val pool = (0..4).flatMap { tier -> (0..9).map { i ->
            EquipmentCard("$tier-$i", "Card", "Piece", "Material", tier, 20, 40, EquipmentSlot.LEGS)
        } }
        val sizes = (0..50).map { seed ->
            val draw = EquipmentStore.draw(pool, 10, false, Random(seed))
            assertEquals(draw.size, draw.map { it.id }.distinct().size)
            assertEquals(draw.indices.map { it + 10L }, draw.map { it.serial })
            assertTrue(draw.all { it.base in 20..40 })
            assertEquals(5, EquipmentStore.draw(pool, 10, true, Random(seed)).size)
            draw.size
        }.toSet()
        assertEquals(setOf(4, 5), sizes)
    }

    @Test fun retiredObjectsStayUsableButNeverReturnInNewDraws() {
        val old = EquipmentCard("old", "Old", "Sword", "Iron", 0, 25, 45, EquipmentSlot.WEAPON, available = false)
        val active = (0..9).map { old.copy(id = "refined-$it", available = true) }
        val pool = active + old
        repeat(30) { seed ->
            assertTrue(EquipmentStore.draw(pool, 10, false, Random(seed)).all { it.id.startsWith("refined-") })
        }
        val owned = EquipmentPiece(1, old.id, EquipmentFinish.NORMAL, 35, 100, balanceVersion = 2)
        val state = EquipmentStore.State(owned = listOf(owned), equipped = mapOf(EquipmentSlot.WEAPON to 1L))
        assertEquals(35, EquipmentStore.bonuses(state, pool.associateBy { it.id })[Stat.BRA])
        assertEquals(listOf(owned), state.owned)
    }

    @Test fun equipmentAddsBonusesAndSubtractsTheOtherStat() {
        val weapon = EquipmentCard("weapon", "Weapon", "Sword", "Iron", 4, 100, 200, EquipmentSlot.WEAPON, Stat.DOS, 60)
        val guard = EquipmentCard("guard", "Guard", "Shield", "Wood", 1, 30, 70, EquipmentSlot.GUARD)
        val owned = listOf(EquipmentPiece(1, weapon.id, EquipmentFinish.ANIMATED, 120, 114, balanceVersion = 2),
            EquipmentPiece(2, guard.id, EquipmentFinish.NORMAL, 70, 100, balanceVersion = 2))
        val equipped = EquipmentStore.State(owned = owned, equipped = mapOf(EquipmentSlot.WEAPON to 1L, EquipmentSlot.GUARD to 2L))
        assertEquals(mapOf(Stat.BRA to 192, Stat.DOS to 10), EquipmentStore.bonuses(equipped, listOf(weapon, guard).associateBy { it.id }))
    }

    @Test fun friendlyDuelsUseOnlyBothBaseRollsAndFallBackToTrainingStatsTogether() {
        val weapon = EquipmentCard("sword", "Weapon", "Sword", "Iron", 4, 105, 185, EquipmentSlot.WEAPON, Stat.DOS, 60)
        val piece = EquipmentPiece(1, weapon.id, EquipmentFinish.BLESSED, 120, 134, level = 5, balanceVersion = 2)
        val state = EquipmentStore.State(owned = listOf(piece), equipped = mapOf(EquipmentSlot.WEAPON to piece.serial))
        val boosts = EquipmentStore.duelBonuses(state, mapOf(weapon.id to weapon))
        val mine = AthleteStats(values = mapOf(Stat.BRA.code to 80, Stat.DOS.code to 71))
        val theirs = AthleteStats(values = mapOf(Stat.BRA.code to 81))

        assertEquals(mapOf(Stat.BRA.code to 120), boosts)
        assertEquals(listOf(Triple(Stat.BRA, 8096, 8141)), FriendlyDuel.rounds(mine, theirs, boosts, mapOf(Stat.BRA.code to 50)))
        assertEquals(listOf(Triple(Stat.BRA, 8000, 8100)), FriendlyDuel.rounds(mine, theirs, boosts, null))
        assertEquals(listOf(Triple(Stat.BRA, 8096, 8100)), FriendlyDuel.rounds(mine, theirs, boosts, emptyMap()))
        assertFalse(FriendlyDuel.validBonuses(mapOf(Stat.BRA.code to 331)))
        assertFalse(FriendlyDuel.validBonuses(mapOf(Stat.CAR.code to 60)))
    }
}
