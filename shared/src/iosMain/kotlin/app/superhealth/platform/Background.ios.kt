package app.superhealth.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

/**
 * Tâches de fond (WorkManager sur Android) : iOS réveille l'app de temps en temps (au mieux toutes les quelques heures)
 * pour la sauvegarde automatique, la mise à jour des plugins, les rappels de médicaments et les widgets.
 */
object BackgroundRefresh {
    const val ID = "app.superhealth.refresh"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** À appeler au lancement, avant la fin du démarrage de l'app (init de l'App Swift). */
    fun register() {
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(ID, null) { task ->
            val t = task as? BGAppRefreshTask ?: return@registerForTaskWithIdentifier
            schedule()
            val job = scope.launch {
                runCatching {
                    app.superhealth.SuperHealth.init()
                    app.superhealth.backup.BackupWorker.runIfDue(appContext)
                    app.superhealth.modules.Modules.autoUpdate(appContext)
                    app.superhealth.meds.Meds.schedule(appContext)
                    app.superhealth.ui.widget.Widgets.publish()
                    kotlinx.coroutines.delay(1500)
                }
                t.setTaskCompletedWithSuccess(true)
            }
            t.expirationHandler = { job.cancel(); t.setTaskCompletedWithSuccess(false) }
        }
        schedule()
    }

    fun schedule() {
        val req = BGAppRefreshTaskRequest(ID)
        req.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(6 * 3600.0)
        runCatching { BGTaskScheduler.sharedScheduler.submitTaskRequest(req, null) }
    }
}
