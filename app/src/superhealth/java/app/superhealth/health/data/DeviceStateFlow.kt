package app.superhealth.health.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

data class DeviceUi(
    val address: String,
    val name: String,
    val model: String?,
    val connected: Boolean,
    val connecting: Boolean,
    val busy: Boolean,
    val battery: Int?,
    val stateText: String,
    @androidx.annotation.DrawableRes val icon: Int,
    val device: GBDevice,
)

/** Emits the paired device list whenever Gadgetbridge broadcasts a device change. */
fun deviceStateFlow(context: Context): Flow<List<DeviceUi>> = callbackFlow {
    fun snapshot() = app.superhealth.bridge.Watch.devices().map { d ->
        DeviceUi(
            address = d.address,
            name = d.aliasOrName,
            model = d.model,
            connected = d.isInitialized,
            connecting = d.isConnecting,
            busy = d.isBusy,
            battery = d.getBatteryLevel(0).takeIf { it in 0..100 },
            stateText = d.getStateString(context),
            icon = d.deviceCoordinator.defaultIconResource,
            device = d,
        )
    }
    trySend(snapshot())
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            trySend(snapshot())
        }
    }
    val filter = IntentFilter().apply {
        addAction(GBDevice.ACTION_DEVICE_CHANGED)
        addAction(DeviceManager.ACTION_DEVICES_CHANGED)
    }
    val lbm = LocalBroadcastManager.getInstance(context)
    lbm.registerReceiver(receiver, filter)
    awaitClose { lbm.unregisterReceiver(receiver) }
}
