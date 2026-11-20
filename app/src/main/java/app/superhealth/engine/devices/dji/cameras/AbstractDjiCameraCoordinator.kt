package app.superhealth.engine.devices.dji.cameras

import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.dsl.DeviceSettingsSpec
import app.superhealth.engine.activities.devicesettings.dsl.deviceSettings
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.dji.ble.DjiBleSupport

abstract class AbstractDjiCameraCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getManufacturer(): String {
        return "DJI"
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return DjiBleSupport::class.java
    }

    override fun suggestUnbindBeforePair(): Boolean {
        return false
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_NONE
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_camera
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.CAMERA
    }

    override fun supportsGallery(device: GBDevice): Boolean {
        return true
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {

    }

    override fun supportsInternetAccess(device: GBDevice): Boolean = true
}
