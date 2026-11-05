package app.superhealth.league

import android.content.Context
import app.superhealth.backup.BackupCrypto
import app.superhealth.backup.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.superhealth.social.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Only social credentials and contacts, never a health journal or a full .shbak. */
object GroupRecovery {
    @Serializable data class Contact(val id: String, val key: String, val name: String, val alias: String? = null,
        val remote: String? = null, val relayKey: String? = null, val rootKey: String? = null)
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Serializable data class Profile(val v: Int = 1, val identity: GroupIdentity.Identity, val contacts: List<Contact>,
        val secrets: Map<String, String>, val customRelay: Boolean, val relayUrl: String,
        val name: String = "?", val avatar: SocialPosts.Media? = null, val title: String? = null, val badges: List<String> = emptyList(), val earned: Set<String> = emptySet(),
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val sharing: SocialSharing? = null,
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val reading: SocialReading? = null,
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val alerts: SocialAlertOptions? = null,
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val spaces: SpaceStore? = null)
    private val names = listOf("league_mailbox_v2", "league_read_capability_v2", "league_aes_read_key", "league_owner_token_v2", "league_publish_key", "social_space_contact", "social_space_writer", "cards_economy_account", "cards_economy_token")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private fun digest(domain: String, code: String) = MessageDigest.getInstance("SHA-256").digest((domain + BackupCrypto.normalize(code)).toByteArray())
    private fun path(code: String) = "/v2/recovery/" + digest("SuperHealth group recovery path v1:", code).joinToString("") { "%02x".format(it) }
    private fun token(code: String) = digest("SuperHealth group recovery owner v1:", code).joinToString("") { "%02x".format(it) }
    private fun aes(code: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(digest("SuperHealth group recovery encryption v1:", code))
    fun export(ctx: Context): Profile {
        val s = League.state.value
        val spaces = SocialSpaces.repository(ctx).state.value
        return Profile(v = 2, sharing = SocialSharing(s.shareIndex, s.sharePillars, s.shareActivities, s.shareProgress, s.shareProfilePhoto),
            reading = SocialReading(s.hiddenSocialPosts, s.mutedSocialFriends, s.quietSocialFriends, s.silentSocialThreads, s.blockedSocialKeys, s.blockedSocialAliases, s.pendingSocialRevocations, s.silentCircles),
            alerts = SocialAlertOptions(s.notifyFriends, s.notifyPosts, s.notifyReplies, s.notifyReactions, s.notifyProgress, s.syncsPerDay, s.refreshOnOpen),
            spaces = spaces.takeIf { it.own != null }?.copy(frames = emptyList(), pending = emptySet(), records = emptyList(), seen = emptyMap(), cursors = emptyMap(), transport = emptyMap(),notificationBaseline=false,removals=emptyList(),paging=emptyMap(),deferredRecords=emptyList()),
            identity = GroupIdentity.current(), contacts = s.friends.map { Contact(it.id, it.key, it.name, it.alias, it.remote, it.relayKey, it.rootKey) },
            secrets = names.mapNotNull { name -> Secrets.get(ctx, name)?.let { name to it } }.toMap(),
            customRelay = s.customRelay, relayUrl = s.relayUrl, name = (app.superhealth.ui.userName() ?: "?").take(24), avatar = s.profileAvatar.takeIf { s.shareProfilePhoto }, title = s.profileTitle, badges = s.profileBadges, earned = s.earnedBadges)
    }
    fun validate(profile: Profile) {
        require(profile.v in 1..2 && profile.contacts.size <= 500 && profile.secrets.keys.all { it in names })
        GroupIdentity.validate(profile.identity)
        require(profile.secrets.filterKeys { it in names.take(2) || it == "league_owner_token_v2" }.values.all { it.matches(Regex("[a-f0-9]{64}")) })
        profile.secrets["league_aes_read_key"]?.let { require(RelayCrypto.validKey(it)) }
        require(!profile.customRelay || Relay.validBase(profile.relayUrl))
        require(profile.contacts.all { it.id.matches(Regex("[a-f0-9]{16}")) && it.name.length <= 24 &&
            (it.remote == null || Relay.encryptedEndpoint(it.remote)) && (it.relayKey == null || RelayCrypto.validKey(it.relayKey)) })
        require(profile.name.length <= 24 && (profile.avatar?.let(ProfileAvatars::valid) ?: true))
        profile.secrets["social_space_writer"]?.let { require(it.matches(Regex("[a-f0-9]{64}"))) }
        profile.secrets["social_space_contact"]?.let { require(SocialSpaces.validContact(json.decodeFromString(SpaceContact.serializer(), it))) }
        profile.spaces?.let { spaces ->
            require(spaces.circles.size <= 12 && spaces.circles.all { SocialSpaces.validCircle(it.circle) && it.keys.values.all(RelayCrypto::validKey) })
            require(spaces.own == null || (SocialSpaces.validContact(spaces.own) && spaces.own.key == profile.identity.public))
        }
        require(profile.alerts?.frequency in setOf(null, 0, 2, 4))
        require((profile.reading?.blocked?.size ?: 0) <= 10000)
        require(Achievements.validDecor(profile.title, profile.badges))
    }
    fun restore(ctx: Context, profile: Profile, clearHistory: Boolean = true) {
        check(!SocialLifecycle.pending(ctx)) { "Terminez la sécurisation du Groupe avant un nouveau partage." }
        validate(profile)
        if (clearHistory) Secrets.put(ctx, "group_restore_pending", json.encodeToString(Profile.serializer(), profile))
        applyRestore(ctx, profile, clearHistory)
        if (clearHistory) Secrets.put(ctx, "group_restore_pending", null)
    }
    fun resumeRestore(ctx: Context) {
        val pending = Secrets.get(ctx, "group_restore_pending") ?: return
        val profile = json.decodeFromString(Profile.serializer(), pending)
        validate(profile)
        applyRestore(ctx, profile, true)
        Secrets.put(ctx, "group_restore_pending", null)
    }
    private fun applyRestore(ctx: Context, profile: Profile, clearHistory: Boolean) {
        GroupIdentity.restore(profile.identity)
        if (clearHistory) GroupHistory.reset(ctx)
        nodomain.freeyourgadget.gadgetbridge.GBApplication.getPrefs().preferences.edit().putString(nodomain.freeyourgadget.gadgetbridge.model.ActivityUser.PREF_USER_NAME, profile.name).commit()
        names.forEach { Secrets.put(ctx, it, profile.secrets[it]) }
        listOf("league_published_hash_v2", "league_published_at_v2", "profile_avatar_source", "profile_avatar_media", "profile_avatar_at").forEach { Secrets.put(ctx, it, null) }
        if (clearHistory) {
            val file = android.util.AtomicFile(java.io.File(ctx.filesDir, "league/spaces.json").apply { parentFile?.mkdirs() })
            val out = file.startWrite()
            try { out.write(json.encodeToString(SpaceStore.serializer(), profile.spaces ?: SpaceStore()).toByteArray()); file.finishWrite(out) }
            catch (e: Exception) { file.failWrite(out); throw e }
            SocialSpaces.reset()
        }
        val sharing = profile.sharing
        val reading = profile.reading ?: SocialReading()
        val alerts = profile.alerts ?: SocialAlertOptions()
        League.configure(ctx) { it.copy(shareIndex = if (clearHistory) sharing?.index ?: false else it.shareIndex,
            sharePillars = if (clearHistory) sharing?.parts ?: false else it.sharePillars,
            shareActivities = if (clearHistory) sharing?.activities ?: false else it.shareActivities,
            shareProgress = if (clearHistory) sharing?.progress ?: false else it.shareProgress,
            shareProfilePhoto = if (clearHistory) sharing?.photo ?: false else it.shareProfilePhoto,
            hiddenSocialPosts = if (clearHistory) reading.hidden else it.hiddenSocialPosts,
            mutedSocialFriends = if (clearHistory) reading.muted else it.mutedSocialFriends,
            quietSocialFriends = if (clearHistory) reading.quiet else it.quietSocialFriends,
            silentSocialThreads = if (clearHistory) reading.silent else it.silentSocialThreads,
            blockedSocialKeys = if (clearHistory) reading.blocked else it.blockedSocialKeys,
            blockedSocialAliases = if (clearHistory) reading.aliases else it.blockedSocialAliases,
            pendingSocialRevocations = if (clearHistory) reading.revocations else it.pendingSocialRevocations,
            silentCircles = if (clearHistory) reading.circles else it.silentCircles,
            notifyFriends = alerts.friends, notifyPosts = alerts.posts, notifyReplies = alerts.replies, notifyReactions = alerts.reactions,
            notifyProgress = alerts.progress, syncsPerDay = alerts.frequency, refreshOnOpen = alerts.onOpen, friends = if (!clearHistory) it.friends else profile.contacts.map { c -> League.Friend(c.id, c.key, c.name, "", 0, 0,
            alias = c.alias, remote = c.remote, relayKey = c.relayKey, rootKey = c.rootKey) }, customRelay = profile.customRelay,
            relayUrl = profile.relayUrl, profileTitle = profile.title, profileBadges = profile.badges, earnedBadges = profile.earned,
            posts = if (clearHistory) emptyList() else it.posts, reactions = if (clearHistory) emptyList() else it.reactions, profileAvatar = profile.avatar, relayEnabled = true, lastSync = 0, lastSuccessfulSync = 0, syncError = null) }
    }
    suspend fun upload(ctx: Context) = withContext(Dispatchers.IO) {
        val base = Relay.base() ?: return@withContext
        val raw = json.encodeToString(Profile.serializer(), export(ctx))
        val fingerprint = MessageDigest.getInstance("SHA-256").digest((base + raw).toByteArray()).joinToString("") { "%02x".format(it) }
        val last = Secrets.get(ctx, "group_recovery_at")?.toLongOrNull() ?: 0
        if (Secrets.get(ctx, "group_recovery_hash") == fingerprint && System.currentTimeMillis() - last < 86400000) return@withContext
        val code = Secrets.backupCode(ctx); val path = path(code)
        val encrypted = RelayCrypto.encrypt(raw, aes(code), path, System.currentTimeMillis())
        http.newCall(Request.Builder().url(base + path).header("X-Write-Token", token(code))
            .header("Authorization", "Bearer " + Relay.publicationKey(ctx)).put(encrypted.toRequestBody("application/json".toMediaType())).build()).execute().use {
            check(it.isSuccessful) { "Protection du Groupe indisponible. Mettez le serveur à jour." }
        }
        Secrets.put(ctx, "group_recovery_hash", fingerprint); Secrets.put(ctx, "group_recovery_at", System.currentTimeMillis().toString())
    }
    suspend fun revoke(ctx: Context, code: String = Secrets.backupCode(ctx), server: String? = null, publicationKey: String? = null) = withContext(Dispatchers.IO) {
        val base = server ?: Relay.base() ?: error("Activez le serveur du Groupe")
        val health = http.newCall(Request.Builder().url(base + "/health").get().build()).execute().use {
            check(it.isSuccessful); json.parseToJsonElement(it.body.string()).jsonObject
        }
        check(health["recoveryRevocation"]?.jsonPrimitive?.intOrNull == 1) { "Mettez à jour le relais pour révoquer la récupération." }
        http.newCall(Request.Builder().url(base + path(code)).header("X-Write-Token", token(code)).header("Authorization", "Bearer " + (publicationKey ?: Relay.publicationKey(ctx))).delete().build()).execute().use {
            check(it.isSuccessful || it.code == 404) { "Révocation de récupération à réessayer" }
        }
    }
    suspend fun recover(ctx: Context, code: String, server: String? = null) = withContext(Dispatchers.IO) {
        require(BackupCrypto.isValidCode(code)) { "Code de récupération non valable." }
        val base = (server?.takeIf { it.isNotBlank() } ?: Relay.base() ?: Relay.defaultBase())?.trimEnd('/')?.takeIf(Relay::validBase)
            ?: error("Adresse du serveur non valable.")
        val path = path(code)
        val encrypted = http.newCall(Request.Builder().url(base + path).get().build()).execute().use {
            check(it.isSuccessful) { "Groupe introuvable. Vérifiez le code et le serveur." }
            it.body.byteStream().use { input ->
                val bytes = input.readNBytes(98305); require(bytes.size <= 98304); bytes.decodeToString()
            }
        }
        val profile = json.decodeFromString(Profile.serializer(), RelayCrypto.decrypt(encrypted, aes(code), path))
        restore(ctx, profile)
        Secrets.remember(ctx, code); Secrets.put(ctx, Secrets.BACKUP_CODE, code)
        Secrets.put(ctx, "group_recovery_hash", null)
        GroupHistory.refresh(ctx)
        SocialSpaces.sync(ctx)
        profile.avatar?.let { avatar ->
            runCatching { SocialPosts.downloadMedia(ctx, avatar, null).copyTo(app.superhealth.ui.ProfilePhoto.file(ctx), overwrite = true); app.superhealth.ui.ProfilePhoto.load(ctx) }
        }
    }
}
