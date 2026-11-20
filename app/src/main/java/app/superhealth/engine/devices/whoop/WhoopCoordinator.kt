/*  Copyright (C) 2026 SuperHealth

    This file is part of SuperHealth (SuperHealth fork), licensed under the GNU AGPL v3 or later. */
package app.superhealth.engine.devices.whoop

import de.greenrobot.dao.AbstractDao
import de.greenrobot.dao.Property
import app.superhealth.engine.R
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.ComputedHrvSummarySampleProvider
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.devices.GenericHeartRateSampleProvider
import app.superhealth.engine.devices.GenericHrvValueSampleProvider
import app.superhealth.engine.devices.GenericRespiratoryRateSampleProvider
import app.superhealth.engine.devices.GenericSleepScoreSampleProvider
import app.superhealth.engine.devices.GenericStressSampleProvider
import app.superhealth.engine.devices.GenericTemperatureSampleProvider
import app.superhealth.engine.devices.SampleProvider
import app.superhealth.engine.devices.TimeSampleProvider
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.entities.GenericHeartRateSampleDao
import app.superhealth.engine.entities.GenericHrvValueSampleDao
import app.superhealth.engine.entities.GenericRespiratoryRateSampleDao
import app.superhealth.engine.entities.GenericSleepScoreSampleDao
import app.superhealth.engine.entities.GenericStressSampleDao
import app.superhealth.engine.entities.GenericTemperatureSampleDao
import app.superhealth.engine.entities.WhoopActivitySampleDao
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.impl.GBDeviceCandidate
import app.superhealth.engine.model.ActivitySample
import app.superhealth.engine.model.HeartRateSample
import app.superhealth.engine.model.HrvSummarySample
import app.superhealth.engine.model.HrvValueSample
import app.superhealth.engine.model.RespiratoryRateSample
import app.superhealth.engine.model.SleepScoreSample
import app.superhealth.engine.model.StressSample
import app.superhealth.engine.model.TemperatureSample
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.whoop.WhoopSupport
import app.superhealth.engine.service.devices.whoop.WhoopUuids
import java.util.regex.Pattern

/**
 * WHOOP 4.0 et 5.0 / MG : reconnus à leur service (61080001 ou fd4b0001) ou à leur nom. Pouls, sommeil et stades, VFC,
 * pouls au repos, stress et score de sommeil pour les deux ; pas et température cutanée pour le WHOOP 5 ; respiration
 * pour le WHOOP 4. Données recalculées sur le téléphone à partir de l'historique seconde par seconde.
 */
class WhoopCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getManufacturer() = "WHOOP"
    override fun getDeviceNameResource() = R.string.devicetype_whoop
    override fun getDefaultIconResource() = R.drawable.ic_device_miband
    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> = WhoopSupport::class.java
    override fun getDeviceKind(device: GBDevice) = DeviceCoordinator.DeviceKind.FITNESS_BAND
    override fun isExperimental() = true
    override fun getBondingStyle() = BONDING_STYLE_BOND
    override fun suggestUnbindBeforePair() = false

    override fun getSupportedDeviceName(): Pattern = Pattern.compile("^WHOOP.*", Pattern.CASE_INSENSITIVE)

    override fun supports(candidate: GBDeviceCandidate): Boolean =
        candidate.supportsService(WhoopUuids.W4_SERVICE) || candidate.supportsService(WhoopUuids.W5_SERVICE) || super.supports(candidate)

    override fun getSampleProvider(device: GBDevice, session: DaoSession): SampleProvider<out app.superhealth.engine.entities.AbstractActivitySample> = WhoopActivitySampleProvider(device, session)
    override fun getHeartRateRestingSampleProvider(device: GBDevice, session: DaoSession): TimeSampleProvider<out HeartRateSample> = GenericHeartRateSampleProvider(device, session)
    override fun getHrvValueSampleProvider(device: GBDevice, session: DaoSession): TimeSampleProvider<out HrvValueSample> = GenericHrvValueSampleProvider(device, session)
    override fun getHrvSummarySampleProvider(device: GBDevice, session: DaoSession): TimeSampleProvider<out HrvSummarySample> =
        ComputedHrvSummarySampleProvider(getHrvValueSampleProvider(device, session), device, session)
    override fun getTemperatureSampleProvider(device: GBDevice, session: DaoSession): TimeSampleProvider<out TemperatureSample> =
        GenericTemperatureSampleProvider(device, session, TemperatureSample.TYPE_SKIN, TemperatureSample.LOCATION_WRIST)
    override fun getRespiratoryRateSampleProvider(device: GBDevice, session: DaoSession): TimeSampleProvider<out RespiratoryRateSample> = GenericRespiratoryRateSampleProvider(device, session)
    override fun getStressSampleProvider(device: GBDevice, session: DaoSession): TimeSampleProvider<out StressSample> = GenericStressSampleProvider(device, session)
    override fun getSleepScoreProvider(device: GBDevice, session: DaoSession): TimeSampleProvider<out SleepScoreSample> = GenericSleepScoreSampleProvider(device, session)

    override fun supportsActivityTracking(device: GBDevice) = true
    override fun supportsDataFetching(device: GBDevice) = true
    override fun supportsRealtimeData(device: GBDevice) = true
    override fun supportsHeartRateMeasurement(device: GBDevice) = true
    override fun supportsHeartRateRestingMeasurement(device: GBDevice) = true
    override fun supportsManualHeartRateMeasurement(device: GBDevice) = false
    override fun supportsHrvMeasurement(device: GBDevice) = true
    override fun supportsSleepMeasurement(device: GBDevice) = true
    override fun supportsSleepScore(device: GBDevice) = true
    override fun supportsStressMeasurement(device: GBDevice) = true
    override fun supportsRespiratoryRate(device: GBDevice) = true
    override fun supportsTemperatureMeasurement(device: GBDevice) = true
    override fun supportsContinuousTemperature(device: GBDevice) = true
    /** Pas : le podomètre du WHOOP 5 ; le WHOOP 4 n'en a pas (les pas du téléphone prennent le relais). */
    override fun supportsStepCounter(device: GBDevice) = true
    override fun supportsCharts(device: GBDevice) = true
}
