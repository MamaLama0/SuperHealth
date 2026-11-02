package app.superhealth.league

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class LeagueWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!League.available()) return Result.success()
        if (League.state.value.syncsPerDay == 0 && !inputData.getBoolean("onOpen", false)) return Result.success()
        return try { Relay.sync(applicationContext); Result.success() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            League.configure(applicationContext) { it.copy(syncError = e.message) }
            Result.retry()
        }
    }

    companion object {
        fun onOpen(ctx: Context) {
            if (!League.available()) return
            val s = League.state.value
            if (!s.refreshOnOpen || System.currentTimeMillis() - s.lastSync < TimeUnit.MINUTES.toMillis(10)) return
            if (s.relayUrl.isBlank() && s.friends.none { it.remote != null }) return
            WorkManager.getInstance(ctx).enqueueUniqueWork("league_open", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LeagueWorker>().setInputData(workDataOf("onOpen" to true))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
        fun schedule(ctx: Context) {
            val manager = WorkManager.getInstance(ctx)
            val frequency = if (League.available()) League.state.value.syncsPerDay else 0
            if (frequency !in setOf(2, 4)) { manager.cancelUniqueWork("league_sync"); return }
            if (League.state.value.relayUrl.isBlank() && League.state.value.friends.none { it.remote != null }) return
            manager.enqueueUniquePeriodicWork("league_sync", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<LeagueWorker>((24 / frequency).toLong(), TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
