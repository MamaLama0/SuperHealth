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
        app.superhealth.nutrition.widget.Widgets.refresh(applicationContext)
        app.superhealth.training.widget.Widgets.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "superhealth_sync", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).build(),
            )
        }
    }
}
