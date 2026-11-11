package app.superhealth.cards

import app.superhealth.league.Progression
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.math.floor
import kotlin.math.pow

/** Game attributes only. No health measurements or raw tracking leave the device. */
@Serializable
data class CharacterStats(val strength: Int = 8, val dexterity: Int = 8, val constitution: Int = 8,
    val days: Int = 0, val model: Int = 1,
    val forceBonus: Int = 0, val dexBonus: Int = 0, val conBonus: Int = 0,
    val forceScore: Int? = null, val dexScore: Int? = null, val conScore: Int? = null) {
    operator fun get(attribute: CharacterAttribute) = when (attribute) {
        CharacterAttribute.FOR -> strength
        CharacterAttribute.DEX -> dexterity
        CharacterAttribute.CON -> constitution
    }
    fun score(attribute: CharacterAttribute): Int = (when (attribute) {
        CharacterAttribute.FOR -> forceScore
        CharacterAttribute.DEX -> dexScore
        CharacterAttribute.CON -> conScore
    } ?: ((this[attribute] - 8) * 100.0 / 12).roundToInt()).coerceIn(0, 100)

    val scoreLine get() = CharacterAttribute.entries.joinToString(" · ") { score(it).toString() }

    fun bonus(attribute: CharacterAttribute) = when (attribute) {
        CharacterAttribute.FOR -> forceBonus
        CharacterAttribute.DEX -> dexBonus
        CharacterAttribute.CON -> conBonus
    }
    fun valid() = forceBonus in -240..1050 && dexBonus in -240..1050 && conBonus in -240..2050 && model == 1 && days in 0..28 && listOf(strength, dexterity, constitution).all { it in 8..20 }
    fun equipped(bonuses: Map<CharacterAttribute, Int>) = copy(forceBonus = bonuses[CharacterAttribute.FOR] ?: 0,
        dexBonus = bonuses[CharacterAttribute.DEX] ?: 0, conBonus = bonuses[CharacterAttribute.CON] ?: 0)
    val total get() = strength + dexterity + constitution
    companion object {
        fun modifier(value: Int) = Math.floorDiv(value - 10, 2)
        data class Component(val label: String, val ratio: Double?, val weight: Double, val required: Boolean = false)
        data class Calculation(val attribute: CharacterAttribute, val parts: List<Component>) {
            val available get() = parts.filter { it.ratio != null }
            val weight get() = parts.filter { it.ratio != null || it.required }.sumOf { it.weight }
            val ratio get() = if (weight == 0.0) 0.0 else available.sumOf { it.ratio!! * it.weight } / weight
            val score get() = floor(100 * ratio.coerceIn(0.0, 1.0) + 1e-9).toInt()
            val value get() = 8 + score * 12 / 100
        }
        fun breakdown(progress: Progression.Result?, selected: Set<Progression.Track>, athlete: AthleteStats?): List<Calculation> {
            val today = progress?.summary?.throughDay ?: 0L
            val history = (progress?.recentValidated.orEmpty() + (today to progress?.validatedToday.orEmpty())).filterKeys { it in today - 27..today }
            fun ratio(track: Progression.Track): Double? {
                if (progress == null || track !in selected) return null
                val count = history.values.count { track in it }
                val target = if (track.weekly) (progress.summary.streaks.firstOrNull { it.track == track.name }?.target ?: 3).coerceIn(1, 7) * 4 else 28
                return (count.toDouble() / target).coerceIn(0.0, 1.0)
            }
            val gym = athlete?.training
            return listOf(
                Calculation(CharacterAttribute.FOR, listOf(Component("Records de force · PR et 1RM estimés", gym?.prScore, .85, required = true),
                    Component("Objectif de musculation", ratio(Progression.Track.STRENGTH), .15))),
                Calculation(CharacterAttribute.DEX, listOf(Component("Capacité cardio", athlete?.get(Stat.CAR)?.div(99.0)?.coerceIn(0.0, 1.0)?.pow(1.25), .65, required = true),
                    Component("Objectif de pas", ratio(Progression.Track.STEPS), .2),
                    Component("Esprit et mobilité", ratio(Progression.Track.MIND), .15))),
                Calculation(CharacterAttribute.CON, listOf(Component("Volume hebdomadaire moyen · 12 mois", gym?.volumeScore, .45, required = true),
                    Component("Régularité en salle · 12 mois", gym?.consistencyScore, .15, required = true),
                    Component("Objectif de sommeil", ratio(Progression.Track.SLEEP), .25),
                    Component("Objectif de nutrition", ratio(Progression.Track.FOOD), .15))))
        }
        fun from(progress: Progression.Result, selected: Set<Progression.Track>, athlete: AthleteStats? = null): CharacterStats {
            val parts = breakdown(progress, selected, athlete)
            val today = progress.summary.throughDay
            val history = (progress.recentValidated + (today to progress.validatedToday)).filterKeys { it in today - 27..today }
            return CharacterStats(parts[0].value, parts[1].value, parts[2].value,
                history.values.count { it.intersect(selected).isNotEmpty() },
                forceScore = parts[0].score, dexScore = parts[1].score, conScore = parts[2].score)
        }
        /** Explicitly labelled training preview for contacts on an older version. */
        fun legacy(stats: AthleteStats): CharacterStats {
            fun value(values: List<Int>) = if (values.isEmpty()) 8 else (8 + 12 * values.average() / 100).roundToInt().coerceIn(8, 20)
            return CharacterStats(value(Stat.families.mapNotNull { stats[it] }), value(listOfNotNull(stats[Stat.CAR])),
                value(listOfNotNull(stats[Stat.REG])))
        }
    }
}

enum class CharacterAttribute(val label: String, val attack: String) {
    FOR("Force", "Frappe puissante"), DEX("Dextérité", "Frappe précise"), CON("Constitution", "Second souffle")
}

@Serializable
enum class CombatStance(val title: String, val detail: String) {
    ASSAULT("Assaut", "Brise la feinte"), FEINT("Feinte", "Contourne la garde"), GUARD("Garde", "Contient l’assaut");
    fun beats(other: CombatStance) = (ordinal + 1) % 3 == other.ordinal
}

/** Mirrored by combat.mjs. Integer-only, deterministic and symmetrical: no tap speed advantage. */
internal object CharacterCombat {
    const val HEALTH = 10_000
    data class Exchange(val stat: CharacterAttribute, val myDamage: Int, val theirDamage: Int, val myHealth: Int, val theirHealth: Int)
    data class Battle(val exchanges: List<Exchange>) {
        val myHealth get() = exchanges.lastOrNull()?.myHealth ?: HEALTH
        val theirHealth get() = exchanges.lastOrNull()?.theirHealth ?: HEALTH
        val winner get() = myHealth.compareTo(theirHealth)
    }
    fun battle(mine: CharacterStats, theirs: CharacterStats): Battle {
        var a = HEALTH; var b = HEALTH
        val rounds = buildList {
            for (stat in CharacterAttribute.entries) {
                if (a == 0 || b == 0) break
                val myPower = mine[stat].toLong() * (10_000 + mine.bonus(stat))
                val theirPower = theirs[stat].toLong() * (10_000 + theirs.bonus(stat))
                val total = myPower + theirPower
                val outgoing = (5200L * myPower / total).toInt().coerceAtMost(b)
                val incoming = (5200L * theirPower / total).toInt().coerceAtMost(a)
                a -= incoming; b -= outgoing
                add(Exchange(stat, outgoing, incoming, a, b))
            }
        }
        return Battle(rounds)
    }
}
