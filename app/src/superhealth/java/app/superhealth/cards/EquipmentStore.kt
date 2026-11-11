package app.superhealth.cards

import android.content.Context
import android.util.AtomicFile
import app.superhealth.SuperPrefs
import app.superhealth.league.ProgressRepository
import app.superhealth.league.Progression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.random.Random

enum class EquipmentSlot(val key: String, val title: String, val stat: Stat, val tint: Long) {
    WEAPON("weapon", "Armes et bras", Stat.BRA, 0xFFEF8DA9),
    CHEST("chest", "Torse", Stat.PEC, 0xFFD9A3E7),
    GUARD("guard", "Protections", Stat.DOS, 0xFF84DAE7),
    LEGS("legs", "Jambes", Stat.JAM, 0xFFE6B38F)
}

val EquipmentSlot.attribute: CharacterAttribute get() = when (this) {
    EquipmentSlot.WEAPON -> CharacterAttribute.FOR
    EquipmentSlot.LEGS -> CharacterAttribute.DEX
    else -> CharacterAttribute.CON
}
val Stat.characterAttribute: CharacterAttribute get() = when (this) {
    Stat.BRA -> CharacterAttribute.FOR
    Stat.JAM, Stat.CAR -> CharacterAttribute.DEX
    else -> CharacterAttribute.CON
}

// Booster families are separate from the four equipment slots. Historical saves keep their slots.
internal object EquipmentBoosters {
    val slots = listOf(EquipmentSlot.WEAPON, EquipmentSlot.CHEST)
    fun contains(booster: EquipmentSlot, slot: EquipmentSlot) =
        if (booster == EquipmentSlot.WEAPON) slot == EquipmentSlot.WEAPON else slot != EquipmentSlot.WEAPON
}

@Serializable
internal enum class PackEdition(val key: String, val title: String) {
    STANDARD("", "Standard"), SOLAR("or-solaire", "Or"), NOIR("or-noir", "Or noir"), ASTRAL("or-astral", "Or astral"),
    PRISM("prisme", "Prisme"), CURSED("maudit", "Sceau maudit");

    val gold get() = this == SOLAR || this == NOIR || this == ASTRAL

    companion object {
        fun forDraw(gold: Boolean): PackEdition = if (gold) SOLAR else STANDARD
    }
}

internal fun packSkin(day: Long, slot: EquipmentSlot) =
    Random(day xor (slot.ordinal.toLong() shl 21)).nextInt(3)

@Serializable
internal data class EquipmentCard(
    val id: String,
    val title: String,
    val type: String,
    val material: String,
    val tier: Int,
    val min: Int,
    val max: Int,
    val slot: EquipmentSlot,
    val penalty: Stat? = null,
    val penaltyPoints: Int = 0,
    val school: EquipmentSchool = EquipmentSchool.WARRIOR,
    val damageTypes: List<DamageType> = emptyList(),
    val setId: String? = null,
    val setName: String? = null,
    val available: Boolean = true,
) {
    val attribute get() = slot.attribute
    val penaltyAttribute get() = penalty?.characterAttribute
    val affinities get() = damageTypes.distinct().ifEmpty { listOf(school.damageType) }
}

internal enum class EquipmentRarity(val title: String, val tint: Long, val weight: Double) {
    COMMON("Commune", 0xFFBCC3CA, .515),
    UNCOMMON("Peu commune", 0xFF88D6AB, .30),
    RARE("Rare", 0xFF8FCAFF, .145),
    EPIC("Épique", 0xFFE5A4FF, .035),
    LEGENDARY("Légendaire", 0xFFF9D281, .005);

    fun weight(gold: Boolean) = weight * if (gold && this >= RARE) 3 else 1
}

@Serializable
internal enum class EquipmentFinish(val key: String, val title: String, val multiplier: Int, val chance: Double) {
    NORMAL("normal", "Normale", 100, 0.0),
    SHINY("shiny", "Brillante", 108, .0080),
    ANIMATED("animated", "Animée", 114, .0022),
    CHROMA("chroma", "Chromatique", 125, .0011),
    BLESSED("blessed", "Bénédiction", 134, .0006),
    BUG("bug", "Bug", 100, .0001);

