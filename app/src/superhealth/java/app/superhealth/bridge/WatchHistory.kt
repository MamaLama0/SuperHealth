package app.superhealth.bridge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitImporter
import org.slf4j.LoggerFactory

/**
 * Activités passées (course, randonnée, natation…) :
 * - récupérées sur la montre Garmin, y compris celles déjà envoyées à Garmin Connect (la montre les garde en mémoire) ;
 * - ou importées depuis des fichiers .fit, l'export ZIP de Garmin Connect (« Exporter vos données ») ou de Strava.
 */
object WatchHistory {
    private val LOG = LoggerFactory.getLogger(WatchHistory::class.java)

    private fun garmin(): List<GBDevice> = Watch.devices().filter { it.deviceCoordinator is GarminCoordinator }

    /** Une montre (Garmin) dont on peut relire l'historique, ou vers laquelle importer des fichiers .fit. */
    fun supported(): Boolean = garmin().isNotEmpty()

    /**
     * Demande à chaque montre Garmin connectée la liste complète de ses activités, pas seulement les nouvelles.
     * Renvoie le nombre de montres interrogées (0 : aucune connectée).
     */
    fun fetchFromWatch(): Int {
        val connected = garmin().filter { it.isInitialized }
        connected.forEach { GBApplication.deviceService(it).onFetchRecordedData(RecordedDataTypes.TYPE_HISTORY) }
        return connected.size
    }

    data class ImportResult(val imported: Int, val failed: Int)

    /**
     * Importe des fichiers .fit (éventuellement .fit.gz) ou des archives ZIP qui en contiennent (même imbriquées,
     * comme l'export Garmin Connect). Les fichiers sont gardés dans l'app : le détail d'une séance les relit.
     */
    suspend fun import(ctx: Context, uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        val device = garmin().firstOrNull() ?: error("no Garmin device")
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "fit-import").apply { mkdirs() }
        val files = mutableListOf<File>()
        uris.forEach { uri ->
            val name = displayName(ctx, uri) ?: uri.lastPathSegment ?: "import"
            ctx.contentResolver.openInputStream(uri)?.use { extract(it.buffered(), name, dir, files) }
        }
        val importer = FitImporter(ctx, device)
        var ok = 0; var ko = 0
        files.forEach { f ->
            runCatching { importer.importFile(f, false) }
                .onSuccess { ok++ }
                .onFailure { e -> ko++; LOG.warn("Import failed for {}", f, e); f.delete() }
        }
        ImportResult(ok, ko)
    }

    private fun extract(input: InputStream, name: String, dir: File, out: MutableList<File>) {
        val lower = name.lowercase()
        when {
            lower.endsWith(".zip") -> {
                val zip = ZipInputStream(input)
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory) extract(NonClosing(zip), e.name.substringAfterLast('/'), dir, out)
                }
            }
            lower.endsWith(".fit.gz") -> extract(GZIPInputStream(input), name.dropLast(3), dir, out)
            lower.endsWith(".fit") -> {
                val f = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                if (f.exists() && f.length() > 0) return // déjà importé
                f.outputStream().use { input.copyTo(it) }
                out += f
            }
        }
    }

    /** Lecture d'une entrée de ZIP sans fermer l'archive. */
    private class NonClosing(private val s: InputStream) : InputStream() {
        override fun read() = s.read()
        override fun read(b: ByteArray, off: Int, len: Int) = s.read(b, off, len)
        override fun close() {}
    }

    private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
