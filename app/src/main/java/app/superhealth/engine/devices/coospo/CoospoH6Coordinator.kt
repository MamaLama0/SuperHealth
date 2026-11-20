package app.superhealth.engine.devices.coospo

import app.superhealth.engine.R
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.impl.GBDevice
import java.util.regex.Pattern

/// #5025
class CoospoH6Coordinator: CoospoHeartRateCoordinator() {
    override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("^H6M [0-9]{5}$")
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_coospo_h6
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.CHEST_STRAP
    }
}