    fun chance(gold: Boolean) = chance * if (gold && this != BUG) 3 else 1

    fun rollMultiplier(random: Random): Int = if (this != BUG) multiplier else when (random.nextDouble()) {
        in 0.0..<.96 -> random.nextInt(45, 91)
        in .96..<.9975 -> random.nextInt(90, 146)
        else -> random.nextInt(230, 341)
    }
}

@Serializable
internal data class EquipmentPiece(
    val serial: Long,
    val id: String,
    val finish: EquipmentFinish,
    val base: Int,
    val multiplier: Int,
    val level: Int = 1,
    val serverKey: String? = null,
    val balanceVersion: Int = 1,
) {
    // Keep the percentile of legacy rolls; the stored roll/version remain immutable.
    fun baseBonus(card: EquipmentCard): Int {
        if (balanceVersion >= 2) return base.coerceIn(card.min, card.max)
        val old = listOf(20..40, 32..62, 50..90, 78..140, 105..185)[card.tier]
        val percentile = (base.coerceIn(old) - old.first).toDouble() / (old.last - old.first)
        return (card.min + percentile * (card.max - card.min)).roundToInt()
    }

    fun boost(card: EquipmentCard): Int =
        (baseBonus(card) * (multiplier / 100.0) * (if (card.penalty != null) 1.4 else 1.0) * (1 + .1 * (level - 1)))
            .roundToInt().coerceAtMost(if (finish == EquipmentFinish.BUG) 1000 else 550)
}

internal data class PackProgress(
    val day: Long,
    val targets: Set<Progression.Track>,
    val done: Set<Progression.Track>,
    val required: Int,
) {
    val completed get() = done.count { it in targets }
    val ready get() = required > 0 && completed >= required
    fun canOpen(lastOpenedDay: Long?) = ready && (lastOpenedDay == null || lastOpenedDay < day)

    companion object {
        fun from(day: Long, selected: Set<Progression.Track>, done: Set<Progression.Track>): PackProgress {
            val daily = selected.filterNot { it.weekly }.toSet()
            val required = when {
                daily.isNotEmpty() -> daily.size / 2 + 1
                selected.isNotEmpty() -> 1
                else -> 0
            }
            return PackProgress(day, daily.ifEmpty { selected }, done, required)
        }
    }
}

internal object EquipmentStore {
    private val build = nodomain.freeyourgadget.gadgetbridge.BuildConfig.ARSENAL_UNLIMITED
    val unlimitedPacks = build && nodomain.freeyourgadget.gadgetbridge.BuildConfig.APPLICATION_ID == "app.superhealth.arsenalqa"

    @Serializable
    data class Opening(val slot: EquipmentSlot, val pieces: List<EquipmentPiece>, val gold: Boolean,
        val skin: Int = 0, val edition: PackEdition = if (gold) PackEdition.SOLAR else PackEdition.STANDARD,
        val affinities: AffinityPair? = null)

