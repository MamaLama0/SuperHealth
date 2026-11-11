package app.superhealth.cards

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
internal enum class DamageType(val label: String, val mark: String, val tint: Long) {
    MELEE("Mêlée", "◆", 0xFFDFC4A3),
    RANGED("À distance", "➶", 0xFFB3DF93),
    ARCANIST("Arcaniste", "✦", 0xFF9BC7FF),
    DIVINE("Divine", "✧", 0xFFFFDF89),
}

@Serializable
internal enum class EquipmentSchool(val label: String, val damageType: DamageType) {
    WARRIOR("Guerrier", DamageType.MELEE),
    MAGE("Mage", DamageType.ARCANIST),
    PRIEST("Prêtre", DamageType.DIVINE),
    RANGER("Rôdeur", DamageType.RANGED),
    OCCULTIST("Occultiste", DamageType.ARCANIST),
    ARTIFICER("Artificier", DamageType.ARCANIST),
}

@Serializable
internal data class AffinityPair(val first: EquipmentSchool, val second: EquipmentSchool) {
    operator fun contains(card: EquipmentCard) = card.affinities.all { it == first.damageType || it == second.damageType }
    operator fun contains(school: EquipmentSchool) = school.damageType == first.damageType || school.damageType == second.damageType

    companion object {
        private val representatives = listOf(EquipmentSchool.WARRIOR, EquipmentSchool.RANGER, EquipmentSchool.MAGE, EquipmentSchool.PRIEST)
        private val pairs = representatives.flatMapIndexed { index, school ->
            representatives.drop(index + 1).map { AffinityPair(school, it) }
        }
        fun forPack(day: Long, slot: EquipmentSlot) = pairs[(day + slot.ordinal).mod(pairs.size)]
    }
}

internal data class EnemyAffinity(val weak: DamageType, val resist: DamageType, val ward: Boolean = false) {
    companion object {
        private val floors = listOf(
            EnemyAffinity(DamageType.DIVINE, DamageType.ARCANIST),
            EnemyAffinity(DamageType.RANGED, DamageType.MELEE),
            EnemyAffinity(DamageType.ARCANIST, DamageType.RANGED),
            EnemyAffinity(DamageType.MELEE, DamageType.RANGED),
            EnemyAffinity(DamageType.ARCANIST, DamageType.MELEE),
        )
        fun floor(floor: Int) = if (floor % 4 == 0)
            EnemyAffinity(DamageType.ARCANIST, DamageType.MELEE, ward = true)
        else floors[(floor - 1).mod(floors.size)]

        fun weekly(week: Long) = if (((week + 3) / 7) % 2 == 0L)
            EnemyAffinity(DamageType.ARCANIST, DamageType.RANGED, ward = true)
        else EnemyAffinity(DamageType.DIVINE, DamageType.ARCANIST, ward = true)
    }

    fun hit(base: Int, mix: Map<DamageType, Double>): Int {
        val types = mix.filterValues { it.isFinite() && it > 0 }.ifEmpty { mapOf(DamageType.MELEE to 1.0) }
        val scale = types.entries.sumOf { (type, count) ->
            count * when (type) { weak -> 1.65; resist -> .55; else -> 1.0 }
        } / types.values.sum()
        val shield = if (ward && weak !in types) .72 else 1.0
        return (base * scale * shield).roundToInt().coerceAtLeast(12)
    }
}
