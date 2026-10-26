package app.superhealth.bridge

import app.superhealth.platform.*

import app.superhealth.SuperPrefs
import app.superhealth.health.data.HealthRepository
import app.superhealth.health.data.HealthSources
import app.superhealth.health.data.SourceDevice

/**
 * Module santé : sur iOS, Apple Santé (HealthKit) tient le rôle du moteur Gadgetbridge d'Android. Les montres (Apple Watch,
 * Garmin via Garmin Connect…) écrivent dans Santé ; SuperHealth lit Santé. Désactivé, ses cartes disparaissent.
 */
object Watch {
    val enabled: Boolean get() = SuperPrefs.settings.value.watchModule && SuperPrefs.settings.value.healthConnect

    /** L'appareil « Apple Santé » quand l'accès est activé. */
    fun devices(): List<SourceDevice> =
        if (enabled && HealthSources.current.available) listOf(SourceDevice("healthkit", HealthSources.current.name)) else emptyList()

    fun allDevices(): List<SourceDevice> = devices()

    fun setEnabled(on: Boolean) {
        if (on == SuperPrefs.settings.value.watchModule) return
        SuperPrefs.update { it.copy(watchModule = on) }
        HealthRepository.invalidate()
    }

    fun connectAll() = Unit

    /** Relit Santé (les données y arrivent toutes seules depuis la montre). */
    fun sync(device: SourceDevice) = HealthRepository.invalidate()

    fun syncAll() = HealthRepository.invalidate()

    fun supportsHistory(device: SourceDevice) = false
    fun fetchHistory(device: SourceDevice) = Unit
    fun historyRequested(device: SourceDevice) = true
}
