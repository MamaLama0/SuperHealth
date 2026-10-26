package app.superhealth.health.data

import app.superhealth.platform.*

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.superhealth.SuperPrefs

data class DeviceUi(
    val address: String,
    val name: String,
    val model: String?,
    val connected: Boolean,
    val connecting: Boolean,
    val busy: Boolean,
    val battery: Int?,
    val stateText: String,
    val device: SourceDevice,
)

/** Sources de santé actives (Apple Santé), relues quand les réglages changent. */
fun deviceStateFlow(context: Context): Flow<List<DeviceUi>> = SuperPrefs.settings.map {
    app.superhealth.bridge.Watch.devices().map { d ->
        DeviceUi(d.address, d.name, d.model, connected = true, connecting = false, busy = false, battery = null,
            stateText = app.superhealth.ui.tr("Connecté"), device = d)
    }
}
