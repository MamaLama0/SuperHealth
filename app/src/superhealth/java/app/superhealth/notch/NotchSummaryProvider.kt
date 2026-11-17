package app.superhealth.notch

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Résumé du jour en lecture seule pour Notch : content://app.superhealth.notch.provider/today, une ligne
 * (colonnes de [NotchSummary]) ; et les montres : …/watch, une ligne par montre connue, connectées d'abord
 * (colonnes [WATCH_COLUMNS] ; l'adresse n'est jamais donnée, seulement son condensat).
 *
 * Exporté mais protégé par app.superhealth.permission.READ_SUMMARY, de niveau « dangerous » : ce sont des données de
 * santé, l'utilisateur doit donc l'accorder lui-même (fenêtre d'Android). « signature » est impossible (Notch n'est pas
 * signé avec la clé de SuperHealth) et « normal » laisserait n'importe quelle app lire ces données en silence.
 * Aucune écriture : insert, update et delete sont refusés.
 */
class NotchSummaryProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val path = uri.pathSegments.singleOrNull()
        if (path != PATH_TODAY && path != PATH_WATCH) return null
        val ctx = context ?: return null
        // Le fournisseur peut être appelé avant la fin de SuperHealthApp.onCreate (démarrage à froid par Notch).
        if (!NotchBridge.awaitReady(READY_TIMEOUT_MS)) return null
        if (path == PATH_WATCH) return watches(projection)
        // Lecture avec l'identité de SuperHealth, pas celle de l'appelant : certains services système vérifient l'appelant.
        val token = Binder.clearCallingIdentity()
        val row = try {
            runBlocking { withTimeoutOrNull(READ_TIMEOUT_MS) { NotchSummary.today(ctx) } }
        } finally {
            Binder.restoreCallingIdentity(token)
        } ?: return null
        val columns = projection?.filter { it in NotchSummary.COLUMNS } ?: NotchSummary.COLUMNS
        return MatrixCursor(columns.toTypedArray(), 1).apply { addRow(columns.map { row[it] }) }
    }

    /** name, connected (0/1), battery (−1 : inconnue), charging (0/1), updated_at (ms), address_hash. */
    private fun watches(projection: Array<out String>?): Cursor {
        val columns = projection?.filter { it in WATCH_COLUMNS } ?: WATCH_COLUMNS
        val cursor = MatrixCursor(columns.toTypedArray())
        app.superhealth.bridge.WatchStatus.state.value.forEach { w ->
            val row = mapOf(
                "name" to w.name,
                "connected" to if (w.connected) 1 else 0,
                "battery" to (w.battery ?: -1),
                "charging" to if (w.charging) 1 else 0,
                "updated_at" to w.updatedAt,
                "address_hash" to w.key,
            )
            cursor.addRow(columns.map { row[it] })
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = when (uri.pathSegments.singleOrNull()) {
        PATH_TODAY -> "vnd.android.cursor.item/vnd.app.superhealth.today"
        PATH_WATCH -> "vnd.android.cursor.dir/vnd.app.superhealth.watch"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("read-only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("read-only")

    private companion object {
        const val PATH_TODAY = "today"
        const val PATH_WATCH = "watch"
        val WATCH_COLUMNS = listOf("name", "connected", "battery", "charging", "updated_at", "address_hash")
        const val READY_TIMEOUT_MS = 3_000L
        const val READ_TIMEOUT_MS = 4_000L
    }
}
