package app.superhealth.cards

import java.util.SplittableRandom

/**
 * Tirage d'un pack, fonction pure. Trois cartes, et une quatrième une fois sur quatre. Chaque carte tire ensuite, à part,
 * ce qui la rend spéciale : duo (1 chance sur 200, à toute rareté) et, indépendamment, un effet qui dépend de sa rareté
 * ([EFFECTS]) : une épique chromatique duo existe. L'animée, au bonus plus faible, sort plus souvent que la chromatique. Pas de
 * garantie : un pack sur dix est doré et triple les chances de rare, d'épique, de duo, d'animée, de chromatique et de
 * pack Prisme (trois rares et une épique). La charte affichée sous les packs est calculée ici, à partir des mêmes tables.
 */
object PackEngine {
    const val BONUS_ODDS = 0.25
    const val PRISM_ODDS = 1.0 / 1000
    const val DUO_ODDS = 0.005
    /** Le 10ᵉ, le 20ᵉ… pack ouvert est doré. */
    const val GOLD_EVERY = 10
    const val GOLD_BOOST = 3.0
    val PRISM = listOf(Rarity.RARE, Rarity.RARE, Rarity.RARE, Rarity.EPIQUE)

    val slot1 = listOf(Rarity.COMMUNE to 82.0, Rarity.PEU_COMMUNE to 18.0)
    val slot2 = listOf(Rarity.COMMUNE to 55.0, Rarity.PEU_COMMUNE to 45.0)
    val slot3 = listOf(Rarity.COMMUNE to 20.0, Rarity.PEU_COMMUNE to 76.7, Rarity.RARE to 2.6, Rarity.EPIQUE to 0.7)
    val slot4 = listOf(Rarity.PEU_COMMUNE to 90.5, Rarity.RARE to 7.5, Rarity.EPIQUE to 2.0)

    /** Effets par rareté, en % par carte (duo compris) : chromatique, animée, brillante (le reste est normal). */
    val EFFECTS: Map<Rarity, Triple<Double, Double, Double>> = mapOf(
        Rarity.COMMUNE to Triple(0.3, 0.8, 3.0), Rarity.PEU_COMMUNE to Triple(0.5, 1.2, 5.0),
        Rarity.RARE to Triple(0.8, 2.0, 8.0), Rarity.EPIQUE to Triple(1.2, 3.0, 12.0))

    data class Result(val pulls: List<Pull>, val prism: Boolean, val gold: Boolean)

    fun seed(secret: Long, counter: Long): Long = SplittableRandom(secret xor (counter * -0x61c8864680b583ebL)).nextLong()

    /** Le pack numéro [opened] + 1 est-il doré ? */
    fun isGold(opened: Long) = (opened + 1) % GOLD_EVERY == 0L
    /** Packs à ouvrir avant le prochain doré, celui-ci compris (1 : le prochain est doré). */
    fun untilGold(opened: Long) = (GOLD_EVERY - (opened % GOLD_EVERY)).toInt()

    /** Table d'un emplacement, rares et épiques triplées dans un pack doré. */
    fun table(t: List<Pair<Rarity, Double>>, gold: Boolean) = if (!gold) t else t.map { (r, w) -> r to if (r >= Rarity.RARE) w * GOLD_BOOST else w }
    fun prismOdds(gold: Boolean) = if (gold) PRISM_ODDS * GOLD_BOOST else PRISM_ODDS
    fun duoOdds(gold: Boolean) = if (gold) DUO_ODDS * GOLD_BOOST else DUO_ODDS

    /** Probabilité, pour une carte de rareté [r], d'avoir l'effet [f] (la brillante n'est pas triplée en doré). */
    fun effectOdds(r: Rarity, f: Finish, gold: Boolean = false): Double {
        val (chroma, anim, shine) = EFFECTS.getValue(r)
        val k = if (gold) GOLD_BOOST else 1.0
        return when (f) {
            Finish.CHROMATIQUE -> chroma * k / 100
            Finish.ANIMEE -> anim * k / 100
            Finish.BRILLANTE -> shine / 100
            Finish.NORMALE -> 1 - (chroma * k + anim * k + shine) / 100
        }
    }

