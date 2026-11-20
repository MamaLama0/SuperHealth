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

import androidx.annotation.StringRes
import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst
import app.superhealth.engine.activities.devicesettings.dsl.DeviceSettingsScope
import app.superhealth.engine.activities.devicesettings.dsl.LabeledEntry
import app.superhealth.engine.activities.devicesettings.dsl.SeekBarSetting
import app.superhealth.engine.util.Prefs


inline fun <reified T> DeviceSettingsScope.equalizerPreset(
    key: String = DeviceSettingsPreferenceConst.PREF_HEADPHONES_EQUALIZER,
    @StringRes title: Int = R.string.prefs_equalizer_preset,
    defaultValue: T,
    dependency: String? = null,
    connectedOnly: Boolean = true,
    noinline filter: ((T) -> Boolean)? = null,
    noinline visibleWhen: ((Prefs) -> Boolean)? = null,
) where T : Enum<T>, T : LabeledEntry {
    enumList<T>(
        key = key,
        title = title,
        icon = R.drawable.ic_equalizer,
        defaultValue = defaultValue,
        dependency = dependency,
        connectedOnly = connectedOnly,
        filter = filter,
        visibleWhen = visibleWhen,
    )
}

fun DeviceSettingsScope.volume(
    key: String = DeviceSettingsPreferenceConst.PREF_VOLUME,
    max: Int,
    defaultValue: Int,
    connectedOnly: Boolean = true,
    visibleWhen: ((Prefs) -> Boolean)? = null,
) {
    items.add(
        SeekBarSetting(
            key = key,
            title = R.string.menuitem_volume,
            icon = R.drawable.ic_volume_up,
            max = max,
            defaultValue = defaultValue,
            connectedOnly = connectedOnly,
            visibleWhen = visibleWhen,
        )
    )
}
