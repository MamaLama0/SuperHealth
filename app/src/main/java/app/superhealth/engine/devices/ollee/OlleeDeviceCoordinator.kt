/*  Copyright (C) 2026 Ken Blizzard-Caron

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
package app.superhealth.engine.devices.ollee

import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettings
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsScreen
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.devices.SampleProvider
import app.superhealth.engine.entities.AbstractActivitySample
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.ollee.OlleeDeviceSupport
import java.util.regex.Pattern

class OlleeDeviceCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getSupportedDeviceName(): Pattern {
        // The watch advertises as "Ollee Watch" (verified on hardware)
        return Pattern.compile("^Ollee.*")
    }

    override fun getManufacturer(): String = "Ollee"

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.WATCH

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> =
        OlleeDeviceSupport::class.java

    override fun getDeviceNameResource(): Int = R.string.devicetype_ollee_watch_one

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_default

    override fun getBondingStyle(): Int = BONDING_STYLE_NONE

    override fun getAlarmSlotCount(device: GBDevice): Int = 1

    override fun getWorldClocksSlotCount(): Int = 1

    override fun getWorldClocksLabelLength(): Int = 0

    override fun getDeviceSpecificSettings(device: GBDevice): DeviceSpecificSettings {
        // Surfaces the world-clock configuration screen; without this the single world-clock
        // slot is unreachable from the UI.
        val settings = DeviceSpecificSettings()
        settings.addRootScreen(DeviceSpecificSettingsScreen.DATE_TIME)
            .add(R.xml.devicesettings_world_clocks)
        return settings
    }

    override fun supportsActivityTracking(device: GBDevice): Boolean = true

    override fun supportsDataFetching(device: GBDevice): Boolean = true

    // Defaults for both follow supportsActivityTracking — the watch reports neither.
    override fun supportsSleepMeasurement(device: GBDevice): Boolean = false

    override fun supportsActivityDistance(device: GBDevice): Boolean = false

    override fun getSampleProvider(device: GBDevice, session: DaoSession): SampleProvider<out AbstractActivitySample> =
        OlleeActivitySampleProvider(device, session)

    override fun supportsAppNotifications(device: GBDevice): Boolean = true
}
