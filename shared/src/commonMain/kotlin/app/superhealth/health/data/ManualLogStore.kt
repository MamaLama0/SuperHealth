package app.superhealth.health.data

import app.superhealth.platform.*

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class ManualLog(val id: Long, val metric: Metric, val timestampMs: Long, val value: Double)

/**
 * Values the user logs by hand (hydration, glucose…). Sur Android, une petite base SQLite ; ici un fichier JSON
 * (quelques centaines de lignes au plus), même interface.
 */
class ManualLogStore private constructor(context: Context) {
    @Serializable private data class Row(val id: Long, val metric: String, val ts: Long, val value: Double)

    private val file = File(context.filesDir, "localhealth_manual.json")
    private val json = Json { ignoreUnknownKeys = true }
    private var rows: List<Row> = runCatching { json.decodeFromString(ListSerializer(Row.serializer()), file.readText()) }.getOrDefault(emptyList())

    private val _changes = MutableStateFlow(0)
    /** Bumped on every write so screens can reload. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private fun save() {
        file.writeText(json.encodeToString(ListSerializer(Row.serializer()), rows))
        _changes.value++
    }

    suspend fun add(metric: Metric, timestampMs: Long, value: Double) = withContext(Dispatchers.IO) {
        synchronized(this@ManualLogStore) {
            rows = rows + Row((rows.maxOfOrNull { it.id } ?: 0L) + 1, metric.name, timestampMs, value)
            save()
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        synchronized(this@ManualLogStore) { rows = rows.filter { it.id != id }; save() }
    }

    private fun Row.log(): ManualLog? = Metric.entries.firstOrNull { it.name == metric }?.let { ManualLog(id, it, ts, value) }

    suspend fun query(metric: Metric?, fromMs: Long, toMs: Long): List<ManualLog> = withContext(Dispatchers.IO) {
        rows.filter { it.ts in fromMs until toMs && (metric == null || it.metric == metric.name) }
            .sortedByDescending { it.ts }.mapNotNull { it.log() }
    }

    suspend fun latestBefore(metric: Metric, toMs: Long): ManualLog? = withContext(Dispatchers.IO) {
        rows.filter { it.metric == metric.name && it.ts < toMs }.maxByOrNull { it.ts }?.log()
    }

    /** Remplace toutes les saisies (restauration d'une sauvegarde). */
    suspend fun replaceAll(logs: List<ManualLog>) = withContext(Dispatchers.IO) {
        synchronized(this@ManualLogStore) {
            rows = logs.mapIndexed { i, l -> Row(i + 1L, l.metric.name, l.timestampMs, l.value) }
            save()
        }
    }

    companion object {
        private var instance: ManualLogStore? = null
        fun get(context: Context): ManualLogStore =
            instance ?: synchronized(this) { instance ?: ManualLogStore(context.applicationContext).also { instance = it } }
    }
}
