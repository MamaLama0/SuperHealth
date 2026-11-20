/*  Copyright (C) 2016-2024 Andreas Shimokawa, Carsten Pfeiffer, Damien
    Gaignon, Daniel Dakhno, Daniele Gobbetti, José Rebelo, Petr Vaněk

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
package app.superhealth.engine.devices.vibratissimo;


import androidx.annotation.NonNull;

import java.util.EnumSet;
import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator;
import app.superhealth.engine.devices.DeviceCoordinator;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.ServiceDeviceSupport;
import app.superhealth.engine.service.devices.vibratissimo.VibratissimoSupport;

public class VibratissimoCoordinator extends AbstractBLEDeviceCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("Vibratissimo.*");
    }

    @Override
    public String getManufacturer() {
        return "Amor AG";
    }

    @Override
    public boolean supportsRealtimeData(@NonNull GBDevice device) {
        return false; // hmmm well, it has a temperature sensor :D
    }

    @Override
    public boolean supportsFindDevice(@NonNull GBDevice device) {
        return true;
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(@NonNull final GBDevice device) {
        return VibratissimoSupport.class;
    }

    @Override
    public EnumSet<ServiceDeviceSupport.Flags> getInitialFlags() {
        return EnumSet.of(ServiceDeviceSupport.Flags.THROTTLING, ServiceDeviceSupport.Flags.BUSY_CHECKING);
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_vibratissimo;
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_lovetoy;
    }

    @Override
    public DeviceCoordinator.DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceCoordinator.DeviceKind.UNKNOWN;
    }
}
