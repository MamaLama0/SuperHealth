package app.superhealth.bridge

import app.superhealth.SuperPrefs
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs

/**
 * Module LocalHealth : le moteur Gadgetbridge (montres, balances, capteurs Bluetooth).
 * Désactivé, il ne se connecte plus à rien et ses cartes disparaissent ; Health Connect prend le relais pour les constantes.
 */
object Watch {
    private const val SAVED_AUTOCONNECT = "superhealth_saved_autoconnect"

    val enabled: Boolean get() = SuperPrefs.settings.value.watchModule

    fun devices(): List<GBDevice> = if (enabled) GBApplication.app().deviceManager.devices else emptyList()

    /** Tous les appareils associés, même module désactivé (relire une séance déjà enregistrée). */
    fun allDevices(): List<GBDevice> = GBApplication.app().deviceManager.devices

    fun setEnabled(on: Boolean) {
        if (on == enabled) return
        SuperPrefs.update { it.copy(watchModule = on) }
        val sp = GBApplication.getPrefs().preferences
        if (!on) {
            sp.edit()
                .putBoolean(SAVED_AUTOCONNECT, sp.getBoolean(GBPrefs.AUTO_CONNECT_BLUETOOTH, true))
                .putBoolean(GBPrefs.AUTO_CONNECT_BLUETOOTH, false)
                .apply()
            GBApplication.app().deviceManager.devices.filter { it.isInitialized || it.isConnecting }
                .forEach { GBApplication.deviceService(it).disconnect() }
            GBApplication.deviceService().quit()
        } else {
            sp.edit().putBoolean(GBPrefs.AUTO_CONNECT_BLUETOOTH, sp.getBoolean(SAVED_AUTOCONNECT, true)).apply()
            connectAll()
        }
    }

    /** Reconnexion de toutes les montres associées (au démarrage et à la réactivation). */
    fun connectAll() {
        if (!enabled) return
        GBApplication.app().deviceManager.devices.filter { !it.isInitialized && !it.isConnecting }
            .forEach { GBApplication.deviceService(it).connect() }
    }

    /** Montre connectée : récupère les données enregistrées ; déconnectée : se reconnecte. */
    fun sync(device: GBDevice) {
        if (!enabled) return
        if (!device.isInitialized) GBApplication.deviceService(device).connect()
        else GBApplication.deviceService(device).onFetchRecordedData(nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes.TYPE_SYNC)
    }

    fun syncAll() = devices().forEach(::sync)
}
