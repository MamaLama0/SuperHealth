/*  Copyright (C) 2020-2024 Daniel Dakhno, José Rebelo, Lesur Frederic

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

import androidx.annotation.NonNull;

import app.superhealth.engine.R;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.impl.GBDeviceCandidate;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.devices.hplus.SG2Support;

/**
 * Pseudo Coordinator for the Lemfo SG2, a sub type of the HPLUS devices
 */
public class SG2Coordinator extends HPlusCoordinator {
    @Override
    public boolean supports(GBDeviceCandidate candidate) {
        String name = candidate.getName();
        if (name != null && name.startsWith("SG2")) {
            HPlusCoordinator.setNotificationLinesNumber(candidate.getDevice().getAddress(), 9);
            HPlusCoordinator.setUnicodeSupport(candidate.getDevice().getAddress(), true);
            HPlusCoordinator.setDisplayIncomingMessageIcon(candidate.getDevice().getAddress(), false);
            return true;
        }

        return false;
    }

    @Override
    public String getManufacturer() {
        return "Lemfo";
    }

    @Override
    public boolean supportsWeather(final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsSmartWakeup(GBDevice device, int position) {
        return true;
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_ASK;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_sg2;
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return SG2Support.class;
    }
}
