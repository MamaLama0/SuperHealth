package app.superhealth.cards

import android.content.Context
import app.superhealth.backup.Secrets
import app.superhealth.league.Relay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import app.superhealth.cards.CardStore.PACK_PRICE

/**
 * Client de l'économie tenue par le serveur SuperHealth (route /v2/economy, voir economy.mjs) : éclats, packs offerts,
 * collection, fusion des doublons, échanges entre joueurs, pack doré, quêtes et tirage. L'app n'envoie que des événements (séance, quête) avec un identifiant opaque
 * et une date ; le serveur fixe les quêtes et leurs montants et refuse doublons et triches. Le mode démo reste local.
 */
object Economy {
    class Unavailable(message: String) : Exception(message)

    /**
     * Quête fixée par le serveur : son genre, son palier de difficulté (0 à 2, adapté à l'utilisateur) et ses éclats.
     * L'app en tire l'objectif concret à partir des habitudes locales (voir [CardStore.quests]).
     */
    @Serializable data class QuestDef(val id: String, val kind: String, val scope: String, val tier: Int = 1, val reward: Int = 0, val stat: String? = null)
    @Serializable data class Gain(val key: String, val copiesBefore: Int, val copiesAfter: Int, val refund: Int = 0)
    /** Mise d'un échange : une carte ou des éclats, jamais les deux. */
    @Serializable data class Stake(val card: String? = null, val eclats: Int? = null) {
        val pull get() = card?.let(Pull::of)
        val json get() = buildJsonObject { if (card != null) put("card", card) else put("eclats", eclats ?: 0) }
    }
    /** Proposition d'échange : [give] est mis de côté par celui qui propose ([from] ou [to] : l'autre joueur). */
    @Serializable data class Trade(val id: String, val give: Stake, val want: Stake, val at: Long, val to: String? = null, val from: String? = null)
    @Serializable data class Trades(val out: List<Trade> = emptyList(), @kotlinx.serialization.SerialName("in") val incoming: List<Trade> = emptyList())
    @Serializable data class Browse(val owned: Map<String, Int> = emptyMap())
    /**
     * Défi de la semaine entre amis : même quête et même objectif pour tous ([members], identifiants d'échange). En
     * course ([race]) le premier qui la finit gagne [reward] ; ensemble, chacun le reçoit quand tous l'ont finie.
     */
    @Serializable data class Challenge(val id: String, val type: String, val kind: String, val goal: Int, val stat: String? = null, val reward: Int = 0, val week: Long = 0,
                                       val members: List<String> = emptyList(), val done: List<String> = emptyList(), val winner: String? = null, val packs: Int = 0) {
        val race get() = type == "race"
    }
    /**
     * Récompenses en vigueur, envoyées par le serveur à chaque appel (réglables sans mise à jour de l'app, voir rewards.mjs) :
     * quêtes de la semaine en packs offerts, défis entre amis, éclats des quêtes du jour.
     */
    @Serializable data class Prize(val packs: Int = 0, val eclats: Int = 0)
    @Serializable data class Weekly(val count: Int = 3, val packs: Int = 1, val done: Int = 0)
    @Serializable data class Challenges(val race: Prize = Prize(2), val together: Prize = Prize(1))
    @Serializable data class Rewards(val weekly: Weekly = Weekly(), val challenge: Challenges = Challenges())
    /** Cadeau du serveur (packs offerts avec une mise à jour, par exemple), versé une fois par compte. */
    @Serializable data class Gift(val id: String = "", val packs: Int = 0, val eclats: Int = 0, val note: String = "")
    @Serializable data class View(val eclats: Int = 0, val bonusPacks: Int = 0, val opened: Long = 0, val untilGold: Int = PackEngine.GOLD_EVERY, val owned: Map<String, Int> = emptyMap(),
                                  val merged: Map<String, Int> = emptyMap(), val tradeId: String? = null, val trades: Trades = Trades(), val browse: Browse? = null, val traded: Boolean? = null, val challenge: Challenge? = null,
                                  val badges: List<String>? = null, val baseline: Boolean? = null, val streaks: Map<String, CardStore.StreakPaid>? = null,
                                  val claimed: List<String> = emptyList(), val quests: List<QuestDef> = emptyList(), val pulls: List<Gain>? = null, val prism: Boolean = false, val gold: Boolean = false,
                                  val rewards: Rewards? = null, val received: List<Gift>? = null, val price: Int = PACK_PRICE)
    sealed interface Event { val json: JsonObject }
    data class Session(val id: String, val start: Long, val target: Int) : Event {
        override val json get() = buildJsonObject { put("type", "session"); put("id", id); put("start", start); put("target", target) }
    }
    data class Quest(val id: String) : Event { override val json get() = buildJsonObject { put("type", "quest"); put("id", id) } }
    data class League(val id: String) : Event { override val json get() = buildJsonObject { put("type", "league"); put("id", id) } }
    /** Palier de badge atteint : payé une fois. Série tenue cette semaine : bonus qui grandit de semaine en semaine. */
    data class Badge(val id: String) : Event { override val json get() = buildJsonObject { put("type", "badge"); put("id", id) } }
    /** Départ de la mise à jour : les badges déjà obtenus comptent comme payés, sans éclats. */
    data class Baseline(val badges: List<String>) : Event { override val json get() = buildJsonObject { put("type", "baseline"); put("badges", JsonArray(badges.map(::JsonPrimitive))) } }
    data class Streak(val track: String, val weeks: Int) : Event { override val json get() = buildJsonObject { put("type", "streak"); put("track", track); put("weeks", weeks) } }

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    private fun token() = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    @Synchronized private fun secret(ctx: Context, name: String) = Secrets.get(ctx, name) ?: token().also { Secrets.put(ctx, name, it) }

