package app.superhealth.modules

import android.content.Context
import androidx.work.*

/** La sélection de l'accueil survit à la fermeture de l'écran et à une coupure réseau. */
class PluginSelectionWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Modules.refresh()
        var complete = true
        for (id in inputData.getStringArray("ids").orEmpty()) {
            val installed = Modules.installed.value.firstOrNull { it.id == id }
            if (installed == null) {
                val entry = Modules.catalog.value.firstOrNull { it.module.id == id }
                if (entry == null || !Modules.install(applicationContext, entry)) complete = false
            }
        }
        return if (complete) Result.success() else Result.retry()
    }
    companion object {
        fun enqueue(ctx: Context, ids: List<String>) {
            if (ids.isEmpty()) {
                WorkManager.getInstance(ctx).cancelUniqueWork("superhealth_plugin_selection")
                return
            }
            WorkManager.getInstance(ctx).enqueueUniqueWork("superhealth_plugin_selection", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PluginSelectionWorker>().setInputData(workDataOf("ids" to ids.toTypedArray()))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
