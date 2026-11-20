package app.superhealth.engine.devices.garmin

import android.content.Context
import app.superhealth.engine.R
import app.superhealth.engine.devices.DeviceCardAction
import app.superhealth.engine.devices.cards.ActionCardItem
import app.superhealth.engine.devices.cards.DefaultDeviceCardItems
import app.superhealth.engine.devices.cards.DeviceCardItem
import app.superhealth.engine.devices.garmin.actions.GarminSendWaypointActivity
import app.superhealth.engine.impl.GBDevice

fun garminDeviceCardItems(device: GBDevice, coordinator: GarminCoordinator): List<DeviceCardItem> {
    val items = DefaultDeviceCardItems.build(coordinator, device).toMutableList()

    if (coordinator.supportsSendWaypoint(device)) {
        items.add(ActionCardItem(sendWaypointAction(), visible = { it.isInitialized }, optional = true))
    }

    return items
}

private fun sendWaypointAction(): DeviceCardAction = object : DeviceCardAction {
    override fun getId(): String = GarminPreferences.PREF_GARMIN_SEND_WAYPOINT
    override fun getIcon(device: GBDevice) = R.drawable.ic_add_location_alt_24px
    override fun getDescription(device: GBDevice, context: Context): String =
        context.getString(R.string.activity_send_waypoint_titel)

    override fun onClick(device: GBDevice, context: Context) {
        GarminSendWaypointActivity.handlePreferenceClick(context, device)
    }
}
