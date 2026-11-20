package app.superhealth.engine.activities.debug

import android.os.Bundle
import app.superhealth.engine.GBApplication
import app.superhealth.engine.R
import app.superhealth.engine.impl.GBDevice

class DeviceListDebugFragment : AbstractDebugFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.debug_preferences_empty, rootKey)
        preferenceScreen?.title = getString(R.string.bottom_nav_devices)
        reloadDevices()
    }

    private fun reloadDevices() {
        removeDynamicPrefs(preferenceScreen)

        val devices = GBApplication.app().deviceManager.devices.sortedBy { it.aliasOrName }

        if (devices.isNotEmpty()) {
            for (device in devices) {
                addDynamicDevicePref(
                    preferenceScreen,
                    device.aliasOrName,
                    device.address,
                    device.deviceCoordinator.defaultIconResource,
                    device.isConnected
                ) {
                    goTo(
                        DeviceDebugFragment().apply {
                            arguments = Bundle().apply {
                                putParcelable(GBDevice.EXTRA_DEVICE, device)
                            }
                        }
                    )
                }
            }
        } else {
            addDynamicPref(preferenceScreen, "", "No devices")
        }
    }
}
