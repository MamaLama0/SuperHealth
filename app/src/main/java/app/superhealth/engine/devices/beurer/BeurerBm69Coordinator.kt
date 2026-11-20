package app.superhealth.engine.devices.beurer

import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.dsl.DeviceSettingsSpec
import app.superhealth.engine.activities.devicesettings.dsl.components.bloodPressureActiveUser
import app.superhealth.engine.activities.devicesettings.dsl.deviceSettings
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.devices.GenericBloodPressureSampleProvider
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.generic_bp.GenericBloodPressureSupport
import java.util.regex.Pattern

class BeurerBm69Coordinator : AbstractBLEDeviceCoordinator() {
    protected override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("^BM69")
    }

    override fun getManufacturer(): String {
        return "Beurer"
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_beurer_bm_69
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_NONE
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return GenericBloodPressureSupport::class.java
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.BLOOD_PRESSURE_METER
    }

    override fun getBatteryCount(device: GBDevice): Int {
        return 0 // unconfirmed
    }

    override fun suggestUnbindBeforePair(): Boolean {
        // Works just fine if already paired
        return false
    }

    override fun supportsBloodPressureMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun getBloodPressureSampleProvider(
        device: GBDevice,
        session: DaoSession
    ): GenericBloodPressureSampleProvider {
        return GenericBloodPressureSampleProvider(device, session)
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        bloodPressureActiveUser(device)
    }
}
