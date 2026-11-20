/*  Copyright (C) 2021-2024 Damien Gaignon, Daniel Dakhno, José Rebelo

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

class QC35Coordinator : AbstractBoseCoordinator() {
    protected override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("Bose QC 35.*")
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_bose_qc35
    }

    override val deviceConfig = BoseDeviceConfig.QC35
}
