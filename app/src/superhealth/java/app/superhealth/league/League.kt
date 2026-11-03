package app.superhealth.league

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.superhealth.Feature
import app.superhealth.SuperPrefs
import app.superhealth.bridge.Bridge
import app.superhealth.has
import app.superhealth.health.data.Metric
import app.superhealth.meditation.Meditation
import app.superhealth.nutrition.NutriApp
import app.superhealth.stretch.Stretch
import app.superhealth.training.WorkoutApp
import app.superhealth.training.domain.Analytics
import app.superhealth.nutrition.domain.stepGoalFor
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Indice d'habitudes v4 sur sept jours terminés et comparaisons sportives entre amis.
 * Cartes EC P-256 : QR/message, proximité NSD ou relais personnel facultatif.
 * Les parties inconnues restent nulles ; le score nécessite au moins trois parties.
 * La signature protège l'identité, pas la sincérité des statistiques déclarées.
 */
object League {
    enum class Pillar(val label: String) { STRENGTH("Musculation"), CARDIO("Cardio"), STEPS("Pas"), SLEEP("Sommeil"), FOOD("Nutrition"), MIND("Esprit et mobilité") }

    /** Partie : son taux de 0 à 100, null si elle ne compte pas pour cette personne. */
    data class Score(val week: String, val total: Int, val pillars: Map<Pillar, Int?>,
        val ready: Boolean = false, val endDay: Long = 0, val metrics: List<Comparison> = emptyList(), val progress: Progression.Summary? = null) {
        val coverage get() = Pillar.entries.fold(0) { mask, p -> if (pillars[p] != null) mask or (1 shl p.ordinal) else mask }
        fun comparable(friend: Friend, nowSeconds: Long) = ready && friend.ready &&
            friend.model == HealthIndex.MODEL && friend.endDay == endDay && Integer.bitCount(friend.coverage) >= HealthIndex.MIN_DOMAINS &&
            friend.time >= nowSeconds - 48 * 3600
    }

    @Serializable data class Comparison(val key: String, val label: String, val unit: String, val value: Double,
        val lowerWins: Boolean = false, val scope: String = "7d")

    @Serializable data class Card(val v: Int = 1, val k: String, val n: String, val w: String, val t: Long, val s: Int, val p: List<Int?> = emptyList(), val st: Int = 0,
        val model: Int = 1, val ready: Boolean = false, val endDay: Long = 0,
        val metrics: List<Comparison> = emptyList(), val detailSnapshot: Boolean = false, val remote: String? = null, val revision: Long = 0, val coverage: Int = 0, val hello: Boolean = false, val relayKey: String? = null, val progress: Progression.Summary? = null, val invite: Boolean = false, val profileTitle: String? = null, val profileBadges: List<String> = emptyList(), val posts: List<SocialPosts.Post> = emptyList(), val avatar: SocialPosts.Media? = null, val reactions: List<SocialReactions.Reaction> = emptyList(), val previousKey: String? = null, val endorsement: String? = null)

    @Serializable data class Friend(val id: String, val key: String, val name: String, val week: String, val time: Long, val score: Int,
                                    val pillars: List<Int?> = emptyList(), val streak: Int = 0,
                                    /** Nom choisi par moi pour cet ami (le sien reste dans [name]) ; scores reçus par semaine ; date d'ajout. */
                                    val alias: String? = null, val history: Map<String, Int> = emptyMap(), val addedAt: Long = 0,
                                    val model: Int = 1, val ready: Boolean = false, val endDay: Long = 0,
                                    val metrics: List<Comparison> = emptyList(), val metricsTime: Long = 0, val remote: String? = null, val revision: Long = 0, val coverage: Int = 0, val metricsEndDay: Long = 0, val relayKey: String? = null, val progress: Progression.Summary? = null,
                                    val trends: Map<String, List<TrendPoint>> = emptyMap(), val relayEtag: String? = null, val inviteRevision: Long = 0, val profileTitle: String? = null, val profileBadges: List<String> = emptyList(), val posts: List<SocialPosts.Post> = emptyList(), val avatar: SocialPosts.Media? = null, val reactions: List<SocialReactions.Reaction> = emptyList(), val rootKey: String? = null, val profileRevision: Long = 0) {
        val shown get() = alias?.takeIf { it.isNotBlank() } ?: name
    }

