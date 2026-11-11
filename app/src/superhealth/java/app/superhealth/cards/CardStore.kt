package app.superhealth.cards

import android.content.Context
import android.util.AtomicFile
import app.superhealth.Feature
import app.superhealth.SuperPrefs
import app.superhealth.bridge.Bridge
import app.superhealth.has
import app.superhealth.health.data.Metric
import app.superhealth.league.League
import app.superhealth.league.leagueTarget
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.domain.Sex
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.Analytics
import app.superhealth.training.domain.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/**
 * Collection, portefeuille et statistiques de la carte Athlète. Le portefeuille et la collection sont tenus par le
 * serveur ([Economy]) ; cet état en est le reflet, pour l'affichage hors ligne. Une seule monnaie : les éclats, gagnés
 * avec les séances et les quêtes que le serveur génère pour chacun, qui ouvrent les packs. Pas de boutique : les
 * doublons se fusionnent pour renforcer un fond ([Levels]) ou s'échangent entre amis, contre une carte ou des éclats.
 * Le mode démo des builds de test joue tout en local.
 */
object CardStore {
    const val PACK_PRICE = 100

    /** Bonus de série déjà touché pour un suivi : semaine (lundi, jour epoch) et semaines d'affilée. */
    @Serializable data class StreakPaid(val week: Long, val n: Int)
    /** Bonus de série : 10 éclats la première semaine, +10 chaque semaine tenue d'affilée, jusqu'à 100 à 10 semaines (BADGES.streak du serveur). */
    const val STREAK_STEP = 10
    const val STREAK_WEEKS = 10

    @Serializable data class Earn(val time: Long, val amount: Int, val label: String)

    @Serializable
    data class State(
        val eclats: Int = 0, val bonusPacks: Int = 0,
        val packsOpened: Long = 0,
        val lastCredited: Long = 0, val secret: Long = 0,
        /** Clé de [Pull] → exemplaires reçus (doublons compris). */
        val owned: Map<String, Int> = emptyMap(),
        val background: String? = null,
        val claimed: Set<String> = emptySet(),
        val welcomed: Boolean = false,
        val walkoutMin: Rarity = Rarity.EPIQUE, val walkoutNewOnly: Boolean = false,
        val demo: Boolean = false,
        val ledger: List<Earn> = emptyList(),
        val cutout: Boolean = false,
        /** Photo affichée sur la carte, indépendante de la photo de profil du Groupe. */
        val photo: PhotoSource = PhotoSource.PROFIL,
        val booster: Booster = Booster.MAGMA,
        /** Quêtes du jour et de la semaine fixées par le serveur (vide : quêtes par défaut, mode démo ou hors ligne). */
        val quests: List<Economy.QuestDef> = emptyList(),
        /** Dernière note globale calculée (dixièmes), pour les widgets qui s'affichent avant le calcul. */
        val lastOvr10: Int = 0,
        /** Dernière synchronisation réussie avec le serveur (0 : jamais). */
        val synced: Long = 0,
        /** Fond → doublons déjà fusionnés (le niveau en découle). */
        val merged: Map<String, Int> = emptyMap(),
        /** Mon identifiant d'échange, donné par le serveur et partagé aux amis. */
        val tradeId: String? = null,
        val trades: Economy.Trades = Economy.Trades(),
        /** Défi de la semaine avec des amis, s'il y en a un. */
        val challenge: Economy.Challenge? = null,
        /** Dernières stats calculées, pour dessiner la carte dans le widget. */
        val lastStats: AthleteStats? = null,
        /** Badges dont les éclats ont été touchés, et bonus de série par suivi. */
        val badgesPaid: Set<String> = emptySet(),
        /** Départ de la mise à jour fait : anciens badges comptés comme payés, quête de la semaine remise à zéro. */
        val started: Boolean = false,
        /** Pack ouvert côté serveur mais pas encore révélé. */
        val pending: Pending? = null,
        val streakPaid: Map<String, StreakPaid> = emptyMap(),
        /** Mise à jour Dopamine : pop-up déjà montrée. */
        val dopamineSeen: Boolean = false,
        /** Récompenses en vigueur, fixées par le serveur (packs des quêtes de la semaine, défis). */
        val rewards: Economy.Rewards = Economy.Rewards(),
        val price: Int = PACK_PRICE,
        /** Cadeaux du serveur reçus mais pas encore montrés (packs offerts avec une mise à jour…). */
        val gifts: List<Economy.Gift> = emptyList(),
        /** 3.8 : vitrine, jusqu'à [SHOWCASE_MAX] fonds mis en avant sur « Ma carte » (clés de [Pull]). */
        val showcase: List<String> = emptyList(),
        val collectionVersion: Int = 0,
    ) {
        fun owns(card: CardDef) = owned.keys.any { it.startsWith(card.id + ":") }
        /** Exemplaires reçus d'un fond, toutes finitions confondues. */
        fun copies(card: CardDef) = owned.entries.sumOf { (k, v) -> if (k.startsWith(card.id + ":")) v else 0 }
        fun level(card: CardDef) = Levels.of(1 + (merged[card.id] ?: 0))
        /** Doublons d'un fond : tous ses exemplaires sauf un par finition possédée. */
        fun spare(card: CardDef) = Finish.entries.sumOf { f -> ((owned["${card.id}:${f.name}"] ?: 0) - 1).coerceAtLeast(0) }
        /** Doublons à fusionner pour le niveau suivant (null au max). */
        fun fuseCost(card: CardDef) = Levels.cost(level(card))
        fun fusePrice(card: CardDef) = Levels.price(level(card))
        /** Assez de doublons et d'éclats pour la fusion suivante. */
        fun canFuse(card: CardDef) = owns(card) && fuseCost(card)?.let { spare(card) >= it } == true && eclats >= (fusePrice(card) ?: 0)
        /** Échanges reçus en attente de réponse. */
        val pendingTrades get() = trades.incoming.size
        /** Packs offerts (cadeau de lancement, mode démo), ouverts avant de dépenser des éclats. */
        val packsAvailable get() = bonusPacks
        /** Packs à ouvrir avant le prochain pack doré, celui-ci compris. */
        val untilGold get() = PackEngine.untilGold(packsOpened)
        val nextGold get() = PackEngine.isGold(packsOpened)
    }

