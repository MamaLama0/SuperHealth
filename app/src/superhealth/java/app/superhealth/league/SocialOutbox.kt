package app.superhealth.league

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Durable, bounded encrypted attachment queue. The original URI is no longer needed after enqueue. */
object SocialOutbox {
    @Serializable data class Entry(val post: SocialPosts.Post, val ownerKey: String = "")
    @Serializable data class Store(val entries: List<Entry> = emptyList())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun directory(ctx: Context) = File(ctx.filesDir, "league/outbox").apply { mkdirs() }
    private fun index(ctx: Context) = AtomicFile(File(directory(ctx), "queue.json"))
    @Synchronized fun load(ctx: Context): Store {
        val file = index(ctx)
        if (!file.baseFile.exists()) return Store()
        val store = file.openRead().bufferedReader().use { json.decodeFromString(Store.serializer(), it.readText()) }
        return Store(store.entries.map { entry -> if (entry.ownerKey.isNotBlank()) entry else entry.copy(ownerKey =
            GroupHistory.state.value.events.firstOrNull { it.id == entry.post.id && it.post != null }?.key.orEmpty()) })
    }
    private fun save(ctx: Context, store: Store) {
        val file = index(ctx); val out = file.startWrite()
        try { out.write(json.encodeToString(Store.serializer(), store).toByteArray()); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    @Synchronized fun enqueue(ctx: Context, post: SocialPosts.Post, encrypted: File?) {
        val old = load(ctx)
        require(old.entries.size < 20) { "La file contient déjà 20 publications. Envoyez-les avant de continuer." }
        val size = directory(ctx).listFiles().orEmpty().sumOf { it.length() }
        require(size + (encrypted?.length() ?: 0) <= 250L * 1024 * 1024) { "La file de médias est pleine." }
        val target = File(directory(ctx), post.id + ".enc")
        if (encrypted != null) {
            val atomic = AtomicFile(target); val out = atomic.startWrite()
            try { encrypted.inputStream().use { it.copyTo(out) }; atomic.finishWrite(out) }
            catch (e: Exception) { atomic.failWrite(out); throw e }
        }
        try { save(ctx, Store(old.entries + Entry(post, League.publicKey()))); League.pendingMediaPosts = pendingIds(ctx) }
        catch (e: Exception) { target.delete(); throw e }
    }
    @Synchronized fun replace(ctx: Context, post: SocialPosts.Post) {
        save(ctx, Store(load(ctx).entries.map { if (it.post.id == post.id) it.copy(post = post) else it }))
    }
    @Synchronized fun cancel(ctx: Context, id: String) {
        save(ctx, Store(load(ctx).entries.filterNot { it.post.id == id }))
        League.pendingMediaPosts = pendingIds(ctx)
        File(directory(ctx), id + ".enc").delete()
    }
    fun flush(ctx: Context, endpoint: String) {
        // Every operation is idempotent. On a crash, both attachment PUT and post recording can be repeated.
        for (entry in load(ctx).entries) {
            if (entry.ownerKey != League.publicKey()) continue
            entry.post.media?.let { SocialPosts.sendAttachment(ctx, endpoint, it, File(directory(ctx), entry.post.id + ".enc")) }
            commit(ctx, entry)
        }
    }
    @Synchronized private fun commit(ctx: Context, entry: Entry) {
        if (load(ctx).entries.firstOrNull { it.post.id == entry.post.id } != entry) return
        if (!GroupHistory.recordQueued(ctx, entry.post)) { cancel(ctx, entry.post.id); return }
        League.configure(ctx) { it.copy(posts = SocialPosts.visible(listOf(entry.post) + it.posts.filterNot { p -> p.id == entry.post.id })) }
        cancel(ctx, entry.post.id)
    }
    fun pendingIds(ctx: Context) = load(ctx).entries.map { it.post.id }.toSet()
}
