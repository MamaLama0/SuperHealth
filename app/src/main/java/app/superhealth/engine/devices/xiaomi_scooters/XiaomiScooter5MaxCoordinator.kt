package app.superhealth.engine.devices.xiaomi_scooters

import app.superhealth.engine.R
import java.util.regex.Pattern

class XiaomiScooter5MaxCoordinator: XiaomiScooterCoordinator() {
    override fun getSupportedDeviceName(): Pattern {
        return Pattern.compile("^xiaomi.scooter.5max$")
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_xiaomi_scooter_5_max
    }
}