    /** Carte du jour, d'il y a 7 jours (ruban « En forme ») et d'il y a 28 jours (duel contre soi). */
    data class Snapshot(val now: AthleteStats, val week: AthleteStats, val month: AthleteStats) {
        val inForm get() = Stat.entries.any { s -> (now[s] ?: 0) > (week[s] ?: 99) }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val state = MutableStateFlow(State())
    val snapshot = MutableStateFlow<Snapshot?>(null)
    @Volatile private var loaded = false

    private fun dir(ctx: Context) = File(ctx.filesDir, "cards").apply { mkdirs() }
    private fun file(ctx: Context) = AtomicFile(File(dir(ctx), "state.json"))
    fun cutoutFile(ctx: Context) = File(dir(ctx), "cutout.png")

    @Synchronized fun load(ctx: Context) {
        if (loaded) return
        state.value = runCatching { json.decodeFromString(State.serializer(), file(ctx).openRead().bufferedReader().use { it.readText() }) }.getOrDefault(State())
        if (state.value.secret == 0L) state.value = state.value.copy(secret = SecureRandom().nextLong())
        loaded = true
        if (state.value.collectionVersion < ArsenalGame.COLLECTION_VERSION) update(ctx) {
            it.copy(collectionVersion = ArsenalGame.COLLECTION_VERSION, owned = emptyMap(), merged = emptyMap(),
                background = null, showcase = emptyList(), trades = Economy.Trades(), pending = null, packsOpened = 0)
        }
    }

    /** Relit l'état depuis le disque (après une restauration de sauvegarde). */
    @Synchronized fun reload(ctx: Context) { loaded = false; load(ctx) }

    @Synchronized fun update(ctx: Context, f: (State) -> State) {
        load(ctx)
        val s = f(state.value)
        val atomic = file(ctx)
        val out = atomic.startWrite()
        try { out.write(json.encodeToString(State.serializer(), s).toByteArray()); atomic.finishWrite(out) }
        catch (e: Exception) { atomic.failWrite(out); throw e }
        state.value = s
    }

    fun earn(ctx: Context, eclats: Int, label: String) {
        if (eclats <= 0) return
        update(ctx) { it.copy(eclats = it.eclats + eclats, ledger = (it.ledger + Earn(System.currentTimeMillis(), eclats, label)).takeLast(40)) }
    }

    private fun history(): List<Workout> = runCatching { WorkoutApp.store.state.value.history.distinctBy { it.id } }.getOrDefault(emptyList())

    /** Synchronise le portefeuille avec le serveur (en déclarant les séances récentes), puis recalcule la carte. */
    suspend fun refresh(ctx: Context) = withContext(Dispatchers.Default) {
        load(ctx)
        val history = history()
        if (!state.value.demo && Economy.available()) runCatching {
            // Le serveur ne crédite qu'une fois chaque séance des quatre derniers jours ; il ignore les autres.
            val since = System.currentTimeMillis() - 4 * 86_400_000L
            val target = runCatching { WorkoutApp.store.state.value.let { st -> st.programs.firstOrNull { it.id == st.settings.activeProgramId }?.leagueTarget() } }.getOrNull() ?: 3
            val sessions = history.filter { it.startTime >= since && it.exercises.any { e -> Analytics.workingSets(e).isNotEmpty() } }
                .map { Economy.Session(it.id.take(64).replace(Regex("[^\\w-]"), "_"), it.startTime, target) }
            Economy.sync(ctx, sessions)
        }
        snapshot.value = if (state.value.demo) Demo.snapshot() else compute(ctx, history)
        snapshot.value?.now?.takeIf { it.character != state.value.lastStats?.character || it.values != state.value.lastStats?.values || it.ovr10 != state.value.lastStats?.ovr10 || it.training != state.value.lastStats?.training }?.let { now ->
            update(ctx) { it.copy(lastOvr10 = now.ovr10 ?: it.lastOvr10, lastStats = now.copy(muscles = emptyMap())) }
        }
        runCatching { app.superhealth.ui.widget.CardWidgets.refresh(ctx) }
    }

