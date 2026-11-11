package app.superhealth.cards

import android.content.Context
import app.superhealth.SuperPrefs
import app.superhealth.league.ProgressRepository
import app.superhealth.league.Progression
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.LocalDate

internal object ArsenalGame {
    class Upgrade : Exception("Mets à jour le serveur pour les nouveaux boosters et combats.")
    class Unsupported : Exception("Le relais doit être mis à jour pour le marché et le boss de groupe.")

    fun raidWeek(today: Long): Long = Progression.week(today)

    @Serializable data class Piece(val key: String, val id: String, val finish: EquipmentFinish, val base: Int, val multiplier: Int, val level: Int = 1, val balanceVersion: Int = 1) {
        fun local(serial: Long) = EquipmentPiece(serial, id, finish, base, multiplier, level, key, balanceVersion)
    }
    @Serializable data class Opening(val day: Long, val slot: EquipmentSlot, val gold: Boolean, val pieces: List<Piece>, val schools: List<EquipmentSchool> = emptyList())
    @Serializable data class Listing(val key: String, val piece: Piece, val price: Int, val bid: Int = 0, val end: Long,
        val recipient: String? = null, val seller: String? = null, val fixed: Boolean = false, val wanted: String? = null, val mine: Boolean = false, val leading: Boolean = false, val incoming: Boolean = false)
    @Serializable data class Raid(val code: String, val members: Int, val hp: Int, val max: Int, val won: Boolean,
        val qualifiers: Int, val required: Int, val mine: Int, val hit: Boolean, val week: Long? = null,
        val canInvite: Boolean = false, val invited: List<String> = emptyList(), val goldReward: Int = 300, val iceReward: Int = 2)
    @Serializable data class RaidInvite(val code: String, val from: String)
    @Serializable data class View(val revision: Long = 0, val rules: Int = 1, val gold: Int, val opened: Int, val owned: List<Piece>, val opening: Opening? = null,
        val listings: List<Listing> = emptyList(), val raid: Raid? = null, val dungeon: Dungeon? = null,
        val packCredits: List<String> = emptyList(), val ice: Int = 0, val raidInvites: List<RaidInvite> = emptyList(), val duels: List<Duel> = emptyList(), val boosters: Int = 0, val combat: Int = 0, val arena: Int = 0, val raidRules: Int = 0, val collectionVersion: Int = 0, val packPrice: Int = PACK_PRICE)
    @Serializable data class Duel(val id: String, val opponent: String, val incoming: Boolean, val status: String,
        val expires: Long, val mine: CharacterStats? = null, val theirs: CharacterStats? = null,
        val stance: CombatStance? = null, val otherStance: CombatStance? = null)

    suspend fun character(ctx: Context): CharacterStats {
        EquipmentStore.load(ctx)
        val stats = CharacterStats.from(ProgressRepository.compute(ctx), ProgressRepository.selectedTracks(SuperPrefs.settings.value), CardStore.snapshot.value?.now ?: CardStore.state.value.lastStats)
        return stats.equipped(EquipmentStore.characterBonuses(EquipmentStore.state.value, EquipmentStore.cards.value))
    }
    private fun JsonObjectBuilder.addCharacter(stats: CharacterStats) {
        put("character", buildJsonObject {
            put("strength", stats.strength); put("dexterity", stats.dexterity)
            put("constitution", stats.constitution); put("days", stats.days); put("model", stats.model)
            stats.forceScore?.let { put("forceScore", it) }
            stats.dexScore?.let { put("dexScore", it) }
            stats.conScore?.let { put("conScore", it) }
        })
        val state = EquipmentStore.state.value
        put("loadout", buildJsonArray { EquipmentSlot.entries.forEach { slot -> state.equipped(slot)?.serverKey?.let { add(JsonPrimitive(it)) } } })
    }

    suspend fun duel(ctx: Context, op: String, target: String, request: String): View {
        if ((data.value ?: act(ctx, "state")).arena < 1) throw Upgrade()
        val stats = character(ctx)
        return act(ctx, "v3-duel-$op") {
            put(if (op == "invite") "to" else "duel", target)
            put("request", request)
            addCharacter(stats)
        }
    }

    data class Day(val day: Long, val rank: Int)
    data class Quests(val today: Long, val gate: PackProgress, val days: List<Day>, val sportDays: Set<Long> = emptySet(),
        val attackDays: Set<Long> = days.map { it.day }.toSet(), val character: CharacterStats = CharacterStats(), val fullDays: Set<Long> = emptySet()) {
        val canAttack get() = today in attackDays
        val fullToday get() = today in fullDays
        val rank get() = days.firstOrNull { it.day == today }?.rank ?: 0
        val weekCount get() = days.count { it.day >= Progression.week(today) }
        val activity get() = gate.done.any { it in activities }
    }

