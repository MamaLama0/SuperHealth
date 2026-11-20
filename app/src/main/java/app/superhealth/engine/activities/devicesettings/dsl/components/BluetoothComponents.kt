/*  Copyright (C) 2026 José Rebelo

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
package app.superhealth.engine.activities.devicesettings.dsl.components

import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst
import app.superhealth.engine.activities.devicesettings.dsl.DeviceSettingsScope
import app.superhealth.engine.activities.devicesettings.dsl.TextSetting
import app.superhealth.engine.activities.multipoint.MultipointPairingActivity
import app.superhealth.engine.util.Prefs

fun DeviceSettingsScope.multipointPairing() {
    externalSettings(
        key = DeviceSettingsPreferenceConst.PREF_MULTIPOINT,
        title = R.string.bluetooth_multipoint_pairing,
        icon = R.drawable.ic_bluetooth_searching,
        activityClass = MultipointPairingActivity::class.java,
    )
}

fun DeviceSettingsScope.deviceName(
    key: String = DeviceSettingsPreferenceConst.PREF_DEVICE_NAME,
    maxLength: Int? = null,
    connectedOnly: Boolean = true,
    visibleWhen: ((Prefs) -> Boolean)? = null,
) {
    items.add(
        TextSetting(
            key = key,
            title = R.string.prefs_device_name,
            icon = R.drawable.ic_bluetooth,
            maxLength = maxLength,
            connectedOnly = connectedOnly,
            visibleWhen = visibleWhen,
        )
    )
}
