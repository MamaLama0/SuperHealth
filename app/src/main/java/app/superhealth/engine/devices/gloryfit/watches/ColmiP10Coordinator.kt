/*  Copyright (C) 2026 David Giron

    This file is part of SuperHealth.

    SuperHealth is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuperHealth is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package app.superhealth.engine.devices.gloryfit.watches

import app.superhealth.engine.R
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsScreen
import app.superhealth.engine.activities.devicesettings.dsl.DeviceSettingsSpec
import app.superhealth.engine.activities.devicesettings.dsl.deviceSettings
import app.superhealth.engine.devices.DeviceCoordinator
import app.superhealth.engine.devices.gloryfit.GloryFitCoordinator
import app.superhealth.engine.impl.GBDevice
import java.util.regex.Pattern

class ColmiP10Coordinator : GloryFitCoordinator() {
    override fun getManufacturer(): String = "Colmi"

    override fun getSupportedDeviceName(): Pattern = Pattern.compile("^P10\\(ID-[0-9A-F]{4}\\)$")

    override fun getDeviceNameResource(): Int = R.string.devicetype_colmi_p10

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_amazfit_bip

    // Otherwise gets stuck in fetching data
    override fun supportsSpo2(device: GBDevice): Boolean = false
}
