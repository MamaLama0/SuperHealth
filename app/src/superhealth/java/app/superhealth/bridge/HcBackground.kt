package app.superhealth.bridge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import kotlinx.coroutines.CancellationException

/** Accès facultatif, demandé depuis les réglages, pour les lectures Health Connect hors de l'app. */
object HcBackground {
    const val PERMISSION = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    fun supported(ctx: Context): Boolean = runCatching {
        HealthConnectHub.status(ctx) == HealthConnectHub.Status.AVAILABLE &&
            HealthConnectClient.getOrCreate(ctx).features.getFeatureStatus(
                HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
            ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    suspend fun allowed(ctx: Context): Boolean {
        if (!supported(ctx)) return false
        return try {
            PERMISSION in HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}