    fun available() = Relay.base() != null

    private const val ACCOUNT = "cards_economy_account"
    private const val TOKEN = "cards_economy_token"
    @Serializable private data class Account(val account: String, val token: String)
    private val hex = Regex("[a-f0-9]{64}")

    /** Compte et jeton du serveur, pour la sauvegarde chiffrée (null tant qu'aucun appel n'a créé le compte). */
    fun exportAccount(ctx: Context): String? {
        val a = Secrets.get(ctx, ACCOUNT) ?: return null; val t = Secrets.get(ctx, TOKEN) ?: return null
        return json.encodeToString(Account.serializer(), Account(a, t))
    }

    /** Reprend le compte d'une sauvegarde : la collection et le portefeuille reviennent à la prochaine synchronisation. */
    fun importAccount(ctx: Context, text: String) {
        val a = runCatching { json.decodeFromString(Account.serializer(), text) }.getOrNull() ?: return
        if (a.account.matches(hex) && a.token.matches(hex)) Secrets.putAll(ctx, mapOf(ACCOUNT to a.account, TOKEN to a.token))
    }

    private suspend fun call(ctx: Context, body: JsonObject): View = withContext(Dispatchers.IO) {
        val base = Relay.base() ?: throw Unavailable("Les packs passent par le serveur SuperHealth : active-le dans Groupe › Réglages.")
        val request = Request.Builder().url("$base/v2/economy/" + secret(ctx, ACCOUNT))
            .header("X-Write-Token", secret(ctx, TOKEN))
            .apply { Relay.publicationKey(ctx).takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .post(JsonObject(body + ("today" to JsonPrimitive(java.time.LocalDate.now().toEpochDay()))).toString().toRequestBody("application/json".toMediaType())).build()
        val response = try { http.newCall(request).execute() } catch (e: java.io.IOException) { throw Unavailable("Serveur injoignable. Réessaie dans un instant.") }
        response.use { r ->
            val text = r.body.string()
            when {
                r.code == 402 -> throw Unavailable(if (body["op"]?.jsonPrimitive?.content?.startsWith("trade") == true) "Il manque la carte ou les éclats pour cet échange." else "Pas assez d'éclats pour ça.")
                r.code == 404 && body["op"]?.jsonPrimitive?.content?.startsWith("trade") == true -> throw Unavailable("Cet échange n'existe plus, ou ton ami n'a pas encore ouvert de pack.")
                r.code == 404 || r.code == 405 -> throw Unavailable("Le serveur SuperHealth doit être mis à jour pour les packs.")
                r.code == 429 -> throw Unavailable("Doucement : réessaie dans une minute.")
                !r.isSuccessful && text.isBlank() -> throw Unavailable("Le serveur a refusé la demande (${r.code}).")
            }
            json.decodeFromString(View.serializer(), text)
        }
    }

    /** Reporte la vue du serveur dans l'état local (affichage hors ligne, fonds possédés). */
    private fun apply(ctx: Context, v: View) = CardStore.update(ctx) { s ->
        s.copy(eclats = v.eclats, bonusPacks = v.bonusPacks, packsOpened = v.opened, owned = v.owned, quests = v.quests, merged = v.merged, challenge = v.challenge, tradeId = v.tradeId ?: s.tradeId, trades = v.trades,
            badgesPaid = v.badges?.toSet() ?: s.badgesPaid, started = s.started || v.baseline == true, streakPaid = v.streaks ?: s.streakPaid,
            claimed = v.claimed.filter { it.startsWith("q:") }.map { it.removePrefix("q:") }.toSet(), synced = System.currentTimeMillis(),
            rewards = v.rewards ?: s.rewards, price = v.price, gifts = (s.gifts + v.received.orEmpty().filter { g -> g.packs > 0 || g.eclats > 0 }).takeLast(5))
    }

    suspend fun sync(ctx: Context, events: List<Event> = emptyList()) {
        // Les identifiants d'échange des amis : le serveur ne forme un défi qu'entre amis réciproques.
        val friends = JsonArray(app.superhealth.league.League.state.value.friends.mapNotNull { it.cardTrade }.distinct().take(200).map(::JsonPrimitive))
        val body = if (events.isEmpty()) buildJsonObject { put("op", "state"); put("friends", friends) }
            else buildJsonObject { put("op", "events"); put("events", JsonArray(events.take(40).map { it.json })); put("friends", friends) }
        apply(ctx, call(ctx, body))
    }

    /** Défi fini : le serveur paie le premier (course) ou tout le monde quand le dernier finit (ensemble). */
    suspend fun finishChallenge(ctx: Context, id: String) = apply(ctx, call(ctx, buildJsonObject { put("op", "challenge-done"); put("id", id) }))

    suspend fun open(ctx: Context, booster: Booster): CardStore.Opening {
        val v = call(ctx, buildJsonObject { put("op", "open"); put("booster", booster.ordinal) })
        apply(ctx, v)
        val st = CardStore.state.value
        val gains = v.pulls.orEmpty().mapNotNull { g -> Pull.of(g.key)?.let { CardStore.Gain(it, g.copiesBefore == 0, g.copiesBefore, g.copiesAfter, st.level(it.card), st.canFuse(it.card)) } }
        if (gains.isEmpty()) throw Unavailable("Le pack n'a pas pu être ouvert.")
        return CardStore.Opening(PackEngine.Result(gains.map { it.pull }, v.prism, v.gold), gains, booster)
    }

    suspend fun fuse(ctx: Context, card: CardDef) = apply(ctx, call(ctx, buildJsonObject { put("op", "fuse"); put("card", card.id) }))

    /** Collection d'un ami (exemplaires par fond), pour choisir ce qu'on lui demande. */
    suspend fun browse(ctx: Context, tradeId: String): Map<String, Int> =
        call(ctx, buildJsonObject { put("op", "trade-browse"); put("to", tradeId) }).also { apply(ctx, it) }.browse?.owned.orEmpty()

    suspend fun offer(ctx: Context, tradeId: String, give: Stake, want: Stake) =
        apply(ctx, call(ctx, buildJsonObject { put("op", "trade-offer"); put("to", tradeId); put("give", give.json); put("want", want.json) }))

    /** Répond à un échange : « accept », « decline » (reçu) ou « cancel » (proposé). Vrai si l'échange a eu lieu. */
    suspend fun answer(ctx: Context, id: String, op: String): Boolean =
        call(ctx, buildJsonObject { put("op", "trade-$op"); put("id", id) }).also { apply(ctx, it) }.traded == true
}