    private suspend fun compute(ctx: Context, history: List<Workout>): Snapshot? {
        val db = withTimeoutOrNull(5000) { WorkoutApp.db.first { it != null } } ?: return null
        val sh = SuperPrefs.settings.value
        val analytics = runCatching { withTimeoutOrNull(5000) { NutriApp.repository.analytics().first() } }.getOrNull()
        val profile = analytics?.profile
        val sex = profile?.sex ?: Sex.MALE
        val age = (LocalDate.now().year - (profile?.birthYear ?: 1995)).coerceIn(14, 90)
        val bodyweight = analytics?.currentTrendKg ?: 75.0
        val now = System.currentTimeMillis()
        var vo2: Double? = null
        var runs = emptyList<AthleteStatsEngine.Run>()
        var cardioMinutes: Double? = null
        var steps: Double? = null
        val activityDays = mutableSetOf<Long>()
        var endurance: List<AthleteStatsEngine.Endurance>? = null
        var daySteps: List<Pair<Long, Double>>? = null
        var resting: Double? = null
        if (sh.has(Feature.HEALTH)) {
            vo2 = runCatching {
                (0L until 30L).firstNotNullOfOrNull { Bridge.healthValue(ctx, Metric.VO2MAX, LocalDate.now().minusDays(it))?.takeIf { v -> v > 0 } }
            }.getOrNull()
            // Un an d'activités : les vieilles courses gardent un peu de poids (records), les récentes l'essentiel.
            val acts = runCatching { Bridge.activityFacts(now - 365 * 86_400_000L, now) }.getOrDefault(emptyList())
            runs = acts.filter { it.kind == Bridge.Kind.RUN }.mapNotNull { a -> a.distanceM?.let { AthleteStatsEngine.Run(a.start, it, (a.end - a.start) / 1000.0) } }
            // Toutes les activités d'endurance comptent, pondérées par leur intensité.
            cardioMinutes = acts.filter { it.start >= now - 28 * 86_400_000L }.sumOf { a -> a.minutes * when (a.kind) {
                Bridge.Kind.RUN, Bridge.Kind.SWIM -> 1.0; Bridge.Kind.BIKE -> 0.9; Bridge.Kind.HIKE -> 0.8; Bridge.Kind.WALK -> 0.5; Bridge.Kind.OTHER -> 0.6
                Bridge.Kind.YOGA -> 0.3; Bridge.Kind.STRENGTH -> 0.0 } }
            // Séances et distance en équivalent course : le vélo compte un tiers, la nage quatre fois, rando et marche moitié.
            endurance = acts.filter { it.kind != Bridge.Kind.STRENGTH && it.kind != Bridge.Kind.YOGA && it.minutes >= 10 }.map { a ->
                AthleteStatsEngine.Endurance(a.start, a.minutes * when (a.kind) { Bridge.Kind.RUN, Bridge.Kind.SWIM -> 1.0; Bridge.Kind.BIKE -> 0.9; Bridge.Kind.HIKE -> 0.8; Bridge.Kind.WALK -> 0.5; else -> 0.6 },
                    (a.distanceM ?: 0.0) / 1000 * when (a.kind) { Bridge.Kind.BIKE -> 0.33; Bridge.Kind.SWIM -> 4.0; Bridge.Kind.HIKE, Bridge.Kind.WALK -> 0.5; else -> 1.0 }) }
            resting = runCatching { (0L until 14L).mapNotNull { Bridge.healthValue(ctx, Metric.RESTING_HR, LocalDate.now().minusDays(it))?.takeIf { v -> v > 0 } }.takeIf { it.size >= 3 }?.average() }.getOrNull()
            daySteps = runCatching { (1L..56L).mapNotNull { d -> LocalDate.now().minusDays(d).let { day -> Bridge.healthValue(ctx, Metric.STEPS, day)?.let {
                day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() to it } } } }.getOrNull()
            steps = daySteps?.filter { it.first >= now - 29 * 86_400_000L }?.map { it.second }?.takeIf { it.size >= 7 }?.average()
            activityDays += acts.map { java.time.Instant.ofEpochMilli(it.start).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() }
        }
        // Jours où l'app a servi : repas notés, pesées, activités (les séances sont ajoutées par le moteur).
        val tracked = runCatching { NutriApp.repository.entries.loggedDays().toSet() + NutriApp.repository.weighIns.all().map { it.epochDay } }.getOrDefault(emptySet()) + activityDays
        val target = runCatching {
            WorkoutApp.store.state.value.let { st -> st.programs.firstOrNull { it.id == st.settings.activeProgramId }?.leagueTarget() }
        }.getOrNull() ?: League.state.value.weeklyTargets["STRENGTH"] ?: 3
        fun at(t: Long) = AthleteStatsEngine.compute(AthleteStatsEngine.Input(history, db, bodyweight, sex, age, t, target, vo2, runs, cardioMinutes, trackedDays = tracked, dailySteps28 = steps,
            endurance = endurance, steps = daySteps, restingHr = resting, heightCm = profile?.heightCm?.takeIf { analytics?.currentTrendKg != null }, bodyweightKnown = analytics?.currentTrendKg != null))
        val current = at(now)
        val progress = app.superhealth.league.ProgressRepository.current.value
        val character = progress?.takeIf { it.summary.throughDay == LocalDate.now().toEpochDay() }?.let {
            CharacterStats.from(it, app.superhealth.league.ProgressRepository.selectedTracks(sh), current)
        }
        return Snapshot(current.copy(character = character), at(now - 7 * 86_400_000L), at(now - 28 * 86_400_000L))
    }

    // ---- Packs -------------------------------------------------------------------------------------------

    /** Ce qu'a donné chaque carte : nouvelle, ou doublon (avec son niveau et si une fusion est prête). */
    data class Gain(val pull: Pull, val fresh: Boolean, val copiesBefore: Int, val copiesAfter: Int, val level: Int = 1, val fusable: Boolean = false)
    data class Opening(val result: PackEngine.Result, val gains: List<Gain>, val booster: Booster) {
        val fresh get() = gains.filter { it.fresh }.map { it.pull.key }.toSet()
        val saved get() = Pending(booster, gains.map { SavedGain(it.pull.key, it.copiesBefore, it.copiesAfter, it.level, it.fusable) }, result.prism, result.gold)
    }
    /** Pack payé dont on n'a pas encore vu les cartes (retour en arrière, appli fermée) : on le reprend au prochain passage. */
    @Serializable data class SavedGain(val key: String, val before: Int, val after: Int, val level: Int, val fusable: Boolean)
    @Serializable data class Pending(val booster: Booster, val gains: List<SavedGain>, val prism: Boolean, val gold: Boolean) {
        fun opening(): Opening? {
            val g = gains.mapNotNull { s -> Pull.of(s.key)?.let { Gain(it, s.before == 0, s.before, s.after, s.level, s.fusable) } }
            return if (g.isEmpty()) null else Opening(PackEngine.Result(g.map { it.pull }, prism, gold), g, booster)
        }
    }
    /** Le pack en attente a été vu jusqu'au bout. */
    fun seen(ctx: Context) { if (state.value.pending != null) update(ctx) { it.copy(pending = null) } }

