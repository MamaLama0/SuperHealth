/*  Copyright (C) 2026 SuperHealth

    This file is part of Gadgetbridge (SuperHealth fork), licensed under the GNU AGPL v3 or later. */
package nodomain.freeyourgadget.gadgetbridge.devices.whoop

import de.greenrobot.dao.AbstractDao
import de.greenrobot.dao.Property
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.ComputedHrvSummarySampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.GenericHeartRateSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericHrvValueSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericRespiratoryRateSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericSleepScoreSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericStressSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericTemperatureSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHeartRateSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHrvValueSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GenericRespiratoryRateSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GenericSleepScoreSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GenericStressSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTemperatureSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.WhoopActivitySampleDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.model.HeartRateSample
import nodomain.freeyourgadget.gadgetbridge.model.HrvSummarySample
import nodomain.freeyourgadget.gadgetbridge.model.HrvValueSample
import nodomain.freeyourgadget.gadgetbridge.model.RespiratoryRateSample
import nodomain.freeyourgadget.gadgetbridge.model.SleepScoreSample
import nodomain.freeyourgadget.gadgetbridge.model.StressSample
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureSample
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.whoop.WhoopSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.whoop.WhoopUuids
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

    override fun getAllDeviceDao(session: DaoSession): MutableMap<AbstractDao<*, *>, Property> = hashMapOf(
        session.whoopActivitySampleDao to WhoopActivitySampleDao.Properties.DeviceId,
        session.genericHeartRateSampleDao to GenericHeartRateSampleDao.Properties.DeviceId,
        session.genericHrvValueSampleDao to GenericHrvValueSampleDao.Properties.DeviceId,
        session.genericTemperatureSampleDao to GenericTemperatureSampleDao.Properties.DeviceId,
        session.genericRespiratoryRateSampleDao to GenericRespiratoryRateSampleDao.Properties.DeviceId,
        session.genericStressSampleDao to GenericStressSampleDao.Properties.DeviceId,
        session.genericSleepScoreSampleDao to GenericSleepScoreSampleDao.Properties.DeviceId,
    )

    override fun getSampleProvider(device: GBDevice, session: DaoSession): SampleProvider<out nodomain.freeyourgadget.gadgetbridge.entities.AbstractActivitySample> = WhoopActivitySampleProvider(device, session)
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
