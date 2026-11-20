package app.superhealth.engine.devices.onetouch

import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettings
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsCustomizer
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.ServiceDeviceSupport
import app.superhealth.engine.service.devices.onetouch.OneTouchSupport
import java.util.EnumSet
import java.util.regex.Pattern

class OneTouchCoordinator : AbstractBLEDeviceCoordinator() {
    override fun isExperimental(): Boolean {
        return true
    }

    override fun getManufacturer(): String {
        return "LifeScan"
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport?> {
        return OneTouchSupport::class.java
    }

    override fun getInitialFlags(): EnumSet<ServiceDeviceSupport.Flags> {
        // Support class has message queuing, we don't need busy checking
        return EnumSet.noneOf(ServiceDeviceSupport.Flags::class.java)
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_BOND
    }

    override fun suggestUnbindBeforePair(): Boolean {
        return false
    }

    override fun getDefaultIconResource(): Int {
        // TODO dedicated icon
        return R.drawable.ic_device_default
    }

    override fun getSupportedDeviceName(): Pattern? {
        // Contains last 4 digits of the serial number
        return Pattern.compile("^OneTouch [A-Z0-9]{4}$")
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_onetouch
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.GLUCOSE_METER
    }

    override fun getDeviceSpecificSettings(device: GBDevice): DeviceSpecificSettings {
        val settings = DeviceSpecificSettings()

        settings.addRootScreen(R.xml.devicesettings_glucose_limits)
        // FIXME: Read-only for now
        //settings.addConnectedPreferences(DeviceSettingsPreferenceConst.PREF_GLUCOSE_THRESHOLD_LOW)
        //settings.addConnectedPreferences(DeviceSettingsPreferenceConst.PREF_GLUCOSE_THRESHOLD_HIGH)

        return settings
    }

    override fun getDeviceSpecificSettingsCustomizer(device: GBDevice): DeviceSpecificSettingsCustomizer? {
        return OneTouchSettingsCustomizer()
    }

    override fun supportsDataFetching(device: GBDevice): Boolean {
        return true
    }

    override fun supportsCharts(device: GBDevice): Boolean {
        return false // TODO charts are not yet implemented
    }

    override fun supportsGlucoseMeasurement(device: GBDevice): Boolean {
        return true
    }
}