    fun canOpen() = state.value.bonusPacks > 0 || state.value.eclats >= state.value.price

    /** Ouvre un pack : par le serveur, ou en local en mode démo. Un pack offert passe avant les éclats. */
    suspend fun open(ctx: Context, booster: Booster): Opening {
        if (!state.value.demo) return Economy.open(ctx, booster).also { o -> update(ctx) { it.copy(booster = booster, pending = o.saved) } }
        var opening: Opening? = null
        update(ctx) { s ->
            val paid = when { s.bonusPacks > 0 -> s.copy(bonusPacks = s.bonusPacks - 1); s.eclats >= s.price -> s.copy(eclats = s.eclats - s.price); else -> return@update s }
            val result = PackEngine.open(PackEngine.seed(s.secret, s.packsOpened), PackEngine.isGold(s.packsOpened), booster)
            var st = paid
            for (p in result.pulls) st = st.copy(owned = st.owned + (p.key to ((st.owned[p.key] ?: 0) + 1)))
            val gains = result.pulls.map { p ->
                val before = paid.copies(p.card) + result.pulls.takeWhile { it !== p }.count { it.card == p.card }
                Gain(p, before == 0, before, before + 1, st.level(p.card), st.canFuse(p.card))
            }
            opening = Opening(result, gains, booster)
            st.copy(booster = booster, packsOpened = st.packsOpened + 1, pending = opening!!.saved)
        }
        return opening ?: throw Economy.Unavailable("Pas assez d'éclats pour ce pack.")
    }

    /** Fusionne des doublons d'un fond pour lui faire gagner un niveau (+10 % d'effet). */
    suspend fun fuse(ctx: Context, card: CardDef) {
        if (!state.value.demo) return Economy.fuse(ctx, card)
        if (!state.value.canFuse(card)) throw Economy.Unavailable("Pas assez de doublons ou d'éclats pour cette fusion.")
        update(ctx) { s ->
            var cost = s.fuseCost(card) ?: 0
            var owned = s.owned
            for (f in Finish.entries) {
                val key = "${card.id}:${f.name}"; val n = minOf(cost, (owned[key] ?: 0) - 1)
                if (n > 0) { owned = owned + (key to owned.getValue(key) - n); cost -= n }
            }
            s.copy(owned = owned, eclats = s.eclats - (s.fusePrice(card) ?: 0), merged = s.merged + (card.id to (s.merged[card.id] ?: 0) + s.fuseCost(card)!!))
        }
    }

    // ---- Échanges ------------------------------------------------------------------------------------------

    /** Collection d'un ami, pour choisir ce qu'on lui demande (en démo : celle du rival). */
    suspend fun browse(ctx: Context, tradeId: String): Map<String, Int> =
        if (state.value.demo || tradeId == Demo.TRADE_ID) Demo.collection else Economy.browse(ctx, tradeId)

    suspend fun offer(ctx: Context, tradeId: String, give: Economy.Stake, want: Economy.Stake) {
        if (!state.value.demo) return Economy.offer(ctx, tradeId, give, want)
        val s = state.value
        if (give.card != null && (s.owned[give.card] ?: 0) < 1 || give.eclats != null && s.eclats < give.eclats) throw Economy.Unavailable("Il manque la carte ou les éclats pour cet échange.")
        update(ctx) { st -> hold(st, give).copy(trades = st.trades.copy(out = st.trades.out + Economy.Trade("demo-" + System.nanoTime(), give, want, System.currentTimeMillis(), to = tradeId))) }
    }

    /** « accept », « decline » ou « cancel ». */
    suspend fun answer(ctx: Context, trade: Economy.Trade, op: String): Boolean {
        if (!state.value.demo) return Economy.answer(ctx, trade.id, op)
        var done = false
        update(ctx) { st ->
            val t = st.trades.copy(out = st.trades.out.filterNot { it.id == trade.id }, incoming = st.trades.incoming.filterNot { it.id == trade.id })
            when (op) {
                "cancel" -> pay(st, trade.give).copy(trades = t)
                "decline" -> st.copy(trades = t)
                else -> {
                    val w = trade.want
                    if (w.card != null && (st.owned[w.card] ?: 0) < 1 || w.eclats != null && st.eclats < w.eclats) throw Economy.Unavailable("Il manque la carte ou les éclats pour cet échange.")
                    done = true; pay(hold(st, w), trade.give).copy(trades = t)
                }
            }
        }
        return done
    }

    private fun hold(s: State, k: Economy.Stake) = if (k.card != null) s.copy(owned = (s.owned + (k.card to (s.owned[k.card] ?: 0) - 1)).filterValues { it > 0 }) else s.copy(eclats = s.eclats - (k.eclats ?: 0))
    private fun pay(s: State, k: Economy.Stake) = if (k.card != null) s.copy(owned = s.owned + (k.card to (s.owned[k.card] ?: 0) + 1)) else s.copy(eclats = s.eclats + (k.eclats ?: 0))

