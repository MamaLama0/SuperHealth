package app.superhealth.league

import app.superhealth.platform.*
import app.superhealth.ui.widget.Widgets
import kotlinx.coroutines.*

/** iOS choisit les réveils de fond ; la fréquence est un intervalle minimum, jamais une garantie. */
object LeagueWorker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null
    fun schedule(ctx: Context) { onOpen(ctx) }
    fun onOpen(ctx: Context, force:Boolean=false) {
        if (SocialLifecycle.pending(ctx)) { synchronized(this) { if (pending?.isActive != true) pending = scope.launch { SocialLifecycle.resume(ctx) } };return }
        if (!League.available() || !League.state.value.relayEnabled) return
        val s = League.state.value
        if (!force && !s.refreshOnOpen && !s.notifyPosts && !s.notifyReplies) return
        if (!force && System.currentTimeMillis()-s.lastSync < 30000) return
        synchronized(this) { if (pending?.isActive != true) pending = scope.launch { sync(ctx) } }
    }
    suspend fun runIfDue(ctx: Context) {
        val s = League.state.value
        if (!League.available() || !s.relayEnabled || s.syncsPerDay !in setOf(2,4)) return
        if (System.currentTimeMillis()-s.lastSync >= TimeUnit.HOURS.toMillis((24/s.syncsPerDay).toLong())) sync(ctx)
    }
    private suspend fun sync(ctx: Context) {
        try { Relay.sync(ctx); if(League.state.value.pendingSocialRevocations.isNotEmpty())SocialPrivacy.finish(ctx); Widgets.publish()
            // Photo détourée publique de la carte : facultative, un serveur pas encore à jour ne bloque pas la synchro.
            runCatching { Faces.publishCardIfNeeded(ctx) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { League.configure(ctx) { it.copy(syncError="Synchronisation indisponible. Réessayez plus tard.") } }
    }
}
