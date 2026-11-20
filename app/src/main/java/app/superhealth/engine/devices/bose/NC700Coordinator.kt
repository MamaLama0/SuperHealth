/*  Copyright (C) 2026 Dominic Monroe

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
package app.superhealth.engine.devices.bose

import app.superhealth.engine.R
import java.util.regex.Pattern

class NC700Coordinator : AbstractBoseCoordinator() {
    protected override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("(LE-)?Bose NC 700.*")
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_bose_nc700
    }

    override val deviceConfig = BoseDeviceConfig.NC700
}
