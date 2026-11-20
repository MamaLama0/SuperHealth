/*  Copyright (C) 2026 Davide Gessa

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
package app.superhealth.engine.devices.casio.gbd200;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import app.superhealth.engine.devices.casio.CasioIntervalTimerSettingsCustomizer;
import app.superhealth.engine.devices.casio.gbx100.CasioGBX100DeviceCoordinator;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.ActivitySummaryParser;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.devices.casio.gbd200.CasioGBD200DeviceSupport;
import app.superhealth.engine.util.preferences.DevicePrefs;

public class CasioGBD200DeviceCoordinator extends CasioGBX100DeviceCoordinator {
    @Override
    protected void applyDefaultPreferences(final DevicePrefs devicePreferences, final SharedPreferences.Editor editor) {
        editor.putString(
                DeviceSettingsPreferenceConst.PREFS_DEVICE_CHARTS_TABS,
                "activity,activitylist,stepsweek"
        );
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^CASIO GBD-200.*");
    }

    @Override
    public int getBatteryCount(final GBDevice device) {
        return 0;
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return CasioGBD200DeviceSupport.class;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_casiogbd200;
    }

    @Override
    public boolean supportsRecordedActivities(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public ActivitySummaryParser getActivitySummaryParser(final GBDevice device, final Context context) {
        return (summary, forDetails) -> summary;
    }

    @Override
    public int getWorldClocksSlotCount() {
        // wire carries 2 slots; slot 0 is the home clock, so the generic UI manages 1
        return 1;
    }

    @Override
    public int getWorldClocksLabelLength() {
        return 18;
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(GBDevice device) {
        return new int[]{
                R.xml.devicesettings_find_phone,
                R.xml.devicesettings_wearlocation,
                R.xml.devicesettings_timeformat,
                R.xml.devicesettings_autolight,
                R.xml.devicesettings_key_vibration,
                R.xml.devicesettings_operating_sounds,
                R.xml.devicesettings_fake_ring_duration,
                R.xml.devicesettings_autoremove_message,
                R.xml.devicesettings_transliteration,
                R.xml.devicesettings_preview_message_in_title,
                R.xml.devicesettings_casio_alert,
                R.xml.devicesettings_world_clocks,
                R.xml.devicesettings_casio_interval_timer,
                R.xml.devicesettings_workout_start_on_phone
        };
    }

    @Override
    public DeviceSpecificSettingsCustomizer getDeviceSpecificSettingsCustomizer(final GBDevice device) {
        return new CasioIntervalTimerSettingsCustomizer(device);
    }
}
