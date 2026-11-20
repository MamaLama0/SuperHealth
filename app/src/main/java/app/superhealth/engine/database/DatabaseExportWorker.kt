package app.superhealth.engine.database

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.Worker
import androidx.work.WorkerParameters
import app.superhealth.engine.GBApplication
import app.superhealth.engine.GBDatabaseManager
import app.superhealth.engine.R
import app.superhealth.engine.util.GB
import app.superhealth.engine.util.GBPrefs
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DatabaseExportWorker(
    private val mContext: Context,
    workerParams: WorkerParameters
) : Worker(mContext, workerParams) {
    override fun doWork(): Result {
        val enabled = GBApplication.getPrefs().getBoolean(GBPrefs.AUTO_EXPORT_DB_ENABLED, false)
        if (!enabled) {
            LOG.warn("DB export started, but is disabled")
            // Should not need i18n, this should never happen
            GB.updateExportFailedNotification("DB export started, but is disabled", mContext)
            return Result.failure()
        }

        val dst = GBApplication.getPrefs().getString(GBPrefs.AUTO_EXPORT_DB_LOCATION, "")

        LOG.info("Starting DB export, dst={}", dst)

        if (dst == null) {
            LOG.warn("Unable to export DB, export location not set")
            GB.updateExportFailedNotification(mContext.getString(R.string.notif_export_location_not_set), mContext)
            broadcastSuccess(false)
            return Result.failure()
        }

        try {
            mContext.contentResolver.openOutputStream(dst.toUri()).use { out ->
                GBDatabaseManager.exportDB(out)
            }

            GBApplication.getPrefs().preferences.edit {
                putLong(GBPrefs.AUTO_EXPORT_DB_LAST_EXECUTION, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            GB.updateExportFailedNotification(mContext.getString(R.string.notif_export_failed_title), mContext)
            LOG.error("Exception while exporting DB", e)
            broadcastSuccess(false)
            return Result.failure()
        }

        LOG.info("DB export completed")

        broadcastSuccess(true)

        return Result.success()
    }

    private fun broadcastSuccess(success: Boolean) {
        if (!GBApplication.getPrefs().getBoolean(GBPrefs.INTENT_API_BROADCAST_EXPORT_DB, false)) {
            return
        }

        LOG.info("Broadcasting database export success={}", success)

        val action: String = if (success) ACTION_DATABASE_EXPORT_SUCCESS else ACTION_DATABASE_EXPORT_FAIL
        val exportedNotifyIntent = Intent(action)
        mContext.sendBroadcast(exportedNotifyIntent)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(DatabaseExportWorker::class.java)

        const val ACTION_DATABASE_EXPORT_SUCCESS: String =
            "app.superhealth.engine.action.DATABASE_EXPORT_SUCCESS"
        const val ACTION_DATABASE_EXPORT_FAIL: String =
            "app.superhealth.engine.action.DATABASE_EXPORT_FAIL"
    }
}
