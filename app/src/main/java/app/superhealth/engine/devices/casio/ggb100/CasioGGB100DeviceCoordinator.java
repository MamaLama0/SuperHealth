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
package app.superhealth.engine.devices.casio.ggb100;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.bluetooth.BluetoothDevice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Collections;

import app.superhealth.engine.R;
import app.superhealth.engine.activities.casio.CasioLocationIndicatorActivity;
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import app.superhealth.engine.activities.workouts.entries.ActivitySummaryTableRowEntry;
import app.superhealth.engine.activities.workouts.charts.DefaultWorkoutCharts;
import app.superhealth.engine.devices.DeviceCardAction;
import app.superhealth.engine.devices.GenericAltitudeSampleProvider;
import app.superhealth.engine.devices.SampleProvider;
import app.superhealth.engine.devices.TimeSampleProvider;
import app.superhealth.engine.devices.cards.ActionCardItem;
import app.superhealth.engine.devices.cards.DeviceCardItem;
import app.superhealth.engine.devices.cards.MoreCardItem;
import app.superhealth.engine.devices.casio.Casio2C2DDeviceCoordinator;
import app.superhealth.engine.devices.casio.CasioLocationIndicatorSettingsCustomizer;
import app.superhealth.engine.devices.casio.gbx100.CasioGBX100SampleProvider;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.BaseActivitySummary;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.ActivitySample;
import app.superhealth.engine.model.ActivitySummaryParser;
import app.superhealth.engine.model.ActivitySummaryData;
import app.superhealth.engine.model.AltitudeSample;
import app.superhealth.engine.model.ActivityPoint;
import app.superhealth.engine.model.ActivityKind;
import app.superhealth.engine.model.workout.Workout;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.devices.casio.ggb100.CasioGGB100DeviceSupport;
import app.superhealth.engine.util.preferences.DevicePrefs;

public class CasioGGB100DeviceCoordinator extends Casio2C2DDeviceCoordinator {
    protected static final Logger LOG = LoggerFactory.getLogger(CasioGGB100DeviceCoordinator.class);

    @Override
    protected void applyDefaultPreferences(final DevicePrefs devicePreferences, final SharedPreferences.Editor editor) {
        editor.putString(DeviceSettingsPreferenceConst.PREFS_DEVICE_CHARTS_TABS, "overview,activity,activitylist,stepsweek,altitude");
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^CASIO GG-B100.*");
    }

    @Override
    public int getBlePhyMask() {
        return BluetoothDevice.PHY_LE_1M_MASK;
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_LAZY;
    }

    @Override
    public boolean supportsAlarmSnoozing(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public Class<? extends Activity> getPairingActivity() {
        return null;
    }

    @Override
    public boolean supportsDataFetching(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsActivityTracking(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsRecordedActivities(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsSleepMeasurement(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsSpeedzones(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsPointAltitudeMeasurement(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public TimeSampleProvider<? extends AltitudeSample> getAltitudeSampleProvider(final GBDevice device, final DaoSession session) {
        return new GenericAltitudeSampleProvider(device, session);
    }

    @Override
    public SampleProvider<? extends ActivitySample> getSampleProvider(GBDevice device, DaoSession session) {
        return new CasioGBX100SampleProvider(device, session);
    }

    @Override
    public ActivitySummaryParser getActivitySummaryParser(final GBDevice device, final Context context) {
        return new ActivitySummaryParser() {
            @Override
            public BaseActivitySummary parseBinaryData(final BaseActivitySummary summary, final boolean forDetails) {
                return summary;
            }

            @Override
            public Workout parseWorkout(final BaseActivitySummary summary, final boolean forDetails) {
                final ActivitySummaryData data = ActivitySummaryData.fromJson(summary.getSummaryData());
                if (!forDetails) {
                    return new Workout(summary, data, Collections.emptyList());
                }

                // Create the elevation chart from watch elevation data
                final long baseTs = summary.getStartTime().getTime();
                final List<ActivityPoint> points = new ArrayList<>();
                for (int i = 1; data.has("sample_" + i); i++) {
                    if (data.get("sample_" + i) instanceof ActivitySummaryTableRowEntry row
                        && row.getColumns().size() >= 3 && row.getColumns().get(1).value() instanceof Number minutes
                        && row.getColumns().get(2).value() instanceof Number alt) {
                        final ActivityPoint point = new ActivityPoint(
                            new Date(baseTs + (long) (minutes.doubleValue() * 60_000)));
                        point.setAltitude(alt.doubleValue());
                        points.add(point);
                    }
                }
                return new Workout(summary, data, DefaultWorkoutCharts.buildDefaultCharts(context, points,
                    ActivityKind.fromCode(summary.getActivityKind()), data, device));
            }
        };
    }

    @Override
    public int getAlarmSlotCount(GBDevice device) {
        return 5;
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(GBDevice device) {
        return new int[]{
            R.xml.devicesettings_find_phone,
            R.xml.devicesettings_wearlocation,
            R.xml.devicesettings_timeformat,
            R.xml.devicesettings_autolight,
            R.xml.devicesettings_operating_sounds,
            R.xml.devicesettings_light_duration_longer,
            R.xml.devicesettings_casio_ggb100_basic_settings,
            R.xml.devicesettings_casio_ggb100_sensor_display,
            R.xml.devicesettings_casio_ggb100_mode_display,
            R.xml.devicesettings_hourly_chime_enable,
            R.xml.devicesettings_time_sync,
            R.xml.devicesettings_casio_ggb100_connection_duration,
            R.xml.devicesettings_casio_location_indicator,
            R.xml.devicesettings_casio_ggb100_countdown_timer,
            R.xml.devicesettings_workout_start_on_phone
        };
    }

    @Override
    public int getBatteryCount(final GBDevice device) {
        return 0;
    }

    @Override
    public List<DeviceCardItem> getCardItems(@NonNull final GBDevice device) {
        final List<DeviceCardItem> items = new ArrayList<>(super.getCardItems(device));
        final ActionCardItem locationIndicator = new ActionCardItem(
                DeviceCardAction.forActivity(
                        R.drawable.ic_gps_location,
                        R.string.pref_casio_location_indicator_title,
                        CasioLocationIndicatorActivity.class),
                false,
                "casio_location_indicator",
                d -> true,
                false);
        final int moreIndex = items.indexOf(MoreCardItem.INSTANCE);
        items.add(moreIndex >= 0 ? moreIndex : items.size(), locationIndicator);
        return items;
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return CasioGGB100DeviceSupport.class;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_casioggb100;
    }

    @Override
    public DeviceSpecificSettingsCustomizer getDeviceSpecificSettingsCustomizer(final GBDevice device) {
        return new CasioLocationIndicatorSettingsCustomizer(device);
    }
}
