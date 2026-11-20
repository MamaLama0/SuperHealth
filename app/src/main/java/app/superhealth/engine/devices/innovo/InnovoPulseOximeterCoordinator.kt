package app.superhealth.engine.devices.innovo

import app.superhealth.engine.R
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.devices.GenericRespiratoryRateSampleProvider
import app.superhealth.engine.devices.GenericSpo2SampleProvider
import app.superhealth.engine.devices.SampleProvider
import app.superhealth.engine.devices.TimeSampleProvider
import app.superhealth.engine.devices.generic_hr.GenericHeartRateActivitySampleProvider
import app.superhealth.engine.entities.AbstractActivitySample
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.model.RespiratoryRateSample
import app.superhealth.engine.model.Spo2Sample
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.innovo.InnovoPulseOximeterSupport
import java.util.regex.Pattern

class InnovoPulseOximeterCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getSupportedDeviceName(): Pattern {
        return Pattern.compile("^iP900BPB$")
    }

    override fun getManufacturer(): String {
        return "Innovo"
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_innovo_ip900bp_b
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return InnovoPulseOximeterSupport::class.java
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_NONE
    }

    override fun suggestUnbindBeforePair(): Boolean {
        return false
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.PULSE_OXIMETER
    }

    override fun supportsHeartRateMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsManualHeartRateMeasurement(device: GBDevice): Boolean {
        return false
    }

    override fun getSampleProvider(
        device: GBDevice,
        session: DaoSession
    ): SampleProvider<out AbstractActivitySample> {
        return GenericHeartRateActivitySampleProvider(device, session)
    }

    override fun supportsSpo2(device: GBDevice): Boolean {
        return true
    }

    override fun getSpo2SampleProvider(
        device: GBDevice,
        session: DaoSession
    ): TimeSampleProvider<out Spo2Sample> {
        return GenericSpo2SampleProvider(device, session)
    }

    override fun supportsRespiratoryRate(device: GBDevice): Boolean {
        return true
    }

    override fun supportsDayRespiratoryRate(device: GBDevice): Boolean {
        return true
    }

    override fun supportsSleepRespiratoryRate(device: GBDevice): Boolean {
        return false
    }

    override fun getRespiratoryRateSampleProvider(
        device: GBDevice,
        session: DaoSession
    ): TimeSampleProvider<out RespiratoryRateSample> {
        return GenericRespiratoryRateSampleProvider(device, session)
    }
}
