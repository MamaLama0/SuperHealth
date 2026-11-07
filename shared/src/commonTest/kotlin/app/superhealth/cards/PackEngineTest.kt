package app.superhealth.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Les tables de tirage doivent rester celles du serveur (economy.mjs, RATES) : 3.6.2. */
class PackEngineTest {
    @Test fun ratesMatchServer362() {
        assertEquals(listOf(Rarity.COMMUNE to 20.0, Rarity.PEU_COMMUNE to 76.7, Rarity.RARE to 2.6, Rarity.EPIQUE to 0.7), PackEngine.slot3)
        assertEquals(listOf(Rarity.PEU_COMMUNE to 90.5, Rarity.RARE to 7.5, Rarity.EPIQUE to 2.0), PackEngine.slot4)
        listOf(PackEngine.slot1, PackEngine.slot2, PackEngine.slot3, PackEngine.slot4).forEach { assertEquals(100.0, it.sumOf { p -> p.second }, 1e-9) }
    }

    @Test fun rewardsDefaultsMatchServer37() {
        // 3.7 : les quêtes de la semaine donnent des packs, réglés par le serveur (rewards.json)
        assertEquals(3, Economy.Rewards().weekly.count)
        assertEquals(1, Economy.Rewards().weekly.packs)
        assertEquals(100, CardStore.PACK_PRICE)
    }

    @Test fun catalogHas180Backgrounds() {
        assertEquals(180, Catalog.cards.size)
        Booster.entries.forEach { b -> Rarity.entries.forEach { r -> assertTrue(Catalog.ofBooster(b).any { it.rarity == r && !it.duo }) } }
    }

    @Test fun openingIsDeterministicAndValid() {
        val a = PackEngine.open(PackEngine.seed(42, 3), gold = false, Booster.GLACIER)
        val b = PackEngine.open(PackEngine.seed(42, 3), gold = false, Booster.GLACIER)
        assertEquals(a, b)
        assertTrue(a.pulls.size in 3..4)
        assertTrue(a.pulls.all { Element.GLACE in it.card.elements })
    }

    @Test fun oddsAreProbabilities() {
        Rarity.entries.forEach { r -> assertTrue(PackEngine.average(r) in 0.0..1.0) }
        assertEquals(1.0, PackEngine.perPack(Rarity.COMMUNE), 1e-9)
    }

    @Test fun badgeIdsMatchServerCatalog() {
        val a = app.superhealth.league.Achievements
        assertEquals(25, a.reward(a.find("strength_10")!!))
        assertEquals(200, a.reward(a.find("streak_RUN_10")!!))
        assertEquals(600, a.reward(a.find("distance_25000")!!))
    }
}
