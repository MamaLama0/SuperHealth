package app.superhealth.training.ui

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import app.superhealth.MainActivity
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * Fin du repos (RestTimerSoundChannel / rest_timer_notification_service de MacroFactor Workouts) :
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
