package app.superhealth.engine.devices.coospo

import app.superhealth.engine.R
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.impl.GBDevice
import java.util.regex.Pattern

class CoospoH9ZCoordinator: CoospoHeartRateCoordinator() {
    override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("^H9Z [0-9]{5}$")
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_coospo_h9z
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.CHEST_STRAP
    }
}