    const val COLLECTION_VERSION = 1
    const val PACK_PRICE = 75
    const val WEEKLY_GOLD = 25
    val weeklyGoals = listOf(2, 4, 6)

    private val activities = setOf(Progression.Track.STRENGTH, Progression.Track.RUN, Progression.Track.HIKE, Progression.Track.MIND)
    private val data = MutableStateFlow<View?>(null)
    val remote = data.asStateFlow()

    internal fun quests(progress: Progression.Result, selected: Set<Progression.Track>): Quests {
        val today = progress.summary.throughDay
        val history = progress.recentValidated + (today to progress.validatedToday)
        val fullDays = mutableSetOf<Long>()
        val days = history.entries.sortedBy { it.key }.mapNotNull { (day, done) ->
            val gate = PackProgress.from(day, selected, done)
            val full = gate.targets.isNotEmpty() && gate.completed == gate.targets.size
            if (full) fullDays += day
            val rank = when {
                !gate.ready -> 0
                full && done.size >= 3 && done.any { it in activities } -> 3
                full && done.size >= 2 -> 2
                else -> 1
            }
            Day(day, rank).takeIf { rank > 0 }
        }
        val attackDays = history.filterValues { done -> done.any { it in selected } }.keys
        return Quests(today, PackProgress.from(today, selected, progress.validatedToday), days, progress.sportDays, attackDays, CharacterStats.from(progress, selected, CardStore.snapshot.value?.now ?: CardStore.state.value.lastStats), fullDays)
    }

    suspend fun quests(ctx: Context): Quests {
        CardStore.load(ctx)
        return quests(ProgressRepository.compute(ctx), ProgressRepository.selectedTracks(SuperPrefs.settings.value))
    }

    private fun JsonObjectBuilder.addDays(quests: Quests) {
        put("today", quests.today)
        put("attackDays", JsonArray(quests.attackDays.sorted().map(::JsonPrimitive)))
        put("raidFullDays", JsonArray(quests.fullDays.sorted().map(::JsonPrimitive)))
        put("sportDays", JsonArray(quests.sportDays.sorted().map(::JsonPrimitive)))
        put("days", buildJsonArray { quests.days.forEach { day -> add(buildJsonObject { put("day", day.day); put("rank", day.rank) }) } })
    }

    suspend fun claimPack(ctx: Context, slot: EquipmentSlot, key: String, day: Long): View =
        request(ctx, "v3-open") {
            put("slot", slot.name)
            put("booster", if (slot == EquipmentSlot.WEAPON) "WEAPON" else "ARMOR")
            put("rewardKey", key)
            put("today", day)
            put("days", JsonArray(emptyList()))
        }

    suspend fun purchasePack(ctx: Context, request: String) {
        if (Economy.available() && !EquipmentStore.unlimitedPacks) {
            act(ctx, "purchase-pack") { put("request", request) }
        } else {
            EquipmentStore.purchasePack(ctx, request)
        }
    }

    suspend fun freeze(ctx: Context, quests: Quests) {
        if (!EquipmentStore.unlimitedPacks && Economy.available() && (data.value?.rules ?: 0) >= 3) act(ctx, "v3-freeze") { addDays(quests) }
        else EquipmentStore.freeze(ctx, quests)
    }

    suspend fun sync(ctx: Context, quests: Quests): View =
        act(ctx, "v2-sync") { addDays(quests) }

    suspend fun advance(ctx: Context, quests: Quests, attack: Boolean = false) {
        if (!EquipmentStore.unlimitedPacks && Economy.available()) try {
            if (!attack) sync(ctx, quests) else {
                val combat = (data.value ?: sync(ctx, quests)).combat
                if (combat < 2) throw Upgrade()
                val power = (EquipmentStore.bonuses(EquipmentStore.state.value, EquipmentStore.cards.value).values.sum() / 100).coerceIn(0, 5)
                act(ctx, "v2-solo") { addDays(quests); put("power", power); put("combatVersion", minOf(combat, 3)); addCharacter(quests.character); addDamageMix() }
            }
            return
        } catch (_: Unsupported) {
            // An older relay still supports the original social features and a local dungeon.
        }
        EquipmentStore.dungeon(ctx, quests, attack)
    }

