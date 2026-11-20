package app.superhealth.engine.devices.shokz

import app.superhealth.engine.R
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.impl.GBDevice
import java.util.regex.Pattern

class ShokzOpenSwimProCoordinator: ShokzCoordinator() {
    override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("^OpenSwim Pro by Shokz$")
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_shokz_openswim_pro
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.HEADPHONES
    }
}