    /**
     * Probabilité exacte, par pack, d'au moins une carte qui vérifie tout ce qui est demandé : une rareté parmi
     * [rarities], un duo ([duo] vrai) ou non (faux) ou peu importe (null), un effet parmi [finishes] (null : peu importe).
     */
    fun odds(rarities: Set<Rarity> = Rarity.entries.toSet(), duo: Boolean? = null, finishes: Set<Finish>? = null, gold: Boolean = false): Double {
        fun card(r: Rarity): Double = if (r !in rarities) 0.0 else cardOdds(r, duo, finishes, gold)
        fun slot(t: List<Pair<Rarity, Double>>) = table(t, gold).let { tt -> tt.sumOf { (r, w) -> w * card(r) } / tt.sumOf { it.second } }
        val normal = 1 - (1 - slot(slot1)) * (1 - slot(slot2)) * (1 - slot(slot3)) * (1 - BONUS_ODDS * slot(slot4))
        val prism = 1 - PRISM.fold(1.0) { acc, r -> acc * (1 - card(r)) }
        return (1 - prismOdds(gold)) * normal + prismOdds(gold) * prism
    }

    /** Probabilité qu'une carte de rareté [r] soit (ou non) un duo et porte un effet parmi [finishes]. */
    fun cardOdds(r: Rarity, duo: Boolean? = null, finishes: Set<Finish>? = null, gold: Boolean = false): Double {
        val d = duoOdds(gold)
        val effect = finishes?.sumOf { effectOdds(r, it, gold) } ?: 1.0
        return effect * when (duo) { true -> d; false -> 1 - d; null -> 1.0 }
    }

    private fun expected(gold: Boolean): Double { val pr = prismOdds(gold); return (1 - pr) * (3 + BONUS_ODDS) + pr * PRISM.size }

    /** Part de la rareté [r] parmi toutes les cartes tirées (cartes attendues de cette rareté / cartes attendues). */
    fun share(r: Rarity, gold: Boolean = false): Double {
        fun w(t: List<Pair<Rarity, Double>>) = table(t, gold).let { tt -> tt.filter { it.first == r }.sumOf { it.second } / tt.sumOf { it.second } }
        val pr = prismOdds(gold)
        return ((1 - pr) * (w(slot1) + w(slot2) + w(slot3) + BONUS_ODDS * w(slot4)) + pr * PRISM.count { it == r }) / expected(gold)
    }

    /** Probabilité qu'une carte tirée, toutes raretés confondues, porte l'effet [f]. */
    fun effectShare(f: Finish, gold: Boolean = false) = Rarity.entries.sumOf { share(it, gold) * cardOdds(it, null, setOf(f), gold) }

    /** Probabilité exacte, par pack, d'au moins une carte de rareté [r] ou mieux. */
    fun perPack(r: Rarity, gold: Boolean = false) = odds(Rarity.entries.filter { it >= r }.toSet(), gold = gold)

    /** Taux moyen sur dix packs (neuf normaux, un doré). */
    fun average(r: Rarity) = (perPack(r) * (GOLD_EVERY - 1) + perPack(r, true)) / GOLD_EVERY

    fun duoPerPack(gold: Boolean = false) = odds(duo = true, gold = gold)
    fun chromaPerPack(gold: Boolean = false) = odds(finishes = setOf(Finish.CHROMATIQUE), gold = gold)

    fun open(seed: Long, gold: Boolean, booster: Booster): Result {
        val rng = SplittableRandom(seed)
        val isPrism = rng.nextDouble() < prismOdds(gold)
        val rarities = if (isPrism) PRISM.toMutableList()
            else listOf(slot1, slot2, slot3).map { pick(rng, table(it, gold)) }.toMutableList().also { if (rng.nextDouble() < BONUS_ODDS) it += pick(rng, table(slot4, gold)) }
        rarities.sort()
        val content = Catalog.ofBooster(booster)
        val pulls = rarities.map { r ->
            val duo = rng.nextDouble() < duoOdds(gold)
            val pool = content.filter { it.rarity == r && it.duo == duo }
            val card = pool[rng.nextInt(pool.size)]
            Pull(card, finish(rng, r, gold))
        }
        return Result(pulls, isPrism, gold)
    }

    private fun finish(rng: SplittableRandom, r: Rarity, gold: Boolean): Finish {
        var x = rng.nextDouble()
        for (f in listOf(Finish.CHROMATIQUE, Finish.ANIMEE, Finish.BRILLANTE)) { x -= effectOdds(r, f, gold); if (x < 0) return f }
        return Finish.NORMALE
    }

    private fun pick(rng: SplittableRandom, table: List<Pair<Rarity, Double>>): Rarity {
        var x = rng.nextDouble() * table.sumOf { it.second }
        for ((r, w) in table) { x -= w; if (x < 0) return r }
        return table.last().first
    }
}
