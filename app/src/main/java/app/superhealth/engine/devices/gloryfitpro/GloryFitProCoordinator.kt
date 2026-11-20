/*  Copyright (C) 2026 SuperHealth contributors

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
package app.superhealth.engine.devices.gloryfitpro

import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettings
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsScreen
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.devices.SampleProvider
import app.superhealth.engine.devices.gloryfit.GloryFitActivitySampleProvider
import app.superhealth.engine.entities.AbstractActivitySample
import app.superhealth.engine.entities.DaoSession
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.gloryfitpro.GloryFitProSupport

/**
 * Base coordinator for watches speaking the "GloryFit Pro" BLE dialect (com.yc.gloryfitpro).
 * Concrete per-model coordinators (e.g. [app.superhealth.engine.devices.gloryfitpro.watches.DM58Coordinator])
 * only supply the advertised-name pattern, manufacturer and display name. MVP: connect + firmware + time.
 */
abstract class GloryFitProCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_amazfit_bip
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.WATCH
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return GloryFitProSupport::class.java
    }

    override fun suggestUnbindBeforePair(): Boolean {
        return false
    }

    override fun supportsActivityTracking(device: GBDevice): Boolean {
        return true
    }

    override fun supportsMusicInfo(device: GBDevice): Boolean {
        // Not music info as such, but the watch can control media playback.
        return true
    }

    override fun supportsFindDevice(device: GBDevice): Boolean {
        return true
    }

    override fun getContactsSlotCount(device: GBDevice): Int {
        return 20
    }

    override fun getAlarmSlotCount(device: GBDevice): Int {
        return 5
    }

    override fun supportsWeather(device: GBDevice): Boolean {
        return true
    }

    override fun supportsAppNotifications(device: GBDevice): Boolean = true

    override fun supportsDataFetching(device: GBDevice): Boolean {
        return true
    }

    override fun getSampleProvider(
        device: GBDevice,
        session: DaoSession
    ): SampleProvider<out AbstractActivitySample>? {
        return GloryFitActivitySampleProvider(device, session)
    }

    override fun getDeviceSpecificSettings(device: GBDevice): DeviceSpecificSettings {
        val settings = DeviceSpecificSettings()
        val notifications = settings.addRootScreen(DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS)
        notifications.add(R.xml.devicesettings_header_notifications)
        notifications.add(R.xml.devicesettings_send_app_notifications)
        notifications.add(R.xml.devicesettings_per_app_notifications)
        if (getContactsSlotCount(device) > 0) {
            notifications.add(R.xml.devicesettings_contacts)
        }
        return settings
    }
}
