package app.superhealth.league

import android.content.Context
import android.util.AtomicFile
import app.superhealth.backup.Secrets
import app.superhealth.social.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.KeyAgreement

object SocialSpaces {
    private var instance: SpaceRepository? = null
    private val json = Json { encodeDefaults = true }
    fun validContact(contact: SpaceContact): Boolean = runCatching {
        val root = contact.root ?: contact.key
        require(contact.id == GroupHistory.author(root) && contact.name.length in 1..24 && contact.endpoint.length <= 512)
        require(Relay.encryptedEndpoint(contact.endpoint) && RelayCrypto.validKey(contact.aes))
        require(GroupIdentity.validEndorsement(contact.root, contact.key, contact.endorsement))
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getUrlDecoder().decode(contact.key)))
        true
    }.getOrDefault(false)
    fun validCircle(circle: Circle) = circle.id.matches(Regex("[a-f0-9]{32}")) && circle.name.length in 1..60 && circle.epoch > 0 &&
        circle.members.size in 1..8 && circle.members.distinctBy { it.id }.size == circle.members.size && circle.owner in circle.members && circle.members.all(::validContact)
    @Synchronized fun repository(ctx: Context): SpaceRepository = instance ?: SpaceRepository(AndroidSpaceHost(ctx.applicationContext)).also { instance = it }
    @Synchronized fun reset() { instance = null }
    fun contact(): SpaceContact? = instance?.state?.value?.own
    suspend fun sync(ctx: Context) = withContext(Dispatchers.IO) { discoverWithdrawals(ctx); val repo=repository(ctx);val before=repo.state.value;repo.sync();SocialNotifications.spacesReceived(ctx,before,repo.state.value) }
    private fun discoveryId(key: String) = MessageDigest.getInstance("SHA-256").digest(("SuperHealth withdrawal discovery v1:" + key).toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    private fun discoveryKey(key: String): String {
        val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getUrlDecoder().decode(key)))
        val secret = KeyAgreement.getInstance("ECDH").run { init(GroupIdentity.privateKey()); doPhase(peer, true); generateSecret() }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest("SuperHealth withdrawal discovery v1:".toByteArray() + secret))
    }
    suspend fun publishWithdrawalDiscovery(ctx: Context) = withContext(Dispatchers.IO) {
        val endpoint = Relay.myEndpoint(ctx) ?: return@withContext
        val contact = repository(ctx).state.value.own ?: return@withContext
        val signed = League.signSocial(json.encodeToString(SpaceContact.serializer(), contact))
        val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
        for (friend in League.state.value.friends) {
            val url = endpoint + "/handoffs/" + discoveryId(friend.key)
            val encrypted = RelayCrypto.encrypt(signed, discoveryKey(friend.key), url.toHttpUrlOrNull()!!.encodedPath, System.currentTimeMillis())
            client.newCall(Relay.auth(ctx, Request.Builder().url(url)).put(encrypted.toRequestBody("application/json".toMediaType())).build()).execute().use { check(it.isSuccessful) }
        }
    }
    private suspend fun discoverWithdrawals(ctx: Context) {
        val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).followRedirects(false).build()
        for (friend in League.state.value.friends.filter { it.remote != null && it.space == null }) {
            try {
                val url = friend.remote + "/handoffs/" + discoveryId(League.publicKey())
                val encrypted = client.newCall(Request.Builder().url(url).get().build()).execute().use {
                    if (!it.isSuccessful) return@use null
                    val bytes = it.body.byteStream().use { input -> input.readNBytes(24577) }; require(bytes.size <= 24576); bytes.decodeToString()
                } ?: continue
                val signed = RelayCrypto.decrypt(encrypted, discoveryKey(friend.key), url.toHttpUrlOrNull()!!.encodedPath)
                val raw = League.verifySocial(signed, friend.key) ?: continue
                val contact = json.decodeFromString(SpaceContact.serializer(), raw)
                require(validContact(contact) && contact.id == friend.id && contact.key == friend.key)
                League.configure(ctx) { it.copy(friends = it.friends.map { peer -> if (peer.id == friend.id) peer.copy(space = contact) else peer }) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { }
        }
    }

    private class AndroidSpaceHost(val ctx: Context) : SpaceHost {
        private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
        private fun file() = AtomicFile(File(ctx.filesDir, "league/spaces.json").apply { parentFile?.mkdirs() })
        override fun now() = System.currentTimeMillis()
        override fun randomId() = java.util.UUID.randomUUID().toString().replace("-", "")
        override fun newKey() = RelayCrypto.newKey()
        override fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        override fun validContact(contact: SpaceContact) = SocialSpaces.validContact(contact)
        override fun validKey(key: String) = RelayCrypto.validKey(key)
        override fun sign(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(Signature.getInstance("SHA256withECDSA").run {
            initSign(GroupIdentity.privateKey()); update(text.toByteArray()); sign()
        })
        override fun verify(key: String, text: String, signature: String) = runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getUrlDecoder().decode(key))))
                update(text.toByteArray()); verify(Base64.getUrlDecoder().decode(signature))
            }
        }.getOrDefault(false)
        override fun sharedKey(key: String): String {
            val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getUrlDecoder().decode(key)))
            val secret = KeyAgreement.getInstance("ECDH").run { init(GroupIdentity.privateKey()); doPhase(peer, true); generateSecret() }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest("SuperHealth spaces ECDH v1:".toByteArray() + secret))
        }
        override fun encrypt(text: String, key: String, domain: String, revision: Long) = RelayCrypto.encrypt(text, key, domain, revision)
        override fun decrypt(text: String, key: String, domain: String) = RelayCrypto.decrypt(text, key, domain)
        override fun readStore(): String? = if (file().baseFile.exists()) file().openRead().bufferedReader().use { it.readText() } else null
        override fun writeStore(text: String) {
            val f = file(); val out = f.startWrite()
            try { out.write(text.toByteArray()); f.finishWrite(out) } catch (e: Exception) { f.failWrite(out); throw e }
        }
        private fun draftFile(circle: String): AtomicFile {
            require(circle.matches(Regex("[a-f0-9]{32}")))
            return AtomicFile(File(ctx.filesDir,"league/space-drafts/" + circle + ".json").apply { parentFile?.mkdirs() })
        }
        override fun readDraft(circle: String): String? = runCatching { draftFile(circle).openRead().bufferedReader().use { it.readText() } }.getOrNull()
        override fun writeDraft(circle: String, text: String) {
            val file=draftFile(circle);val out=file.startWrite()
            try { out.write(text.toByteArray());file.finishWrite(out) } catch(e:Exception){file.failWrite(out);throw e}
        }
        override fun contacts() = League.state.value.friends.mapNotNull { it.space }.filterNot { blocked(it.key) }
        override fun withdrawContact(contact: SpaceContact) {
            if (League.state.value.friends.none { it.id == contact.id && it.key == contact.key }) return
            League.configure(ctx) { it.copy(pendingSocialRevocations = it.pendingSocialRevocations + contact.id,
                blockedSocialKeys = it.blockedSocialKeys + (contact.root ?: contact.key), blockedSocialAliases = it.blockedSocialAliases + (contact.key to (contact.root ?: contact.key))) }
            LeagueWorker.onOpen(ctx, force = true)
        }
        override fun blocked(key: String) = SocialPreferences.blocked(key)
        override suspend fun removeAttachment(media: SpaceAttachment) {
            require(media.endpoint==readStore()?.let{json.decodeFromString(SpaceStore.serializer(),it).own?.endpoint})
            http.newCall(Request.Builder().url(media.endpoint+"/media/"+media.id).header("Authorization","Bearer "+Relay.publicationKey(ctx))
                .header("X-Write-Token",Secrets.get(ctx,"social_space_writer").orEmpty()).delete().build()).execute().use{check(it.isSuccessful || it.code==404)}
        }
        override suspend fun sendAttachment(media: SpaceAttachment) = SocialPosts.sendCircleMedia(ctx, media)
        override suspend fun createStream(): SpaceContact {
            val base = Relay.base() ?: error("Activez le serveur du Groupe")
            val saved = Secrets.get(ctx, "social_space_contact")?.let { json.decodeFromString(SpaceContact.serializer(), it) }
            val contact = saved ?: run {
                val identity = GroupIdentity.current()
                SpaceContact(League.myId(), identity.public, (app.superhealth.ui.userName() ?: "?").take(24),
                    "$base/v2/cards/${hash(randomId())}/${hash(randomId())}", newKey(), identity.previous, identity.endorsement)
            }
            if (saved == null) Secrets.putAll(ctx, mapOf("social_space_contact" to json.encodeToString(SpaceContact.serializer(), contact), "social_space_writer" to hash(randomId())))
            val path = contact.endpoint.toHttpUrlOrNull()!!.encodedPath
            put(contact.endpoint, encrypt("SuperHealth private social stream 1", contact.aes, path, now()))
            return contact
        }
        override suspend fun get(url: String): String {
            http.newCall(Request.Builder().url(url).get().build()).execute().use {
                check(it.isSuccessful) { "Lecture des cercles indisponible" }
                val bytes = it.body.byteStream().use { input -> input.readNBytes(1024 * 1024 + 1) }
                require(bytes.size <= 1024 * 1024); return bytes.decodeToString()
            }
        }
        override suspend fun put(url: String, body: String) {
            http.newCall(Relay.auth(ctx, Request.Builder().url(url)).header("X-Write-Token", Secrets.get(ctx, "social_space_writer").orEmpty())
                .put(body.toRequestBody("application/json".toMediaType())).build()).execute().use {
                check(it.isSuccessful) { "Envoi aux cercles indisponible · HTTP ${it.code}" }
            }
        }
    }
}
