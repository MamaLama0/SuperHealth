package app.superhealth.league

import app.superhealth.platform.*
import app.superhealth.social.*
import app.superhealth.ui.tr

/** Private history is available even when system notifications are disabled. */
object SocialNotifications {
    fun show(ctx:Context,id:String,route:String,category:String,group:String?=null) {
        val title=tr("Du nouveau dans le Groupe")
        val body=tr("Une nouvelle activité vous attend dans votre Groupe.")
        if(SocialInbox.items.value.any { it.id==id || id in it.eventIds })return
        SocialInbox.add(ctx,id,title,body,route,group)
        val state=League.state.value
        val enabled=when(category){"FRIEND","INVITE"->state.notifyFriends;"REPLY"->state.notifyReplies;"REACTION"->state.notifyReactions;else->state.notifyPosts}
        if(enabled && state.relayEnabled) scheduleNotification("social:"+(group?:id),title,body,System.currentTimeMillis()+1000,category="social",route=route)
    }
    fun spaces(ctx:Context,before:SpaceStore,after:SpaceStore) {
        spaceAlerts(before,after,System.currentTimeMillis()).forEach { alert ->
            val author=if(alert.category=="INVITE")after.invitations.firstOrNull{it.circle==alert.circle}?.owner?.id else alert.id.substringBefore(':')
            val settings=League.state.value
            if(author in settings.quietSocialFriends || author in settings.mutedSocialFriends || author in FriendSocial.repository(ctx).state.value.muted)return@forEach
            show(ctx,alert.id,"group/circles?circle="+alert.circle+(alert.post?.let{"&post="+it}?:""),alert.category,"circle:"+alert.circle)
        }
    }
    fun timeline(ctx:Context,before:TimelineStore,after:TimelineStore) {
        val repository=FriendSocial.repository(ctx)
        val known=repository.resolvedEvents(before).map { it.key to it.id }.toSet()
        val state=League.state.value
        repository.resolvedEvents(after).filter { (it.key to it.id) !in known && it.key!=League.publicKey() && !it.deleted && it.revision>System.currentTimeMillis()-86400000 && !SocialPrivacy.blocked(it.key) }.forEach { event ->
            val author=state.friends.firstOrNull{it.key==event.key}?.id?:Sha256.digest(Base64.getUrlDecoder().decode(event.key)).take(8).joinToString(""){"%02x".format(it)}
            if(author in state.mutedSocialFriends || author in state.quietSocialFriends || author in after.muted)return@forEach
            val post=event.post?.id?:event.reply?.post?:event.reaction?.post?:return@forEach
            val owner=event.reply?.author?:event.reaction?.author?:author
            if("$owner/$post" in after.silent || "$owner/$post" in state.silentSocialThreads || "$owner/$post" in after.hidden)return@forEach
            val kind=if(event.reply!=null)"REPLY" else if(event.reaction!=null)"REACTION" else "POST"
            show(ctx,event.key+event.id,"group/post/$owner/$post",kind,"$kind:$owner:$post")
        }
    }
}
