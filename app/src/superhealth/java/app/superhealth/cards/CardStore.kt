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
import kotlinx.coroutines.launch
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

/**
 * Collection, portefeuille et statistiques de la carte Athlète. Le portefeuille et la collection sont tenus par le
 * serveur ([Economy]) ; cet état en est le reflet, pour l'affichage hors ligne. Une seule monnaie : les éclats, gagnés
 * avec les séances et les quêtes (≈ 2 à 3 packs par semaine quand on tient son objectif), qui ouvrent les packs et
 * fabriquent un fond précis. Les doublons font monter le niveau d'un fond, puis rendent des éclats au niveau max.
 * Le mode démo des builds de test joue tout en local.
 */
object CardStore {
    const val PACK_PRICE = 100

    @Serializable data class Earn(val time: Long, val amount: Int, val label: String)

    @Serializable
    data class State(
        val eclats: Int = 0, val bonusPacks: Int = 0,
        val packsOpened: Long = 0, val sinceRare: Int = 0, val sinceEpic: Int = 0,
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
        /** Éclats gagnés cette semaine et plafond fixé par le serveur. */
        val weekEarned: Int = 0, val weekCap: Int = Economy.Rewards.WEEK_CAP,
        /** Dernière synchronisation réussie avec le serveur (0 : jamais). */
        val synced: Long = 0,
    ) {
        fun owns(card: CardDef) = owned.keys.any { it.startsWith(card.id + ":") }
        /** Exemplaires reçus d'un fond, toutes finitions confondues. */
        fun copies(card: CardDef) = owned.entries.sumOf { (k, v) -> if (k.startsWith(card.id + ":")) v else 0 }
        fun level(card: CardDef) = Levels.of(copies(card))
        /** Packs offerts (cadeau de lancement, mode démo), ouverts avant de dépenser des éclats. */
        val packsAvailable get() = bonusPacks
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
    }

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
        if (sh.has(Feature.HEALTH)) {
            vo2 = runCatching {
                (0L until 30L).firstNotNullOfOrNull { Bridge.healthValue(ctx, Metric.VO2MAX, LocalDate.now().minusDays(it))?.takeIf { v -> v > 0 } }
            }.getOrNull()
            val acts = runCatching { Bridge.activityFacts(now - 56 * 86_400_000L, now) }.getOrDefault(emptyList())
            runs = acts.filter { it.kind == Bridge.Kind.RUN }.mapNotNull { a -> a.distanceM?.let { AthleteStatsEngine.Run(a.start, it, (a.end - a.start) / 1000.0) } }
            // Toutes les activités d'endurance comptent, pondérées par leur intensité.
            cardioMinutes = acts.filter { it.start >= now - 28 * 86_400_000L }.sumOf { a -> a.minutes * when (a.kind) {
                Bridge.Kind.RUN, Bridge.Kind.SWIM -> 1.0; Bridge.Kind.BIKE -> 0.9; Bridge.Kind.HIKE -> 0.8; Bridge.Kind.WALK -> 0.5; Bridge.Kind.OTHER -> 0.6
                Bridge.Kind.YOGA -> 0.3; Bridge.Kind.STRENGTH -> 0.0 } }
            steps = runCatching { (1L..28L).mapNotNull { Bridge.healthValue(ctx, Metric.STEPS, LocalDate.now().minusDays(it)) }.takeIf { it.size >= 7 }?.average() }.getOrNull()
            activityDays += acts.map { java.time.Instant.ofEpochMilli(it.start).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() }
        }
        // Jours où l'app a servi : repas notés, pesées, activités (les séances sont ajoutées par le moteur).
        val tracked = runCatching { NutriApp.repository.entries.loggedDays().toSet() + NutriApp.repository.weighIns.all().map { it.epochDay } }.getOrDefault(emptySet()) + activityDays
        val target = runCatching {
            WorkoutApp.store.state.value.let { st -> st.programs.firstOrNull { it.id == st.settings.activeProgramId }?.leagueTarget() }
        }.getOrNull() ?: League.state.value.weeklyTargets["STRENGTH"] ?: 3
        fun at(t: Long) = AthleteStatsEngine.compute(AthleteStatsEngine.Input(history, db, bodyweight, sex, age, t, target, vo2, runs, cardioMinutes, trackedDays = tracked, dailySteps28 = steps))
        return Snapshot(at(now), at(now - 7 * 86_400_000L), at(now - 28 * 86_400_000L))
    }

    // ---- Packs -------------------------------------------------------------------------------------------

    /** Ce qu'a donné chaque carte : nouvelle, niveau gagné (avant → après) ou éclats rendus au niveau max. */
    data class Gain(val pull: Pull, val fresh: Boolean, val copiesBefore: Int, val copiesAfter: Int, val refund: Int) {
        val levelBefore get() = Levels.of(copiesBefore)
        val levelAfter get() = Levels.of(copiesAfter)
        val levelUp get() = !fresh && levelAfter > levelBefore
    }
    data class Opening(val result: PackEngine.Result, val gains: List<Gain>, val booster: Booster) {
        val fresh get() = gains.filter { it.fresh }.map { it.pull.key }.toSet()
        val refund get() = gains.sumOf { it.refund }
    }

    fun canOpen() = state.value.bonusPacks > 0 || state.value.eclats >= PACK_PRICE

    /** Ouvre un pack : par le serveur, ou en local en mode démo. Un pack offert passe avant les éclats. */
    suspend fun open(ctx: Context, booster: Booster): Opening {
        if (!state.value.demo) return Economy.open(ctx, booster).also { update(ctx) { it.copy(booster = booster) } }
        var opening: Opening? = null
        update(ctx) { s ->
            val paid = when { s.bonusPacks > 0 -> s.copy(bonusPacks = s.bonusPacks - 1); s.eclats >= PACK_PRICE -> s.copy(eclats = s.eclats - PACK_PRICE); else -> return@update s }
            val result = PackEngine.open(PackEngine.seed(s.secret, s.packsOpened), s.sinceRare, s.sinceEpic, booster)
            var st = paid
            val gains = result.pulls.map { p ->
                val before = st.copies(p.card)
                // Au niveau max, un doublon d'une finition déjà connue rend des éclats ; une nouvelle finition est toujours gardée.
                val maxed = Levels.of(before) >= Levels.MAX && p.key in st.owned
                if (maxed) { st = st.copy(eclats = st.eclats + p.card.rarity.refund); Gain(p, false, before, before, p.card.rarity.refund) }
                else { st = st.copy(owned = st.owned + (p.key to ((st.owned[p.key] ?: 0) + 1))); Gain(p, before == 0, before, before + 1, 0) }
            }
            opening = Opening(result, gains, booster)
            st.copy(booster = booster, packsOpened = st.packsOpened + 1, sinceRare = result.sinceRare, sinceEpic = result.sinceEpic)
        }
        return opening ?: throw Economy.Unavailable("Pas assez d'éclats pour ce pack.")
    }

    suspend fun craft(ctx: Context, card: CardDef) {
        if (!state.value.demo) return Economy.craft(ctx, card)
        if (state.value.eclats < card.rarity.craft) throw Economy.Unavailable("Pas assez d'éclats pour ce fond.")
        update(ctx) { s -> s.copy(eclats = s.eclats - card.rarity.craft, owned = s.owned + ("${card.id}:${Finish.NORMALE.name}" to ((s.owned["${card.id}:${Finish.NORMALE.name}"] ?: 0) + 1))) }
    }

    fun setBackground(ctx: Context, key: String?) = update(ctx) { it.copy(background = key) }
    fun background(): Pull? = state.value.background?.let(Pull::of)

    // ---- Quêtes ------------------------------------------------------------------------------------------

    enum class Difficulty(val label: String, val color: Long) {
        FACILE("Facile", 0xFF39D98A), NORMAL("Normal", 0xFF4FA8FF), DIFFICILE("Difficile", 0xFFFFB238), HEROIQUE("Héroïque", 0xFFFF4F7B)
    }
    /** [reward] : éclats affichés, les mêmes que ceux que crédite le serveur (avant le plafond de la semaine). */
    data class Quest(val id: String, val title: String, val difficulty: Difficulty, val progress: Int, val target: Int, val claimed: Boolean, val reward: Int) {
        val complete get() = progress >= target
    }

    fun quests(today: LocalDate = LocalDate.now()): List<Quest> {
        val zone = ZoneId.systemDefault()
        val history = history()
        val db = WorkoutApp.db.value
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekStart = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val todays = history.filter { it.startTime >= dayStart }
        val week = history.filter { it.startTime >= weekStart }
        val claimed = state.value.claimed
        val d = today.toEpochDay(); val w = monday.toEpochDay()
        val target = runCatching { WorkoutApp.store.state.value.let { st -> st.programs.firstOrNull { it.id == st.settings.activeProgramId }?.leagueTarget() } }.getOrNull() ?: 3
        val sets = todays.sumOf { wk -> wk.exercises.sumOf { Analytics.workingSets(it).size } }
        val rirDone = todays.any { wk -> wk.exercises.flatMap { Analytics.workingSets(it) }.let { s -> s.isNotEmpty() && s.all { it.rir != null } } }
        val legs = week.count { wk -> wk.exercises.any { e -> db?.exercise(e.exerciseId)?.muscleGroups?.any { it in Stat.JAM.muscles } == true } }
        val record = db != null && week.any { wk -> wk.exercises.any { e ->
            val ex = db.exercise(e.exerciseId) ?: return@any false
            val now = Analytics.bestPerformance(ex, Analytics.workingSets(e)) ?: return@any false
            val before = history.filter { it.startTime < weekStart }.flatMap { it.exercises }.filter { it.exerciseId == e.exerciseId }
                .mapNotNull { Analytics.bestPerformance(ex, Analytics.workingSets(it)) }.maxOrNull()
            ex.usesWeight && before != null && now > before * 1.005
        } }
        fun q(id: String, title: String, diff: Difficulty, p: Int, t: Int, reward: Int) = Quest(id, title, diff, p.coerceAtMost(t), t, id in claimed, reward)
        val r = Economy.Rewards
        return listOf(
            q("d:session:$d", "Valider une séance aujourd'hui", Difficulty.FACILE, todays.size, 1, r.DAILY),
            q("d:rir:$d", "Noter le RIR de toutes les séries d'une séance", Difficulty.FACILE, if (rirDone) 1 else 0, 1, r.DAILY),
            q("d:sets:$d", "Faire 12 séries de travail aujourd'hui", Difficulty.FACILE, sets, 12, r.DAILY),
            q("w:target:$w", "Atteindre ton objectif de $target séances", Difficulty.DIFFICILE, week.size, target, r.TARGET),
            q("w:legs:$w", "Deux séances qui travaillent les jambes", Difficulty.DIFFICILE, legs, 2, r.LEGS),
            q("w:pr:$w", "Battre un record de 1RM estimé", Difficulty.HEROIQUE, if (record) 1 else 0, 1, r.PR),
        )
    }

    /** Récupère une quête accomplie : le serveur vérifie la date et le montant, et applique le plafond de la semaine. */
    suspend fun claim(ctx: Context, quest: Quest) {
        if (!quest.complete || quest.id in state.value.claimed) return
        if (!state.value.demo) return Economy.sync(ctx, listOf(Economy.Quest(quest.id)))
        update(ctx) { s -> s.copy(claimed = (s.claimed + quest.id).toList().takeLast(200).toSet(), eclats = s.eclats + quest.reward,
            ledger = (s.ledger + Earn(System.currentTimeMillis(), quest.reward, quest.title)).takeLast(40)) }
    }

    /** Quête de la semaine du Groupe accomplie : quelques éclats, trois fois par semaine au plus (décidé par le serveur). */
    fun earnLeague(ctx: Context, id: String) {
        if (state.value.demo) return earn(ctx, Economy.Rewards.LEAGUE, "Quête de la semaine")
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { runCatching { Economy.sync(ctx, listOf(Economy.League(id))) } }
    }

    // ---- Mode démo (builds de test) ------------------------------------------------------------------------

    fun setDemo(ctx: Context, on: Boolean) = update(ctx) { it.copy(demo = on, bonusPacks = if (on) it.bonusPacks + 10 else it.bonusPacks, eclats = if (on) it.eclats + 500 else it.eclats,
        // Le premier pack de démo garantit une épique, pour voir l'invocation tout de suite.
        sinceRare = if (on) PackEngine.RARE_PITY - 2 else it.sinceRare, sinceEpic = if (on) PackEngine.EPIC_PITY - 1 else it.sinceEpic) }

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
        val rival = stats(Stat.PEC to 90, Stat.DOS to 90, Stat.BRA to 88, Stat.JAM to 84, Stat.REG to 84, Stat.CAR to 74)
    }
}
