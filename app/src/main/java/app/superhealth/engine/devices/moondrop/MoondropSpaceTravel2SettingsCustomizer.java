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

import android.os.Parcel;

import androidx.preference.Preference;

import java.util.Collections;
import java.util.Set;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsHandler;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.util.Prefs;

import static app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_MOONDROP_ANC_MODE;

public class MoondropSpaceTravel2SettingsCustomizer implements DeviceSpecificSettingsCustomizer {
    private final GBDevice device;

    public MoondropSpaceTravel2SettingsCustomizer(final GBDevice device) {
        this.device = device;
    }

    @Override
    public void onPreferenceChange(final Preference preference, final DeviceSpecificSettingsHandler handler) {

    }

    @Override
    public void customizeSettings(final DeviceSpecificSettingsHandler handler, final Prefs prefs,
            final String rootKey) {
        if (rootKey == null) {
            // The device does not emit notifications when the user changes ANC mode via
            // touch, so refresh the current mode whenever the user opens the settings screen.
            GBApplication.deviceService(device).onReadConfiguration(PREF_MOONDROP_ANC_MODE);
        }
    }

    @Override
    public Set<String> getPreferenceKeysWithSummary() {
        return Collections.emptySet();
    }

    public static final Creator<MoondropSpaceTravel2SettingsCustomizer> CREATOR = new Creator<MoondropSpaceTravel2SettingsCustomizer>() {
        @Override
        public MoondropSpaceTravel2SettingsCustomizer createFromParcel(final Parcel in) {
            final GBDevice device = in.readParcelable(MoondropSpaceTravel2SettingsCustomizer.class.getClassLoader());
            return new MoondropSpaceTravel2SettingsCustomizer(device);
        }

        @Override
        public MoondropSpaceTravel2SettingsCustomizer[] newArray(final int size) {
            return new MoondropSpaceTravel2SettingsCustomizer[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeParcelable(device, 0);
    }
}