    @Serializable
    data class State(
        val day: Long? = null,
        val opened: Int = 0,
        val nextSerial: Long = 1,
        val owned: List<EquipmentPiece> = emptyList(),
        val equipped: Map<EquipmentSlot, Long> = emptyMap(),
        val pending: Opening? = null,
        val serverOpened: Int? = null,
        val remotePendingDay: Long? = null,
        val rewardPending: String? = null,
        val freePacks: Int = 0,
        val ice: Int = 0,
        val revision: Long = 0,
        val gold: Int = 0,
        val goldRanks: Map<Long, Int> = emptyMap(),
        val dungeon: ArsenalGame.Dungeon = ArsenalGame.Dungeon(),
    ) {
        val goldNext get() = (serverOpened ?: opened) % 10 == 9
        fun equipped(slot: EquipmentSlot) = owned.firstOrNull { it.serial == equipped[slot] }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val changes = MutableStateFlow(State())
    val state = changes.asStateFlow()
    private val catalog = MutableStateFlow<Map<String, EquipmentCard>>(emptyMap())
    val cards = catalog.asStateFlow()
    private val lock = Mutex()
    private var loaded = false

    const val SAVE_FILE = "arsenal.json"
    private fun file(ctx: Context) = AtomicFile(File(ctx.filesDir, SAVE_FILE))

    private fun loadLocked(ctx: Context) {
        if (loaded) return
        catalog.value = ctx.assets.open("arsenal/loot.json").bufferedReader().use {
            json.decodeFromString<List<EquipmentCard>>(it.readText()).associateBy(EquipmentCard::id)
        }
        val save = file(ctx)
        val saved = try { json.decodeFromString<State>(save.readFully().decodeToString()) }
            catch (_: java.io.FileNotFoundException) { State() }
        if (saved.pending == null) changes.value = saved else persist(ctx, saved.copy(pending = null))
        loaded = true
    }

    suspend fun load(ctx: Context) = withContext(Dispatchers.IO) { lock.withLock { loadLocked(ctx) } }

    suspend fun reload(ctx: Context) = withContext(Dispatchers.IO) {
        lock.withLock { loaded = false; loadLocked(ctx) }
    }

    private fun persist(ctx: Context, state: State) {
        val save = file(ctx)
        val output = save.startWrite()
        try {
            output.write(json.encodeToString(state).encodeToByteArray())
            save.finishWrite(output)
        } catch (e: Exception) {
            save.failWrite(output)
            throw e
        }
        changes.value = state
    }

    suspend fun open(ctx: Context, slot: EquipmentSlot): Opening? = withContext(Dispatchers.IO) {
        if (unlimitedPacks) return@withContext lock.withLock {
            loadLocked(ctx)
            val old = changes.value
            val day = LocalDate.now().toEpochDay() + old.opened
            openLocal(ctx, old.copy(opened = old.opened + 1), slot, day, old.opened % 10 == 9)
        }
        val quests = ArsenalGame.quests(ctx)
        lock.withLock {
            loadLocked(ctx)
            val old = changes.value
            val day = quests.today
            if (day != LocalDate.now().toEpochDay()) return@withLock null
            val daily = quests.gate.canOpen(old.day)
            val reward = old.rewardPending ?: if (!daily && old.freePacks == 0) ArsenalGame.remote.value?.packCredits?.firstOrNull() else null
            val retryDaily = old.remotePendingDay == day
            if (!daily && !retryDaily && reward == null && old.freePacks == 0) return@withLock null
            if (reward != null || retryDaily || daily && Economy.available() && ArsenalGame.canOpenOnline(ctx)) {
                val requestState = if (reward != null) old.copy(rewardPending = reward) else old.copy(remotePendingDay = day)
                if (requestState != old) persist(ctx, requestState)
                val remote = if (reward == null) ArsenalGame.open(ctx, slot, quests) else ArsenalGame.claimPack(ctx, slot, reward, quests)
                val pack = remote.opening
                if (pack == null) {
                    if (reward != null) persist(ctx, mergeRemote(requestState, remote).copy(rewardPending = null))
                    return@withLock null
                }
                val merged = mergeRemote(requestState, remote)
                var serial = merged.nextSerial
                val pieces = pack.pieces.map { piece ->
                    merged.owned.firstOrNull { it.serverKey == piece.key } ?: piece.local(serial++)
                }.sortedBy { catalog.value.getValue(it.id).tier }
                val pair = pack.schools.takeIf { it.size == 2 }?.let { AffinityPair(it[0], it[1]) }
                val variant = day + if (reward != null) serial else 0
                persist(ctx, merged.copy(day = if (reward == null) day else merged.day,
                    remotePendingDay = if (reward == null) null else merged.remotePendingDay,
                    rewardPending = if (reward != null) null else merged.rewardPending, nextSerial = serial))
                Opening(pack.slot, pieces, pack.gold, packSkin(variant, pack.slot), PackEdition.forDraw(pack.gold), pair)
            } else if (daily) {
                openLocal(ctx, old.copy(day = day, opened = old.opened + 1), slot, day, old.goldNext)
            } else {
                openLocal(ctx, old.copy(freePacks = old.freePacks - 1), slot, day + old.nextSerial, false)
            }
        }
    }

    private fun openLocal(ctx: Context, state: State, slot: EquipmentSlot, variant: Long, gold: Boolean): Opening {
        val pair = AffinityPair.forPack(variant, slot)
        val pool = catalog.value.values.filter { EquipmentBoosters.contains(slot, it.slot) }
        val pieces = draw(pool, state.nextSerial, gold, affinities = pair).sortedBy { catalog.value.getValue(it.id).tier }
        persist(ctx, state.copy(nextSerial = state.nextSerial + pieces.size, owned = state.owned + pieces))
        return Opening(slot, pieces, gold, packSkin(variant, slot), PackEdition.forDraw(gold), pair)
    }

    private fun mergeRemote(old: State, remote: ArsenalGame.View): State {
        if (remote.revision < old.revision) return old
        val existing = old.owned.mapNotNull { piece -> piece.serverKey?.let { it to piece.serial } }.toMap()
        var serial = old.nextSerial
        val owned = old.owned.filter { it.serverKey == null } + remote.owned.map { it.local(existing[it.key] ?: serial++) }
        val ownedIds = owned.map { it.serial }.toSet()
        return old.copy(owned = owned, nextSerial = serial, equipped = old.equipped.filterValues { it in ownedIds },
            serverOpened = remote.opened, revision = remote.revision, dungeon = remote.dungeon ?: old.dungeon)
    }

    suspend fun syncRemote(ctx: Context, remote: ArsenalGame.View) = change(ctx) { mergeRemote(it, remote) }

    suspend fun dungeon(ctx: Context, quests: ArsenalGame.Quests, attack: Boolean = false, stance: CombatStance = CombatStance.ASSAULT) = change(ctx) { old ->
        val state = ArsenalGame.settle(old.dungeon, quests)
        val (next, gold) = if (attack) ArsenalGame.attack(state, quests, bonuses(old, catalog.value).values.sum(), damageMix(old, catalog.value), stance) else state to 0
        val recent = old.goldRanks.filterKeys { it >= quests.today - 35 }.toMutableMap()
        val rewards = intArrayOf(0, 25, 45, 75)
        val previousRank = recent[quests.today] ?: 0
        val rank = maxOf(previousRank, quests.rank)
        if (rank > previousRank) recent[quests.today] = rank
        val drops = if (next.floor > state.floor) ArsenalGame.drops(state.floor) else ArsenalGame.Drops(0, 0)
        old.copy(dungeon = next, gold = old.gold + gold + rewards[rank] - rewards[previousRank], goldRanks = recent,
            freePacks = old.freePacks + drops.packs, ice = old.ice + drops.ice)
    }

    suspend fun freeze(ctx: Context, quests: ArsenalGame.Quests) = change(ctx) { old ->
        val current = ArsenalGame.settle(old.dungeon, quests)
        if (old.ice <= 0 || current.frozenDay == quests.today || current.health == 0) old
        else old.copy(ice = old.ice - 1, dungeon = current.copy(frozenDay = quests.today))
    }

    suspend fun equip(ctx: Context, slot: EquipmentSlot, serial: Long?) = change(ctx) { old ->
        if (serial == null) old.copy(equipped = old.equipped - slot)
        else if (old.owned.any { it.serial == serial && catalog.value[it.id]?.slot == slot })
            old.copy(equipped = old.equipped + (slot to serial))
        else old
    }

    suspend fun fuse(ctx: Context, serial: Long) {
        load(ctx)
        val old = state.value
        val piece = old.owned.firstOrNull { it.serial == serial } ?: return
        val spare = spare(old, piece) ?: return
        if (piece.serverKey != null && spare.serverKey != null) {
            ArsenalGame.act(ctx, "fuse") { put("key", piece.serverKey); put("spare", spare.serverKey) }
            return
        }
        change(ctx) { current ->
            val live = current.owned.firstOrNull { it.serial == serial } ?: return@change current
            val copy = spare(current, live) ?: return@change current
            current.copy(owned = current.owned.filterNot { it.serial == copy.serial }
                .map { if (it.serial == serial) it.copy(level = it.level + 1) else it })
        }
    }

    fun spare(state: State, piece: EquipmentPiece) = state.owned.firstOrNull {
        (piece.serverKey == null) == (it.serverKey == null) && piece.level < 5 && it.level == 1 &&
            it.serial != piece.serial && it.id == piece.id && it.finish == piece.finish &&
            it.serial !in state.equipped.values && state.pending?.pieces.orEmpty().none { pending -> pending.serial == it.serial }
    }

    fun damageMix(state: State, cards: Map<String, EquipmentCard>): Map<DamageType, Double> = buildMap {
        EquipmentSlot.entries.forEach { slot ->
            val card = state.equipped(slot)?.let { cards[it.id] }?.takeIf { it.slot == slot } ?: return@forEach
            val weight = 1.0 / card.affinities.size
            card.affinities.forEach { type -> put(type, (get(type) ?: 0.0) + weight) }
        }
    }

    // Compatibility payload for relays predating explicit multi-type damage.
    fun primarySchools(state: State, cards: Map<String, EquipmentCard>): Map<EquipmentSchool, Int> =
        EquipmentSlot.entries.mapNotNull { slot -> state.equipped(slot)?.let { cards[it.id] }?.takeIf { it.slot == slot } }
            .map { card -> EquipmentSchool.entries.first { it.damageType == card.affinities.first() } }.groupingBy { it }.eachCount()

    fun bonuses(state: State, cards: Map<String, EquipmentCard>): Map<Stat, Int> = buildMap {
        EquipmentSlot.entries.forEach { slot ->
            val piece = state.equipped(slot) ?: return@forEach
            val card = cards[piece.id] ?: return@forEach
            put(slot.stat, (get(slot.stat) ?: 0) + piece.boost(card))
            card.penalty?.let { put(it, (get(it) ?: 0) - card.penaltyPoints) }
        }
    }

    fun characterBonuses(state: State, cards: Map<String, EquipmentCard>): Map<CharacterAttribute, Int> = buildMap {
        EquipmentSlot.entries.forEach { slot ->
            val piece = state.equipped(slot) ?: return@forEach
            val card = cards[piece.id]?.takeIf { it.slot == slot } ?: return@forEach
            put(card.attribute, (get(card.attribute) ?: 0) + piece.boost(card))
            card.penaltyAttribute?.let { put(it, (get(it) ?: 0) - card.penaltyPoints) }
        }
    }

    fun duelBonuses(state: State, cards: Map<String, EquipmentCard>): Map<String, Int> = buildMap {
        EquipmentSlot.entries.forEach { slot ->
            val piece = state.equipped(slot) ?: return@forEach
            val card = cards[piece.id]?.takeIf { it.slot == slot } ?: return@forEach
            put(slot.stat.code, piece.baseBonus(card))
        }
    }

    private suspend fun change(ctx: Context, transform: (State) -> State) = withContext(Dispatchers.IO) {
        lock.withLock {
            loadLocked(ctx)
            val old = changes.value
            val next = transform(old)
            if (next != old) persist(ctx, next)
        }
    }

    internal fun draw(pool: List<EquipmentCard>, next: Long, gold: Boolean, random: Random = Random.Default,
        affinities: AffinityPair? = null): List<EquipmentPiece> {
        val remaining = pool.filter { it.available && (affinities == null || it in affinities) }.shuffled(random).toMutableList()
        val size = if (gold || random.nextInt(4) == 0) 5 else 4
        return List(size) { i ->
            val target = if (i == 0) affinities?.first else if (i == 1) affinities?.second else null
            val candidates = remaining.filter { target == null || target.damageType in it.affinities }
            val options = EquipmentRarity.entries.filter { rarity -> candidates.any { it.tier == rarity.ordinal } }
            var roll = random.nextDouble() * options.sumOf { it.weight(gold) }
            val rarity = options.firstOrNull { roll -= it.weight(gold); roll < 0 } ?: options.last()
            val card = candidates.first { it.tier == rarity.ordinal }
            remaining.remove(card)
            var effectRoll = random.nextDouble()
            val finish = EquipmentFinish.entries.firstOrNull { finish ->
                effectRoll -= finish.chance(gold)
                effectRoll < 0
            } ?: EquipmentFinish.NORMAL
            EquipmentPiece(next + i, card.id, finish, random.nextInt(card.min, card.max + 1), finish.rollMultiplier(random), balanceVersion = 2)
        }
    }
}
