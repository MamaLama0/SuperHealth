package app.superhealth.backup

import android.content.Context
import android.net.Uri
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.ManualLog
import app.superhealth.health.data.ManualLogStore
import app.superhealth.health.data.Metric
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.data.AppJson
import app.superhealth.nutrition.data.Backup
import app.superhealth.training.WorkoutApp
import app.superhealth.ui.tr
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.util.backup.ZipBackupCallback
import nodomain.freeyourgadget.gadgetbridge.util.backup.ZipBackupExportJob
import nodomain.freeyourgadget.gadgetbridge.util.backup.ZipBackupImportJob

/**
 * Sauvegarde complète en une archive : nutrition, musculation, réglages SuperHealth, saisies santé et, dans « montre.zip »,
 * la sauvegarde Gadgetbridge (base de la montre, réglages des appareils, fichiers .fit). Toujours chiffrée avec le code de
 * récupération ([BackupCrypto]) ; la restauration lit aussi les anciennes archives ZIP en clair et les JSON de La Bouffe /
 * La Salle.
 */
object Archive {
    const val EXT = "shbak"
    const val MIME = "application/octet-stream"

    @kotlinx.serialization.Serializable
    private data class LogRow(val metric: String, val ts: Long, val value: Double)

    private val PLUGIN_FILES = listOf("stretch_log.json", "stretch_personal.json", "stretch_mobility.json", "meditation_log.json", "league/state.json", "league/spaces.json", "league/draft.json", "league/friend-history.json", "league/friend-draft.json", "league/group-history.json", "league/notifications.json", "hiking/library.json", "hiking/session.json", "hiking/progress.json", "profile.jpg", "cards/state.json", "cards/cutout.png", "wellbeing/journal.json", "wellbeing/mood_moments.json", "wellbeing/checkin.json", "wellbeing/cycle.json", "meds/meds.json", "coach/chat.json", "coach/brief.json")

    fun fileName(date: java.time.LocalDate = java.time.LocalDate.now()) = "superhealth-$date.$EXT"

    /** Archive chiffrée, prête à écrire où l'on veut. */
    suspend fun build(ctx: Context): ByteArray = BackupCrypto.encrypt(zip(ctx), Secrets.backupCode(ctx))

