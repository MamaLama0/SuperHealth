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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.RequestBody.Companion.asRequestBody

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
        ((nodomain.freeyourgadget.gadgetbridge.BuildConfig.APPLICATION_ID in setOf("app.superhealth.grouptest", "app.superhealth.socialtest", "app.superhealth.cardstest") ||
            (nodomain.freeyourgadget.gadgetbridge.BuildConfig.DEBUG && nodomain.freeyourgadget.gadgetbridge.BuildConfig.APPLICATION_ID in setOf("app.superhealth.qa", "app.superhealth.hikingtest"))) &&
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
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { League.state.value.profileAvatar }
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
    internal suspend fun ensureSocialDescriptor(ctx: Context) = lock.withLock { withContext(Dispatchers.IO) {
        SocialSpaces.repository(ctx).initialize()
        val endpoint = myEndpoint(ctx) ?: error("Activez le serveur du Groupe")
        publishCard(ctx, endpoint, withAvatar = false, force = true)
    } }
    /** Explicit publication does not depend on whether a friend's server answers. */
    internal suspend fun publishOnly(ctx: Context) {
        check(!SocialLifecycle.pending(ctx)) { "Terminez la sécurisation du Groupe avant un nouveau partage." }
        SocialPreferences.finishRevocations(ctx)
        publishLocked(ctx)
    }
    private suspend fun publishLocked(ctx: Context) = lock.withLock { withContext(Dispatchers.IO) {
        check(League.available())
        finishRotation(ctx)
        try { SocialSpaces.sync(ctx) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { }
        val endpoint = myEndpoint(ctx) ?: error("Activez le serveur dans les préférences sociales.")
        publishCard(ctx, endpoint)
        SocialOutbox.flush(ctx, endpoint)
        publishCard(ctx, endpoint)
        GroupHistory.publish(ctx, endpoint)
        // A failed recovery backup must not report an already published post as unsent.
        try { GroupRecovery.upload(ctx) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* The next synchronization retries; recovery has an explicit status screen. */ }
    } }
    internal suspend fun withIdentityLock(block:suspend ()->Unit)=lock.withLock { block() }
    suspend fun sync(ctx: Context) {
        if (SocialLifecycle.pending(ctx)) { SocialLifecycle.resume(ctx); return }
        SocialPreferences.finishRevocations(ctx)
        syncLocked(ctx)
    }
    private suspend fun syncLocked(ctx: Context) = lock.withLock { withContext(Dispatchers.IO) {
        if (!League.available() || !League.state.value.relayEnabled) return@withContext
        finishRotation(ctx)
        try { SocialSpaces.sync(ctx) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Spaces retain their own durable error state. */ }
        val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
        try { InvitationCodes.collectResponses(ctx) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { errors += "Invitations indisponibles. Réessayez plus tard." }
        myEndpoint(ctx)?.let { endpoint ->
            try { publishCard(ctx, endpoint) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { errors += "Publication impossible. Vérifiez la connexion ou les réglages du serveur." }
            try { SocialOutbox.flush(ctx, endpoint); publishCard(ctx, endpoint); GroupHistory.publish(ctx, endpoint) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { errors += "Des commentaires ou publications attendent encore leur envoi. Réessayez." }
        }
        try { GroupRecovery.upload(ctx) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { errors += "Protection du Groupe à réessayer dans les paramètres." }
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
                            if (r.code == 404 && GroupLinks.resolve(ctx, friend)) return@use
                            check(r.isSuccessful) { "HTTP ${r.code}" }
                            val encrypted = readBounded(r)
                            val raw = RelayCrypto.decrypt(encrypted, friend.relayKey!!, endpoint.toHttpUrlOrNull()!!.encodedPath)
                            val card = League.parse(raw) ?: error("signature ou carte non valable")
                            check(League.matches(friend, card) && card.remote == endpoint && card.relayKey == null) { "identité ou destination différente" }
                            check(json.decodeFromString(RelayCrypto.Envelope.serializer(), encrypted).revision == card.revision) { "révision incohérente" }
                            if (League.state.value.friends.any { it.id == friend.id }) {
                                League.accept(ctx, card)
                                League.configure(ctx) { s -> s.copy(friends = s.friends.map { if (it.key == friend.key) it.copy(relayEtag = r.header("ETag")?.takeIf { tag -> tag.length <= 100 }) else it }) }
                            }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { errors += "${friend.shown} : lecture impossible. Réessayez plus tard." }
                }
            } }.awaitAll()
        }
        try { GroupHistory.refresh(ctx) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { errors += "Historique du Groupe indisponible. Réessayez." }
        try { myEndpoint(ctx)?.let { GroupHistory.publish(ctx,it) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { errors += "Des commentaires ou publications attendent encore leur envoi. Réessayez." }
        League.configure(ctx) { it.copy(lastSync = System.currentTimeMillis(), lastSuccessfulSync = if (errors.isEmpty()) System.currentTimeMillis() else it.lastSuccessfulSync,
            syncError = errors.distinct().take(3).joinToString(" · ").ifBlank { null }) }
    } }
    @Serializable private data class Rotation(val old: String, val owner: String, val publishKey: String,
        val mailbox: String, val read: String, val aes: String, val writer: String, val backup: String, val excluded: Set<String>,
        val completedMedia: Set<String> = emptySet(), val historyPrepared: Boolean = false)
    private val transfer = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES)
        .followRedirects(false).followSslRedirects(false).build()

    /** Resumable migration: prepare the new mailbox and media, inform retained friends, then revoke the old URL. */
    private suspend fun finishRotation(ctx: Context) {
        val raw = Secrets.get(ctx, "group_rotation_pending") ?: return
        var rotation = json.decodeFromString(Rotation.serializer(), raw)
        fun saveRotation() = Secrets.put(ctx, "group_rotation_pending", json.encodeToString(Rotation.serializer(), rotation))
        val base = rotation.old.substringBefore("/v2/cards/")
        val endpoint = "$base/v2/cards/${rotation.mailbox}/${rotation.read}"
        fun authorized(url: String) = Request.Builder().url(url).header("X-Write-Token", rotation.writer)
            .header("Authorization", "Bearer " + rotation.publishKey)
        val switched = Secrets.get(ctx, "league_mailbox_v2") == rotation.mailbox
        if (!switched) {
            GroupHistory.loadOwnHistory(ctx)
            val card = League.card(app.superhealth.ui.userName() ?: "?", League.compute(ctx), endpoint, includeAvatar = false)
            val parsed = League.parse(card) ?: error("Carte du Groupe non valable")
            val encrypted = RelayCrypto.encrypt(card, rotation.aes, endpoint.toHttpUrlOrNull()!!.encodedPath, parsed.revision)
            transfer.newCall(authorized(endpoint).put(encrypted.toRequestBody("application/json".toMediaType())).build()).execute().use { check(it.isSuccessful) }
            val media = (GroupHistory.posts(ctx).filter { it.second == null && it.first.id !in SocialOutbox.pendingIds(ctx) }.mapNotNull { it.first.media } + listOfNotNull(League.state.value.profileAvatar)).distinctBy { it.id }
            for (item in media) {
                if (item.id in rotation.completedMedia) continue
                val tmp = java.io.File.createTempFile("group-rotation-", ".enc", ctx.cacheDir)
                try {
                    val digest = MessageDigest.getInstance("SHA-256"); var size = 0L
                    transfer.newCall(Request.Builder().url(rotation.old + "/media/" + item.id).get().build()).execute().use { response ->
                        check(response.isSuccessful) { "Un média est indisponible. Le partage actuel reste conservé." }
                        response.body.byteStream().use { input -> tmp.outputStream().use { out ->
                            val buffer = ByteArray(65536)
                            while (true) { val n = input.read(buffer); if (n < 0) break; size += n
                                check(size <= item.bytes); digest.update(buffer, 0, n); out.write(buffer, 0, n) }
                        } }
                    }
                    check(size == item.bytes.toLong() && digest.digest().joinToString("") { "%02x".format(it) } == item.hash)
                    transfer.newCall(authorized(endpoint + "/media/" + item.id).put(tmp.asRequestBody("application/octet-stream".toMediaType())).build()).execute().use {
                        check(it.isSuccessful || it.code == 409) { "Migration des médias à réessayer." }
                    }
                } finally { tmp.delete() }
                rotation = rotation.copy(completedMedia = rotation.completedMedia + item.id)
                saveRotation()
            }
            GroupLinks.publish(ctx, rotation.old, rotation.owner, rotation.publishKey, endpoint, rotation.aes, rotation.excluded)
            Secrets.remember(ctx, Secrets.backupCode(ctx))
            Secrets.putAll(ctx, mapOf("league_mailbox_v2" to rotation.mailbox, "league_read_capability_v2" to rotation.read,
                "league_aes_read_key" to rotation.aes, "league_owner_token_v2" to rotation.writer,
                Secrets.BACKUP_CODE to rotation.backup, "league_published_hash_v2" to null, "league_published_at_v2" to null,
                "group_recovery_hash" to null, "profile_avatar_source" to null))
        }
        if (!rotation.historyPrepared) {
            GroupHistory.republish(ctx)
            rotation = rotation.copy(historyPrepared = true)
            saveRotation()
        }
        GroupHistory.publish(ctx, endpoint)
        transfer.newCall(Request.Builder().url(rotation.old).header("X-Write-Token", rotation.owner)
            .header("Authorization", "Bearer " + rotation.publishKey).delete().build()).execute().use {
            check(it.isSuccessful || it.code == 404) { "Ancien accès à révoquer : la reprise est enregistrée." }
        }
        InvitationCodes.revokeCurrent(ctx, rotation.publishKey)
        League.configure(ctx) { s -> s.copy(friends = s.friends.filterNot { it.id in rotation.excluded }) }
        Secrets.put(ctx, "group_rotation_pending", null)
    }
    suspend fun rotate(ctx: Context, excluded: Set<String> = emptySet()) = lock.withLock { withContext(Dispatchers.IO) {
        if (Secrets.get(ctx, "group_rotation_pending") == null) {
            val old = myEndpoint(ctx) ?: error("Activez le serveur du Groupe.")
            val owner = secret(ctx, "league_owner_token_v2", ::token)
            val rotation = Rotation(old, owner, publicationKey(ctx), token(), token(), RelayCrypto.newKey(), token(),
                Secrets.backupCode(ctx), excluded)
            Secrets.put(ctx, "group_rotation_pending", json.encodeToString(Rotation.serializer(), rotation))
        }
        finishRotation(ctx)
        val endpoint = myEndpoint(ctx)!!
        publishCard(ctx, endpoint)
        GroupHistory.publish(ctx, endpoint)
        GroupRecovery.upload(ctx)
    } }
}
