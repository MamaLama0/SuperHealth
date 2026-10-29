package app.superhealth.wellbeing

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.superhealth.Feature
import app.superhealth.MainActivity
import app.superhealth.SuperPrefs
import app.superhealth.has
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Rappels choisis dans Réglages > Rappels (tous coupés au départ) : pesée du matin, journal alimentaire du soir, journal
 * du moral et coucher conseillé (30 min avant). Un rappel déjà inutile ce jour-là (pesée faite, humeur notée, repas notés
 * dans les 4 dernières heures) ne sonne pas.
 */
object Reminders {
    enum class Kind(val title: String, val route: String) {
        WEIGH("Pesée du matin", "weight"),
        LOG("Journal alimentaire", "log"),
        JOURNAL("Comment s'est passée la journée ?", "journal"),
        BEDTIME("Bientôt l'heure du coucher", "home"),
    }

    private const val CHANNEL = "reminders"
    private const val ACTION = "app.superhealth.REMINDER"

    /** Minute du jour du rappel ; null : coupé (ou sans objet, comme le coucher sans assez de nuits). */
    private suspend fun minuteOf(ctx: Context, k: Kind): Int? {
        val s = SuperPrefs.settings.value
        return when (k) {
            Kind.WEIGH -> s.remindWeigh.takeIf { it >= 0 && s.has(Feature.NUTRITION) }
            Kind.LOG -> s.remindLog.takeIf { it >= 0 && s.has(Feature.NUTRITION) }
            Kind.JOURNAL -> s.remindJournal.takeIf { it >= 0 && s.has(Feature.JOURNAL) }
            Kind.BEDTIME -> if (s.remindBedtime) runCatching { Bedtime.tonight(ctx) }.getOrNull()?.let { Math.floorMod(it.bedMinute - 30, 24 * 60) } else null
        }
    }

    private fun pending(ctx: Context, k: Kind) = PendingIntent.getBroadcast(ctx, 7300 + k.ordinal,
        Intent(ctx, RemindersReceiver::class.java).setAction(ACTION).putExtra("kind", k.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Programme le prochain passage de chaque rappel actif (à rappeler après chaque changement de réglage). */
    fun schedule(ctx: Context) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            val am = app.getSystemService(AlarmManager::class.java)
            val zone = ZoneId.systemDefault()
            for (k in Kind.entries) {
                val pi = pending(app, k)
                val minute = minuteOf(app, k)
                if (minute == null) { am.cancel(pi); continue }
                var at = LocalDate.now().atStartOfDay().plusMinutes(minute.toLong())
                if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
                val ms = at.atZone(zone).toInstant().toEpochMilli()
                // À l'heure dite quand Android l'autorise, sinon dans la fenêtre d'une heure du système
                if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            }
        }
    }

    /** Le rappel sert-il encore aujourd'hui ? */
    private suspend fun useful(ctx: Context, k: Kind): Boolean {
        val today = LocalDate.now().toEpochDay()
        val repo = app.superhealth.nutrition.NutriApp.repository
        return when (k) {
            Kind.WEIGH -> repo.weighIns.all().none { it.epochDay == today }
            Kind.LOG -> repo.entries.forDay(today).first().none { System.currentTimeMillis() - it.createdAt < 4 * 3_600_000L }
            Kind.JOURNAL -> Journal.today() == null
            Kind.BEDTIME -> true
        }
    }

    internal suspend fun fire(ctx: Context, k: Kind) {
        if (minuteOf(ctx, k) != null && useful(ctx, k)) notify(ctx, k)
        schedule(ctx)
    }

    private suspend fun notify(ctx: Context, k: Kind) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Rappels"), NotificationManager.IMPORTANCE_DEFAULT))
        val body = when (k) {
            Kind.WEIGH -> tr("Montez sur la balance avant de manger.")
            Kind.LOG -> tr("Notez vos repas de la journée.")
            Kind.JOURNAL -> tr("Une note d'humeur, dix secondes.")
            Kind.BEDTIME -> Bedtime.tonight(ctx)?.let { trf("Coucher conseillé à %1\$s.", Bedtime.clock(it.bedMinute)) } ?: return
        }
        val open = PendingIntent.getActivity(ctx, 7400 + k.ordinal, Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, k.route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(7500 + k.ordinal, NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(nodomain.freeyourgadget.gadgetbridge.R.drawable.ic_notification)
            .setContentTitle(tr(k.title)).setContentText(body).setContentIntent(open).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).build())
    }
}

class RemindersReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                when (intent.action) {
                    "app.superhealth.REMINDER" -> Reminders.Kind.entries.firstOrNull { it.name == intent.getStringExtra("kind") }?.let { Reminders.fire(app, it) }
                    else -> Reminders.schedule(app)
                }
            }
            pending.finish()
        }
    }
}
