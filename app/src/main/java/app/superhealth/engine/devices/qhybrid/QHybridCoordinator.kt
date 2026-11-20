/*  Copyright (C) 2019-2026 Andreas Shimokawa, Arjan Schrijver, Carsten
    Pfeiffer, Damien Gaignon, Daniel Dakhno, Hasan Ammar, José Rebelo, Morten
    Rieger Hannemose, Petr Vaněk

    This file is part of SuperHealth.

    SuperHealth is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuperHealth is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package app.superhealth.engine.devices.qhybrid

import android.app.Activity
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.ParcelUuid
import app.superhealth.engine.GBApplication
import app.superhealth.engine.R
import app.superhealth.engine.activities.appmanager.AppManagerActivity
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsCustomizer
import app.superhealth.engine.activities.devicesettings.dsl.DeviceSettingsSpec
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.devices.HybridHRSpo2SampleProvider
import app.superhealth.engine.devices.InstallHandler
import app.superhealth.engine.devices.SampleProvider
import app.superhealth.engine.devices.TimeSampleProvider
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.entities.HybridHRActivitySample
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.impl.GBDeviceCandidate
import app.superhealth.engine.model.ActivitySummaryParser
import app.superhealth.engine.model.BatteryConfig
import app.superhealth.engine.model.DeviceType
import app.superhealth.engine.model.Spo2Sample
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.qhybrid.QHybridSupport
import app.superhealth.engine.service.devices.qhybrid.parser.HybridHRWorkoutSummaryParser
import app.superhealth.engine.util.FileUtils
import app.superhealth.engine.util.Version
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

class QHybridCoordinator : AbstractBLEDeviceCoordinator() {
    override fun supports(candidate: GBDeviceCandidate): Boolean {
        for (uuid in candidate.serviceUuids) {
            if (uuid.uuid.toString() == "3dda0001-957f-7d4a-34a6-74696673696d") {
                return true
            }
        }
        return false
    }

    override fun createBLEScanFilters(): MutableCollection<out ScanFilter?> {
        return mutableListOf<ScanFilter?>(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString("3dda0001-957f-7d4a-34a6-74696673696d"))
                .build()
        )
    }

    override fun supportsDataFetching(device: GBDevice): Boolean {
        return isFossilHybrid(device) && device.state == GBDevice.State.INITIALIZED
    }

    override fun supportsActivityTracking(device: GBDevice): Boolean {
        return true
    }

    override fun supportsRecordedActivities(device: GBDevice): Boolean {
        return isHybridHR(device)
    }

    override fun getActivitySummaryParser(
        device: GBDevice,
        context: Context
    ): ActivitySummaryParser {
        return HybridHRWorkoutSummaryParser()
    }

    override fun supportsUnicodeEmojis(device: GBDevice): Boolean {
        return true
    }

    override fun getSampleProvider(
        device: GBDevice,
        session: DaoSession
    ): SampleProvider<out HybridHRActivitySample> {
        return HybridHRActivitySampleProvider(device, session)
    }

    override fun getSpo2SampleProvider(
        device: GBDevice,
        session: DaoSession
    ): TimeSampleProvider<out Spo2Sample?> {
        return HybridHRSpo2SampleProvider(device, session)
    }

    @Suppress("DEPRECATION")
    override fun findInstallHandler(uri: Uri, options: Bundle, context: Context): InstallHandler? {
        if (this.isHybridHR) {
            val installHandler = FossilHRInstallHandler(uri, context)
            if (!installHandler.isValid) {
                LOG.warn("Not a Fossil Hybrid firmware or app!")
                return null
            } else {
                return installHandler
            }
        }
        val installHandler = FossilInstallHandler(uri, context)
        return if (installHandler.isValid) installHandler else null
    }

    private fun supportsAlarmConfiguration(device: GBDevice): Boolean {
        return isFossilHybrid(device) && device.state == GBDevice.State.INITIALIZED
    }

    override fun getAlarmSlotCount(device: GBDevice): Int {
        return if (supportsAlarmConfiguration(device)) 5 else 0
    }

    override fun getCannedRepliesSlotCount(device: GBDevice): Int {
        if (isHybridHR(device)) {
            return 16
        }

        return 0
    }

    override fun supportsAlarmTitle(device: GBDevice): Boolean {
        return isHybridHR(device)
    }

    override fun supportsAlarmDescription(device: GBDevice): Boolean {
        return isHybridHR(device)
    }

    override fun supportsHeartRateMeasurement(device: GBDevice): Boolean {
        return isHybridHR(device)
    }

    override fun getManufacturer(): String {
        return "Fossil"
    }

    override fun supportsAppsManagement(device: GBDevice): Boolean {
        return isHybridHR(device)
    }

    override fun supportsAppListFetching(device: GBDevice): Boolean {
        return isHybridHR(device)
    }

    override fun getAppsManagementActivity(device: GBDevice): Class<out Activity?>? {
        return if (isHybridHR(device)) AppManagerActivity::class.java else null
    }

    override fun getWatchfaceDesignerActivity(device: GBDevice): Class<out Activity?>? {
        return if (isHybridHR(device)) HybridHRWatchfaceDesignerActivity::class.java else null
    }

    /**
     * Returns the directory containing the watch app cache.
     * @throws IOException when the external files directory cannot be accessed
     */
    @Throws(IOException::class)
    override fun getAppCacheDir(): File {
        return File(FileUtils.getExternalFilesDir(), "qhybrid-app-cache")
    }

    /**
     * Returns a String containing the device app sort order filename.
     */
    override fun getAppCacheSortFilename(): String {
        return "wappcacheorder.txt"
    }

    /**
     * Returns a String containing the file extension for watch apps.
     */
    override fun getAppFileExtension(): String {
        return ".wapp"
    }

    override fun supportsWeather(device: GBDevice): Boolean {
        return isHybridHR(device)
    }

    override fun supportsFindDevice(device: GBDevice): Boolean {
        return true
    }

    override fun supportsFlashing(device: GBDevice): Boolean {
        return true
    }

    override fun supportsCalendarEvents(device: GBDevice): Boolean {
        return isHybridHR(device)
    }

    override fun getBatteryConfig(device: GBDevice): Array<BatteryConfig> {
        return arrayOf(
            BatteryConfig(
                0,
                GBDevice.BATTERY_ICON_DEFAULT.toInt(),
                GBDevice.BATTERY_LABEL_DEFAULT.toInt(),
                if (isHybridHR(device)) 10 else 2,
                100
            )
        )
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec =
        qhybridDeviceSettings(isHybridHR(device), getFirmwareVersion(device))

    override fun getDeviceSpecificSettingsCustomizer(device: GBDevice): DeviceSpecificSettingsCustomizer {
        return QHybridSettingsCustomizer()
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return QHybridSupport::class.java
    }

    @Suppress("DEPRECATION")
    override fun getSupportedDeviceSpecificAuthenticationSettings(): IntArray {
        return if (this.isHybridHR) {
            intArrayOf(
                R.xml.devicesettings_pairingkey
            )
        } else {
            IntArray(0)
        }
    }

    override fun getAuthHelp(): String {
        return "https://gadgetbridge.org/basics/pairing/fossil-server/"
    }

    @get:Deprecated("")
    private val isHybridHR: Boolean
        // we should use the isHybridHR(GBDevice) instead of iterating every single device
        get() {
            val devices =
                GBApplication.app().deviceManager.selectedDevices
            for (device in devices) {
                if (isHybridHR(device)) {
                    return true
                }
            }
            return false
        }

    fun isHybridHR(device: GBDevice): Boolean {
        if (!isFossilHybrid(device)) return false
        return device.name
            .startsWith("Hybrid HR") || device.name == "Fossil Gen. 6 Hybrid"
    }

    private fun getFirmwareVersion(device: GBDevice): Version? {
        if (isFossilHybrid(device)) {
            return Version(device.firmwareVersion2)
        }

        return null
    }

    private fun isFossilHybrid(device: GBDevice): Boolean {
        return device.type == DeviceType.FOSSILQHYBRID
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_qhybrid
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_zetime
    }

    override fun supportsNavigation(device: GBDevice): Boolean {
        return isHybridHR(device)
    }

    override fun supportsAppNotifications(device: GBDevice): Boolean = true

    override fun supportsPhoneCalls(device: GBDevice): Boolean = true

    override fun supportsCallControl(device: GBDevice): Boolean = true

    override fun supportsSmsReply(device: GBDevice): Boolean = isHybridHR(device)

    override fun supportsMusicInfo(device: GBDevice): Boolean {
        return isHybridHR(device)
    }

    override fun supportsSpo2(device: GBDevice): Boolean {
        return device.name == "Fossil Gen. 6 Hybrid"
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.WATCH
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(QHybridCoordinator::class.java)
    }
}
