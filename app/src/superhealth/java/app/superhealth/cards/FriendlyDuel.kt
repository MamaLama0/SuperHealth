package app.superhealth.cards

internal object FriendlyDuel {
    fun validBonuses(bonuses: Map<String, Int>): Boolean =
        bonuses.all { (stat, boost) -> Stat.of(stat)?.muscular == true && boost in 20..330 }

    // Centi-points keep small equipment bonuses visible in the comparison.
    fun points(value: Int, boost: Int): Int = (value * (10_000 + boost) + 50) / 100

    fun rounds(mine: AthleteStats, theirs: AthleteStats, myBonuses: Map<String, Int>, theirBonuses: Map<String, Int>?): List<Triple<Stat, Int, Int>> =
        Stat.entries.mapNotNull { stat ->
            val a = mine[stat] ?: return@mapNotNull null
            val b = theirs[stat] ?: return@mapNotNull null
            val local = if (theirBonuses == null) 0 else myBonuses[stat.code] ?: 0
            Triple(stat, points(a, local), points(b, theirBonuses?.get(stat.code) ?: 0))
        }

    data class Exchange(val stat: Stat, val myPower: Int, val theirPower: Int,
        val myDamage: Int, val theirDamage: Int, val myHealth: Int, val theirHealth: Int)

    data class Battle(val exchanges: List<Exchange>) {
        val myHealth get() = exchanges.lastOrNull()?.myHealth ?: HEALTH
        val theirHealth get() = exchanges.lastOrNull()?.theirHealth ?: HEALTH
        val winner get() = myHealth.compareTo(theirHealth)
    }

    const val HEALTH = 10_000 // Hundredths of a health point; small rarity bonuses still count.

    /** A six-exchange bout. Every shared stat has the same damage budget, independent of its scale.
     * Both attacks in an exchange resolve together, so playing on the left never gives an advantage.
     * There is no randomness or tap-speed bonus. Highest remaining health wins; a KO ends the bout.
     */
    fun battle(mine: AthleteStats, theirs: AthleteStats, myBonuses: Map<String, Int>, theirBonuses: Map<String, Int>?): Battle {
        val powers = rounds(mine, theirs, myBonuses, theirBonuses)
        if (powers.isEmpty()) return Battle(emptyList())
        var myHealth = HEALTH
        var theirHealth = HEALTH
        val exchanges = buildList {
            for ((index, pair) in powers.withIndex()) {
                val (stat, a, b) = pair
                val budget = 2 * (8_000 / powers.size + if (index < 8_000 % powers.size) 1 else 0)
                val share = (a.coerceAtLeast(1).toDouble() / (a.coerceAtLeast(1).toDouble() + b.coerceAtLeast(1))).coerceIn(.2, .8)
                val outgoing = kotlin.math.round(budget * share).toInt()
                val myDamage = outgoing.coerceAtMost(theirHealth)
                val theirDamage = (budget - outgoing).coerceAtMost(myHealth)
                myHealth -= theirDamage
                theirHealth -= myDamage
                add(Exchange(stat, a, b, myDamage, theirDamage, myHealth, theirHealth))
                if (myHealth == 0 || theirHealth == 0) break
            }
        }
        return Battle(exchanges)
    }
}
