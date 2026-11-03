package app.superhealth.league

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.superhealth.MainActivity
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import nodomain.freeyourgadget.gadgetbridge.R

/** Événements reçus après vérification des cartes ; aucun contenu personnel sur l'écran verrouillé. */
object SocialNotifications {
    private const val FRIENDS = "social_friends"
    private const val POSTS = "social_posts"
    private const val REACTIONS = "social_reactions"

    internal fun newPosts(old: League.Friend?, next: League.Friend, now: Long = System.currentTimeMillis()): List<SocialPosts.Post> {
        // Premier chargement : établir la référence sans notifier tout l'historique d'un nouvel ami.
        if (old == null || old.time <= 0) return emptyList()
        val known = old.posts.map { it.id }.toSet()
        return SocialPosts.visible(next.posts, now).filter { it.id !in known && it.created > now - 86400000 }
    }

    fun friendAdded(ctx: Context, friend: League.Friend) {
        show(ctx, FRIENDS, friend.id, tr("Nouvel ami"), trf("%1\$s vous a ajouté en ami. Vous êtes maintenant connectés tous les deux.", friend.shown), "friends")
    }

    fun postsReceived(ctx: Context, old: League.Friend?, next: League.Friend) {
        val posts = newPosts(old, next)
        if (posts.isEmpty()) return
        val text = if (posts.size > 1) trf("%1\$s a partagé %2\$s nouvelles publications.", next.shown, posts.size)
        else trf(when (posts.first().kind) {
            "WORKOUT" -> "%1\$s a partagé une séance."
            "RUN" -> "%1\$s a partagé une sortie."
            "STREAK" -> "%1\$s a partagé sa série."
            "PHOTO" -> "%1\$s a partagé une photo."
            "VIDEO" -> "%1\$s a partagé une vidéo."
            else -> "%1\$s a publié dans le fil."
        }, next.shown)
        show(ctx, POSTS, next.id + posts.joinToString { it.id }, tr("Du nouveau dans le Groupe"), text, "feed")
    }

    fun reactionsReceived(ctx: Context, old: League.Friend?, next: League.Friend) {
        if (old == null || old.time <= 0 || next.reactions.isEmpty()) return
        val me = League.myId()
        val posts = League.state.value.posts.map { it.id }.toSet()
        val fresh = SocialReactions.visible(next.reactions).filter { r -> r.author == me && r.post in posts &&
            r.at > System.currentTimeMillis() - 86400000 && old.reactions.none { it.author == r.author && it.post == r.post && it.kind == r.kind && it.at >= r.at } }
        if (fresh.isNotEmpty()) show(ctx, REACTIONS, next.id + fresh.maxOf { it.at }, tr("Réaction à votre publication"), trf("%1\$s a réagi à votre publication.", next.shown), "feed")
    }

    fun groupRepliesReceived(ctx: Context, friend: League.Friend, eventId: String = "") {
        show(ctx, "group_replies", friend.id + eventId, tr("Réponse à votre publication"), trf("%1\$s a répondu dans le Groupe.", friend.shown), "feed")
    }

    fun progressEarned(ctx: Context, badges: Int, level: Int?) {
        val text = if (level != null) trf("Vous avez atteint le niveau %1\$s !", level) else trf("%1\$s nouveau(x) badge(s) débloqué(s).", badges)
        show(ctx, "progress_rewards", "${level ?: 0}:$badges:${java.time.LocalDate.now()}", tr("Bravo pour vos progrès"), text, "progress", requiresRelay = false)
    }

    private fun show(ctx: Context, channel: String, key: String, title: String, text: String, route: String, requiresRelay: Boolean = true) {
        if (!League.available()) return
        SocialInbox.add(ctx, channel + key, title, text, route)
        val settings = League.state.value
        val enabled = when (channel) {
            FRIENDS -> settings.notifyFriends
            POSTS -> settings.notifyPosts
            REACTIONS -> settings.notifyReactions
            "group_replies" -> settings.notifyReplies
            else -> settings.notifyProgress
        }
        if (!enabled || (requiresRelay && !League.state.value.relayEnabled) || !NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(FRIENDS, tr("Ajouts d'amis"), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(POSTS, tr("Publications des amis"), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(REACTIONS, tr("Réactions aux publications"), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel("progress_rewards", tr("Badges et niveaux"), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel("group_replies", tr("Réponses aux publications"), NotificationManager.IMPORTANCE_DEFAULT))
        val id = (channel + key).hashCode()
        val open = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(ctx, channel).setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("SuperHealth").setContentText(tr("Nouvelle activité entre amis")).build()
        val notification = NotificationCompat.Builder(ctx, channel).setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).build()
        runCatching { nm.notify(channel + key, id, notification) }
    }
}
