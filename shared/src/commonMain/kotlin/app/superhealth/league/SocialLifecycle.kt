package app.superhealth.league

import app.superhealth.platform.*
import app.superhealth.backup.BackupCrypto
import app.superhealth.backup.Secrets
import app.superhealth.social.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Durable workflow for operations that revoke remote capabilities before replacing local identity. */
object SocialLifecycle {
    @Serializable data class Operation(val mode: String, val profile: GroupRecovery.Profile, val code: String,
        val publicationKey: String, val nextIdentity: GroupIdentity.Identity, val nextCode: String,
        val posts: List<FriendPost>, val nextSecrets: Map<String, String>, val migratedMedia: Set<String> = emptySet(), val stage: Int = 0, val transportRevision: Long = 0)
    private const val PENDING = "social_lifecycle_pending"
    private val lock = Mutex()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val http = SocialHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    fun pending(ctx: Context) = Secrets.get(ctx, PENDING) != null
    private fun save(ctx: Context, operation: Operation) = Secrets.put(ctx, PENDING, json.encodeToString(Operation.serializer(), operation))
    private fun base(profile: GroupRecovery.Profile) = if (profile.customRelay) profile.relayUrl.trimEnd('/') else Relay.defaultBase() ?: error("Serveur indisponible")
    private fun endpoint(profile: GroupRecovery.Profile): String? {
        val mailbox = profile.secrets["league_mailbox_v2"] ?: return null
        val read = profile.secrets["league_read_capability_v2"] ?: return null
        return "${base(profile)}/v2/cards/$mailbox/$read"
    }
    private fun request(operation: Operation, url: String, token: String, method: String) {
        val builder = SocialRequest.Builder().url(url).header("Authorization", "Bearer " + operation.publicationKey).header("X-Write-Token", token)
        if (method == "DELETE") builder.delete() else builder.post(SocialBody(ByteArray(0), "application/octet-stream"))
        http.newCall(builder.build()).execute().use { check(it.isSuccessful || (method == "DELETE" && it.code == 404)) { "Révocation à réessayer · HTTP ${it.code}" } }
    }
    suspend fun begin(ctx: Context, mode: String): Boolean = withContext(Dispatchers.IO) {
        require(mode in setOf("SECURE", "DELETE"))
        if (!pending(ctx)) {
            SocialPrivacy.finish(ctx)
            Relay.ensureSocialDescriptor(ctx)
            FriendSocial.loadOwnHistory(ctx)
            val profile = GroupRecovery.export(ctx)
            val health = http.newCall(SocialRequest.Builder().url(base(profile) + "/health").get().build()).execute().use {
                check(it.isSuccessful); json.parseToJsonElement(it.body.bytes.decodeToString()).jsonObject
            }
            check(health["recoveryRevocation"]?.jsonPrimitive?.intOrNull == 1 && health["streamFreeze"]?.jsonPrimitive?.intOrNull == 1) {
                "Mettez à jour le relais avant de supprimer ou sécuriser le Groupe."
            }
            val pair = generateSigningIdentity()
            fun encode(bytes:ByteArray)=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            val identity=GroupIdentity.Identity(encode(P256Encoding.pkcs8(pair.privateKey,pair.publicKey)),encode(pair.publicKey))
            fun token()=secureRandomBytes(32).joinToString(""){"%02x".format(it)}
            val nextSecrets = mapOf("league_mailbox_v2" to token(), "league_read_capability_v2" to token(), "league_aes_read_key" to RelayCrypto.newKey(), "league_owner_token_v2" to token())
            save(ctx, Operation(mode, profile, Secrets.backupCode(ctx), Relay.publicationKey(ctx), identity, BackupCrypto.newCode(),
                FriendSocial.repository(ctx).posts().filter { it.second.id == League.myId() }.map { it.first }, nextSecrets))
            LeagueWorker.onOpen(ctx, force = true)
        }
        resume(ctx)
    }
    suspend fun resume(ctx: Context): Boolean = lock.withLock { withContext(Dispatchers.IO) {
        val raw = Secrets.get(ctx, PENDING) ?: return@withContext true
        var operation = json.decodeFromString(Operation.serializer(), raw)
        val spaces = SocialSpaces.repository(ctx)
        if (operation.stage == 0) {
            for (access in spaces.state.value.circles.filter { !it.departed && !it.circle.closed }) {
                if (access.circle.owner.key == operation.profile.identity.public) spaces.closeCircle(access.circle.id)
                else spaces.leave(access.circle.id)
            }
            SocialSpaces.publishWithdrawalDiscovery(ctx)
            spaces.requestFriendWithdrawals()
            spaces.sync()
            check(spaces.state.value.pending.isEmpty()) { "Les demandes de départ attendent leur envoi." }
            operation = operation.copy(stage = 1); save(ctx, operation)
        }
        if (operation.stage == 1) {
            operation.profile.spaces?.own?.let { contact ->
                request(operation, contact.endpoint + "/freeze", operation.profile.secrets["social_space_writer"].orEmpty(), "POST")
            }
            InvitationCodes.revokeCurrent(ctx)
            operation = operation.copy(stage = 2); save(ctx, operation)
        }
        if (operation.stage == 2) {
            if (operation.mode == "SECURE") {
                val target = "${base(operation.profile)}/v2/cards/${operation.nextSecrets.getValue("league_mailbox_v2")}/${operation.nextSecrets.getValue("league_read_capability_v2")}"
                val revision = maxOf(System.currentTimeMillis(), operation.transportRevision + 1)
                operation = operation.copy(transportRevision = revision); save(ctx, operation)
                val encrypted = RelayCrypto.encrypt("SuperHealth identity provision", operation.nextSecrets.getValue("league_aes_read_key"), target.toHttpUrlOrNull()!!.encodedPath, revision)
                http.newCall(SocialRequest.Builder().url(target).header("Authorization", "Bearer " + operation.publicationKey)
                    .header("X-Write-Token", operation.nextSecrets.getValue("league_owner_token_v2"))
                    .put(encrypted.toRequestBody("application/json".toMediaType())).build()).execute().use { check(it.isSuccessful) }
                val old = endpoint(operation.profile)
                for (media in (operation.posts.mapNotNull { it.media } + listOfNotNull(operation.profile.avatar)).distinctBy { it.id }) {
                    if (media.id in operation.migratedMedia) continue
                    require(old != null)
                    val tmp = File(ctx.cacheDir, "social-identity-" + UUID.randomUUID().toString() + ".enc")
                    try {
                        val queuedPost = operation.posts.firstOrNull { it.media?.id == media.id }
                        val local = File(ctx.filesDir, "league/ios-media/" + media.id + ".enc")
                        if (local?.isFile == true) local.copyTo(tmp, overwrite = true)
                        else http.newCall(SocialRequest.Builder().url(old + "/media/" + media.id).maxResponseBytes(51*1024*1024).get().build()).execute().use { response ->
                            check(response.isSuccessful) { "Un média doit être récupéré avant la révocation." }
                            val bytes = response.body.bytes; require(bytes.size == media.bytes); tmp.writeBytes(bytes)
                        }
                        FriendSocial.sendEncryptedCopy(ctx, target, media, tmp, operation.nextSecrets.getValue("league_owner_token_v2"))
                        operation = operation.copy(migratedMedia = operation.migratedMedia + media.id); save(ctx, operation)
                    } finally { tmp.delete() }
                }
            }
            endpoint(operation.profile)?.let { request(operation, it, operation.profile.secrets["league_owner_token_v2"].orEmpty(), "DELETE") }
            GroupRecovery.revoke(ctx, operation.code, base(operation.profile), operation.publicationKey)
            operation = operation.copy(stage = 3); save(ctx, operation)
        }
        if (operation.stage == 3) {
            spaces.sync()
            val departures = operation.profile.spaces?.circles.orEmpty().filter { !it.departed && !it.circle.closed && it.circle.owner.key != operation.profile.identity.public }
            val waitingCircles = departures.any { old -> spaces.state.value.circles.none { it.circle.id == old.circle.id && (it.removalConfirmed || it.circle.closed) } }
            val waitingPeers = operation.profile.contacts.any { contact ->
                if (contact.id in spaces.state.value.withdrawalAcks) false
                else contact.remote?.let { old ->
                    runCatching { http.newCall(SocialRequest.Builder().url(old + "/status").get().build()).execute().use {
                        it.isSuccessful && json.parseToJsonElement(it.body.bytes.decodeToString()).jsonObject["revoked"]?.jsonPrimitive?.booleanOrNull == true
                    } }.getOrDefault(false).not()
                } ?: false
            }
            if (waitingCircles || waitingPeers) {
                League.configure(ctx) { it.copy(syncError = "Sécurisation en attente de confirmation des contacts et créateurs de cercles.") }
                return@withContext false
            }
            operation.profile.spaces?.own?.let { request(operation, it.endpoint, operation.profile.secrets["social_space_writer"].orEmpty(), "DELETE") }
            operation = operation.copy(stage = 4); save(ctx, operation)
        }
        if (operation.stage == 4) {
            Relay.withIdentityLock {
            val oldHistory = SocialArchive(profile = json.encodeToJsonElement(GroupRecovery.Profile.serializer(), operation.profile).jsonObject,
                events = FriendSocial.repository(ctx).state.value.events.map { json.encodeToJsonElement(FriendEvent.serializer(), it).jsonObject }, proofs = FriendSocial.repository(ctx).state.value.proofs,
                spaces = spaces.state.value)
            if (operation.mode == "SECURE") {
            val archive = AtomicFile(File(ctx.filesDir, "league/previous-group.shsocial")); val archiveOut = archive.startWrite()
            try { archiveOut.write(BackupCrypto.encrypt(json.encodeToString(SocialArchive.serializer(), oldHistory).toByteArray(), operation.code)); archive.finishWrite(archiveOut) }
            catch (e: Exception) { archive.failWrite(archiveOut); throw e }
            } else File(ctx.filesDir, "league/previous-group.shsocial").delete()
            Secrets.remember(ctx, operation.code)
            GroupIdentity.restore(operation.nextIdentity)
            val clear = operation.profile.secrets.keys.associateWith { null as String? } + mapOf(
                Secrets.BACKUP_CODE to operation.nextCode, "group_recovery_hash" to null, "group_recovery_at" to null,
                "friend_invitation_code" to null, "friend_invitation_owner" to null, "friend_invitation_base" to null,
                "friend_invitation_expiry" to null, "group_rotation_pending" to null)
            val next = if (operation.mode == "SECURE") operation.nextSecrets + mapOf("league_publish_key" to operation.profile.secrets["league_publish_key"]) else emptyMap()
            Secrets.putAll(ctx, clear + next)
            FriendSocial.resetHistory(ctx)
            val draftFile=File(ctx.filesDir,"league/friend-draft.json")
            if(operation.mode=="DELETE") { draftFile.delete();File(ctx.filesDir,"league/ios-media").deleteRecursively() }
            else if(draftFile.isFile) {
                val draft=json.decodeFromString(TimelineDraft.serializer(),draftFile.readText()).copy(id=UUID.randomUUID().toString().replace("-",""))
                draftFile.writeText(json.encodeToString(TimelineDraft.serializer(),draft))
            }
            val file = AtomicFile(File(ctx.filesDir, "league/spaces.json")); val out = file.startWrite()
            try { out.write(json.encodeToString(SpaceStore.serializer(), SpaceStore()).toByteArray()); file.finishWrite(out) }
            catch (e: Exception) { file.failWrite(out); throw e }
            File(ctx.filesDir,"league/space-drafts").deleteRecursively()
            if(operation.mode=="DELETE")ctx.cacheDir.listFiles().orEmpty().filter{it.name.startsWith("social-")}.forEach{it.delete()}
            SocialInbox.clear(ctx)
            SocialSpaces.reset()
            League.configure(ctx) { it.copy(friends = if (operation.mode == "SECURE") operation.profile.contacts.map { c -> League.Friend(c.id, c.key, c.name, "", 0, 0,
                alias = c.alias, rootKey = c.rootKey) } else emptyList(), ownPosts = emptyList(), profileAvatar = if (operation.mode == "SECURE") operation.profile.avatar else null, profileTitle = null, profileBadges = emptyList(),
                hiddenSocialPosts = emptySet(), mutedSocialFriends = emptySet(), quietSocialFriends = emptySet(), silentSocialThreads = emptySet(),
                blockedSocialKeys = if(operation.mode=="SECURE")it.blockedSocialKeys else emptySet(), blockedSocialAliases = if(operation.mode=="SECURE")it.blockedSocialAliases else emptyMap(), pendingSocialRevocations = emptySet(), silentCircles = emptySet(),
                shareIndex = false, sharePillars = false, shareActivities = false, shareProgress = false, shareProfilePhoto = false,
                customRelay=operation.profile.customRelay,relayUrl=operation.profile.relayUrl,relayEnabled = operation.mode == "SECURE", lastSync = 0, lastSuccessfulSync = 0, syncError = null) }
            if (operation.mode == "SECURE") {
                for (post in operation.posts) FriendSocial.repository(ctx).publish(post)
            }
            Secrets.put(ctx, PENDING, null)
            }
        }
        true
    } }
    suspend fun export(ctx: Context): ByteArray = lock.withLock { withContext(Dispatchers.IO) {
        val profile = GroupRecovery.export(ctx)
        val history = FriendSocial.repository(ctx).state.value
        val payload = SocialArchive(profile = json.encodeToJsonElement(GroupRecovery.Profile.serializer(), profile).jsonObject,
            events = history.events.map { json.encodeToJsonElement(FriendEvent.serializer(), it).jsonObject }, proofs = history.proofs,
            spaces = SocialSpaces.repository(ctx).state.value)
        val bytes = json.encodeToString(SocialArchive.serializer(), payload).toByteArray()
        require(bytes.size <= 32 * 1024 * 1024) { "L’historique est trop volumineux pour cet export." }
        BackupCrypto.encrypt(bytes, Secrets.backupCode(ctx))
    } }
    suspend fun importArchive(ctx: Context, bytes: ByteArray, code: String) = lock.withLock { withContext(Dispatchers.IO) {
        check(!pending(ctx)) { "Terminez la sécurisation du Groupe avant un nouveau partage." }
        require(bytes.size <= 40 * 1024 * 1024)
        val payload = json.decodeFromString(SocialArchive.serializer(), BackupCrypto.decrypt(bytes, code).decodeToString())
        require(payload.kind == "superhealth-social" && payload.version == 1 && payload.events.size <= 50000)
        val profile = json.decodeFromJsonElement(GroupRecovery.Profile.serializer(), payload.profile)
        GroupRecovery.validate(profile)
        val events = payload.events.map { json.decodeFromJsonElement(FriendEvent.serializer(), it) }
        require(events.all { FriendTimelineRules.validEvent(it, System.currentTimeMillis()) })
        GroupRecovery.restore(ctx, profile.copy(spaces = payload.spaces ?: profile.spaces))
        val store = TimelineStore(events = events, pending = events.filter { it.key == profile.identity.public }.map { it.id }.toSet(), proofs = payload.proofs, baseline = true,
            hidden = profile.reading?.hidden.orEmpty(), muted = profile.reading?.muted.orEmpty(), silent = profile.reading?.silent.orEmpty())
        val f = AtomicFile(File(ctx.filesDir, "league/friend-history.json")); val out = f.startWrite()
        try { out.write(json.encodeToString(TimelineStore.serializer(), store).toByteArray()); f.finishWrite(out) }
        catch (e: Exception) { f.failWrite(out); throw e }
        FriendSocial.resetCache()
        Secrets.remember(ctx,code);Secrets.put(ctx,Secrets.BACKUP_CODE,code)
    } }
}
