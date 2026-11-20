/*  Copyright (C) 2026 Jan Petrlík

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
package app.superhealth.engine.devices.moondrop;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettings;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsScreen;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.devices.moondrop.MoondropSpaceTravel2DeviceSupport;

public class MoondropSpaceTravel2Coordinator extends MoondropSpaceTravelCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("Space Travel 2");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_moondrop_space_travel_2;
    }

    @Override
    public DeviceSpecificSettings getDeviceSpecificSettings(final GBDevice device) {
        final DeviceSpecificSettings settings = new DeviceSpecificSettings();

        settings.addRootScreen(R.xml.devicesettings_moondrop_space_travel_2_audio);
        settings.addRootScreen(DeviceSpecificSettingsScreen.TOUCH_OPTIONS);
        settings.addSubScreen(
                DeviceSpecificSettingsScreen.TOUCH_OPTIONS,
                R.xml.devicesettings_moondrop_space_travel_touch);
        settings.addRootScreen(DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS);
        settings.addSubScreen(
                DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
                R.xml.devicesettings_headphones);

        return settings;
    }

    @Override
    public DeviceSpecificSettingsCustomizer getDeviceSpecificSettingsCustomizer(final GBDevice device) {
        return new MoondropSpaceTravel2SettingsCustomizer(device);
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return MoondropSpaceTravel2DeviceSupport.class;
    }
}
