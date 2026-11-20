/*  Copyright (C) 2026 Toby Murray

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
package app.superhealth.engine.devices.una

import androidx.core.content.edit
import app.superhealth.engine.GBApplication
import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.devices.SampleProvider
import app.superhealth.engine.entities.AbstractActivitySample
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.impl.GBDeviceCandidate
import app.superhealth.engine.model.DeviceType
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.una.UnaDeviceSupport
import app.superhealth.engine.util.GBPrefs
import java.util.regex.Pattern

class UnaDeviceCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getSupportedDeviceName(): Pattern {
        // Digit count isn't a confirmed fixed width; unconstrained to avoid missing real units.
        return Pattern.compile("^UNA Watch \\d+$")
    }

    override fun getManufacturer(): String = "UNA"

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.WATCH

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> =
        UnaDeviceSupport::class.java

    override fun getDeviceNameResource(): Int = R.string.devicetype_una_watch

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_miwatch

    // No app-layer secret; standard BLE bonding is the entire security gate on this firmware.
    override fun getBondingStyle(): Int = BONDING_STYLE_BOND

    override fun createDevice(candidate: GBDeviceCandidate, deviceType: DeviceType): GBDevice {
        val gbDevice = super.createDevice(candidate, deviceType)
        GBApplication.getDevicePrefs(gbDevice).preferences.edit {
            putBoolean(DeviceSettingsPreferenceConst.PREF_CONNECTION_PRIORITY_LOW_POWER, true)
            putBoolean(GBPrefs.DEVICE_CONNECT_BACK, true)
            putBoolean(GBPrefs.DEVICE_AUTO_RECONNECT, true)
        }
        return gbDevice
    }

    override fun supportsDataFetching(device: GBDevice): Boolean = true

    override fun supportsRecordedActivities(device: GBDevice): Boolean = true

    // One aggregate sample per day for steps and floors, which have no per-minute source on this
    // firmware, plus a real per-minute heart rate timeline.
    override fun supportsActivityTracking(device: GBDevice): Boolean = true

    override fun supportsHeartRateMeasurement(device: GBDevice): Boolean = true

    // No command exists to take a reading on demand, so the manual measurement UI would be a dead
    // button. Stated explicitly because it otherwise follows supportsHeartRateMeasurement.
    override fun supportsManualHeartRateMeasurement(device: GBDevice): Boolean = false

    override fun getSampleProvider(device: GBDevice, session: DaoSession): SampleProvider<out AbstractActivitySample> =
        UnaDailySampleProvider(device, session)

    override fun supportsAppNotifications(device: GBDevice): Boolean = true
}
