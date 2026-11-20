package app.superhealth.engine.devices.coospo

import app.superhealth.engine.R
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.SampleProvider
import app.superhealth.engine.devices.generic_hr.GenericHeartRateActivitySampleProvider
import app.superhealth.engine.entities.AbstractActivitySample
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.generic_hr.GenericHeartRateSupport

/**
 * This class pulls most of the logic from the
 * {@link app.superhealth.engine.devices.generic_hr.GenericHeartRateCoordinator}.
 */
abstract class CoospoHeartRateCoordinator: AbstractBLEDeviceCoordinator() {
    override fun isExperimental(): Boolean {
        return true
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_NONE
    }

    override fun suggestUnbindBeforePair(): Boolean {
        // Not needed
        return false
    }

    override fun getManufacturer(): String {
        return "Coospo"
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return GenericHeartRateSupport::class.java
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
}
