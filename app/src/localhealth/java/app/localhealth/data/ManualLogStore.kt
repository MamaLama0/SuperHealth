package app.localhealth.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ManualLog(val id: Long, val metric: Metric, val timestampMs: Long, val value: Double)

/**
 * Values the user logs by hand (weight, hydration, glucose…), kept in a small local SQLite
 * database next to Gadgetbridge's, since those aren't tied to a paired device.
 */
class ManualLogStore private constructor(context: Context) :
    SQLiteOpenHelper(context, "localhealth_manual.db", null, 1) {

    private val _changes = MutableStateFlow(0)
    /** Bumped on every write so screens can reload. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE logs (id INTEGER PRIMARY KEY AUTOINCREMENT, metric TEXT NOT NULL, ts INTEGER NOT NULL, value REAL NOT NULL)")
        db.execSQL("CREATE INDEX logs_metric_ts ON logs (metric, ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    suspend fun add(metric: Metric, timestampMs: Long, value: Double) = withContext(Dispatchers.IO) {
        writableDatabase.insert("logs", null, ContentValues().apply {
            put("metric", metric.name)
            put("ts", timestampMs)
            put("value", value)
        })
        _changes.value++
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete("logs", "id = ?", arrayOf(id.toString()))
        _changes.value++
    }

    suspend fun query(metric: Metric?, fromMs: Long, toMs: Long): List<ManualLog> = withContext(Dispatchers.IO) {
        val where = StringBuilder("ts >= ? AND ts < ?")
        val args = mutableListOf(fromMs.toString(), toMs.toString())
        if (metric != null) {
            where.append(" AND metric = ?")
            args.add(metric.name)
        }
        readableDatabase.query("logs", arrayOf("id", "metric", "ts", "value"), where.toString(), args.toTypedArray(),
            null, null, "ts DESC").use { c ->
            buildList {
                while (c.moveToNext()) {
                    val m = runCatching { Metric.valueOf(c.getString(1)) }.getOrNull() ?: continue
                    add(ManualLog(c.getLong(0), m, c.getLong(2), c.getDouble(3)))
                }
            }
        }
    }

    suspend fun latestBefore(metric: Metric, toMs: Long): ManualLog? =
        withContext(Dispatchers.IO) {
            readableDatabase.query("logs", arrayOf("id", "ts", "value"), "metric = ? AND ts < ?",
                arrayOf(metric.name, toMs.toString()), null, null, "ts DESC", "1").use { c ->
                if (c.moveToFirst()) ManualLog(c.getLong(0), metric, c.getLong(1), c.getDouble(2)) else null
            }
        }

    companion object {
        @Volatile private var instance: ManualLogStore? = null
        fun get(context: Context): ManualLogStore =
            instance ?: synchronized(this) { instance ?: ManualLogStore(context.applicationContext).also { instance = it } }
    }
}
