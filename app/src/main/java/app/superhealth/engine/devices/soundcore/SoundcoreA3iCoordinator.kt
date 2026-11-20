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
package app.superhealth.engine.devices.soundcore

import app.superhealth.engine.R
import app.superhealth.engine.impl.GBDevice
import java.util.regex.Pattern

class SoundcoreA3iCoordinator : SoundcoreEarbudsCoordinator() {
    override fun getModelName(): String = "A3992"

    override fun getSupportedDeviceName(): Pattern =
        Pattern.compile("^soundcore A3i$", Pattern.CASE_INSENSITIVE)

    override fun getDeviceNameResource(): Int = R.string.devicetype_soundcore_a3i

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_galaxy_buds_pro

    override fun isExperimental(): Boolean = true

    override fun getDefaultCapabilities(): Set<Capability> = setOf(
        Capability.FindDevice,
        Capability.NoiseControl,
    )
}