    fun setBackground(ctx: Context, key: String?) = update(ctx) { it.copy(background = key) }

    const val SHOWCASE_MAX = 3
    /** Ajoute ou retire un fond de la vitrine ; plein, le plus ancien laisse sa place. */
    fun toggleShowcase(ctx: Context, key: String) = update(ctx) { s ->
        s.copy(showcase = if (key in s.showcase) s.showcase - key else (s.showcase + key).takeLast(SHOWCASE_MAX))
    }
    /** Fonds de la vitrine encore possédés (un échange a pu en retirer un). */
    fun showcase(): List<Pull> = state.value.let { s -> s.showcase.filter { (s.owned[it] ?: 0) > 0 }.mapNotNull(Pull::of) }
    fun background(): Pull? = state.value.background?.let(Pull::of)

    // ---- Quêtes ------------------------------------------------------------------------------------------

    enum class Difficulty(val label: String, val color: Long) {
        FACILE("Facile", 0xFF39D98A), NORMAL("Normal", 0xFF4FA8FF), DIFFICILE("Difficile", 0xFFFFB238), HEROIQUE("Héroïque", 0xFFFF4F7B)
    }
    /** [reward] : éclats et [packs] : packs offerts, fixés par le serveur pour cette quête. */
    /** [league] : quête de la semaine du Groupe (récupérée par [League.claimQuest]). */
    data class Quest(val id: String, val title: String, val difficulty: Difficulty, val progress: Int, val target: Int, val claimed: Boolean, val reward: Int, val weekly: Boolean = false,
                     val league: app.superhealth.league.WeeklyQuests.Metric? = null, val challenge: Economy.Challenge? = null, val note: String? = null, val packs: Int = 0,
                     /** Mode d'une quête de la semaine : simple (la sienne), groupe (ensemble) ou rapidité (course). */
                     val mode: Mode = Mode.SIMPLE) {
        val complete get() = progress >= target
    }
    enum class Mode(val label: String) { SIMPLE("Simple"), GROUPE("Groupe"), RAPIDITE("Rapidité") }

    /** Quêtes du jour par défaut, sans serveur (mode démo, ou avant la première synchronisation). */
    private fun defaults(day: Long) = listOf(
        Economy.QuestDef("d:$day:session", "session", "d", 0, 6), Economy.QuestDef("d:$day:sets", "sets", "d", 1, 7), Economy.QuestDef("d:$day:focus", "focus", "d", 0, 6, "JAM"))

