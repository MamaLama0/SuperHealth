package app.superhealth.engine.devices.bm6

import app.superhealth.engine.R
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.devices.GenericTemperatureSampleProvider
import app.superhealth.engine.devices.TimeSampleProvider
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.model.TemperatureSample
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.bm6.Bm6Support
import java.util.regex.Pattern


class Bm6Coordinator : AbstractBLEDeviceCoordinator() {
    override fun isExperimental(): Boolean {
        // #6236 - Untested
        return true;
    }

    override fun getSupportedDeviceName(): Pattern {
        return Pattern.compile("^BM6$")
    }

    override fun getManufacturer(): String {
        return "BM6"
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return Bm6Support::class.java
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

    override fun getTemperatureSampleProvider(
        device: GBDevice,
        session: DaoSession
    ): TimeSampleProvider<out TemperatureSample?> {
        return GenericTemperatureSampleProvider(device, session)
    }

    override fun supportsTemperatureMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsContinuousTemperature(device: GBDevice): Boolean {
        return true
    }
}
