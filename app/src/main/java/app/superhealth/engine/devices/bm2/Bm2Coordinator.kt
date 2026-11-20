package app.superhealth.engine.devices.bm2

import app.superhealth.engine.R
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.bm2.Bm2Support
import java.util.regex.Pattern

class Bm2Coordinator : AbstractBLEDeviceCoordinator() {
    override fun getSupportedDeviceName(): Pattern {
        return Pattern.compile("^MATSON Monitor$")
    }

    override fun getManufacturer(): String {
        return "Matson"
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return Bm2Support::class.java
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_battery_monitor
    }

    override fun suggestUnbindBeforePair(): Boolean {
        return false
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_NONE
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_car
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.BATTERY_MONITOR
    }
}
