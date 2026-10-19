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
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitImporter
import org.slf4j.LoggerFactory

/**
 * Import d'activités (course, randonnée, natation…) depuis des fichiers .fit (.fit.gz) ou une archive ZIP qui en contient,
 * même imbriquée : export « Vos données » de Garmin Connect, export Strava, copie de la mémoire de la montre.
 * Les séances sont enregistrées comme celles synchronisées depuis la montre (doublons ignorés).
 */
object FitImport {
    private val LOG = LoggerFactory.getLogger(FitImport::class.java)

    private fun garmin(): List<GBDevice> = Watch.allDevices().filter { it.deviceCoordinator is GarminCoordinator }

    /** Une montre Garmin est associée : les fichiers .fit lui sont rattachés. */
    fun supported(): Boolean = garmin().isNotEmpty()

    /** [skipped] : fichiers déjà importés. */
    data class ImportResult(val imported: Int, val failed: Int, val skipped: Int)

    /**
     * Importe des fichiers .fit (éventuellement .fit.gz) ou des archives ZIP qui en contiennent (même imbriquées,
     * comme l'export Garmin Connect). Les fichiers sont gardés dans l'app : le détail d'une séance les relit.
     */
    suspend fun import(ctx: Context, uris: List<Uri>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ImportResult = withContext(Dispatchers.IO) {
        val device = garmin().firstOrNull() ?: error("no Garmin device")
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "fit-import").apply { mkdirs() }
        val files = mutableListOf<File>()
        var skipped = 0
        uris.forEach { uri ->
            val name = displayName(ctx, uri) ?: uri.lastPathSegment ?: "import"
            ctx.contentResolver.openInputStream(uri)?.use { skipped += extract(it.buffered(), name, dir, files) }
        }
        val importer = FitImporter(ctx, device)
        var ok = 0; var ko = 0
        files.forEachIndexed { i, f ->
            onProgress(i, files.size)
            runCatching { importer.importFile(f, false) }
                .onSuccess { ok++ }
                .onFailure { e -> ko++; LOG.warn("Import failed for {}", f, e); f.delete() }
        }
        ImportResult(ok, ko, skipped)
    }

    /** Extrait les .fit vers [dir] ; renvoie le nombre de fichiers déjà importés (ignorés). */
    private fun extract(input: InputStream, name: String, dir: File, out: MutableList<File>): Int {
        val lower = name.lowercase()
        var skipped = 0
        when {
            lower.endsWith(".zip") -> {
                val zip = ZipInputStream(input)
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory) skipped += extract(NonClosing(zip), e.name.substringAfterLast('/'), dir, out)
                }
            }
            lower.endsWith(".fit.gz") -> skipped += extract(GZIPInputStream(input), name.dropLast(3), dir, out)
            lower.endsWith(".fit") -> {
                val f = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                if (f.exists() && f.length() > 0) return 1 // déjà importé
                f.outputStream().use { input.copyTo(it) }
                out += f
            }
        }
        return skipped
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
