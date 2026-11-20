/*  Copyright (C) 2018-2024 Daniel Dakhno, Daniele Gobbetti, José Rebelo,
    tiparega

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
package app.superhealth.engine.devices.hplus;

/*
* @author Alejandro Ladera Chamorro &lt;11555126+tiparega@users.noreply.github.com&gt;
*/

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.devices.hplus.Q8Support;

/**
 * Pseudo Coordinator for the Q8, a sub type of the HPLUS devices
 */
public class Q8Coordinator extends HPlusCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("Q8.*");
    }

    @Override
    public String getManufacturer() {
        return "Makibes";
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_q8;
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return Q8Support.class;
    }
}
