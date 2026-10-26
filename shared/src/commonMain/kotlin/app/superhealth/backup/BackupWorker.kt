package app.superhealth.backup

import app.superhealth.platform.*

import app.superhealth.SuperPrefs

/**
 * Sauvegarde automatique : sur iOS, vérifiée à l'ouverture de l'app (et par la tâche de fond du système quand iOS la
 * lance) ; envoyée quand la dernière date de plus que la fréquence choisie, avec 2 h de marge.
 */
object BackupWorker {
    private const val DAY = 86_400_000L
    private const val HOUR = 3_600_000L

    suspend fun runIfDue(ctx: Context) {
        val s = SuperPrefs.settings.value
        if (Remote.kind() == Remote.Kind.NONE || s.backupEvery <= 0) return
        if (System.currentTimeMillis() - s.lastBackup < s.backupEvery * DAY - 2 * HOUR) return
        runCatching { now(ctx) }
    }

    /** Construit l'archive chiffrée et l'envoie ; l'état (date ou erreur) est gardé dans les réglages. */
    suspend fun now(ctx: Context) {
        try {
            Remote.upload(ctx, Archive.fileName(), Archive.build(ctx))
            SuperPrefs.update { it.copy(lastBackup = System.currentTimeMillis(), lastBackupError = null) }
        } catch (e: Exception) {
            SuperPrefs.update { it.copy(lastBackupError = e.message ?: e::class.simpleName) }
            throw e
        }
    }
}
