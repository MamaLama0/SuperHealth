package app.superhealth.training.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import app.superhealth.engine.R

/**
 * Fin du repos :
 * alarme système, notification et son « beep.wav » repris de l'APK.
 */
object RestTimer {
    private const val CHANNEL = "rest"
    private const val ID = 7

    fun schedule(ctx: Context, at: Long, sound: Boolean) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pending(ctx, sound))
        // 3.8 : alarme exacte quand Android l'autorise ; l'alarme « inexacte » pouvait sonner plusieurs minutes trop tard en veille
        val exact = android.os.Build.VERSION.SDK_INT < 31 || runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
        if (exact) runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, sound)) }
            .onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, sound)) }
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, sound))
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, true))
        ctx.getSystemService(NotificationManager::class.java).cancel(ID)
        WatchRest.clear(ctx)
    }

    private fun pending(ctx: Context, sound: Boolean) = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, RestDoneReceiver::class.java).putExtra("sound", sound),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Fin du repos : son et vibration seulement. Plus de notification « série suivante » en bandeau : elle masquait le
     * chronomètre de la notification de séance, qui affiche déjà la série à faire.
     */
    fun notifyDone(ctx: Context, sound: Boolean) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.cancel(ID)
        nm.deleteNotificationChannel(CHANNEL)
        // 3.8 : le signal sonore ne dépend que de son propre réglage (Musculation > Réglages > Signal sonore de fin de récupération)
        if (sound) beep(ctx)
        // la notification de séance repasse du compte à rebours au chronomètre de séance
        SessionNotification.update(ctx, app.superhealth.training.WorkoutApp.store.state.value)
        if (app.superhealth.SuperPrefs.settings.value.notifyRest) runCatching { ctx.getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1)) }
        WatchRest.restDone(ctx)
    }

    fun beep(ctx: Context) {
        val pool = SoundPool.Builder().setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        ).setMaxStreams(1).build()
        pool.setOnLoadCompleteListener { p, id, _ ->
            p.play(id, 1f, 1f, 1, 0, 1f)
            // libéré après le son (un SoundPool par bip restait ouvert)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { p.release() } }, 2500)
        }
        pool.load(ctx, R.raw.beep, 1)
    }
}

class RestDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = RestTimer.notifyDone(context, intent.getBooleanExtra("sound", true))
}

/**
 * Fin du repos sur la montre, désactivable dans Réglages > Notifications :
 * - directement sur les montres connectées à SuperHealth (Garmin sans Garmin Connect, Amazfit, Huawei, Xiaomi, Bangle.js…),
 *   qui vibrent et affichent la prochaine série ;
 * - en option, par une notification du téléphone que relaient les apps de montre (Wear OS, Garmin Connect, Zepp,
 *   Galaxy Wearable…). Elle s'efface seule au bout d'une minute.
 */
object WatchRest {
    private const val CHANNEL = "rest_watch"
    private const val ID = 8
    private var lastSpecId = 0

    fun restDone(ctx: Context) {
        val prefs = app.superhealth.SuperPrefs.settings.value
        if (!prefs.restOnWatch && !prefs.restWatchNotification) return
        val st = app.superhealth.training.WorkoutApp.store.state.value
        if (st.session == null) return
        val title = app.superhealth.ui.tr("Fin du repos")
        val body = SessionNotification.nextSetText(st) ?: return
        if (prefs.restOnWatch) runCatching { toConnectedWatches(title, body) }
        if (prefs.restWatchNotification) runCatching { mirrored(ctx, title, body) }
    }

    /** Essai depuis les réglages, sans séance en cours. */
    fun test(ctx: Context) {
        val prefs = app.superhealth.SuperPrefs.settings.value
        val title = app.superhealth.ui.tr("Fin du repos")
        val body = app.superhealth.ui.tr("Essai : la prochaine série s'affichera ici")
        if (prefs.restOnWatch) runCatching { toConnectedWatches(title, body) }
        if (prefs.restWatchNotification) runCatching { mirrored(ctx, title, body) }
    }

    /** Les montres reçoivent une notification ; la précédente est retirée pour ne pas en empiler une par série. */
    private fun toConnectedWatches(title: String, body: String) {
        if (app.superhealth.bridge.Watch.devices().none { it.isInitialized }) return
        val service = app.superhealth.engine.GBApplication.deviceService()
        if (lastSpecId != 0) service.onDeleteNotification(lastSpecId)
        val spec = app.superhealth.engine.model.NotificationSpec().apply {
            type = app.superhealth.engine.model.NotificationType.UNKNOWN
            sourceName = "SuperHealth"
            sourceAppId = app.superhealth.engine.GBApplication.getContext().packageName
            this.title = title
            this.body = body
            attachedActions = ArrayList()
        }
        lastSpecId = spec.id
        service.onNotification(spec)
    }

    private fun mirrored(ctx: Context, title: String, body: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, app.superhealth.ui.tr("Fin du repos sur la montre"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            enableVibration(true); setSound(null, null); setShowBadge(false)
        })
        val n = androidx.core.app.NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_workout)
            .setContentTitle(title).setContentText(body).setAutoCancel(true).setTimeoutAfter(60_000)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
            .extend(androidx.core.app.NotificationCompat.WearableExtender())
            .build()
        nm.notify(ID, n)
    }

    /** Coupé avec la séance : plus de rappel en attente sur la montre ou le téléphone. */
    fun clear(ctx: Context) {
        runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(ID) }
        if (lastSpecId != 0) runCatching { app.superhealth.engine.GBApplication.deviceService().onDeleteNotification(lastSpecId) }
        lastSpecId = 0
    }
}
