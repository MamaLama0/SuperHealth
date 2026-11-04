package app.superhealth.bridge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.superhealth.SuperPrefs
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Health Connect avec l'accord d'arrière-plan, données de la montre vers la nutrition, puis widgets. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (SuperPrefs.settings.value.healthConnect && HcBackground.allowed(applicationContext)) {
            val fullSyncDue = System.currentTimeMillis() - SuperPrefs.settings.value.lastHcSync >= TimeUnit.HOURS.toMillis(1)
            if (fullSyncDue || HealthConnectHub.activitiesChanged(applicationContext)) {
                runCatching { HealthConnectHub.sync(applicationContext) }
                    .onFailure { if (it is CancellationException) throw it }
            }
        }
        runCatching { Bridge.pullWatch(applicationContext) }
            .onFailure { if (it is CancellationException) throw it }
        app.superhealth.nutrition.widget.Widgets.refresh(applicationContext)
        app.superhealth.training.widget.Widgets.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private val schedulingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun schedule(ctx: Context) {
            val app = ctx.applicationContext
            schedulingScope.launch {
                val background = HcBackground.allowed(app) && SuperPrefs.settings.value.healthConnect
                WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                    "superhealth_sync", ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<SyncWorker>(if (background) 15L else 60L, TimeUnit.MINUTES).build(),
                )
            }
        }
    }
}
