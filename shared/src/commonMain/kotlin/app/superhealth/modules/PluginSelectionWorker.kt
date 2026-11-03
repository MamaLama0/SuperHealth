package app.superhealth.modules

import app.superhealth.platform.*
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Sélection persistée : une installation interrompue est reprise au prochain réveil ou lancement. */
object PluginSelectionWorker {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val lock=kotlinx.coroutines.sync.Mutex()
    private fun file(ctx: Context)=File(ctx.filesDir,"plugin_selection.json")
    fun enqueue(ctx: Context,ids: List<String>) {
        file(ctx).writeText(Json.encodeToString(ListSerializer(String.serializer()),ids))
        scope.launch { runPending(ctx) }
    }
    suspend fun runPending(ctx: Context) {
        lock.lock()
        try {
            val ids=runCatching { Json.decodeFromString(ListSerializer(String.serializer()),file(ctx).readText()) }.getOrDefault(emptyList())
            if(ids.isEmpty())return
            Modules.refresh()
            var done=true
            ids.forEach { id ->
                if(Modules.installed.value.none { it.id==id }) {
                    val entry=Modules.catalog.value.firstOrNull { it.module.id==id }
                    if(entry==null || !Modules.install(ctx,entry))done=false
                }
            }
            if(done)file(ctx).delete()
        } finally { lock.unlock() }
    }
}