    /**
     * Quêtes du moment, qui rapportent des éclats : deux ou trois quêtes du jour, dont le serveur choisit le genre, le
     * palier et les éclats (l'objectif concret vient des habitudes de l'utilisateur sur quatre semaines), puis la quête
     * de la semaine du Groupe et sa secondaire, adaptées en local à tous les domaines suivis ([WeeklyQuests]).
     */
    fun quests(today: LocalDate = LocalDate.now()): List<Quest> {
        val zone = ZoneId.systemDefault()
        val history = history()
        val db = WorkoutApp.db.value
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekStart = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val todays = history.filter { it.startTime >= dayStart }
        val week = history.filter { it.startTime >= weekStart }
        val past = history.filter { it.startTime in (weekStart - 28 * 86_400_000L) until weekStart }
        val claimed = state.value.claimed
        val d = today.toEpochDay(); val w = monday.toEpochDay()
        val target = runCatching { WorkoutApp.store.state.value.let { st -> st.programs.firstOrNull { it.id == st.settings.activeProgramId }?.leagueTarget() } }.getOrNull() ?: 3
        fun sets(list: List<Workout>, stat: Stat? = null) = list.sumOf { wk -> wk.exercises.sumOf { e ->
            if (stat != null && db?.exercise(e.exerciseId)?.muscleGroups?.any { it in stat.muscles } != true) 0 else Analytics.workingSets(e).size } }
        // Habitudes : séries par séance (médiane), séances et séries par semaine (moyenne des quatre semaines passées).
        // Caractère du jour : les habitudes de ce jour de la semaine (séries des séances de ce jour-là quand il y en a).
        val dow = today.dayOfWeek
        fun dateOf(w: Workout) = java.time.Instant.ofEpochMilli(w.startTime).atZone(zone).toLocalDate()
        val sameDay = history.filter { it.startTime in (dayStart - 28 * 86_400_000L) until dayStart && dateOf(it).dayOfWeek == dow }
        val usual = past.map { sets(listOf(it)) }.filter { it > 0 }.sorted().let { if (it.isEmpty()) 14 else it[it.size / 2] }
        val perSession = sameDay.map { sets(listOf(it)) }.filter { it > 0 }.sorted().takeIf { it.size >= 2 }?.let { it[it.size / 2] } ?: usual
        // Jour d'entraînement : une séance aujourd'hui, ou au moins deux des quatre derniers mêmes jours (sans historique : oui).
        val trainingDay = todays.isNotEmpty() || history.isEmpty() || sameDay.map { dateOf(it) }.distinct().size >= 2
        // Jour de repos : les suivis du quotidien déjà en place (nutrition, pas, mobilité, sommeil), les plus réguliers d'abord.
        val dailyTracks = runCatching { app.superhealth.league.ProgressRepository.current.value?.summary?.streaks.orEmpty() }.getOrDefault(emptyList())
            .filter { !it.weekly && it.track in setOf("FOOD", "STEPS", "MIND", "SLEEP") }.sortedByDescending { it.current }
        val perWeek = if (past.isEmpty()) target.toDouble() else past.size / 4.0
        val setsPerWeek = if (past.isEmpty()) perSession * target.toDouble() else sets(past) / 4.0
        val rirDone = todays.any { wk -> wk.exercises.flatMap { Analytics.workingSets(it) }.let { s -> s.isNotEmpty() && s.all { it.rir != null } } }
        val record = db != null && week.any { wk -> wk.exercises.any { e ->
            val ex = db.exercise(e.exerciseId) ?: return@any false
            val now = Analytics.bestPerformance(ex, Analytics.workingSets(e)) ?: return@any false
            val before = history.filter { it.startTime < weekStart }.flatMap { it.exercises }.filter { it.exerciseId == e.exerciseId }
                .mapNotNull { Analytics.bestPerformance(ex, Analytics.workingSets(it)) }.maxOrNull()
            ex.usesWeight && before != null && now > before * 1.005
        } }
        val defs = state.value.quests.filter { it.id.startsWith("d:$d:") }.ifEmpty { defaults(d) }
        val weekly = runCatching { League.state.value.questWeeks.firstOrNull { it.start == app.superhealth.league.Progression.week(d) }?.quests.orEmpty() }.getOrDefault(emptyList())
            .map { q -> Quest("lq:" + q.metric.name, app.superhealth.ui.league.questGoal(q), if (q.secondary) Difficulty.FACILE else Difficulty.NORMAL,
                q.progress.coerceAtMost(q.target), q.target, q.claimed, 0, weekly = true, league = q.metric, packs = state.value.rewards.weekly.packs) }
        val training = setOf("session", "sets", "rir", "focus")
        return defs.mapIndexedNotNull { i, q ->
            val tier = q.tier.coerceIn(0, 2)
            // Un jour de repos, les quêtes d'entraînement deviennent des quêtes du quotidien (même récompense, fixée par le serveur).
            if (!trainingDay && q.kind in training && dailyTracks.isNotEmpty()) {
                val t = dailyTracks[i % dailyTracks.size]
                val title = when (t.track) {
                    "FOOD" -> "Rester dans ta plage calorique aujourd'hui"; "STEPS" -> "Atteindre ton objectif de pas aujourd'hui"
                    "MIND" -> "10 min de méditation ou de mobilité aujourd'hui"; else -> "Atteindre ton objectif de sommeil"
                }
                return@mapIndexedNotNull Quest(q.id, title, Difficulty.entries[tier], if (t.progress >= t.target.coerceAtLeast(1)) 1 else 0, 1, q.id in claimed, q.reward)
            }
            val k = floatArrayOf(0.7f, 0.9f, 1.1f)[tier]
            val stat = q.stat?.let { c -> Stat.entries.firstOrNull { it.name == c } }
            val (title, progress, goal) = when (q.kind) {
                "session" -> Triple("Valider une séance aujourd'hui", todays.size, 1)
                "rir" -> Triple("Noter le RIR de toutes les séries d'une séance", if (rirDone) 1 else 0, 1)
                "sets" -> (perSession * k).roundToInt().coerceAtLeast(6).let { Triple("Faire $it séries de travail aujourd'hui", sets(todays), it) }
                "focus" -> (stat ?: Stat.JAM).let { st -> intArrayOf(3, 5, 8)[tier].let { Triple("$it séries pour ${st.mine} aujourd'hui", sets(todays, st), it) } }
                "target" -> {
                    val base = minOf(target, perWeek.roundToInt().coerceAtLeast(1))
                    val n = when (tier) { 0 -> (base - 1).coerceAtLeast(1); 1 -> base; else -> minOf(target + 1, base + 1) }
                    Triple(if (n == target) "Atteindre ton objectif de $n séances" else "Faire $n séances cette semaine", week.size, n)
                }
                "volume" -> (setsPerWeek * k).roundToInt().coerceAtLeast(15).let { Triple("Faire $it séries de travail cette semaine", sets(week), it) }
                "family" -> (stat ?: Stat.JAM).let { st -> (tier + 1).let { n -> Triple(if (n == 1) "Une séance qui travaille ${st.mine}" else "$n séances qui travaillent ${st.mine}",
                    week.count { sets(listOf(it), st) > 0 }, n) } }
                "pr" -> Triple("Battre un record de 1RM estimé", if (record) 1 else 0, 1)
                else -> return@mapIndexedNotNull null // genre ajouté côté serveur après cette version
            }
            val diff = if (q.kind == "pr") Difficulty.HEROIQUE else if (q.scope == "w") Difficulty.entries[tier + 1] else Difficulty.entries[tier]
            Quest(q.id, title, diff, progress.coerceAtMost(goal), goal, q.id in claimed, q.reward, q.scope == "w")
        }.distinctBy { it.title } + weekly + listOfNotNull(state.value.challenge?.takeIf { it.week == w }?.let { c ->
            val stat = c.stat?.let { code -> Stat.entries.firstOrNull { it.name == code } } ?: Stat.JAM
            val (title, progress) = when (c.kind) {
                "family" -> (if (c.goal == 1) "Une séance qui travaille ${stat.mine}" else "${c.goal} séances qui travaillent ${stat.mine}") to week.count { sets(listOf(it), stat) > 0 }
                "volume" -> "Faire ${c.goal} séries de travail cette semaine" to sets(week)
                else -> "Faire ${c.goal} séances cette semaine" to week.size
            }
            val me = state.value.tradeId
            val names = c.members.filter { it != me }.map { id -> runCatching { League.state.value.friends.firstOrNull { it.cardTrade == id }?.shown }.getOrNull()
                ?: if (id == Demo.TRADE_ID) "lohan" else "un ami d'ami" }.map { "@" + it.lowercase().replace(" ", "") }
            val who = "Toi, " + names.dropLast(1).joinToString(", ").let { if (it.isEmpty()) "" else "$it et " } + names.last()
            val status = when {
                c.race && c.winner == me -> "tu as gagné la course"
                c.race && c.winner != null -> "déjà remportée, finis-la quand même"
                c.race -> "le premier à la finir gagne"
                else -> "${c.done.size}/${c.members.size} l'ont finie · tout le monde gagne quand tous l'ont finie"
            }
            Quest("ch:" + c.id, title, Difficulty.DIFFICILE, progress.coerceAtMost(c.goal), c.goal, me in c.done, c.reward, weekly = true, challenge = c,
                note = who + " · " + status, packs = c.packs, mode = if (c.race) Mode.RAPIDITE else Mode.GROUPE)
        })
    }

