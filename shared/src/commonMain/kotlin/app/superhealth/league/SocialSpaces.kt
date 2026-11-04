package app.superhealth.league

import app.superhealth.platform.*
import app.superhealth.backup.Secrets
import app.superhealth.social.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json


object SocialSpaces {
    private var instance: SpaceRepository? = null
    private val json = Json { encodeDefaults = true }
    fun validContact(contact: SpaceContact): Boolean = runCatching {
        val root = contact.root ?: contact.key
        val id = Sha256.digest(Base64.getUrlDecoder().decode(root)).take(8).joinToString("") { "%02x".format(it) }
        require(contact.id == id && contact.name.length in 1..24 && contact.endpoint.length <= 512)
        require(Relay.encryptedEndpoint(contact.endpoint) && RelayCrypto.validKey(contact.aes))
        if (contact.root != null) require(contact.endorsement != null && verifyIdentity(Base64.getUrlDecoder().decode(contact.root),
            ("SuperHealth identity successor v1:" + contact.key).toByteArray(), Base64.getUrlDecoder().decode(contact.endorsement)))
        else require(contact.endorsement == null)
        true
    }.getOrDefault(false)
    fun validCircle(circle: Circle) = circle.id.matches(Regex("[a-f0-9]{32}")) && circle.name.length in 1..60 && circle.epoch > 0 &&
        circle.members.size in 1..8 && circle.members.distinctBy { it.id }.size == circle.members.size && circle.owner in circle.members && circle.members.all(::validContact)
    fun repository(ctx: Context): SpaceRepository = instance ?: SpaceRepository(AppleSpaceHost(ctx)).also { instance = it }
    fun reset() { instance = null }
    fun contact(): SpaceContact? = instance?.state?.value?.own
    suspend fun sync(ctx: Context) = withContext(Dispatchers.IO) { discoverWithdrawals(ctx);val repo=repository(ctx);val before=repo.state.value;repo.sync();SocialNotifications.spaces(ctx,before,repo.state.value) }

