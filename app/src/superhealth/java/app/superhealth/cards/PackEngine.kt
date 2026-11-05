package app.superhealth.cards

import java.util.SplittableRandom

/**
 * Tirage d'un pack, fonction pure. Deux cartes, et une troisième une fois sur cinq. Taux affichés tels quels dans
 * l'écran Taux : ≈ 3 % de packs avec une Rare ou mieux (≈ 3,9 % garantie comprise), ≈ 0,5 % avec une épique. Pack Prisme (deux rares et une
 * épique) 1 fois sur 1 000. Pity visible : une Rare au plus tard au 50ᵉ pack, une épique au 200ᵉ.
 */
object PackEngine {
    const val BONUS_ODDS = 0.20
    const val PRISM_ODDS = 1.0 / 1000
    const val RARE_PITY = 50
    const val EPIC_PITY = 200

    val slot1 = listOf(Rarity.COMMUNE to 85.0, Rarity.PEU_COMMUNE to 15.0)
    val slot2 = listOf(Rarity.COMMUNE to 40.0, Rarity.PEU_COMMUNE to 58.05, Rarity.RARE to 1.6, Rarity.EPIQUE to 0.35)
    val slot3 = listOf(Rarity.PEU_COMMUNE to 94.45, Rarity.RARE to 4.8, Rarity.EPIQUE to 0.75)

    data class Result(val pulls: List<Pull>, val prism: Boolean, val sinceRare: Int, val sinceEpic: Int)

    fun seed(secret: Long, counter: Long): Long = SplittableRandom(secret xor (counter * -0x61c8864680b583ebL)).nextLong()

    /** Probabilité exacte, par pack, d'au moins une carte de rareté [r] ou mieux (hors pity). */
    fun perPack(r: Rarity): Double {
        fun p(t: List<Pair<Rarity, Double>>) = t.filter { it.first >= r }.sumOf { it.second } / t.sumOf { it.second }
        val normal = 1 - (1 - p(slot1)) * (1 - p(slot2)) * (1 - BONUS_ODDS * p(slot3))
        return (1 - PRISM_ODDS) * normal + PRISM_ODDS
    }

    /** Taux réel avec la garantie : inverse de l'attente moyenne entre deux tirages, plafonnée à [pity]. */
    fun withPity(r: Rarity): Double {
        val q = 1 - perPack(r); val pity = if (r == Rarity.EPIQUE) EPIC_PITY else RARE_PITY
        return (1 - q) / (1 - Math.pow(q, pity.toDouble()))
    }

    fun open(seed: Long, sinceRare: Int, sinceEpic: Int, booster: Booster): Result {
        val rng = SplittableRandom(seed)
        val isPrism = rng.nextDouble() < PRISM_ODDS
        val rarities = if (isPrism) mutableListOf(Rarity.RARE, Rarity.RARE, Rarity.EPIQUE)
            else mutableListOf(pick(rng, slot1), pick(rng, slot2)).also { if (rng.nextDouble() < BONUS_ODDS) it += pick(rng, slot3) }
        val last = rarities.lastIndex
        if (sinceEpic + 1 >= EPIC_PITY && rarities.none { it == Rarity.EPIQUE }) rarities[last] = Rarity.EPIQUE
        else if (sinceRare + 1 >= RARE_PITY && rarities.none { it >= Rarity.RARE }) rarities[last] = Rarity.RARE
        rarities.sort()
        val content = Catalog.ofBooster(booster)
        val pulls = rarities.map { r ->
            val pool = content.filter { it.rarity == r }
            Pull(pool[rng.nextInt(pool.size)], finish(rng, r))
        }
        return Result(pulls, isPrism, if (rarities.any { it >= Rarity.RARE }) 0 else sinceRare + 1, if (rarities.any { it == Rarity.EPIQUE }) 0 else sinceEpic + 1)
    }

    private fun finish(rng: SplittableRandom, r: Rarity): Finish {
        val x = rng.nextDouble() * 100
        return when {
            r >= Rarity.RARE && x < 1.5 -> Finish.ANIMEE
            r >= Rarity.RARE && x < 9.5 -> Finish.BRILLANTE
            r < Rarity.RARE && x < 3.0 -> Finish.BRILLANTE
            else -> Finish.NORMALE
        }
    }

    private fun pick(rng: SplittableRandom, table: List<Pair<Rarity, Double>>): Rarity {
        var x = rng.nextDouble() * table.sumOf { it.second }
        for ((r, w) in table) { x -= w; if (x < 0) return r }
        return table.last().first
    }
}