    /** Récupère une quête accomplie : le serveur vérifie la date et crédite le montant qu'il a fixé. */
    suspend fun claim(ctx: Context, quest: Quest) {
        if (!quest.complete || quest.claimed || quest.id in state.value.claimed) return
        quest.league?.let { return League.claimQuest(ctx, it) }
        quest.challenge?.let { c ->
            if (!state.value.demo) return Economy.finishChallenge(ctx, c.id)
            // Démo : le rival n'a jamais fini avant toi.
            return update(ctx) { s -> s.copy(eclats = s.eclats + c.reward, bonusPacks = s.bonusPacks + c.packs, challenge = c.copy(done = c.done + (s.tradeId ?: "me"), winner = if (c.race) s.tradeId ?: "me" else null)) }
        }
        if (!state.value.demo) return Economy.sync(ctx, listOf(Economy.Quest(quest.id)))
        update(ctx) { s -> s.copy(claimed = (s.claimed + quest.id).toList().takeLast(200).toSet(), eclats = s.eclats + quest.reward, bonusPacks = s.bonusPacks + quest.packs,
            ledger = (s.ledger + Earn(System.currentTimeMillis(), quest.reward, quest.title)).takeLast(40)) }
    }

    /** Ce qu'il y a à récupérer hors quêtes : paliers de badges atteints et bonus des séries tenues cette semaine. */
    data class Payout(val badges: List<app.superhealth.league.Achievements.Badge>, val streaks: List<StreakBonus>) {
        val total get() = badges.sumOf(app.superhealth.league.Achievements::reward) + streaks.sumOf { it.reward }
        val empty get() = badges.isEmpty() && streaks.isEmpty()
    }
    /** [weeks] : semaines d'affilée (celle-ci comprise si elle est tenue) ; [claimable] : semaine tenue et bonus pas encore touché. */
    data class StreakBonus(val track: app.superhealth.league.Progression.Track, val weeks: Int, val best: Int, val days: Int, val need: Int, val done: Boolean, val reward: Int, val claimable: Boolean)

    private fun thisMonday() = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
    fun streakReward(weeks: Int) = STREAK_STEP * weeks.coerceIn(1, STREAK_WEEKS)

    fun payout(): Payout {
        val s = state.value
        val league = runCatching { League.state.value }.getOrNull()
        val earned = league?.earnedBadges.orEmpty().toSet()
        val badges = if (!s.started) emptyList() else app.superhealth.league.Achievements.catalog.filter { it.id in earned && it.id !in s.badgesPaid && app.superhealth.league.Achievements.reward(it) > 0 }
        val week = thisMonday()
        val streaks = runCatching { app.superhealth.league.ProgressRepository.current.value?.summary?.weekly }.getOrNull().orEmpty().mapNotNull { st ->
            val track = app.superhealth.league.Progression.Track.entries.firstOrNull { it.name == st.track } ?: return@mapNotNull null
            val paid = s.streakPaid[st.track]?.week == week
            // Le serveur borne le bonus par les semaines écoulées depuis le dernier : on affiche ce qu'il paiera.
            val cap = s.streakPaid[st.track]?.let { it.n + ((week - it.week) / 7).toInt() } ?: STREAK_WEEKS
            val weeks = if (paid) s.streakPaid[st.track]!!.n else minOf(st.current, cap).coerceAtLeast(1)
            StreakBonus(track, st.current, st.best, st.days, st.need, st.done, streakReward(weeks), st.done && !paid)
        }
        return Payout(badges, streaks)
    }

    /**
     * Départ de la mise à jour, une fois : tout le monde part de 0 éclat. Les badges obtenus avant comptent comme payés
     * (pas de rattrapage), et la quête de la semaine déjà récupérée en XP redevient à récupérer, en éclats cette fois.
     */
    suspend fun start(ctx: Context, earned: Set<String>) {
        load(ctx)
        if (state.value.started) return
        // Hors ligne : on réessaiera, sinon le serveur paierait les anciens badges.
        if (!state.value.demo && runCatching { Economy.sync(ctx, listOf(Economy.Baseline(earned.toList()))) }.isFailure) return
        val week = app.superhealth.league.Progression.week(LocalDate.now().toEpochDay())
        League.configure(ctx) { l -> l.copy(questWeeks = l.questWeeks.map { w -> if (w.start == week) w.copy(quests = w.quests.map { it.copy(claimed = false) }) else w }) }
        update(ctx) { it.copy(started = true, badgesPaid = it.badgesPaid + earned) }
    }

