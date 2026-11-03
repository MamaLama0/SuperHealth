package app.superhealth.league

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Social-only durable history. Health cards and journals never enter this store. */
object GroupHistory {
    const val RETENTION_DAYS = 5 * 366
    @Serializable data class ReplyRef(val author: String, val id: String)
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Serializable data class Reply(val author: String, val post: String, val text: String, val created: Long,
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val to: ReplyRef? = null)
    @Serializable data class Profile(val title: String? = null, val badges: List<String> = emptyList(), val avatar: SocialPosts.Media? = null)
    @Serializable data class Forward(val post: String, val key: String, val signed: String)
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Serializable data class Event(val id: String, val key: String, val name: String, val revision: Long,
        val post: SocialPosts.Post? = null, val reply: Reply? = null, val deleted: Boolean = false, val profile: Profile? = null, val reaction: SocialReactions.Reaction? = null,
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val forward: Forward? = null)
    @Serializable data class Removal(val endpoint: String, val media: String)
    @Serializable data class Store(val events: List<Event> = emptyList(), val pending: Set<String> = emptySet(),
        val cursors: Map<String, String?> = emptyMap(), val notificationBaseline: Boolean = false, val removals: List<Removal> = emptyList(), val proofs: Map<String, String> = emptyMap())
    @Serializable private data class RemoteItem(val id: String, val raw: String)
    @Serializable private data class Page(val items: List<RemoteItem>, val next: String? = null)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val network = Mutex()
    val state = MutableStateFlow(Store())
    private fun file(ctx: Context) = AtomicFile(File(ctx.filesDir, "league/group-history.json").apply { parentFile?.mkdirs() })
    fun load(ctx: Context) {
        state.value = runCatching { file(ctx).openRead().bufferedReader().use { json.decodeFromString(Store.serializer(), it.readText()) } }.getOrDefault(Store())
    }
    @Synchronized private fun update(ctx: Context, change: (Store) -> Store) {
        val next = change(state.value); val atomic = file(ctx); val stream = atomic.startWrite()
        try { stream.write(json.encodeToString(Store.serializer(), next).toByteArray()); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
        state.value = next
    }
    fun author(key: String) = java.security.MessageDigest.getInstance("SHA-256").digest(java.util.Base64.getUrlDecoder().decode(key))
        .take(8).joinToString("") { "%02x".format(it) }
    fun valid(event: Event, now: Long = System.currentTimeMillis()): Boolean = event.id.matches(Regex("[a-f0-9]{32}")) &&
        event.key.length in 32..512 && event.name.length <= 24 && event.revision in 1..(now + 300000) && when {
            event.forward != null -> !event.deleted && event.post == null && event.reply == null && event.profile == null && event.reaction == null && forwarded(event, now) != null
            event.deleted -> event.post == null && event.reply == null && event.profile == null && event.reaction == null
            event.reaction != null -> event.post == null && event.reply == null && event.profile == null && SocialReactions.valid(event.reaction, now)
            event.profile != null -> event.reaction == null && event.post == null && event.reply == null && Achievements.validDecor(event.profile.title, event.profile.badges) && (event.profile.avatar?.let(ProfileAvatars::valid) ?: true)
            event.post != null -> event.reaction == null && event.profile == null && event.reply == null && event.post.id == event.id && SocialPosts.valid(event.post, now)
            event.reply != null -> event.reaction == null && event.profile == null && event.reply.author.matches(Regex("[a-f0-9]{16}")) && event.reply.post.matches(Regex("[a-f0-9]{32}")) &&
                event.reply.text.isNotBlank() && event.reply.text.length <= 1000 && event.reply.created in 1..(now + 300000) &&
                (event.reply.to?.let { it.author.matches(Regex("[a-f0-9]{16}")) && it.id.matches(Regex("[a-f0-9]{32}")) && it.id != event.id } ?: true)
            else -> false
        }
    internal fun forwardId(key: String, id: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(("SuperHealth comment forward v1:" + key + ":" + id).toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    /** Only a post owner may relay a comment; both the owner and the commenter sign it. No contact/health card is relayed. */
    internal fun forwarded(wrapper: Event, now: Long = System.currentTimeMillis()): Event? = runCatching {
        val forward = wrapper.forward ?: return null
        require(forward.post.matches(Regex("[a-f0-9]{32}")))
        require(forward.key.length in 32..512 && forward.signed.length <= 14000)
        val payload = League.verifySocial(forward.signed, forward.key) ?: return null
        val inner = json.decodeFromString(Event.serializer(), payload)
        require(inner.forward == null && inner.key == forward.key && valid(inner, now))
        require(inner.reply != null || inner.deleted)
        require(wrapper.id == forwardId(inner.key, inner.id))
        if (inner.reply != null) require(inner.reply.author == author(wrapper.key) && inner.reply.post == forward.post)
        inner
    }.getOrNull()
    internal fun conversation(events: List<Event>, target: String, post: String, ownerKey: String, trusted: Set<String>): List<Event> {
        val direct = events.filter { it.key in trusted && it.forward == null }
        val relayed = events.filter { it.key == ownerKey && it.forward?.post == post }.mapNotNull { forwarded(it) }
        return (direct + relayed).groupBy { it.key to it.id }.values.map { versions -> versions.maxBy { it.revision } }
            .filter { !it.deleted && it.reply?.author == target && it.reply.post == post }.sortedBy { it.reply!!.created }
    }
    internal fun merge(events: List<Event>, event: Event): List<Event> {
        val previous = events.firstOrNull { it.key == event.key && it.id == event.id }
        if (previous != null && previous.revision >= event.revision) return events
        return events.filterNot { it.key == event.key && it.id == event.id } + event
    }
    private fun ownEvent(id: String, post: SocialPosts.Post? = null, reply: Reply? = null, deleted: Boolean = false): Event {
        val key = League.publicKey()
        val old = state.value.events.firstOrNull { it.key == key && it.id == id }
        return Event(id, key, (app.superhealth.ui.userName() ?: "?").take(24), maxOf(System.currentTimeMillis(), (old?.revision ?: 0) + 1), post, reply, deleted)
    }
    fun record(ctx: Context, post: SocialPosts.Post) {
        val event = ownEvent(post.id, post = post); require(valid(event))
        update(ctx) { it.copy(events = merge(it.events, event), pending = it.pending + post.id) }
    }
    fun delete(ctx: Context, post: SocialPosts.Post) {
        val event = ownEvent(post.id, deleted = true)
        val removal = post.media?.let { media -> Relay.myEndpoint(ctx)?.let { Removal(it, media.id) } }
        update(ctx) { it.copy(events = merge(it.events, event), pending = it.pending + event.id,
            removals = (it.removals + listOfNotNull(removal)).distinct()) }
    }
    private fun reactionId(author: String, post: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(("SuperHealth reaction v1:" + author + ":" + post).toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    fun recordReaction(ctx: Context, author: String, post: String, kind: String?) {
        val id = reactionId(author, post)
        val event = if (kind == null) ownEvent(id, deleted = true) else ownEvent(id).copy(reaction = SocialReactions.Reaction(author, post, kind, System.currentTimeMillis()))
        require(valid(event)); update(ctx) { it.copy(events = merge(it.events, event), pending = it.pending + id) }
    }
    fun selectedReaction(author: String, post: String): String? {
        val event = state.value.events.firstOrNull { it.key == League.publicKey() && it.id == reactionId(author, post) }
        return if (event != null) event.reaction?.kind else SocialReactions.selected(League.state.value.reactions, author, post)
    }
    fun reactionCounts(author: String, post: String): Map<String, Int> {
        val people = listOf(League.publicKey() to League.state.value.reactions) + League.state.value.friends.distinctBy { it.key }.map { it.key to it.reactions }
        return people.mapNotNull { (key, legacy) ->
            val event = state.value.events.firstOrNull { it.key == key && it.id == reactionId(author, post) }
            if (event != null) event.reaction?.kind else SocialReactions.visible(legacy).firstOrNull { it.author == author && it.post == post }?.kind
        }.groupingBy { it }.eachCount()
    }
    suspend fun reply(ctx: Context, target: String, post: SocialPosts.Post, text: String, parent: Event? = null) = withContext(Dispatchers.IO) {
        require(text.trim().isNotBlank() && text.trim().length <= 1000)
        require(target == League.myId() || League.state.value.friends.any { it.id == target })
        require(parent == null || replies(target, post.id).any { it.key == parent.key && it.id == parent.id })
        val id = UUID.randomUUID().toString().replace("-", "")
        val event = ownEvent(id, reply = Reply(target, post.id, text.trim(), System.currentTimeMillis(), parent?.let { ReplyRef(author(it.key), it.id) }))
        require(valid(event)); update(ctx) { it.copy(events = merge(it.events, event), pending = it.pending + id) }
        try { Relay.publishOnly(ctx); true } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { false }
    }
    suspend fun removeReply(ctx: Context, event: Event) = withContext(Dispatchers.IO) {
        check(event.key == League.publicKey())
        val deleted = ownEvent(event.id, deleted = true)
        update(ctx) { it.copy(events = merge(it.events, deleted), pending = it.pending + event.id) }
        Relay.publishOnly(ctx)
    }
    fun replies(target: String, post: String): List<Event> {
        val own = League.publicKey()
        val friends = League.state.value.friends
        val owner = if (target == League.myId()) own else friends.firstOrNull { it.id == target }?.key ?: return emptyList()
        return conversation(state.value.events, target, post, owner, (friends.map { it.key } + own).toSet())
    }
    /** Resolve only a visible reply in the same post; never quote another thread or a deleted message. */
    fun replyParent(event: Event, candidates: List<Event>): Event? {
        val child = event.reply ?: return null
        val ref = child.to ?: return null
        return candidates.firstOrNull { candidate -> !candidate.deleted && candidate.reply != null && candidate.id == ref.id &&
            candidate.reply.author == child.author && candidate.reply.post == child.post &&
            runCatching { author(candidate.key) == ref.author }.getOrDefault(false) }
    }
    data class ReplyRow(val event: Event, val depth: Int)
    /** Parents precede children; missing parents remain readable. Cycles cannot hide comments. */
    fun replyRows(candidates: List<Event>): List<ReplyRow> {
        val events = candidates.filter { !it.deleted && it.reply != null }.distinctBy { it.key to it.id }
            .sortedBy { it.reply!!.created }
        val parents = events.associateWith { replyParent(it, events) }
        val visited = mutableSetOf<Pair<String, String>>()
        val result = mutableListOf<ReplyRow>()
        fun append(start: Event, depth: Int) {
            // Iterative traversal avoids stack overflow on long or malicious reply chains.
            val stack = java.util.ArrayDeque<Pair<Event, Int>>()
            stack.add(start to depth)
            while (stack.isNotEmpty()) {
                val (event, level) = stack.removeLast()
                if (!visited.add(event.key to event.id)) continue
                result += ReplyRow(event, level.coerceAtMost(2))
                events.filter { parents[it] == event }.asReversed().forEach { stack.add(it to level + 1) }
            }
        }
        events.filter { parents[it] == null }.forEach { append(it, 0) }
        events.forEach { append(it, 0) }
        return result
    }
    fun posts(ctx: Context): List<Pair<SocialPosts.Post, League.Friend?>> {
        val own = League.publicKey(); val friends = League.state.value.friends.associateBy { it.key }
        val archived = state.value.events.filter { !it.deleted && it.post != null && it.post.created >= System.currentTimeMillis() - RETENTION_DAYS.toLong() * 86400000 && (it.key == own || friends.containsKey(it.key)) }
            .map { it.post!! to friends[it.key] }
        val removed = state.value.events.filter { it.deleted }.map { it.key to it.id }.toSet()
        val legacy = SocialPosts.visible(League.state.value.posts).map { it to null } + friends.values.flatMap { f -> SocialPosts.visible(f.posts).map { it to f } }
        return (archived + legacy).filterNot { (post, friend) -> ((friend?.key ?: own) to post.id) in removed }
            .distinctBy { (post, friend) -> (friend?.key ?: own) to post.id }.sortedByDescending { it.first.created }
    }
    fun republish(ctx: Context) {
        val key = League.publicKey(); val now = System.currentTimeMillis()
        update(ctx) { store ->
            val own = store.events.filter { it.key == key }.sortedBy {
                it.post?.created ?: it.reply?.created ?: it.reaction?.at ?: Long.MAX_VALUE
            }.mapIndexed { index, event -> event.copy(revision = maxOf(now + index, event.revision + 1)) }
            val ids = own.map { it.id }
            store.copy(events = store.events.filterNot { it.key == key } + own,
                pending = (store.pending - ids.toSet()) + ids)
        }
    }
    fun reset(ctx: Context) = update(ctx) { Store() }
    fun hasOlder() = state.value.cursors.values.any { it != null }
    fun pending(post: String) = post in state.value.pending
    private fun body(response: okhttp3.Response): String = response.body.byteStream().use { input ->
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = input.read(buffer); if (n < 0) break; check(out.size() + n <= 1024 * 1024); out.write(buffer, 0, n) }
        out.toString("UTF-8")
    }
    suspend fun publish(ctx: Context, endpoint: String) = network.withLock { withContext(Dispatchers.IO) {
        val current = League.state.value
        val profile = Profile(current.profileTitle, current.profileBadges.filter { it in current.earnedBadges }.take(3), current.profileAvatar.takeIf { current.shareProfilePhoto })
        val profileId = "0".repeat(32)
        val previousProfile = state.value.events.firstOrNull { it.key == League.publicKey() && it.id == profileId }
        val name = (app.superhealth.ui.userName() ?: "?").take(24)
        if (previousProfile?.profile != profile || previousProfile.name != name) {
            val event = ownEvent(profileId).copy(profile = profile)
            update(ctx) { it.copy(events = merge(it.events, event), pending = it.pending + profileId) }
        }
        for (post in League.state.value.posts) if (state.value.events.none { it.key == League.publicKey() && it.id == post.id }) record(ctx, post)
        val ownKey = League.publicKey()
        val ownPosts = (state.value.events.filter { it.key == ownKey && it.post != null && !it.deleted }.map { it.id } + current.posts.map { it.id }).toSet()
        for ((proofId, signed) in state.value.proofs) {
            val key = proofId.substringBefore(':'); val id = proofId.substringAfter(':')
            val payload = League.verifySocial(signed, key) ?: continue
            val inner = runCatching { json.decodeFromString(Event.serializer(), payload) }.getOrNull() ?: continue
            if (inner.forward != null || inner.key != key || !valid(inner)) continue
            val wrapperId = forwardId(key,id)
            val old = state.value.events.firstOrNull { it.key == ownKey && it.id == wrapperId }
            val post = inner.reply?.takeIf { it.author == League.myId() }?.post ?: old?.forward?.post ?: continue
            if (post !in ownPosts || (inner.reply == null && !inner.deleted)) continue
            val forward = Forward(post,key,signed)
            if (old?.forward == forward) continue
            val event = ownEvent(wrapperId).copy(forward = forward)
            require(valid(event))
            update(ctx) { it.copy(events = merge(it.events,event),pending = it.pending + wrapperId) }
        }
        for (id in state.value.pending.toList()) {
            var event = state.value.events.firstOrNull { it.id == id && it.key == League.publicKey() } ?: continue
            // A previous HTTP response may have been lost after the server committed it.
            // Persist a newer revision before every retry because AES-GCM creates a fresh nonce.
            run {
                event = event.copy(revision = maxOf(System.currentTimeMillis(), event.revision + 1))
                val refreshed = event
                update(ctx) { it.copy(events = merge(it.events, refreshed)) }
            }
            val path = endpoint.toHttpUrlOrNull()!!.encodedPath + "/social/" + id
            val signed = League.signSocial(json.encodeToString(Event.serializer(), event))
            val encrypted = RelayCrypto.encrypt(signed, Relay.readKey(ctx), path, event.revision)
            http.newCall(Relay.auth(ctx, Request.Builder().url(endpoint + "/social/" + id))
                .put(encrypted.toRequestBody("application/json".toMediaType())).build()).execute().use {
                check(it.isSuccessful) { if (it.code == 404) "Mettez à jour le serveur pour l'historique du Groupe et les réponses." else "Envoi au Groupe à réessayer." }
            }
            update(ctx) { store -> if (store.events.firstOrNull { it.key == event.key && it.id == id }?.revision == event.revision)
                store.copy(pending = store.pending - id) else store }
        }
        for (removal in state.value.removals.toList()) {
            if (removal.endpoint != endpoint) continue
            http.newCall(Relay.auth(ctx, Request.Builder().url(endpoint + "/media/" + removal.media)).delete().build()).execute().use {
                check(it.isSuccessful || it.code == 404) { "Suppression du média à réessayer." }
            }
            update(ctx) { it.copy(removals = it.removals - removal) }
        }
    } }
    /** A recovered installation may hold only the first page. Drain the owner's archive before revoking its URL. */
    suspend fun loadOwnHistory(ctx: Context) {
        refresh(ctx, ownOnly = true)
        while (state.value.cursors[League.publicKey()] != null) refresh(ctx, older = true, ownOnly = true)
    }
    suspend fun refresh(ctx: Context, older: Boolean = false, ownOnly: Boolean = false) = network.withLock { withContext(Dispatchers.IO) {
        val failures = mutableListOf<Exception>()
        val own = Relay.myEndpoint(ctx)?.let { Triple(League.publicKey(), it, Relay.readKey(ctx)) }
        val people = listOfNotNull(own) + (if (ownOnly) emptyList() else League.state.value.friends.mapNotNull { f ->
            if (f.remote != null && f.relayKey != null && Relay.encryptedEndpoint(f.remote)) Triple(f.key, f.remote, f.relayKey) else null
        })
        for ((key, endpoint, aes) in people) {
            try {
            if (older && state.value.cursors[key] == null) continue
            val cursor = if (older) state.value.cursors[key] else null
            val url = endpoint.toHttpUrlOrNull()!!.newBuilder().addPathSegment("social").apply { cursor?.let { addQueryParameter("before", it) } }.build()
            val page = http.newCall(Request.Builder().url(url).get().build()).execute().use {
                if (it.code == 404) {
                    update(ctx) { store -> store.copy(cursors = store.cursors + (key to null)) }; return@use null
                }
                check(it.isSuccessful) { "Historique du Groupe indisponible. Réessayez." }
                json.decodeFromString(Page.serializer(), body(it))
            } ?: continue
            require(page.items.size <= 20 && (page.next == null || page.next.matches(Regex("[0-9]{16}:[a-f0-9]{32}"))))
            val newReplies = mutableListOf<String>()
            for (item in page.items) {
                val path = endpoint.toHttpUrlOrNull()!!.encodedPath + "/social/" + item.id
                val signed = RelayCrypto.decrypt(item.raw, aes, path)
                val payload = League.verifySocial(signed, key) ?: error("Signature du Groupe non valable.")
                val event = json.decodeFromString(Event.serializer(), payload)
                val envelope = json.decodeFromString(RelayCrypto.Envelope.serializer(), item.raw)
                require(valid(event) && event.key == key && event.id == item.id && event.revision == envelope.revision)
                val comment = if (event.forward != null) forwarded(event) else event
                val received = comment?.reply
                val repliesToMe = received?.author == League.myId() ||
                    (received?.to?.author == author(League.publicKey()) && comment != null && replyParent(comment, state.value.events)?.key == League.publicKey())
                if (!older && state.value.notificationBaseline && key != League.publicKey() && received != null && repliesToMe &&
                    received.created >= System.currentTimeMillis() - 86400000 && state.value.events.none { it.key == key && it.id == event.id }) newReplies += event.id
                update(ctx) { store -> store.copy(events = merge(store.events, event),
                    proofs = if (key != League.publicKey() && event.forward == null && (event.reply != null || event.deleted))
                        store.proofs + ((key + ":" + event.id) to signed) else store.proofs) }
                event.profile?.let { profile ->
                    League.configure(ctx) { state -> state.copy(friends = state.friends.map { friend ->
                        if (friend.key == key && event.revision > friend.profileRevision) friend.copy(name = event.name, profileTitle = profile.title, profileBadges = profile.badges, avatar = profile.avatar, profileRevision = event.revision) else friend
                    }) }
                }
            }
            if (newReplies.isNotEmpty()) League.state.value.friends.firstOrNull { it.key == key }?.let { SocialNotifications.groupRepliesReceived(ctx, it, newReplies.sorted().joinToString()) }
            update(ctx) { it.copy(cursors = it.cursors + (key to (if (!older && key in it.cursors) it.cursors[key] ?: page.next else page.next))) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { failures += e }
        }
        if (failures.isNotEmpty()) throw IllegalStateException("Une partie du Groupe n'a pas pu être actualisée.", failures.first())
        if (!older) update(ctx) { it.copy(notificationBaseline = true) }
    } }
}
