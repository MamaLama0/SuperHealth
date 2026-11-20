/*  Copyright (C) 2025  Thomas Kuehne

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

package app.superhealth.engine.devices.ultrahuman;

import android.content.SharedPreferences;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettings;
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator;
import app.superhealth.engine.devices.ComputedHrvSummarySampleProvider;
import app.superhealth.engine.devices.DeviceCardAction;
import app.superhealth.engine.devices.GenericHeartRateSampleProvider;
import app.superhealth.engine.devices.GenericHrvValueSampleProvider;
import app.superhealth.engine.devices.GenericSpo2SampleProvider;
import app.superhealth.engine.devices.GenericStressSampleProvider;
import app.superhealth.engine.devices.GenericTemperatureSampleProvider;
import app.superhealth.engine.devices.SampleProvider;
import app.superhealth.engine.devices.TimeSampleProvider;
import app.superhealth.engine.devices.ultrahuman.samples.UltrahumanActivitySampleProvider;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.ActivitySample;
import app.superhealth.engine.model.HeartRateSample;
import app.superhealth.engine.model.HrvSummarySample;
import app.superhealth.engine.model.HrvValueSample;
import app.superhealth.engine.model.Spo2Sample;
import app.superhealth.engine.model.StressSample;
import app.superhealth.engine.model.TemperatureSample;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.devices.ultrahuman.UltrahumanDeviceSupport;
import app.superhealth.engine.util.GBPrefs;
import app.superhealth.engine.util.preferences.DevicePrefs;

public class UltrahumanDeviceCoordinator extends AbstractBLEDeviceCoordinator {
    @Override
    protected void applyDefaultPreferences(final DevicePrefs devicePreferences, final SharedPreferences.Editor editor) {
        // a low powered BLE gadget with gadget initiated connections
        editor.putBoolean(DeviceSettingsPreferenceConst.PREF_CONNECTION_PRIORITY_LOW_POWER, true);
        editor.putBoolean(GBPrefs.DEVICE_CONNECT_BACK, true);
        editor.putBoolean(GBPrefs.DEVICE_AUTO_RECONNECT, true);

        // the gadget loses it's clock when the battery is low
        editor.putBoolean(DeviceSettingsPreferenceConst.PREF_TIME_SYNC, true);

        // O2 measurement with smart rings is still work in progress
        editor.putBoolean(DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING, false);
    }

    @NonNull
    @Override
    public List<DeviceCardAction> getCustomActions() {
        List<DeviceCardAction> list = new ArrayList<>(2);
        list.add(new UltrahumanDeviceCardAction("ultrahuman_airplane", R.drawable.ic_flight, R.string.ultrahuman_airplane_mode_title, R.string.ultrahuman_airplane_mode_question, UltrahumanConstants.ACTION_AIRPLANE_MODE));
        list.add(new UltrahumanDeviceCardAction("ultrahuman_breathing", R.drawable.ic_pulmonology, R.string.ultrahuman_breathing_title, UltrahumanBreathingActivity.class));
        return list;
    }

    @DrawableRes
    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_smartring;
    }

    @StringRes
    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_ultrahuma_ring_air;
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return UltrahumanDeviceSupport.class;
    }

    @Override
    public DeviceSpecificSettings getDeviceSpecificSettings(GBDevice device) {
        final DeviceSpecificSettings settings = new DeviceSpecificSettings();
        settings.addRootScreen(R.xml.devicesettings_ultrahuman_air);
        return settings;
    }

    @Override
    public String getManufacturer() {
        return "Ultrahuman";
    }

    @Override
    public TimeSampleProvider<? extends HrvValueSample> getHrvValueSampleProvider(GBDevice device, DaoSession session) {
        return new GenericHrvValueSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends HrvSummarySample> getHrvSummarySampleProvider(GBDevice device, DaoSession session) {
        return new ComputedHrvSummarySampleProvider(getHrvValueSampleProvider(device, session), device, session);
    }

    @Override
    public TimeSampleProvider<? extends HeartRateSample> getHeartRateMaxSampleProvider(GBDevice device, DaoSession session) {
        return new GenericHeartRateSampleProvider(device, session);
    }

    @Override
    public SampleProvider<? extends ActivitySample> getSampleProvider(final GBDevice device, final DaoSession session) {
        return new UltrahumanActivitySampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends Spo2Sample> getSpo2SampleProvider(GBDevice device, DaoSession session) {
        return new GenericSpo2SampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends StressSample> getStressSampleProvider(GBDevice device, DaoSession session) {
        return new GenericStressSampleProvider(device, session);
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^UH_[0-9A-F]{12}$");
    }

    @Override
    public TimeSampleProvider<? extends TemperatureSample> getTemperatureSampleProvider(GBDevice device, DaoSession session) {
        return new GenericTemperatureSampleProvider(device, session, TemperatureSample.TYPE_SKIN, TemperatureSample.LOCATION_FINGER);
    }

    @Override
    public boolean isExperimental() {
        return true;
    }

    @Override
    public boolean suggestUnbindBeforePair() {
        return false;
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
    public boolean supportsContinuousTemperature(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsHeartRateMeasurement(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsHrvMeasurement(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsManualHeartRateMeasurement(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsRealtimeData(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsSleepMeasurement(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsSpo2(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsStepCounter(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsTemperatureMeasurement(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsSpeedzones(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsStressMeasurement(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.RING;
    }
}
