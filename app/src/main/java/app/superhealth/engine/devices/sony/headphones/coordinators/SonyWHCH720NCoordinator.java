/*  Copyright (C) 2025 Daniel Dakhno, José Rebelo

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
package app.superhealth.engine.devices.sony.headphones.coordinators;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.DeviceCoordinator;
import app.superhealth.engine.devices.sony.headphones.SonyHeadphonesCapabilities;
import app.superhealth.engine.devices.sony.headphones.SonyHeadphonesCoordinator;
import app.superhealth.engine.impl.GBDevice;

public class SonyWHCH720NCoordinator extends SonyHeadphonesCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile(".*WH-CH720N.*");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_sony_wh_ch720n;
    }

    @NonNull
    @Override
    public Set<SonyHeadphonesCapabilities> getDefaultCapabilities() {
        return new HashSet<>(Arrays.asList(
                SonyHeadphonesCapabilities.BatterySingle,
                SonyHeadphonesCapabilities.AmbientSoundControl,
                // TODO SonyHeadphonesCapabilities.AmbientSoundControlButtonMode,
                // TODO SonyHeadphonesCapabilities.AutomaticPowerOffByTime,
                SonyHeadphonesCapabilities.EqualizerWithCustomBands,
                SonyHeadphonesCapabilities.PowerOffFromPhone,
                SonyHeadphonesCapabilities.VoiceNotifications
                // TODO SonyHeadphonesCapabilities.Volume
        ));
    }

    @Override
    public DeviceCoordinator.DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.HEADPHONES;
    }
}