    private fun JsonObjectBuilder.addDamageMix() {
        val mix = EquipmentStore.damageMix(EquipmentStore.state.value, EquipmentStore.cards.value)
        put("damageMix", buildJsonObject { mix.forEach { (type, weight) -> put(type.name, weight) } })
        val primary = EquipmentStore.primarySchools(EquipmentStore.state.value, EquipmentStore.cards.value)
        put("schools", buildJsonObject { primary.forEach { (school, count) -> put(school.name, count) } })
    }

    suspend fun hit(ctx: Context, quests: Quests): View {
        if ((data.value ?: sync(ctx, quests)).raidRules < 2) throw Upgrade()
        return act(ctx, "v2-raid-hit") { addDays(quests); addDamageMix(); addCharacter(quests.character) }
    }

    suspend fun act(ctx: Context, op: String, fields: JsonObjectBuilder.() -> Unit = {}): View =
        request(ctx, op, fields).also { EquipmentStore.syncRemote(ctx, it) }

    private suspend fun request(ctx: Context, op: String, fields: JsonObjectBuilder.() -> Unit): View {
        val body = buildJsonObject { put("op", "arsenal-$op"); put("rules", 2); put("catalogueVersion", 6); put("collectionVersion", COLLECTION_VERSION); fields() }
        return Economy.arsenal(ctx, body).also { next ->
            data.update { current -> next.takeIf { it.revision >= (current?.revision ?: 0) } ?: current }
        }
    }

    @Serializable data class Dungeon(
        val floor: Int = 1,
        val health: Int = 100,
        val monster: Int = 0,
        val lastAttack: Long? = null,
        val settled: Long? = null,
        val log: String? = null,
        val frozenDay: Long? = null,
        val dealt: Int = 0, val received: Int = 0, val defeatedFloor: Int? = null,
    ) {
        val maximum get() = if (floor % 4 == 0) 120 else if (floor % 7 == 0) 76 else 36 + floor % 3 * 4
        val hp get() = if (monster > 0) monster else maximum
        val elite get() = floor % 4 == 0
    }

    fun iceDrop(floor: Int) = if (floor % 3 == 0) 1 else 0

    fun settle(dungeon: Dungeon, quests: Quests): Dungeon {
        val today = quests.today
        val last = dungeon.settled ?: return dungeon.copy(settled = today-1)
        if (today - 1 <= last || quests.gate.targets.isEmpty()) return dungeon
        val qualified = quests.attackDays + quests.sportDays + setOfNotNull(dungeon.frozenDay)
        val missed = ((last + 1).coerceAtLeast(today - 34) until today).count { it !in qualified }
        val remaining = (dungeon.health - missed * (if (dungeon.elite) 32 else 25)).coerceAtLeast(0)
        return dungeon.copy(settled = today-1, health = remaining,
            log = if (missed > 0 && remaining != dungeon.health) "Le monstre t’a attaqué pendant ton absence." else dungeon.log)
    }

    fun attack(dungeon: Dungeon, quests: Quests, bonuses: Int, schools: Map<DamageType, Double> = emptyMap(), completeSet: Boolean = false): Pair<Dungeon, Int> {
        val current = settle(dungeon, quests)
        val today = quests.today
        if (!quests.canAttack || (current.lastAttack ?: Long.MIN_VALUE) >= today) return current to 0
        if (current.health == 0) return current.copy(health = 100, lastAttack = today, dealt = 0, received = 0, defeatedFloor = null, log = "Tu as récupéré tes points de vie.") to 0
        val attributePower = quests.character.dungeonAttack
        val damage = EnemyAffinity.floor(current.floor).hit(48 + attributePower + (quests.rank-1).coerceAtLeast(0) * 9 + (bonuses / 100).coerceIn(0, 5), schools) +
            if (completeSet) 1 else 0
        val remaining = (current.hp - damage).coerceAtLeast(0)
        val won = remaining == 0
        val received = if (won || current.frozenDay == today) 0 else ((if (current.elite) 18 else 12) - quests.character.dungeonDefense).coerceAtLeast(1)
        return current.copy(floor = current.floor + if (won) 1 else 0, monster = remaining,
            health = (current.health - received).coerceAtLeast(0), dealt = damage.coerceAtMost(current.hp), received = received,
            defeatedFloor = current.floor.takeIf { won },
            lastAttack = today, log = if (won) "Monstre vaincu · +${if (current.elite) 35 else 15} or" +
                (if (iceDrop(current.floor) > 0) " · +1 glaçon" else "")
                else "Attaque réussie · −$damage PV") to
            if (won) (if (current.elite) 35 else 15) else 0
    }
}
