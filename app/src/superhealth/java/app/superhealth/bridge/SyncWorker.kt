package app.superhealth.bridge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.superhealth.SuperPrefs
import java.util.concurrent.TimeUnit

/** Tâche horaire : Health Connect (si activé), données de la montre vers la nutrition, puis widgets (changement de jour). */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (SuperPrefs.settings.value.healthConnect) runCatching { HealthConnectHub.sync(applicationContext) }
        runCatching { Bridge.pullWatch(applicationContext) }
        if (app.superhealth.modules.Modules.isActive(app.superhealth.modules.Modules.ADVENTURE)) runCatching { announceTrophies(applicationContext) }
        app.superhealth.nutrition.widget.Widgets.refresh(applicationContext)
        app.superhealth.training.widget.Widgets.refresh(applicationContext)
        return Result.success()
    }

    /** Nouveaux paliers de trophées : une notification (le premier calcul ne fait que mémoriser l'existant). */
    private suspend fun announceTrophies(ctx: Context) {
        val s = app.superhealth.achievements.AchievementData.summary(ctx, force = true)
        val fresh = app.superhealth.achievements.AchievementData.newlyUnlocked(ctx, s)
        if (fresh.isEmpty()) return
        val names = fresh.mapNotNull { k ->
            val (t, i) = k.split(":").let { it[0] to it[1].toInt() }
            runCatching { app.superhealth.achievements.Achievements.Trophy.valueOf(t) }.getOrNull()?.let {
                app.superhealth.ui.tr(it.label) + " (" + app.superhealth.ui.tr(listOf("bronze", "argent", "or")[i]) + ")"
            }
        }
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        nm.createNotificationChannel(android.app.NotificationChannel("achievements", app.superhealth.ui.tr("Succès"), android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val open = android.app.PendingIntent.getActivity(ctx, 4301, android.content.Intent(ctx, app.superhealth.MainActivity::class.java)
            .putExtra(app.superhealth.MainActivity.EXTRA_ROUTE, "achievements").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val n = androidx.core.app.NotificationCompat.Builder(ctx, "achievements").setSmallIcon(nodomain.freeyourgadget.gadgetbridge.R.drawable.ic_stat_trophy)
            .setContentTitle(if (names.size == 1) app.superhealth.ui.tr("Trophée débloqué") else app.superhealth.ui.trf("%1\$s trophées débloqués", names.size))
            .setContentText(names.joinToString(", ")).setContentIntent(open).setAutoCancel(true).build()
        runCatching { nm.notify(4301, n) }
        app.superhealth.achievements.AchievementData.markSeen(ctx, fresh)
    }

    companion object {
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "superhealth_sync", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).build(),
            )
        }
    }
}
