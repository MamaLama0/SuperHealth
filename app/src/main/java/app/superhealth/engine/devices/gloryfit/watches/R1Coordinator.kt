package app.superhealth.engine.devices.gloryfit.watches

import app.superhealth.engine.R
import app.superhealth.engine.devices.gloryfit.GloryFitCoordinator
import java.util.regex.Pattern

class R1Coordinator : GloryFitCoordinator() {
    override fun getManufacturer(): String {
        return "GloryFit"
    }

    override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("^R1\\(ID-[0-9A-F]{4}\\)$")
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_r1
    }
}
