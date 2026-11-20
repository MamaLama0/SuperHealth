package app.superhealth.engine.devices.generic_hr

import app.superhealth.engine.R
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.devices.SampleProvider
import app.superhealth.engine.entities.AbstractActivitySample
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.impl.GBDeviceCandidate
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.btle.GattService
import app.superhealth.engine.service.devices.generic_hr.GenericHeartRateSupport

class GenericHeartRateCoordinator : AbstractBLEDeviceCoordinator() {
    override fun isExperimental(): Boolean {
        // Not heavily tested
        return true
    }

    override fun getOrderPriority(): Int {
        return Int.MAX_VALUE
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_NONE
    }

    override fun suggestUnbindBeforePair(): Boolean {
        // Not needed
        return false
    }

    override fun supports(candidate: GBDeviceCandidate): Boolean {
        return candidate.supportsService(GattService.SERVICE_HEART_RATE.uuid)
    }

    override fun getManufacturer(): String {
        return "Generic"
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return GenericHeartRateSupport::class.java
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_generic_heart_rate_sensor
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_lovetoy
    }

    override fun supportsHeartRateMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsCharts(device: GBDevice): Boolean {
        return true
    }

    override fun getSampleProvider(device: GBDevice, session: DaoSession): SampleProvider<out AbstractActivitySample>? {
        return GenericHeartRateActivitySampleProvider(device, session)
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.CHEST_STRAP
    }
}
