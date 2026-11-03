package app.superhealth.league

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class LeagueWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!League.available() || !League.state.value.relayEnabled) return Result.success()
        if (League.state.value.syncsPerDay == 0 && !inputData.getBoolean("onOpen", false)) return Result.success()
        return try {
            Relay.sync(applicationContext)
            // Mettre à jour les cartes d'accueil après la réception des scores, sans attendre l'ouverture de l'app.
            app.superhealth.ui.widget.DayWidgets.refresh(applicationContext)
            if (League.state.value.syncError == null) Result.success() else Result.retry()
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            League.configure(applicationContext) { it.copy(syncError = "Synchronisation indisponible. Réessayez plus tard.") }
            Result.retry()
        }
    }

    companion object {
        fun onOpen(ctx: Context, force: Boolean = false) {
            if (!League.available() || !League.state.value.relayEnabled) return
            val s = League.state.value
            val social = s.notifyFriends || s.notifyPosts || s.notifyReactions || s.notifyReplies
            if (!force && ((!s.refreshOnOpen && !social) || System.currentTimeMillis() - s.lastSync < (if (social) 30_000 else TimeUnit.MINUTES.toMillis(10)))) return
            if (Relay.base() == null && s.friends.none { it.remote != null }) return
            WorkManager.getInstance(ctx).enqueueUniqueWork("league_open", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LeagueWorker>().setInputData(workDataOf("onOpen" to true))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
        fun schedule(ctx: Context) {
            val manager = WorkManager.getInstance(ctx)
            val frequency = if (League.available() && League.state.value.relayEnabled) League.state.value.syncsPerDay else 0
            if (frequency !in setOf(2, 4)) { manager.cancelUniqueWork("league_sync"); return }
            if (Relay.base() == null && League.state.value.friends.none { it.remote != null }) return
            manager.enqueueUniquePeriodicWork("league_sync", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<LeagueWorker>(if (League.state.value.notifyFriends || League.state.value.notifyPosts || League.state.value.notifyReactions || League.state.value.notifyReplies) 15L else (24 / frequency * 60).toLong(), TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
