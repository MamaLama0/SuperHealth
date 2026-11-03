package app.superhealth.league

import app.superhealth.platform.*
import app.superhealth.ui.widget.Widgets
import kotlinx.coroutines.*

/** iOS choisit les réveils de fond ; la fréquence est un intervalle minimum, jamais une garantie. */
object LeagueWorker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null
    fun schedule(ctx: Context) { onOpen(ctx) }
    fun onOpen(ctx: Context) {
        if (!League.available() || !League.state.value.relayEnabled) return
        val s = League.state.value
        if (!s.refreshOnOpen || System.currentTimeMillis()-s.lastSync < TimeUnit.MINUTES.toMillis(10)) return
        synchronized(this) { if (pending?.isActive != true) pending = scope.launch { sync(ctx) } }
    }
    suspend fun runIfDue(ctx: Context) {
        val s = League.state.value
        if (!League.available() || !s.relayEnabled || s.syncsPerDay !in setOf(2,4)) return
        if (System.currentTimeMillis()-s.lastSync >= TimeUnit.HOURS.toMillis((24/s.syncsPerDay).toLong())) sync(ctx)
    }
    private suspend fun sync(ctx: Context) {
        try { Relay.sync(ctx); Widgets.publish() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { League.configure(ctx) { it.copy(syncError="Synchronisation indisponible. Réessayez plus tard.") } }
    }
}