    private fun discoveryId(key: String) = MessageDigest.getInstance("SHA-256").digest(("SuperHealth withdrawal discovery v1:" + key).toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    private fun discoveryKey(key: String): String {
        val secret = identitySharedSecret(League.socialIdentity().privateKey, Base64.getUrlDecoder().decode(key))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest("SuperHealth withdrawal discovery v1:".toByteArray() + secret))
    }
    suspend fun publishWithdrawalDiscovery(ctx: Context) = withContext(Dispatchers.IO) {
        val endpoint = Relay.myEndpoint(ctx) ?: return@withContext
        val contact = repository(ctx).state.value.own ?: return@withContext
        val signed = League.signSocial(json.encodeToString(SpaceContact.serializer(), contact))
        val client = SocialHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
        for (friend in League.state.value.friends) {
            val url = endpoint + "/handoffs/" + discoveryId(friend.key)
            val encrypted = RelayCrypto.encrypt(signed, discoveryKey(friend.key), url.toHttpUrlOrNull()!!.encodedPath, System.currentTimeMillis())
            client.newCall(SocialRequest.Builder().url(url).header("Authorization", "Bearer " + Relay.publicationKey(ctx)).header("X-Write-Token", Secrets.get(ctx, "league_owner_token_v2").orEmpty()).put(encrypted.toRequestBody("application/json".toMediaType())).build()).execute().use { check(it.isSuccessful) }
        }
    }
    private suspend fun discoverWithdrawals(ctx: Context) {
        val client = SocialHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).followRedirects(false).build()
        for (friend in League.state.value.friends.filter { it.remote != null && it.space == null }) {
            try {
                val url = friend.remote + "/handoffs/" + discoveryId(League.publicKey())
                val encrypted = client.newCall(SocialRequest.Builder().url(url).maxResponseBytes(24576).get().build()).execute().use {
                    if (!it.isSuccessful) return@use null
                    val bytes = it.body.bytes; require(bytes.size <= 24576); bytes.decodeToString()
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

    private class AppleSpaceHost(val ctx: Context) : SpaceHost {
        private val http = SocialHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
        private fun file() = AtomicFile(File(ctx.filesDir, "league/spaces.json").apply { parentFile?.mkdirs() })
        private fun exists() = File(ctx.filesDir, "league/spaces.json").isFile
        override fun now() = System.currentTimeMillis()
        override fun randomId() = UUID.randomUUID().toString().replace("-", "")
        override fun newKey() = RelayCrypto.newKey()
        override fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        override fun validContact(contact: SpaceContact) = SocialSpaces.validContact(contact)
        override fun validKey(key: String) = RelayCrypto.validKey(key)
        override fun sign(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(signIdentity(League.socialIdentity().privateKey, text.toByteArray()))
        override fun verify(key: String, text: String, signature: String) = verifyIdentity(Base64.getUrlDecoder().decode(key), text.toByteArray(), Base64.getUrlDecoder().decode(signature))
        override fun sharedKey(key: String): String {
            val secret = identitySharedSecret(League.socialIdentity().privateKey, Base64.getUrlDecoder().decode(key))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Sha256.digest("SuperHealth spaces ECDH v1:".toByteArray() + secret))
        }
        override fun encrypt(text: String, key: String, domain: String, revision: Long) = RelayCrypto.encrypt(text, key, domain, revision)
        override fun decrypt(text: String, key: String, domain: String) = RelayCrypto.decrypt(text, key, domain)
        override fun readStore(): String? = if (exists()) file().openRead().bufferedReader().use { it.readText() } else null
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
        override fun blocked(key: String) = SocialPrivacy.blocked(key)
        override fun withdrawContact(contact:SpaceContact) = SocialPrivacy.withdraw(ctx,contact)
        override suspend fun removeAttachment(media: SpaceAttachment) {
            require(media.endpoint==readStore()?.let{json.decodeFromString(SpaceStore.serializer(),it).own?.endpoint})
            http.newCall(SocialRequest.Builder().url(media.endpoint+"/media/"+media.id).header("Authorization","Bearer "+Relay.publicationKey(ctx))
                .header("X-Write-Token",Secrets.get(ctx,"social_space_writer").orEmpty()).delete().build()).execute().use{check(it.isSuccessful || it.code==404)}
        }
        override suspend fun sendAttachment(media: SpaceAttachment) = FriendSocial.sendSpaceMedia(ctx, media)
        override suspend fun createStream(): SpaceContact {
            val base = Relay.base() ?: error("Activez le serveur du Groupe")
            val saved = Secrets.get(ctx, "social_space_contact")?.let { json.decodeFromString(SpaceContact.serializer(), it) }
            val contact = saved ?: run {
                val identity = League.socialIdentity()
                SpaceContact(League.myId(), Base64.getUrlEncoder().withoutPadding().encodeToString(identity.publicKey), (app.superhealth.ui.userName() ?: "?").take(24),
                    "$base/v2/cards/${hash(randomId())}/${hash(randomId())}", newKey(), GroupIdentity.current().previous, GroupIdentity.current().endorsement)
            }
            if (saved == null) Secrets.putAll(ctx, mapOf("social_space_contact" to json.encodeToString(SpaceContact.serializer(), contact), "social_space_writer" to hash(randomId())))
            val path = contact.endpoint.toHttpUrlOrNull()!!.encodedPath
            put(contact.endpoint, encrypt("SuperHealth private social stream 1", contact.aes, path, now()))
            return contact
        }
        override suspend fun get(url: String): String {
            http.newCall(SocialRequest.Builder().url(url).maxResponseBytes(1024*1024).get().build()).execute().use {
                check(it.isSuccessful) { "Lecture des cercles indisponible" }
                val bytes = it.body.bytes
                require(bytes.size <= 1024 * 1024); return bytes.decodeToString()
            }
        }
        override suspend fun put(url: String, body: String) {
            http.newCall(SocialRequest.Builder().url(url).header("Authorization", "Bearer " + Relay.publicationKey(ctx)).header("X-Write-Token", Secrets.get(ctx, "social_space_writer").orEmpty())
                .put(body.toRequestBody("application/json".toMediaType())).build()).execute().use {
                check(it.isSuccessful) { "Envoi aux cercles indisponible · HTTP ${it.code}" }
            }
        }
    }
}
