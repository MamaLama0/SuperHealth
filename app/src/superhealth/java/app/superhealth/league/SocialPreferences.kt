package app.superhealth.league

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import android.content.Context

/** Local reading preferences. Never included in signed cards or social events. */
object SocialPreferences {
    private val revocations=Mutex()
    fun thread(author: String, post: String) = "$author/$post"
    fun hidden(author: String, post: String) = thread(author, post) in League.state.value.hiddenSocialPosts
    fun silent(author: String, post: String) = thread(author, post) in League.state.value.silentSocialThreads
    fun blocked(key: String) = key in League.state.value.blockedSocialKeys || League.state.value.blockedSocialAliases[key] in League.state.value.blockedSocialKeys ||
        League.state.value.friends.any { it.key == key && it.rootKey in League.state.value.blockedSocialKeys }
    fun quiet(author: String) = author in League.state.value.quietSocialFriends || muted(author)
    fun quiet(ctx: Context, author: String, on: Boolean) = League.configure(ctx) {
        it.copy(quietSocialFriends = if (on) it.quietSocialFriends + author else it.quietSocialFriends - author)
    }
    suspend fun block(ctx: Context, friend: League.Friend) {
        League.configure(ctx) { it.copy(blockedSocialKeys = it.blockedSocialKeys + (friend.rootKey ?: friend.key), blockedSocialAliases = it.blockedSocialAliases + (friend.key to (friend.rootKey ?: friend.key)),
            pendingSocialRevocations = it.pendingSocialRevocations + friend.id) }
        val spaces = SocialSpaces.repository(ctx)
        for (access in spaces.state.value.circles.filter { !it.departed && it.circle.members.any { member -> member.id == friend.id } }) {
            if (access.circle.owner.id == League.myId()) spaces.removeMember(access.circle.id, friend.id)
            else spaces.leave(access.circle.id)
        }
        LeagueWorker.onOpen(ctx, force = true)
        finishRevocations(ctx)
    }
    suspend fun blockAuthor(ctx: Context, key: String) {
        val friend = League.state.value.friends.firstOrNull { it.key == key }
        if (friend != null) block(ctx, friend)
        else League.configure(ctx) { it.copy(blockedSocialKeys = it.blockedSocialKeys + key) }
    }
    suspend fun finishRevocations(ctx: Context) = revocations.withLock {
        val excluded = League.state.value.pendingSocialRevocations
        if (excluded.isEmpty()) return@withLock
        val repository=SocialSpaces.repository(ctx)
        for(access in repository.state.value.circles.filter { !it.departed && !it.circle.closed && it.circle.members.any { member->member.id in excluded } }) {
            if(access.circle.owner.id==League.myId()) {
                access.circle.members.filter { it.id in excluded && it.id!=League.myId() }.forEach { repository.removeMember(access.circle.id,it.id) }
            } else repository.leave(access.circle.id)
        }
        Relay.rotate(ctx, excluded)
        SocialSpaces.repository(ctx).acknowledgeWithdrawals(excluded)
        SocialSpaces.sync(ctx)
        League.configure(ctx) { it.copy(friends = it.friends.filterNot { f -> f.id in excluded }, pendingSocialRevocations = it.pendingSocialRevocations - excluded) }
    }
    fun unblock(ctx: Context, key: String) = League.configure(ctx) { it.copy(blockedSocialKeys = it.blockedSocialKeys - key) }
    fun muted(author: String) = author in League.state.value.mutedSocialFriends
    fun hide(ctx: Context, author: String, post: String) = League.configure(ctx) {
        it.copy(hiddenSocialPosts = it.hiddenSocialPosts + thread(author, post))
    }
    fun mute(ctx: Context, author: String, on: Boolean) = League.configure(ctx) {
        it.copy(mutedSocialFriends = if (on) it.mutedSocialFriends + author else it.mutedSocialFriends - author)
    }
    fun silence(ctx: Context, author: String, post: String, on: Boolean) = League.configure(ctx) {
        val id = thread(author, post)
        it.copy(silentSocialThreads = if (on) it.silentSocialThreads + id else it.silentSocialThreads - id)
    }
    fun restoreHidden(ctx: Context) = League.configure(ctx) { it.copy(hiddenSocialPosts = emptySet()) }
    fun restoreThreads(ctx: Context) = League.configure(ctx) { it.copy(silentSocialThreads = emptySet()) }
    fun route(author: String, post: String, reply: String? = null): String =
        "group/post/$author/$post" + (reply?.let { "?reply=$it" } ?: "")
}
