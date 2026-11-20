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
package app.superhealth.engine.devices.onetouch

import androidx.preference.Preference
import kotlinx.parcelize.Parcelize
import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst
import app.superhealth.engine.activities.devicesettings.DeviceSettingsUtils
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsCustomizer
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsHandler
import app.superhealth.engine.util.Prefs

@Parcelize
class OneTouchSettingsCustomizer : DeviceSpecificSettingsCustomizer {
    override fun onPreferenceChange(
        preference: Preference,
        handler: DeviceSpecificSettingsHandler
    ) {
    }

    override fun onDeviceChanged(handler: DeviceSpecificSettingsHandler) {
    }

    override fun customizeSettings(
        handler: DeviceSpecificSettingsHandler,
        genericDevicePrefs: Prefs,
        rootKey: String?
    ) {
        // FIXME: Read-only for now
        handler.findPreference<Preference>(DeviceSettingsPreferenceConst.PREF_GLUCOSE_THRESHOLD_LOW)?.isSelectable = false
        handler.findPreference<Preference>(DeviceSettingsPreferenceConst.PREF_GLUCOSE_THRESHOLD_HIGH)?.isSelectable = false

        DeviceSettingsUtils.populateWithRange(
            DeviceSettingsPreferenceConst.PREF_GLUCOSE_THRESHOLD_LOW,
            handler,
            60,
            110,
            R.string.glucose_value_mg_dl,
            false
        )

        DeviceSettingsUtils.populateWithRange(
            DeviceSettingsPreferenceConst.PREF_GLUCOSE_THRESHOLD_HIGH,
            handler,
            90,
            300,
            R.string.glucose_value_mg_dl,
            false
        )
    }

    override fun getPreferenceKeysWithSummary(): Set<String> {
        return setOf()
    }
}
