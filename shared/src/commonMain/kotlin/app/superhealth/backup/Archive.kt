package app.superhealth.backup

import app.superhealth.platform.*

import app.superhealth.platform.Context
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.ManualLog
import app.superhealth.health.data.ManualLogStore
import app.superhealth.health.data.Metric
import app.superhealth.nutrition.NutriApp
import app.superhealth.nutrition.data.AppJson
import app.superhealth.nutrition.data.Backup
import app.superhealth.training.WorkoutApp
import app.superhealth.ui.tr
import app.superhealth.platform.ByteArrayOutputStream
import app.superhealth.platform.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sauvegarde complète en une archive : nutrition, musculation, réglages SuperHealth et saisies santé, au même format que
 * l'app Android (une sauvegarde Android se restaure sur iPhone avec son code ; sa partie « montre.zip », la base
 * Gadgetbridge, est ignorée : sur iOS les données de montre viennent d'Apple Santé). Toujours chiffrée avec le code de
 * récupération ([BackupCrypto]) ; la restauration lit aussi les anciennes archives ZIP en clair et les JSON de La Bouffe /
 * La Salle.
 */
object Archive {
    const val EXT = "shbak"
    const val MIME = "application/octet-stream"

    @kotlinx.serialization.Serializable
    private data class LogRow(val metric: String, val ts: Long, val value: Double)

    fun fileName(date: LocalDate = LocalDate.now()) = "superhealth-$date.$EXT"

    /** Archive chiffrée, prête à écrire où l'on veut. */
    suspend fun build(ctx: Context): ByteArray = BackupCrypto.encrypt(zip(ctx), Secrets.backupCode(ctx))

    private suspend fun zip(ctx: Context): ByteArray {
        val nutrition = AppJson.encodeToString(Backup.serializer(), NutriApp.repository.backup())
        val training = WorkoutApp.store.export()
        val logs = ManualLogStore.get(ctx).query(null, 0L, Long.MAX_VALUE).map { LogRow(it.metric.name, it.timestampMs, it.value) }
        val health = AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(LogRow.serializer()), logs)
        return withContext(Dispatchers.IO) {
            Zip.write(listOf("nutrition.json" to nutrition, "musculation.json" to training, "superhealth.json" to app.superhealth.SuperPrefs.exportSettings(),
                "sante.json" to health).map { (name, text) -> name to text.encodeToByteArray() })
        }
    }

    /**
     * Restaure [bytes] ; une archive chiffrée est ouverte avec le code de ce téléphone ou un code déjà utilisé ici, ou [code]
     * s'il est donné (sauvegarde d'un autre téléphone). Lève [BackupCrypto.WrongCode] si aucun ne l'ouvre. Rend le résumé de
     * ce qui a été restauré.
     */
    suspend fun restore(ctx: Context, bytes: ByteArray, code: String? = null): String {
        val plain = if (BackupCrypto.isEncrypted(bytes)) withContext(Dispatchers.Default) {
            if (code != null) BackupCrypto.decrypt(bytes, code).also { Secrets.remember(ctx, code) }
            else Secrets.restoreCodes(ctx).firstNotNullOfOrNull { c -> runCatching { BackupCrypto.decrypt(bytes, c) }.getOrNull() }
                ?: throw BackupCrypto.WrongCode()
        } else bytes
        val files = withContext(Dispatchers.IO) {
            if (Zip.isZip(plain)) Zip.read(plain).filter { it.first != "montre.zip" }.associate { (n, b) -> n to b.decodeToString() }
            else {
                val text = plain.decodeToString()
                if ("\"foods\"" in text && "\"entries\"" in text) mapOf("nutrition.json" to text) else mapOf("musculation.json" to text)
            }
        }
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
        return if (done.isEmpty()) tr("Fichier illisible") else app.superhealth.ui.trf("Restauré : %1\$s", done.joinToString(", "))
    }
}
