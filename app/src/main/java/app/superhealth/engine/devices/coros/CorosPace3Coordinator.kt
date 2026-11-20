/*  Copyright (C) 2026 David Giron

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
package app.superhealth.engine.devices.coros

import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst
import app.superhealth.engine.activities.devicesettings.dsl.DeviceSettingsSpec
import app.superhealth.engine.activities.devicesettings.dsl.ListEntry
import app.superhealth.engine.activities.devicesettings.dsl.deviceSettings
import app.superhealth.engine.R
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.impl.GBDevice
import app.superhealth.engine.service.DeviceSupport
import app.superhealth.engine.service.devices.coros.CorosPace3Support
import java.util.regex.Pattern

class CorosPace3Coordinator : AbstractBLEDeviceCoordinator() {
    override fun getSupportedDeviceName(): Pattern = Pattern.compile("^COROS PACE 3 [0-9A-F]{6}$")

    override fun getManufacturer(): String = "COROS"

    override fun getDeviceNameResource(): Int = R.string.devicetype_coros_pace_3

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_miwatch

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.WATCH

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> =
        CorosPace3Support::class.java

    override fun getBondingStyle(): Int = BONDING_STYLE_NONE

    override fun suggestUnbindBeforePair(): Boolean = false

    override fun isExperimental(): Boolean = true

    override fun supportsNotificationVibrationPatterns(device: GBDevice): Boolean = false

    override fun supportsNotificationLedPatterns(device: GBDevice): Boolean = false

    override fun supportsNotificationVibrationRepetitionPatterns(device: GBDevice): Boolean = false

    override fun supportsAppNotifications(device: GBDevice): Boolean = true

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        screen(key = "screen_do_not_disturb_no_auto", title = R.string.mi2_prefs_do_not_disturb, icon = R.drawable.ic_block) {
            list(
                key = DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_NOAUTO,
                title = R.string.mi2_prefs_do_not_disturb,
                defaultValue = DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_OFF,
                entries = listOf(
                    ListEntry.Res(DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_OFF, R.string.off),
                    ListEntry.Res(DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_SCHEDULED, R.string.mi2_dnd_scheduled),
                ),
            )
            time(key = DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_NOAUTO_START,
                title = R.string.mi2_prefs_do_not_disturb_start, defaultValue = "22:00")
            time(key = DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_NOAUTO_END,
                title = R.string.mi2_prefs_do_not_disturb_end, defaultValue = "06:00")
        }
    }

    override fun supportsFindDevice(device: GBDevice): Boolean = true
}
