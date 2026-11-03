package app.superhealth.league

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** Chaque personne signe ses propres réactions dans sa carte chiffrée. Les amis communs réunissent les cartes. */
object SocialReactions {
    const val MAX = 64
    val choices = linkedMapOf("LIKE" to "👍", "DISLIKE" to "👎", "HEART" to "❤️", "FIRE" to "🔥", "CLAP" to "👏", "LAUGH" to "😂")
    fun emoji(kind: String) = choices[kind] ?: kind.removePrefix("EMOJI:")
    /** One emoji sequence: supports skin tones, flags, keycaps and joined families. No arbitrary text. */
    fun customKind(input: String): String? {
        val text = input.trim(); if (text.isEmpty() || text.length > 32) return null
        val cp = text.codePoints().toArray(); if (cp.size > 16) return null
        if (cp.all { it in 0x1F1E6..0x1F1FF }) return if (cp.size == 2) "EMOJI:$text" else null
        if (cp[0] in '0'.code..'9'.code || cp[0] == '#'.code || cp[0] == '*'.code) {
            return if ((cp.size == 2 || (cp.size == 3 && cp[1] == 0xFE0F)) && cp.last() == 0x20E3) "EMOJI:$text" else null
        }
        if (cp[0] == 0x1F3F4 && cp.size in 4..10 && cp.last() == 0xE007F && cp.drop(1).dropLast(1).all { it in 0xE0061..0xE007A }) return "EMOJI:$text"
        fun base(value: Int) = (value in 0x1F000..0x1FAFF || value in 0x2600..0x27BF ||
            value in 0x2194..0x2199 || value in 0x21A9..0x21AA || value in 0x231A..0x231B || value in 0x23E9..0x23F3 ||
            value in 0x23F8..0x23FA || value in 0x25AA..0x25AB || value in 0x2934..0x2935 || value in 0x2B05..0x2B07 ||
            value in setOf(0xA9, 0xAE, 0x203C, 0x2049, 0x2122, 0x2139, 0x2328, 0x23CF, 0x24C2, 0x25B6, 0x25C0, 0x25FB, 0x25FC, 0x25FD, 0x25FE, 0x2B1B, 0x2B1C, 0x2B50, 0x2B55, 0x3030, 0x303D, 0x3297, 0x3299)) && value !in 0x1F3FB..0x1F3FF && value !in 0x1F1E6..0x1F1FF
        var index = 0
        while (index < cp.size) {
            if (!base(cp[index++])) return null
            if (index < cp.size && cp[index] in setOf(0xFE0F, 0xFE0E)) index++
            if (index < cp.size && cp[index] in 0x1F3FB..0x1F3FF) index++
            if (index == cp.size) return "EMOJI:$text"
            if (cp[index++] != 0x200D || index == cp.size) return null
        }
        return null
    }
    fun validKind(kind: String) = kind in choices || (kind.startsWith("EMOJI:") && customKind(kind.removePrefix("EMOJI:")) == kind)
    @Serializable data class Reaction(val author: String, val post: String, val kind: String, val at: Long)
    fun valid(r: Reaction, now: Long = System.currentTimeMillis()) = r.author.matches(Regex("[a-f0-9]{16}")) &&
        r.post.matches(Regex("[a-f0-9]{32}")) && validKind(r.kind) && r.at in 1..(now + 300000)
    fun visible(list: List<Reaction>, now: Long = System.currentTimeMillis()) = list.filter { valid(it, now) && it.at >= now - GroupHistory.RETENTION_DAYS.toLong() * 86400000 }
        .sortedByDescending { it.at }.distinctBy { it.author to it.post }.take(MAX)
    fun selected(list: List<Reaction>, author: String, post: String) = list.firstOrNull { it.author == author && it.post == post }?.kind
    fun toggle(list: List<Reaction>, author: String, post: String, kind: String, now: Long): List<Reaction> {
        require(validKind(kind))
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
        check(GroupHistory.posts(ctx).any { (p, friend) -> p.id == post.id && (friend?.id ?: League.myId()) == author } && SocialPosts.valid(post))
        val previous = GroupHistory.selectedReaction(author, post.id)
        League.configure(ctx) { state ->
            val remaining = state.reactions.filterNot { it.author == author && it.post == post.id }
            state.copy(reactions = visible(if (previous == kind) remaining else listOf(Reaction(author, post.id, kind, System.currentTimeMillis())) + remaining))
        }
        GroupHistory.recordReaction(ctx, author, post.id, selected(League.state.value.reactions, author, post.id))
        LeagueWorker.onOpen(ctx, force = true)
        return try { Relay.publishOnly(ctx); true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
    }
}
