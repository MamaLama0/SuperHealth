/*  Copyright (C) 2022-2024 Daniel Dakhno, José Rebelo

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

public class SonyWH1000XM4Coordinator extends SonyHeadphonesCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile(".*WH-1000XM4.*");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_sony_wh_1000xm4;
    }

    @NonNull
    @Override
    public Set<SonyHeadphonesCapabilities> getDefaultCapabilities() {
        return new HashSet<>(Arrays.asList(
                // TODO: Function of [CUSTOM] button
                // TODO R.xml.devicesettings_connect_two_devices,
                SonyHeadphonesCapabilities.BatterySingle,
                SonyHeadphonesCapabilities.AmbientSoundControl,
                SonyHeadphonesCapabilities.WindNoiseReduction,
                SonyHeadphonesCapabilities.SpeakToChatEnabled,
                SonyHeadphonesCapabilities.SpeakToChatConfig,
                SonyHeadphonesCapabilities.SpeakToChatFocusOnVoice,
                SonyHeadphonesCapabilities.AncOptimizer,
                SonyHeadphonesCapabilities.EqualizerWithCustomBands,
                SonyHeadphonesCapabilities.AudioUpsampling,
                SonyHeadphonesCapabilities.TouchSensorSingle,
                SonyHeadphonesCapabilities.PauseWhenTakenOff,
                SonyHeadphonesCapabilities.AutomaticPowerOffWhenTakenOff,
                SonyHeadphonesCapabilities.VoiceNotifications
        ));
    }

    @Override
    public DeviceCoordinator.DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.HEADPHONES;
    }
}
