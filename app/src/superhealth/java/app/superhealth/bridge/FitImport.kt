package app.superhealth.bridge

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitImporter
import org.slf4j.LoggerFactory

/**
 * Fichiers .fit importés quand une montre Garmin est associée : ils sont enregistrés comme les séances synchronisées depuis
 * la montre (doublons ignorés). Le choix des fichiers, les archives et les autres formats sont dans [RunImport].
 */
object FitImport {
    private val LOG = LoggerFactory.getLogger(FitImport::class.java)

    private fun garmin(): List<GBDevice> = Watch.allDevices().filter { it.deviceCoordinator is GarminCoordinator }

    /** Une montre Garmin est associée : les fichiers .fit lui sont rattachés. */
    fun supported(): Boolean = garmin().isNotEmpty()

    /** [skipped] : fichiers déjà importés. */
    data class ImportResult(val imported: Int, val failed: Int, val skipped: Int)

    /**
     * Importe des fichiers .fit déjà extraits (par [RunImport], qui gère archives et .gz) dans la montre Garmin associée.
     * Les fichiers sont gardés dans l'app : le détail d'une séance les relit.
     */
    suspend fun importFiles(ctx: Context, files: List<Pair<String, ByteArray>>): ImportResult = withContext(Dispatchers.IO) {
        val device = garmin().firstOrNull() ?: error("no Garmin device")
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "fit-import").apply { mkdirs() }
        val importer = FitImporter(ctx, device)
        var ok = 0; var ko = 0; var skipped = 0
        files.forEach { (name, bytes) ->
            val f = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            if (f.exists() && f.length() > 0) { skipped++; return@forEach } // déjà importé
            f.writeBytes(bytes)
            runCatching { importer.importFile(f, false) }
                .onSuccess { ok++ }
                .onFailure { e -> ko++; LOG.warn("Import failed for {}", f, e); f.delete() }
        }
        ImportResult(ok, ko, skipped)
    }
}