    /** Récupère d'un coup les paliers de badges et les bonus de séries : le serveur paie chaque badge une seule fois. */
    suspend fun collect(ctx: Context) {
        val p = payout()
        val streaks = p.streaks.filter { it.claimable }
        if (p.badges.isEmpty() && streaks.isEmpty()) return
        if (!state.value.demo) return (p.badges.map { Economy.Badge(it.id) } + streaks.map { Economy.Streak(it.track.name, it.weeks) }).chunked(40).forEach { Economy.sync(ctx, it) }
        val week = thisMonday()
        val gain = p.badges.sumOf(app.superhealth.league.Achievements::reward) + streaks.sumOf { it.reward }
        update(ctx) { s -> s.copy(eclats = s.eclats + gain, badgesPaid = s.badgesPaid + p.badges.map { it.id },
            streakPaid = s.streakPaid + streaks.associate { it.track.name to StreakPaid(week, it.weeks.coerceIn(1, STREAK_WEEKS)) },
            ledger = (s.ledger + Earn(System.currentTimeMillis(), gain, "Badges et séries")).takeLast(40)) }
    }

    /** Quête de la semaine du Groupe accomplie : des packs offerts, autant de quêtes payées par semaine que le serveur le fixe. */
    suspend fun earnLeague(ctx: Context, id: String) {
        if (state.value.demo) return update(ctx) { it.copy(bonusPacks = it.bonusPacks + it.rewards.weekly.packs) }
        Economy.sync(ctx, listOf(Economy.League(id)))
    }

    /** Cadeau du serveur montré : on le retire de la file. */
    fun giftSeen(ctx: Context, id: String) = update(ctx) { s -> s.copy(gifts = s.gifts.filterNot { it.id == id }) }

    /** Récompense d'une quête en clair : « +1 pack » ou « +20 éclats ». */
    fun prize(q: Quest, short: Boolean = false): String = when {
        q.packs > 0 -> "+${q.packs} " + app.superhealth.ui.tr(if (q.packs > 1) "packs" else "pack")
        short -> "+${q.reward} ✦"
        else -> "+${q.reward} " + app.superhealth.ui.tr("éclats")
    }

    // ---- Mode démo (builds de test) ------------------------------------------------------------------------

    // Le premier pack de démo est doré, pour voir l'invocation tout de suite.
    fun setDemo(ctx: Context, on: Boolean) = update(ctx) { it.copy(demo = on, bonusPacks = if (on) it.bonusPacks + 10 else it.bonusPacks, eclats = if (on) it.eclats + 500 else it.eclats,
        packsOpened = if (on) PackEngine.GOLD_EVERY - 1L else it.packsOpened, trades = if (on) Economy.Trades() else it.trades,
        // Démo : une course de la semaine contre le rival, pour voir un défi tout de suite.
        tradeId = if (on) it.tradeId ?: "me" else it.tradeId,
        challenge = if (on) Economy.Challenge("demo", "race", "target", 3, reward = 40, week = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay(),
            members = listOf(it.tradeId ?: "me", Demo.TRADE_ID)) else it.challenge) }

    /** En démo, le rival propose deux échanges dès qu'on a des cartes : une carte contre une carte, une carte contre des éclats. */
    fun seedDemoTrades(ctx: Context) {
        val s = state.value
        if (!s.demo || s.trades.incoming.isNotEmpty() || s.owned.isEmpty()) return
        val mine = s.owned.keys.mapNotNull(Pull::of)
        val forCard = mine.firstOrNull { it.card.rarity == Rarity.COMMUNE } ?: mine.first()
        val theirs = Catalog.cards.first { c -> c.rarity == forCard.card.rarity && !c.duo && !s.owns(c) }
        val now = System.currentTimeMillis()
        val incoming = listOfNotNull(
            Economy.Trade("demo-in-1", Economy.Stake(card = "${theirs.id}:BRILLANTE"), Economy.Stake(card = forCard.key), now, from = Demo.TRADE_ID),
            mine.firstOrNull { it.key != forCard.key }?.let { Economy.Trade("demo-in-2", Economy.Stake(eclats = 40), Economy.Stake(card = it.key), now, from = Demo.TRADE_ID) })
        update(ctx) { it.copy(trades = it.trades.copy(incoming = incoming)) }
    }

    object Demo {
        private fun stats(vararg v: Pair<Stat, Int>, prov: Set<String> = emptySet()): AthleteStats {
            val values = v.associate { it.first.code to it.second }
            val fams = Stat.families.mapNotNull { values[it.code] }
            val ovr10 = ((fams.average() * .8 + (values["RÉG"] ?: 0) * .12 + (values["CAR"] ?: 0) * .08) * 10).toInt()
            return AthleteStats(values = values, provisional = prov, ovr10 = ovr10, computedAt = System.currentTimeMillis(),
                muscles = mapOf("chest" to 88, "lats" to 80, "quads" to 86, "biceps" to 71, "triceps" to 77, "abs" to 70, "glutes" to 84, "sideDelts" to 82))
        }
        fun snapshot() = Snapshot(
            stats(Stat.PEC to 88, Stat.DOS to 81, Stat.BRA to 79, Stat.JAM to 86, Stat.REG to 91, Stat.CAR to 68),
            stats(Stat.PEC to 87, Stat.DOS to 81, Stat.BRA to 78, Stat.JAM to 86, Stat.REG to 90, Stat.CAR to 68),
            stats(Stat.PEC to 83, Stat.DOS to 77, Stat.BRA to 75, Stat.JAM to 82, Stat.REG to 84, Stat.CAR to 64))
        const val TRADE_ID = "demo"
        val collection: Map<String, Int> get() = Catalog.cards.filter { !it.duo }.filterIndexed { i, _ -> i % 5 == 0 }.associate { "${it.id}:NORMALE" to 1 + it.number % 3 }
        val rival = stats(Stat.PEC to 90, Stat.DOS to 90, Stat.BRA to 88, Stat.JAM to 84, Stat.REG to 84, Stat.CAR to 74)
    }
}
