package app.superhealth.league

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import app.superhealth.platform.*
import app.superhealth.social.SpaceContact

object SocialPrivacy {
    private val revocations=Mutex()
    fun blocked(key:String)=key in League.state.value.blockedSocialKeys || League.state.value.blockedSocialAliases[key] in League.state.value.blockedSocialKeys
    fun withdraw(ctx:Context,contact:SpaceContact) {
        if(League.state.value.friends.none { it.id==contact.id && it.key==contact.key })return
        League.configure(ctx){it.copy(pendingSocialRevocations=it.pendingSocialRevocations+contact.id,
            blockedSocialKeys=it.blockedSocialKeys+(contact.root?:contact.key),blockedSocialAliases=it.blockedSocialAliases+(contact.key to (contact.root?:contact.key)))}
        LeagueWorker.onOpen(ctx,force=true)
    }
    suspend fun block(ctx:Context,key:String) {
        val friend=League.state.value.friends.firstOrNull { it.key==key }
        if(friend==null){League.configure(ctx){it.copy(blockedSocialKeys=it.blockedSocialKeys+key)};return}
        League.configure(ctx){it.copy(blockedSocialKeys=it.blockedSocialKeys+(friend.rootKey?:key),blockedSocialAliases=it.blockedSocialAliases+(key to (friend.rootKey?:key)),pendingSocialRevocations=it.pendingSocialRevocations+friend.id)}
        val spaces=SocialSpaces.repository(ctx)
        for(access in spaces.state.value.circles.filter { !it.departed && it.circle.members.any { member->member.id==friend.id } }) {
            if(access.circle.owner.id==League.myId())spaces.removeMember(access.circle.id,friend.id) else spaces.leave(access.circle.id)
        }
        finish(ctx)
    }
    suspend fun finish(ctx:Context) = revocations.withLock {
        val excluded=League.state.value.pendingSocialRevocations
        if(excluded.isEmpty())return@withLock
        val repository=SocialSpaces.repository(ctx)
        for(access in repository.state.value.circles.filter { !it.departed && !it.circle.closed && it.circle.members.any { member->member.id in excluded } }) {
            if(access.circle.owner.id==League.myId()) {
                access.circle.members.filter { it.id in excluded && it.id!=League.myId() }.forEach { repository.removeMember(access.circle.id,it.id) }
            } else repository.leave(access.circle.id)
        }
        Relay.rotate(ctx,excluded)
        SocialSpaces.repository(ctx).acknowledgeWithdrawals(excluded)
        SocialSpaces.sync(ctx)
        League.configure(ctx){it.copy(friends=it.friends.filterNot { peer->peer.id in excluded },pendingSocialRevocations=it.pendingSocialRevocations-excluded)}
    }
}
