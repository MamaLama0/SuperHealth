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

/**
 * Client de l'économie tenue par le serveur SuperHealth (route /v2/economy, voir economy.mjs) : éclats, packs offerts,
 * collection, garanties et tirage. L'app n'envoie que des événements (séance, quête) avec un identifiant opaque et une
 * date ; le serveur fixe les montants et refuse doublons et triches. Le mode démo des builds de test reste local.
 */
object Economy {
    /** Gains affichés dans l'app : le serveur applique les mêmes, plus le plafond de la semaine. */
    object Rewards { const val DAILY = 6; const val TARGET = 30; const val LEGS = 20; const val PR = 30; const val LEAGUE = 15; const val SESSION_POOL = 120; const val WEEK_CAP = 330 }

    class Unavailable(message: String) : Exception(message)

    @Serializable data class Week(val earned: Int = 0, val cap: Int = Rewards.WEEK_CAP)
    @Serializable data class Gain(val key: String, val copiesBefore: Int, val copiesAfter: Int, val refund: Int)
    @Serializable data class View(val eclats: Int = 0, val bonusPacks: Int = 0, val sinceRare: Int = 0, val sinceEpic: Int = 0, val owned: Map<String, Int> = emptyMap(),
                                  val claimed: List<String> = emptyList(), val week: Week = Week(), val pulls: List<Gain>? = null, val prism: Boolean = false)
    sealed interface Event { val json: JsonObject }
    data class Session(val id: String, val start: Long, val target: Int) : Event {
        override val json get() = buildJsonObject { put("type", "session"); put("id", id); put("start", start); put("target", target) }
    }
    data class Quest(val id: String) : Event { override val json get() = buildJsonObject { put("type", "quest"); put("id", id) } }
    data class League(val id: String) : Event { override val json get() = buildJsonObject { put("type", "league"); put("id", id) } }

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    private fun token() = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    @Synchronized private fun secret(ctx: Context, name: String) = Secrets.get(ctx, name) ?: token().also { Secrets.put(ctx, name, it) }

    fun available() = Relay.base() != null

    private suspend fun call(ctx: Context, body: JsonObject): View = withContext(Dispatchers.IO) {
        val base = Relay.base() ?: throw Unavailable("Les packs passent par le serveur SuperHealth : active-le dans Groupe › Réglages.")
        val request = Request.Builder().url("$base/v2/economy/" + secret(ctx, "cards_economy_account"))
            .header("X-Write-Token", secret(ctx, "cards_economy_token"))
            .apply { Relay.publicationKey(ctx).takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val response = try { http.newCall(request).execute() } catch (e: java.io.IOException) { throw Unavailable("Serveur injoignable. Réessaie dans un instant.") }
        response.use { r ->
            val text = r.body.string()
            when {
                r.code == 402 -> throw Unavailable("Pas assez d'éclats pour ça.")
                r.code == 404 || r.code == 405 -> throw Unavailable("Le serveur SuperHealth doit être mis à jour pour les packs.")
                r.code == 429 -> throw Unavailable("Doucement : réessaie dans une minute.")
                !r.isSuccessful && text.isBlank() -> throw Unavailable("Le serveur a refusé la demande (${r.code}).")
            }
            json.decodeFromString(View.serializer(), text)
        }
    }

    /** Reporte la vue du serveur dans l'état local (affichage hors ligne, fonds possédés). */
    private fun apply(ctx: Context, v: View) = CardStore.update(ctx) { s ->
        s.copy(eclats = v.eclats, bonusPacks = v.bonusPacks, sinceRare = v.sinceRare, sinceEpic = v.sinceEpic, owned = v.owned,
            claimed = v.claimed.filter { it.startsWith("q:") }.map { it.removePrefix("q:") }.toSet(), weekEarned = v.week.earned, weekCap = v.week.cap, synced = System.currentTimeMillis())
    }

    suspend fun sync(ctx: Context, events: List<Event> = emptyList()) {
        val body = if (events.isEmpty()) buildJsonObject { put("op", "state") }
            else buildJsonObject { put("op", "events"); put("events", JsonArray(events.take(40).map { it.json })) }
        apply(ctx, call(ctx, body))
    }

    suspend fun open(ctx: Context, booster: Booster): CardStore.Opening {
        val v = call(ctx, buildJsonObject { put("op", "open"); put("booster", booster.ordinal) })
        apply(ctx, v)
        val gains = v.pulls.orEmpty().mapNotNull { g -> Pull.of(g.key)?.let { CardStore.Gain(it, g.copiesBefore == 0, g.copiesBefore, g.copiesAfter, g.refund) } }
        if (gains.isEmpty()) throw Unavailable("Le pack n'a pas pu être ouvert.")
        return CardStore.Opening(PackEngine.Result(gains.map { it.pull }, v.prism, v.sinceRare, v.sinceEpic), gains, booster)
    }

    suspend fun craft(ctx: Context, card: CardDef) = apply(ctx, call(ctx, buildJsonObject { put("op", "craft"); put("card", card.id) }))
}