    @Serializable data class TrendPoint(val day: Long, val value: Double)

    @Serializable data class State(val friends: List<Friend> = emptyList(), val best: Map<String, Int> = emptyMap(), val sharePillars: Boolean = true,
        val shareActivities: Boolean = false, val relayUrl: String = "", val customRelay: Boolean = false, val relayEnabled: Boolean = true, val syncsPerDay: Int = 4,
        val notifyFriends: Boolean = true, val notifyReplies: Boolean = true, val notifyPosts: Boolean = true, val notifyReactions: Boolean = true,
        val notifyProgress: Boolean = true, val progressNotificationBaseline: Boolean = false, val progressNotificationLevel: Int = 0,
        val shareProfilePhoto: Boolean = true, val profileAvatar: SocialPosts.Media? = null,
        val lastSync: Long = 0, val lastSuccessfulSync: Long = 0, val syncError: String? = null, val refreshOnOpen: Boolean = false, val shareProgress: Boolean = true, val tracked: Set<String>? = null, val weeklyTargets: Map<String, Int> = emptyMap(), val earnedBadges: Set<String> = emptySet(), val observedHealthDays: Map<String, Set<Long>> = emptyMap(), val healthSince: Long? = null, val profileTitle: String? = null, val profileBadges: List<String> = emptyList(), val questFocus: Set<WeeklyQuests.Focus>? = null, val questWeeks: List<WeeklyQuests.Week> = emptyList(), val questXp: Long = 0, val questsCompleted: Long = 0, val posts: List<SocialPosts.Post> = emptyList(), val reactions: List<SocialReactions.Reaction> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val ALIAS = "superhealth_league"
    private const val PREFIX = "SH1"
    const val MAX_CARD = 65_536
    fun available() = SuperPrefs.settings.value.leagueEnabled
    val state = MutableStateFlow(State())
    val mine = MutableStateFlow<Score?>(null)
    /** Carte reçue par un lien ou un QR, en attente de confirmation. */
    val incoming = MutableStateFlow<Card?>(null)
    val incomingError = MutableStateFlow(false)

    private fun file(ctx: Context) = File(File(ctx.filesDir, "league").apply { mkdirs() }, "state.json")
    fun load(ctx: Context) { state.value = runCatching { json.decodeFromString(State.serializer(), android.util.AtomicFile(file(ctx)).openRead().bufferedReader().use { it.readText() }) }.getOrDefault(State()).let { if (it.relayUrl.isNotBlank() && !it.customRelay) it.copy(customRelay = true) else it } }
    @Synchronized private fun save(ctx: Context, s: State) {
        val atomic = android.util.AtomicFile(file(ctx))
        val out = atomic.startWrite()
        try { out.write(json.encodeToString(State.serializer(), s).toByteArray()); atomic.finishWrite(out) }
        catch (e: Exception) { atomic.failWrite(out); throw e }
        state.value = s
    }
    fun setSharePillars(ctx: Context, on: Boolean) = configure(ctx) { it.copy(sharePillars = on) }
    @Synchronized fun configure(ctx: Context, f: (State) -> State) = save(ctx, f(state.value))
    fun claimQuest(ctx: Context, metric: WeeklyQuests.Metric, today: Long = LocalDate.now().toEpochDay()) = configure(ctx) { s ->
        val week = s.questWeeks.firstOrNull { it.start == Progression.week(today) } ?: return@configure s
        val updated = WeeklyQuests.claim(week, metric, today)
        val claimed = updated.quests.filter { q -> q.claimed && week.quests.none { it.metric == q.metric && it.claimed } }
        s.copy(questWeeks = s.questWeeks.map { if (it.start == week.start) updated else it },
            questXp = s.questXp + claimed.sumOf { it.reward }, questsCompleted = s.questsCompleted + claimed.size)
    }
    fun remove(ctx: Context, id: String) = configure(ctx) { it.copy(friends = it.friends.filter { f -> f.id != id }) }

    fun weekKey(d: LocalDate = LocalDate.now()) = "%d-W%02d".format(d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    // ---- Score ------------------------------------------------------------------------------------------

    /** Taux d'une partie par rapport à un objectif atteignable une fois dans la semaine. */
    private fun pct(v: Double, target: Double) = ((v / target).coerceIn(0.0, 1.0) * 100).toInt()

    suspend fun compute(ctx: Context, today: LocalDate = LocalDate.now()): Score = withContext(Dispatchers.Default) {
        val exerciseDb = WorkoutApp.db.first { it != null }!!
        val end = today.minusDays(1)
        val start = today.minusDays(7)
        val days = (0L..6L).map { start.plusDays(it) }
        val zone = ZoneId.systemDefault()
        val fromMs = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val sh = SuperPrefs.settings.value
        val p = linkedMapOf<Pillar, Int?>()
        val history = WorkoutApp.store.state.value.history
        val workouts = history.filter { it.startTime in fromMs until toMs }
        val referenceStart = start.minusDays(28).atStartOfDay(zone).toInstant().toEpochMilli()
        val activities = if (sh.has(Feature.HEALTH)) runCatching { Bridge.activities(referenceStart, toMs - 1) }.getOrDefault(emptyList()) else emptyList()
        val acts = activities.filter { it.start in fromMs until toMs }
        val cardio = acts.filter { it.kind != Bridge.Kind.STRENGTH }
        val runs = cardio.filter { it.kind == Bridge.Kind.RUN }
        val referenceRuns = activities.filter { it.start in referenceStart until fromMs && it.kind == Bridge.Kind.RUN }
        p[Pillar.STRENGTH] = if (!sh.has(Feature.TRAINING)) null else {
            val previous = HealthIndex.strengthBests(history.filter { it.startTime in referenceStart until fromMs }, exerciseDb)
            val gain = HealthIndex.strengthBests(workouts, exerciseDb).maxOfOrNull { (id, value) -> HealthIndex.gain(value, previous[id]) } ?: 0.0
            HealthIndex.sport(workouts.size.toDouble(), (WorkoutApp.store.state.value.programs.firstOrNull { it.id == WorkoutApp.store.state.value.settings.activeProgramId }?.leagueTarget() ?: state.value.weeklyTargets["STRENGTH"] ?: 2).toDouble(), gain)
        }
        p[Pillar.CARDIO] = if (!sh.has(Feature.HEALTH) || activities.none { it.kind != Bridge.Kind.STRENGTH }) null else
            HealthIndex.sport(cardio.sumOf { it.minutes.coerceAtLeast(0) }.toDouble(), 150.0, HealthIndex.runGain(runs, referenceRuns))
        val settings = NutriApp.repository.prefs.settings.first()
        suspend fun observed(metric: Metric, date: LocalDate) = Bridge.healthValue(ctx, metric, date)?.takeIf { it > 0 }
        p[Pillar.STEPS] = if (sh.has(Feature.HEALTH)) HealthIndex.daily(days.map { date ->
            val goal = settings.stepGoalFor(date).takeIf { it > 0 } ?: 8000
            runCatching { observed(Metric.STEPS, date) }.getOrNull()?.div(goal)
        }, 1.0) else null
        p[Pillar.SLEEP] = if (sh.has(Feature.HEALTH)) HealthIndex.daily(days.map { date ->
            runCatching { observed(Metric.SLEEP, date) }.getOrNull()
        }, 420.0) else null
        p[Pillar.FOOD] = if (!sh.has(Feature.NUTRITION)) null else {
            val repo = NutriApp.repository
            val an = repo.analytics().first()
            val eaten = repo.entries.rangeFlow(start.toEpochDay(), end.toEpochDay()).first().groupBy { it.epochDay }
            val values = days.map { d ->
                val kcal = eaten[d.toEpochDay()]?.sumOf { it.kcal }
                val target = an.targetFor(d.toEpochDay()).macros.kcal
                if (kcal == null || kcal <= 0 || target <= 0) null else (100 - kotlin.math.abs(kcal / target - 1) * 200).coerceIn(0.0, 100.0)
            }
            HealthIndex.daily(values, 100.0)
        }
        p[Pillar.MIND] = if (!Meditation.available() && !Stretch.available()) null else
            pct((Meditation.minutes(start.toEpochDay(), end.toEpochDay()) + Stretch.minutes(start.toEpochDay(), end.toEpochDay())).toDouble(), 70.0)
        val tracks = ProgressRepository.selectedTracks(sh)
        val chosen = mapOf(Pillar.STRENGTH to Progression.Track.STRENGTH, Pillar.CARDIO to Progression.Track.RUN,
            Pillar.STEPS to Progression.Track.STEPS, Pillar.SLEEP to Progression.Track.SLEEP,
            Pillar.FOOD to Progression.Track.FOOD, Pillar.MIND to Progression.Track.MIND)
        chosen.forEach { (pillar, track) -> if (track !in tracks) p[pillar] = null }
        val index = HealthIndex.total(Pillar.entries.map { p[it] })
        val metrics = mutableListOf<Comparison>()
        metrics += Comparison("activities:count", "Activités", "séances", acts.size.toDouble() + workouts.count { w -> acts.none { it.gymId == w.id } })
        metrics += Comparison("training:count", "Musculation", "séances", workouts.size.toDouble())
        metrics += Comparison("training:volume", "Volume de musculation", "kg·reps", workouts.sumOf { w -> w.exercises.sumOf { Analytics.volume(it.sets) } })
        metrics += Comparison("running:count", "Courses", "courses", runs.size.toDouble())
        metrics += Comparison("running:km", "Distance courue", "km", runs.sumOf { it.distanceM?.takeIf { d -> d.isFinite() && d > 0 } ?: 0.0 } / 1000)
        metrics += Comparison("running:minutes", "Temps de course", "min", runs.sumOf { it.minutes }.toDouble())
        runs.mapNotNull(HealthIndex::runSpeed).maxOrNull()?.let { 1000 / it }?.let {
            metrics += Comparison("running:pace", "Meilleure allure moyenne (course ≥ 1 km)", "s/km", it, lowerWins = true)
        }
        runs.mapNotNull { it.distanceM?.takeIf { d -> d.isFinite() && d >= 0 } }.maxOrNull()?.let { metrics += Comparison("running:longest", "Course la plus longue", "km", it / 1000) }
        // PR : même exercice et même métrique seulement, hors échauffements. Pas de positions GPS ou de journal brut.
        history.flatMap { it.exercises }.groupBy { it.exerciseId }.entries.sortedByDescending { it.value.size }.take(30).forEach { (id, entries) ->
            val e = exerciseDb.exercise(id) ?: return@forEach
            val sets = entries.flatMap { Analytics.workingSets(it) }
            if (e.usesWeight) sets.mapNotNull { it.weight }.maxOrNull()?.takeIf { it > 0 }?.let {
                metrics += Comparison("pr:$id:weight", e.name + " · charge maximale", "kg", it, scope = "all")
            }
            Analytics.bestPerformance(e, sets)?.let { best ->
                metrics += if (e.usesWeight) Comparison("pr:$id:e1rm", e.name + " · 1RM estimé", "kg", best, scope = "all")
                    else Comparison("pr:$id:reps", e.name + " · répétitions", "reps", best, scope = "all")
            }
        }
        val key = weekKey(end)
        val progress = try { ProgressRepository.compute(ctx, today).summary }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { null }
        val s = Score(key, index ?: 0, p, index != null, end.toEpochDay(), metrics.filter { it.value.isFinite() && it.value >= 0 }, progress)
        mine.value = s
        val historyKey = HealthIndex.historyKey(s.endDay, s.coverage)
        if (index != null && state.value.best[historyKey] != index) configure(ctx) { st -> st.copy(best = (st.best + (historyKey to index)).entries.sortedBy { it.key }.takeLast(60).associate { it.key to it.value }) }
        s
    }

    // ---- Identité ---------------------------------------------------------------------------------------

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ensureKey() { GroupIdentity.current() }

    private fun b64(b: ByteArray) = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun unb64(s: String) = java.util.Base64.getUrlDecoder().decode(s)
    private fun idOf(pubB64: String) = MessageDigest.getInstance("SHA-256").digest(unb64(pubB64)).take(8).joinToString("") { "%02x".format(it) }

    private fun myPublic(): String = GroupIdentity.publicKey()
    fun publicKey(): String = myPublic()
    fun signSocial(payload: String): String {
        require(payload.toByteArray().size <= 12000); ensureKey()
        val bytes = payload.toByteArray()
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(GroupIdentity.privateKey())
            update(("SuperHealth group v1:").toByteArray()); update(bytes); sign()
        }
        return "SHG1.${b64(bytes)}.${b64(signature)}"
    }
    fun verifySocial(signed: String, expectedKey: String): String? = runCatching {
        require(signed.length <= 20000)
        val parts = signed.split('.'); require(parts.size == 3 && parts[0] == "SHG1")
        val bytes = unb64(parts[1]); require(bytes.size <= 12000)
        val public = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(expectedKey)))
        require(Signature.getInstance("SHA256withECDSA").run {
            initVerify(public); update(("SuperHealth group v1:").toByteArray()); update(bytes); verify(unb64(parts[2]))
        })
        bytes.decodeToString()
    }.getOrNull()


    /** Empreinte à comparer de vive voix : huit caractères tirés de la clé. */
    fun fingerprint(key: String) = idOf(key).take(8).uppercase().chunked(4).joinToString(" ")
    fun myFingerprint() = fingerprint(myPublic())

    // ---- Carte ------------------------------------------------------------------------------------------

    /** Carte signée de la semaine, prête à partager. */
    private var lastRevision = 0L
    @Synchronized fun card(name: String, s: Score, remote: String? = null, detailed: Boolean = false, hello: Boolean = false, relayKey: String? = null, invite: Boolean = false, includeAvatar: Boolean = true): String {
        val pub = myPublic()
        val revision = maxOf(System.currentTimeMillis(), lastRevision + 1).also { lastRevision = it }
        val c = Card(v = 2, k = pub, previousKey = GroupIdentity.current().previous, endorsement = GroupIdentity.current().endorsement, n = clean(name).ifBlank { "?" }, w = s.week, t = revision / 1000, s = s.total,
            p = if (state.value.sharePillars) Pillar.entries.map { s.pillars[it] } else emptyList(), st = 0,
            model = if (hello || invite) 2 else HealthIndex.MODEL, ready = s.ready, endDay = s.endDay,
            metrics = if (detailed && state.value.shareActivities) s.metrics else emptyList(), detailSnapshot = detailed,
            remote = remote, revision = revision, coverage = s.coverage, hello = hello, relayKey = relayKey, progress = if (state.value.shareProgress && !hello) s.progress else null, invite = invite,
            profileTitle = state.value.profileTitle.takeIf { !hello && !invite },
            posts = if (detailed && !hello && !invite) SocialPosts.visible(state.value.posts).map(SocialPosts::compact) else emptyList(),
            avatar = if (includeAvatar && detailed && !hello && !invite && state.value.shareProfilePhoto) state.value.profileAvatar else null,
            reactions = if (detailed && !hello && !invite) SocialReactions.visible(state.value.reactions) else emptyList(),
            profileBadges = if (!hello && !invite) state.value.profileBadges.filter { it in state.value.earnedBadges }.take(3) else emptyList())
        val body = json.encodeToString(Card.serializer(), boundedCard(c)).toByteArray()
        val key = GroupIdentity.privateKey()
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(key); update(body); sign() }
        return "$PREFIX.${b64(body)}.${b64(sig)}"
    }

    /** Préserve une carte lisible même avec des publications longues et beaucoup de réactions/records. */
    internal fun boundedCard(original: Card): Card {
        // Réserve pour le préfixe, les séparateurs et une signature ECDSA après encodage base64url.
        val budget = (MAX_CARD - 256) * 3 / 4
        var card = original
        while (json.encodeToString(Card.serializer(), card).toByteArray().size > budget) {
            card = when {
                card.metrics.isNotEmpty() -> card.copy(metrics = card.metrics.dropLast(maxOf(1, card.metrics.size / 4)))
                card.posts.size > 1 -> card.copy(posts = card.posts.dropLast(1))
                card.reactions.isNotEmpty() -> card.copy(reactions = card.reactions.dropLast(maxOf(1, card.reactions.size / 4)))
                else -> error("Carte de partage trop volumineuse")
            }
        }
        return card
    }

    /** Preuve éphémère : une carte interceptée ne suffit pas à obtenir les détails d'un ami. */
    fun proof(challenge: String): String {
        require(challenge.matches(Regex("[a-f0-9]{64}")))
        ensureKey()
        return b64(Signature.getInstance("SHA256withECDSA").run {
            initSign(GroupIdentity.privateKey())
            update(("SuperHealth nearby v2:" + challenge).toByteArray()); sign()
        })
    }
    fun verifyProof(key: String, challenge: String, proof: String): Boolean = runCatching {
        require(challenge.matches(Regex("[a-f0-9]{64}")) && proof.length <= 200)
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(key)))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(pub); update(("SuperHealth nearby v2:" + challenge).toByteArray()); verify(unb64(proof))
        }
    }.getOrDefault(false)

    fun link(card: String) = "superhealth://league?c=$card"

    /** Texte d'un nom reçu : sans caractères de contrôle ni d'inversion de sens, 24 signes au plus. */
    private fun clean(s: String) = s.filter { !it.isISOControl() && Character.getType(it) != Character.FORMAT.toInt() }.trim().take(24)

    /** Lit une carte (brute, ou dans un lien ou un message) et vérifie sa signature ; null si elle n'est pas valable. */
    fun parse(raw: String): Card? = runCatching {
        val m = Regex("""$PREFIX\.([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]+)""").find(raw.take(MAX_CARD * 2)) ?: return null
        if (m.value.length > MAX_CARD) return null
        val body = unb64(m.groupValues[1])
        val c = json.decodeFromString(Card.serializer(), body.decodeToString())
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(c.k)))
        val ok = Signature.getInstance("SHA256withECDSA").run { initVerify(pub); update(body); verify(unb64(m.groupValues[2])) }
        if (!GroupIdentity.validEndorsement(c.previousKey, c.k, c.endorsement)) return null
        if (!ok || c.v !in 1..2 || c.s !in 0..1000 || c.st !in 0..1000 || c.p.size > Pillar.entries.size || c.p.any { it != null && it !in 0..100 }) return null
        if (!c.w.matches(Regex("""\d{4}-W\d{2}""")) || c.t > System.currentTimeMillis() / 1000 + 86_400) return null
        if (c.metrics.size > 128 || c.metrics.any { !it.value.isFinite() || it.value < 0 || it.key.length > 160 || it.label.length > 160 || it.unit.length > 16 || it.scope !in setOf("7d", "all") }) return null
        if (c.relayKey != null && (!RelayCrypto.validKey(c.relayKey) || c.remote == null || !Relay.encryptedEndpoint(c.remote))) return null
        if (c.invite && (c.ready || c.s != 0 || c.p.any { it != null } || c.metrics.isNotEmpty() || c.progress != null)) return null
        c.progress?.let { g ->
            if (g.model != Progression.MODEL || g.totalXp !in 0..1_000_000_000L || g.level != Progression.level(g.totalXp) || g.weekXp !in 0..(7 * 120 + WeeklyQuests.MAX_WEEKLY_BONUS) ||
                g.days.size > 7 || g.days.any { it.xp !in 0..120 } || g.streaks.size > 6 || g.streaks.any { it.track !in Progression.Track.entries.map { t -> t.name } || it.current !in 0..100000 || it.best < it.current || it.target !in 1..7 || it.progress !in 0..1000 || (it.percent != null && it.percent !in 0..1000) }) return null
        }
        if (!Achievements.validDecor(c.profileTitle, c.profileBadges)) return null
        if ((c.hello || c.invite) && (c.profileTitle != null || c.profileBadges.isNotEmpty())) return null
        if (c.progress?.totals?.let { it.size > 16 || it.any { (k, v) -> k.length > 40 || v !in 0..1_000_000_000L } } == true) return null
        if (c.posts.size > SocialPosts.MAX_POSTS || c.posts.any { !SocialPosts.valid(it) } || c.posts.map { it.id }.distinct().size != c.posts.size) return null
        if ((c.hello || c.invite) && (c.posts.isNotEmpty() || c.avatar != null)) return null
        if (c.avatar != null && !ProfileAvatars.valid(c.avatar)) return null
        if (c.reactions.size > SocialReactions.MAX || c.reactions.any { !SocialReactions.valid(it) } ||
            c.reactions.distinctBy { it.author to it.post }.size != c.reactions.size) return null
        if ((c.hello || c.invite) && c.reactions.isNotEmpty()) return null
        if (c.coverage !in 0..63) return null
        if (c.hello && (c.s != 0 || c.ready || c.remote != null || c.metrics.isNotEmpty() || c.relayKey != null || c.progress != null || c.p.any { it != null })) return null
        if (c.revision < 0 || (c.revision > 0 && c.revision / 1000 != c.t)) return null
        if (c.v == 2 && (c.model !in 2..HealthIndex.MODEL || c.endDay !in 1..LocalDate.now().toEpochDay())) return null
        if (c.remote != null && !Relay.validEndpoint(c.remote)) return null
        c.copy(n = clean(c.n).ifBlank { "?" })
    }.getOrNull()

    /** Carte reçue : un ami connu est mis à jour directement, un inconnu attend la confirmation. */
    fun receive(ctx: Context, raw: String): Boolean {
        val c = parse(raw)
        if (c == null || c.hello) { incomingError.value = true; return false }
        if (c.k == runCatching { myPublic() }.getOrNull()) return true
        if (state.value.friends.any { matches(it, c) }) accept(ctx, c) else incoming.value = c
        return true
    }

    /**
     * Fusion déterministe des données déjà vérifiées. Le compteur santé est indépendant du compteur d'identité/relais :
     * une invitation récente ne bloque pas une carte santé utile, et une carte santé différée ne rétablit pas un ancien relais.
     */
    internal fun mergeFriend(old: Friend?, c: Card, now: Long): Friend? {
        if (c.hello || (old != null && !matches(old, c))) return null
        val base = old ?: Friend(idOf(c.previousKey ?: c.k), c.k, c.n, c.w, 0, 0, addedAt = now, model = c.model)
        // Les anciennes versions ne mettaient ce compteur à jour que sur invitation : leur dernière carte santé
        // peut donc aussi être la dernière source du nom et du relais.
        val metadataRevision = maxOf(base.inviteRevision, base.revision)
        if (c.invite) {
            if (old != null && c.revision <= metadataRevision) return null
            val sameRelay = c.remote == base.remote && c.relayKey == base.relayKey
            return base.copy(key = c.k, rootKey = c.previousKey ?: c.k, name = c.n, remote = c.remote, relayKey = c.relayKey,
                inviteRevision = maxOf(metadataRevision, c.revision), relayEtag = base.relayEtag.takeIf { sameRelay })
        }
        if (old != null && (if (c.revision > 0 && old.revision > 0) c.revision <= old.revision else c.t <= old.time)) return null
        val updateMetadata = old == null || c.revision >= metadataRevision
        val remote = if (updateMetadata && c.v == 2) c.remote else base.remote
        val relayKey = when {
            !updateMetadata || c.v != 2 -> base.relayKey
            c.remote == null -> null
            c.relayKey != null -> c.relayKey
            c.remote == base.remote -> base.relayKey // les cartes stockées sur le relais n'embarquent jamais cette clé
            else -> null
        }
        val sameRelay = remote == base.remote && relayKey == base.relayKey
        val updateDecor = c.detailSnapshot && c.revision >= base.profileRevision
        val history = if (c.model in 2..HealthIndex.MODEL && c.ready) base.history + (HealthIndex.historyKey(c.endDay, c.coverage, c.model) to c.s) else base.history
        val trends = base.trends.toMutableMap()
        if (c.detailSnapshot) c.metrics.forEach { m ->
            trends[m.key] = (trends[m.key].orEmpty().filter { it.day != c.endDay } + TrendPoint(c.endDay, m.value)).sortedBy { it.day }.takeLast(60)
        }
        return base.copy(key = c.k, rootKey = c.previousKey ?: c.k, name = if (updateMetadata) c.n else base.name, week = c.w, time = c.t, score = c.s,
            pillars = c.p, streak = c.st,
            history = history.entries.sortedBy { it.key }.takeLast(60).associate { it.key to it.value },
            model = c.model, ready = c.ready, endDay = c.endDay,
            metrics = if (c.detailSnapshot) c.metrics else base.metrics,
            metricsTime = if (c.detailSnapshot) c.t else base.metricsTime,
            remote = remote, revision = c.revision, coverage = c.coverage,
            metricsEndDay = if (c.detailSnapshot) c.endDay else base.metricsEndDay,
            relayKey = relayKey, progress = c.progress, trends = trends, profileTitle = if (updateDecor) c.profileTitle else base.profileTitle, profileBadges = if (updateDecor) c.profileBadges else base.profileBadges,
            profileRevision = if (updateDecor) c.revision else base.profileRevision,
            posts = if (c.detailSnapshot) c.posts else base.posts, avatar = if (updateDecor) c.avatar else base.avatar,
            reactions = if (c.detailSnapshot) c.reactions else base.reactions,
            relayEtag = base.relayEtag.takeIf { sameRelay },
            inviteRevision = if (updateMetadata) maxOf(metadataRevision, c.revision) else metadataRevision)
    }

    /** Applique une carte vérifiée ; les rejeux et les cartes sans mise à jour utile sont ignorés. */
    @Synchronized fun accept(ctx: Context, c: Card, notifyAdded: Boolean = false) {
        val id = idOf(c.previousKey ?: c.k)
        val old = state.value.friends.firstOrNull { it.id == id }
        val next = mergeFriend(old, c, System.currentTimeMillis()) ?: return
        save(ctx, state.value.copy(friends = state.value.friends.filter { it.id != id } + next))
        if (notifyAdded && old == null) SocialNotifications.friendAdded(ctx, next)
        SocialNotifications.postsReceived(ctx, old, next)
        SocialNotifications.reactionsReceived(ctx, old, next)
        LeagueWorker.schedule(ctx)
    }

    fun rename(ctx: Context, id: String, alias: String) =
        configure(ctx) { s -> s.copy(friends = s.friends.map { if (it.id == id) it.copy(alias = clean(alias).ifBlank { null }) else it }) }

    fun matches(friend: Friend, card: Card) = (friend.rootKey ?: friend.key) == (card.previousKey ?: card.k) &&
        (friend.key == card.k || GroupIdentity.validEndorsement(card.previousKey, card.k, card.endorsement))

    fun isFriend(key: String) = state.value.friends.any { it.key == key }

    /** Ma carte du moment : score recalculé. */
    suspend fun myCard(ctx: Context, detailed: Boolean = false, includeRelayKey: Boolean = true, includeAvatar: Boolean = true): String = card(app.superhealth.ui.userName() ?: "?", compute(ctx), Relay.myEndpoint(ctx), detailed,
        relayKey = if (includeRelayKey && Relay.myEndpoint(ctx) != null) Relay.readKey(ctx) else null, includeAvatar = includeAvatar)
    fun inviteCard(ctx: Context): String {
        val end = LocalDate.now().minusDays(1)
        return card(app.superhealth.ui.userName() ?: "?", Score(weekKey(end), 0, emptyMap(), endDay = end.toEpochDay()), Relay.myEndpoint(ctx),
            relayKey = if (Relay.myEndpoint(ctx) != null) Relay.readKey(ctx) else null, invite = true)
    }
    fun myId() = idOf(GroupIdentity.root())
    fun helloCard(): String {
        val end = LocalDate.now().minusDays(1)
        return card("?", Score(weekKey(end), 0, emptyMap(), endDay = end.toEpochDay()), hello = true)
    }
}