    private suspend fun zip(ctx: Context): ByteArray {
        val nutrition = AppJson.encodeToString(Backup.serializer(), NutriApp.repository.backup())
        val training = WorkoutApp.store.export()
        val logs = ManualLogStore.get(ctx).query(null, 0L, Long.MAX_VALUE).map { LogRow(it.metric.name, it.timestampMs, it.value) }
        val health = AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(LogRow.serializer()), logs)
        return withContext(Dispatchers.IO) {
            val watch = File(ctx.cacheDir, "montre-backup.zip").apply { delete() }
            runGbJob { cb -> ZipBackupExportJob(ctx, cb, Uri.fromFile(watch)) }
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                listOf("nutrition.json" to nutrition, "musculation.json" to training, "superhealth.json" to app.superhealth.SuperPrefs.exportSettings(),
                    "sante.json" to health).forEach { (name, text) ->
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("group-recovery.json"))
                zip.write(kotlinx.serialization.json.Json.encodeToString(app.superhealth.league.GroupRecovery.Profile.serializer(), app.superhealth.league.GroupRecovery.export(ctx)).toByteArray())
                zip.closeEntry()
                // Journaux des plugins, classement et photo de profil
                PLUGIN_FILES.forEach { name -> File(ctx.filesDir, name).takeIf { it.isFile }?.let { f -> zip.putNextEntry(ZipEntry(name.replace('/', '_'))); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry() } }
                app.superhealth.hiking.HikePhotos.backupFiles(ctx).forEach { photo ->
                    zip.putNextEntry(ZipEntry("hiking_photo_${photo.name}"));photo.inputStream().use{it.copyTo(zip)};zip.closeEntry()
                }
                if (watch.length() > 0) { zip.putNextEntry(ZipEntry("montre.zip")); watch.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
            }
            watch.delete()
            out.toByteArray()
        }
    }

    /**
     * Restaure [bytes] ; une archive chiffrée est ouverte avec le code de ce téléphone ou un code déjà utilisé ici, ou [code]
     * s'il est donné (sauvegarde d'un autre téléphone). Lève [BackupCrypto.WrongCode] si aucun ne l'ouvre. Rend le résumé de
     * ce qui a été restauré.
     */
    suspend fun restore(ctx: Context, bytes: ByteArray, code: String? = null): String {
        check(!app.superhealth.league.SocialLifecycle.pending(ctx)) { "Terminez la sécurisation du Groupe avant une restauration." }
        val plain = if (BackupCrypto.isEncrypted(bytes)) withContext(Dispatchers.Default) {
            if (code != null) BackupCrypto.decrypt(bytes, code).also { Secrets.remember(ctx, code) }
            else Secrets.restoreCodes(ctx).firstNotNullOfOrNull { c -> runCatching { BackupCrypto.decrypt(bytes, c) }.getOrNull() }
                ?: throw BackupCrypto.WrongCode()
        } else bytes
        val watch = File(ctx.cacheDir, "montre-restore.zip").apply { delete() }
        val files = withContext(Dispatchers.IO) {
            if (plain.size > 1 && plain[0] == 'P'.code.toByte() && plain[1] == 'K'.code.toByte()) {
                buildMap {
                    ZipInputStream(plain.inputStream()).use { zip ->
                        generateSequence { zip.nextEntry }.forEach { e ->
                            val plugin = PLUGIN_FILES.firstOrNull { it.replace('/', '_') == e.name }
                            if (e.name.matches(Regex("hiking_photo_[a-f0-9]{64}\\.jpg"))) {
                                val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                                while(true){val n=zip.read(buffer);if(n<0)break;require(out.size()+n<=app.superhealth.hiking.HikePhotos.MAX_BYTES);out.write(buffer,0,n)}
                                File(ctx.filesDir,"hiking/photos/"+e.name.removePrefix("hiking_photo_")).apply{parentFile?.mkdirs();writeBytes(out.toByteArray())}
                            }
                            else if (e.name == "montre.zip") watch.outputStream().use { zip.copyTo(it) }
                            else if (plugin != null) File(ctx.filesDir, plugin).apply { parentFile?.mkdirs() }.outputStream().use { zip.copyTo(it) }
                            else put(e.name, zip.readBytes().decodeToString())
                        }
                    }
                }
            } else {
                val text = plain.decodeToString()
                if ("\"foods\"" in text && "\"entries\"" in text) mapOf("nutrition.json" to text) else mapOf("musculation.json" to text)
            }
        }
        app.superhealth.stretch.Stretch.load(ctx); app.superhealth.meditation.Meditation.load(ctx); app.superhealth.league.League.load(ctx); app.superhealth.league.GroupHistory.load(ctx); app.superhealth.league.SocialInbox.load(ctx); app.superhealth.hiking.Hiking.load(ctx); app.superhealth.league.HikingProgress.load(ctx); app.superhealth.ui.ProfilePhoto.load(ctx)
        files["group-recovery.json"]?.let { app.superhealth.league.GroupRecovery.restore(ctx,
            kotlinx.serialization.json.Json.decodeFromString(app.superhealth.league.GroupRecovery.Profile.serializer(), it), clearHistory = false) }
        app.superhealth.league.GroupHistory.load(ctx)
        app.superhealth.league.SocialSpaces.reset()
        app.superhealth.meds.Meds.init(ctx); app.superhealth.wellbeing.Journal.init(ctx); app.superhealth.wellbeing.MoodMoments.init(ctx); app.superhealth.wellbeing.Checkin.init(ctx); app.superhealth.wellbeing.Cycle.init(ctx)
        val done = mutableListOf<String>()
        files["nutrition.json"]?.let { NutriApp.repository.restore(AppJson.decodeFromString(Backup.serializer(), it)); done += tr("nutrition") }
        files["musculation.json"]?.let { WorkoutApp.store.import(it); done += tr("musculation") }
        files["superhealth.json"]?.let { app.superhealth.SuperPrefs.importSettings(it); done += tr("réglages") }
        files["sante.json"]?.let { json ->
            val rows = AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(LogRow.serializer()), json)
            ManualLogStore.get(ctx).replaceAll(rows.mapNotNull { r ->
                runCatching { Metric.valueOf(r.metric) }.getOrNull()?.let { ManualLog(0, it, r.ts, r.value) }
            })
            done += tr("saisies santé")
        }
        if (watch.length() > 0) {
            val ok = withContext(Dispatchers.IO) { runCatching { runGbJob { cb -> ZipBackupImportJob(ctx, cb, Uri.fromFile(watch)) } }.isSuccess }
            watch.delete()
            HealthRepository.invalidate()
            done += if (ok) tr("montre") else tr("montre (échec)")
        }
        return if (done.isEmpty()) tr("Fichier illisible") else app.superhealth.ui.trf("Restauré : %1\$s", done.joinToString(", "))
    }

    /** Exécute une tâche de sauvegarde Gadgetbridge (synchrone) et lève une erreur si elle échoue. */
    private fun runGbJob(make: (ZipBackupCallback) -> Runnable) {
        var error: String? = null
        var ok = false
        make(object : ZipBackupCallback {
            override fun onProgress(progress: Int, message: String?) {}
            override fun onSuccess(warnings: String?) { ok = true }
            override fun onFailure(errorMessage: String?) { error = errorMessage }
            override fun onConfirmationRequired(message: String) { throw IllegalArgumentException(message) }
        }).run()
        if (!ok) error(error ?: "montre")
    }
}
