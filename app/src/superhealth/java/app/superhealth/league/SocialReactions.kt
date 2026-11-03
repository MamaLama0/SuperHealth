package app.superhealth.league

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** Chaque personne signe ses propres réactions dans sa carte chiffrée. Les amis communs réunissent les cartes. */
object SocialReactions {
    const val MAX = 64
    val choices = linkedMapOf("LIKE" to "👍", "DISLIKE" to "👎", "HEART" to "❤️", "FIRE" to "🔥", "CLAP" to "👏", "LAUGH" to "😂")
    @Serializable data class Reaction(val author: String, val post: String, val kind: String, val at: Long)
    fun valid(r: Reaction, now: Long = System.currentTimeMillis()) = r.author.matches(Regex("[a-f0-9]{16}")) &&
        r.post.matches(Regex("[a-f0-9]{32}")) && r.kind in choices && r.at in 1..(now + 300000)
    fun visible(list: List<Reaction>, now: Long = System.currentTimeMillis()) = list.filter { valid(it, now) && it.at >= now - 34L * 86400000 }
        .sortedByDescending { it.at }.distinctBy { it.author to it.post }.take(MAX)
    fun selected(list: List<Reaction>, author: String, post: String) = list.firstOrNull { it.author == author && it.post == post }?.kind
    fun toggle(list: List<Reaction>, author: String, post: String, kind: String, now: Long): List<Reaction> {
        require(kind in choices)
        val old = selected(list, author, post)
        val next = list.filterNot { it.author == author && it.post == post }
        return visible(if (old == kind) next else listOf(Reaction(author, post, kind, now)) + next, now)
    }
    /** Un vote par personne et publication, limité aux amis réellement connus de celui qui regarde. */
    fun counts(mine: List<Reaction>, friends: List<League.Friend>, author: String, post: String, now: Long = System.currentTimeMillis()): Map<String, Int> =
        (listOf(mine) + friends.distinctBy { it.key }.map { it.reactions }).mapNotNull { list ->
            visible(list, now).firstOrNull { it.author == author && it.post == post }?.kind
        }.groupingBy { it }.eachCount()

    suspend fun react(ctx: Context, author: String, post: SocialPosts.Post, kind: String): Boolean {
        check(League.available())
        val known = if (author == League.myId()) League.state.value.posts else League.state.value.friends.firstOrNull { it.id == author }?.posts.orEmpty()
        check(known.any { it.id == post.id } && SocialPosts.valid(post))
        League.configure(ctx) { it.copy(reactions = toggle(it.reactions, author, post.id, kind, System.currentTimeMillis())) }
        LeagueWorker.onOpen(ctx, force = true)
        return try { Relay.publishOnly(ctx); true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
    }
}
