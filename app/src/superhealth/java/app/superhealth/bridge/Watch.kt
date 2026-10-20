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

    /** Montre Garmin : peut renvoyer les activités qu'elle garde en mémoire, même celles déjà synchronisées. */
    fun supportsHistory(device: GBDevice) = device.deviceCoordinator is nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminCoordinator

    /**
     * Récupère toutes les activités encore stockées sur la montre (courses, sorties…), y compris celles déjà envoyées
     * à Garmin Connect ou à une installation précédente de l'app. Les doublons sont ignorés à l'import.
     */
    fun fetchHistory(device: GBDevice) {
        if (!enabled || !supportsHistory(device)) return
        if (!device.isInitialized) { GBApplication.deviceService(device).connect(); return }
        historyPrefs().edit().putLong(device.address, System.currentTimeMillis()).apply()
        GBApplication.deviceService(device).onFetchRecordedData(nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes.TYPE_HISTORY)
    }

    /** L'historique de cette montre a-t-il déjà été demandé (automatiquement ou à la main) ? */
    fun historyRequested(device: GBDevice) = historyPrefs().contains(device.address)

    private fun historyPrefs() = GBApplication.getContext().getSharedPreferences("superhealth_watch_history", android.content.Context.MODE_PRIVATE)
}
