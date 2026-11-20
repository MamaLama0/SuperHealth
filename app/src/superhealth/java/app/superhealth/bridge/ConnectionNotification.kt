package app.superhealth.bridge

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import app.superhealth.engine.GBApplication
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BATTERY_SHOW_IN_NOTIFICATION
import app.superhealth.engine.util.GB

/**
 * Notification permanente « Connectée · Batterie 80 % » du moteur montre (service de connexion, canal
 * GB.NOTIFICATION_CHANNEL_ID_CONNECTION_STATUS). Android impose cette notification au service : seul l'utilisateur peut
 * bloquer ou rétablir son canal (réglages Android). L'app, elle, peut la réafficher après un balayage et choisir d'y
 * mettre la batterie (réglage de chaque montre).
 */
object ConnectionNotification {
    enum class State {
        /** Toutes les notifications de SuperHealth sont coupées. */
        APP_BLOCKED,
        /** Canal « état de la connexion » bloqué dans les réglages Android. */
        CHANNEL_BLOCKED,
        SHOWN,
        /** Canal en mode discret (volet seulement, sans icône). */
        QUIET,
        /** Autorisée, mais balayée par l'utilisateur : revient au prochain changement de la montre. */
        DISMISSED,
        /** Autorisée ; aucune montre connectée en ce moment. */
        NO_WATCH,
    }

    private const val CHANNEL = GB.NOTIFICATION_CHANNEL_ID_CONNECTION_STATUS

    fun state(ctx: Context): State {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return State.APP_BLOCKED
        val nm = ctx.getSystemService(NotificationManager::class.java)
        runCatching { GB.createNotificationChannels(ctx) }
        val channel = nm.getNotificationChannel(CHANNEL)
        val group = channel?.group?.let { nm.getNotificationChannelGroup(it) }
        if (channel?.importance == NotificationManager.IMPORTANCE_NONE || group?.isBlocked == true) return State.CHANNEL_BLOCKED
        if (!connected()) return State.NO_WATCH
        val shown = runCatching { nm.activeNotifications.any { it.id == GB.NOTIFICATION_ID && it.notification.channelId == CHANNEL } }.getOrDefault(true)
        return when {
            !shown -> State.DISMISSED
            channel != null && channel.importance <= NotificationManager.IMPORTANCE_MIN -> State.QUIET
            else -> State.SHOWN
        }
    }

    /** Autorisée par Android (même balayée ou sans montre connectée). */
    fun allowed(s: State) = s != State.APP_BLOCKED && s != State.CHANNEL_BLOCKED

    /** Réglages Android du canal (ou de l'app quand toutes ses notifications sont coupées). */
    fun openSettings(ctx: Context, s: State = state(ctx)) {
        val intent = if (s == State.APP_BLOCKED) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        else Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
        runCatching { ctx.startActivity(intent.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun connected() = runCatching { Watch.devices().any { it.isInitialized } }.getOrDefault(false)

    /**
     * Réaffiche la notification (balayée, ou batterie ajoutée/retirée), seulement quand une montre est connectée : le
     * service tourne alors, et la notification reste la sienne.
     */
    fun repost(ctx: Context): Boolean {
        if (!connected()) return false
        return runCatching { GB.updateNotification(GBApplication.app().deviceManager.selectedDevices, ctx); true }.getOrDefault(false)
    }

    /** Batterie affichée dans la notification pour toutes les montres (réglage du moteur montre, oui par défaut). */
    fun batteryShown(): Boolean = runCatching {
        Watch.devices().all { GBApplication.getDeviceSpecificSharedPrefs(it.address).getBoolean(PREF_BATTERY_SHOW_IN_NOTIFICATION + 0, true) }
    }.getOrDefault(true)

    fun setBatteryShown(ctx: Context, on: Boolean) {
        runCatching {
            Watch.devices().forEach { d ->
                val e = GBApplication.getDeviceSpecificSharedPrefs(d.address).edit()
                for (i in 0..2) e.putBoolean(PREF_BATTERY_SHOW_IN_NOTIFICATION + i, on)
                e.apply()
            }
        }
        repost(ctx)
    }
}
