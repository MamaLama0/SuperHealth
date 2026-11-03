package app.superhealth.league

import android.content.Context
import app.superhealth.backup.Secrets
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json

/** Boîte chiffrée facultative : identité, prénom, statistiques et clé AES ne sont jamais envoyés en clair au relais. */
object Relay {
    private val lock = Mutex()
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    @Synchronized private fun secret(ctx: Context, name: String, make: () -> String) = Secrets.get(ctx, name) ?: make().also { Secrets.put(ctx, name, it) }
    private fun token() = SecureRandom().generateSeed(32).joinToString("") { "%02x".format(it) }
    fun readKey(ctx: Context) = secret(ctx, "league_aes_read_key", RelayCrypto::newKey)
    private fun allowedTransport(url: okhttp3.HttpUrl) = url.isHttps ||
        (nodomain.freeyourgadget.gadgetbridge.BuildConfig.DEBUG &&
            nodomain.freeyourgadget.gadgetbridge.BuildConfig.APPLICATION_ID == "app.superhealth.qa" &&
            url.scheme == "http" && url.host == "127.0.0.1")
    fun validBase(raw: String): Boolean = raw.toHttpUrlOrNull()?.let {
        allowedTransport(it) && it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null && it.encodedPath == "/"
    } ?: false
    fun validEndpoint(raw: String): Boolean = raw.toHttpUrlOrNull()?.let {
        allowedTransport(it) && it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null &&
            (it.encodedPath.matches(Regex("/v2/cards/[a-f0-9]{64}/[a-f0-9]{64}")) || it.encodedPath.matches(Regex("/v1/cards/[a-f0-9]{16}/[a-f0-9]{64}")))
    } ?: false
    fun encryptedEndpoint(raw: String) = validEndpoint(raw) && raw.toHttpUrlOrNull()?.encodedPath?.startsWith("/v2/") == true
    fun base(): String? {
        val state = League.state.value
        return (if (state.customRelay) state.relayUrl else RelayDefaults.url).trimEnd('/').takeIf { state.relayEnabled && validBase(it) }
    }
    internal fun publicationKey(ctx: Context) = if (League.state.value.customRelay) Secrets.get(ctx, "league_publish_key").orEmpty() else RelayDefaults.key
    internal fun defaultBase() = RelayDefaults.url.takeIf(::validBase)
    fun myEndpoint(ctx: Context): String? {
        val base = base() ?: return null
        val mailbox = secret(ctx, "league_mailbox_v2", ::token)
        val read = secret(ctx, "league_read_capability_v2", ::token)
        return "$base/v2/cards/$mailbox/$read"
    }
    internal fun auth(ctx: Context, request: Request.Builder) = request.header("Authorization", "Bearer " + publicationKey(ctx))
        .header("X-Write-Token", secret(ctx, "league_owner_token_v2", ::token))
    private fun readBounded(r: Response): String = r.body.byteStream().use { input ->
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = input.read(buffer); if (n < 0) break; check(out.size() + n <= 98304) { "réponse trop volumineuse" }; out.write(buffer, 0, n) }
        out.toString("UTF-8")
    }
    private suspend fun publishCard(ctx: Context, endpoint: String, withAvatar: Boolean = true, force: Boolean = false) {
        if (withAvatar) {
            val avatar = try { ProfileAvatars.prepare(ctx, endpoint) }
            catch (_: SocialPosts.MissingMediaMailbox) {
                // Un média exige une boîte existante. Amorcer une carte sans avatar, puis publier la carte complète.
                publishCard(ctx, endpoint, withAvatar = false, force = true)
                ProfileAvatars.prepare(ctx, endpoint)
            }
            if (League.state.value.profileAvatar != avatar) League.configure(ctx) { it.copy(profileAvatar = avatar) }
        }
        val raw = League.myCard(ctx, detailed = true, includeRelayKey = false, includeAvatar = withAvatar)
        val card = League.parse(raw) ?: error("carte locale non valable")
        check(card.relayKey == null)
        val stable = json.encodeToString(League.Card.serializer(), card.copy(t = 0, revision = 0))
        val digest = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray()).joinToString("") { "%02x".format(it) }
        val publishedAt = Secrets.get(ctx, "league_published_at_v2")?.toLongOrNull() ?: 0
        if (force || Secrets.get(ctx, "league_published_hash_v2") != digest || System.currentTimeMillis() - publishedAt > TimeUnit.HOURS.toMillis(24)) {
            val encrypted = RelayCrypto.encrypt(raw, readKey(ctx), endpoint.toHttpUrlOrNull()!!.encodedPath, card.revision)
            http.newCall(auth(ctx, Request.Builder().url(endpoint)).put(encrypted.toRequestBody("application/json".toMediaType())).build()).execute().use {
                check(it.isSuccessful) { "publication : HTTP ${it.code}" }
            }
            Secrets.put(ctx, "league_published_hash_v2", digest); Secrets.put(ctx, "league_published_at_v2", System.currentTimeMillis().toString())
        }
    }
    /** Explicit publication does not depend on whether a friend's server answers. */
    internal suspend fun publishOnly(ctx: Context) = lock.withLock { withContext(Dispatchers.IO) {
        check(League.available())
        val endpoint = myEndpoint(ctx) ?: error("Activez le serveur dans les préférences sociales.")
        publishCard(ctx, endpoint)
    } }
    suspend fun sync(ctx: Context) = lock.withLock { withContext(Dispatchers.IO) {
        if (!League.available() || !League.state.value.relayEnabled) return@withContext
        val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
        try { InvitationCodes.collectResponses(ctx) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { errors += "Invitations indisponibles. Réessayez plus tard." }
        myEndpoint(ctx)?.let { endpoint ->
            try { publishCard(ctx, endpoint) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { errors += "Publication impossible. Vérifiez la connexion ou les réglages du serveur." }
        }
        val permits = Semaphore(3)
        coroutineScope {
            League.state.value.friends.map { friend -> async {
                val endpoint = friend.remote?.takeIf(::validEndpoint) ?: return@async
                if (!encryptedEndpoint(endpoint) || !RelayCrypto.validKey(friend.relayKey.orEmpty())) {
                    errors += "${friend.shown} : échangez une nouvelle carte pour le relais chiffré"; return@async
                }
                permits.withPermit {
                    try {
                        val builder = Request.Builder().url(endpoint)
                        friend.relayEtag?.takeIf { it.length <= 100 }?.let { builder.header("If-None-Match", it) }
                        http.newCall(builder.get().build()).execute().use { r ->
                            if (r.code == 304) return@use
                            check(r.isSuccessful) { "HTTP ${r.code}" }
                            val encrypted = readBounded(r)
                            val raw = RelayCrypto.decrypt(encrypted, friend.relayKey!!, endpoint.toHttpUrlOrNull()!!.encodedPath)
                            val card = League.parse(raw) ?: error("signature ou carte non valable")
                            check(card.k == friend.key && card.remote == endpoint && card.relayKey == null) { "identité ou destination différente" }
                            check(json.decodeFromString(RelayCrypto.Envelope.serializer(), encrypted).revision == card.revision) { "révision incohérente" }
                            if (League.isFriend(card.k)) {
                                League.accept(ctx, card)
                                League.configure(ctx) { s -> s.copy(friends = s.friends.map { if (it.key == friend.key) it.copy(relayEtag = r.header("ETag")?.takeIf { tag -> tag.length <= 100 }) else it }) }
                            }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { errors += "${friend.shown} : lecture impossible. Réessayez plus tard." }
                }
            } }.awaitAll()
        }
        League.configure(ctx) { it.copy(lastSync = System.currentTimeMillis(), syncError = errors.take(3).joinToString(" · ").ifBlank { null }) }
    } }
    /** Révoque toutes les anciennes capacités de lecture ; nécessite un nouvel échange de cartes. */
    suspend fun rotate(ctx: Context) = lock.withLock { withContext(Dispatchers.IO) {
        myEndpoint(ctx)?.let { old ->
            http.newCall(auth(ctx, Request.Builder().url(old)).delete().build()).execute().use { check(it.isSuccessful || it.code == 404) { "révocation : HTTP ${it.code}" } }
        }
        listOf("league_mailbox_v2", "league_read_capability_v2", "league_aes_read_key", "league_owner_token_v2", "league_published_hash_v2", "league_published_at_v2").forEach { Secrets.put(ctx, it, null) }
    } }
}
