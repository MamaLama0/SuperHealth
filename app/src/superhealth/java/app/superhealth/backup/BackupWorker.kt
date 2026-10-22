package app.superhealth.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.superhealth.SuperPrefs
import java.util.concurrent.TimeUnit

/**
 * Sauvegarde automatique : vérifiée toutes les 6 h (réseau requis), envoyée quand la dernière date de plus que la fréquence
 * choisie (un jour ou une semaine, avec 2 h de marge pour ne pas dériver d'un passage à l'autre).
 */
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val s = SuperPrefs.settings.value
        val force = inputData.getBoolean(FORCE, false)
        if (Remote.kind() == Remote.Kind.NONE) return Result.success()
        if (!force && (s.backupEvery <= 0 || System.currentTimeMillis() - s.lastBackup < s.backupEvery * DAY - 2 * HOUR)) return Result.success()
        return runCatching { now(applicationContext) }.fold(
            { Result.success() },
            { e -> if (runAttemptCount < 2 && !force) Result.retry() else Result.success() },
        )
    }

    companion object {
        private const val DAY = 86_400_000L
        private const val HOUR = 3_600_000L
        private const val FORCE = "force"

        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("superhealth_backup", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackupWorker>(6, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }

        /** Sauvegarde tout de suite (bouton « Sauvegarder maintenant »), en arrière-plan. */
        fun runNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork("superhealth_backup_now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<BackupWorker>().setInputData(androidx.work.workDataOf(FORCE to true)).build())
        }

        /** Construit l'archive chiffrée et l'envoie ; l'état (date ou erreur) est gardé dans les réglages. */
        suspend fun now(ctx: Context) {
            try {
                Remote.upload(ctx, Archive.fileName(), Archive.build(ctx))
                SuperPrefs.update { it.copy(lastBackup = System.currentTimeMillis(), lastBackupError = null) }
            } catch (e: Exception) {
                SuperPrefs.update { it.copy(lastBackupError = e.message ?: e.javaClass.simpleName) }
                throw e
            }
        }
    }
}
