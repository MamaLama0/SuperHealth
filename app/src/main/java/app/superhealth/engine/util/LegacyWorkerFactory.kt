package app.superhealth.engine.util

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/**
 * Tâches planifiées enregistrées avant le renommage du paquet du moteur montre (4.2 et avant) :
 * WorkManager garde le nom complet de la classe, on le fait pointer vers le nouveau paquet.
 * Pour tout autre nom, null : WorkManager crée la tâche lui-même.
 */
class LegacyWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        if (!workerClassName.startsWith(OLD_PACKAGE)) return null
        val renamed = NEW_PACKAGE + workerClassName.substring(OLD_PACKAGE.length)
        return try {
            Class.forName(renamed).asSubclass(ListenableWorker::class.java)
                .getDeclaredConstructor(Context::class.java, WorkerParameters::class.java)
                .newInstance(appContext, workerParameters)
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val OLD_PACKAGE = "nodomain.freeyourgadget.gadgetbridge."
        const val NEW_PACKAGE = "app.superhealth.engine."
    }
}
