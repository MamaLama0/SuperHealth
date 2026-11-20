/*  Copyright (C) 2024-2025 José Rebelo, Thomas Kuehne

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
package app.superhealth.engine.devices.cmfwatchpro;

import android.app.Activity;
import android.bluetooth.le.ScanFilter;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.R;
import app.superhealth.engine.activities.appmanager.AppManagerActivity;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettings;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsScreen;
import app.superhealth.engine.capabilities.HeartRateCapability;
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator;
import app.superhealth.engine.devices.CmfSpo2SampleProvider;
import app.superhealth.engine.devices.CmfStressSampleProvider;
import app.superhealth.engine.devices.InstallHandler;
import app.superhealth.engine.devices.SampleProvider;
import app.superhealth.engine.devices.SleepSessionProvider;
import app.superhealth.engine.devices.TimeSampleProvider;
import app.superhealth.engine.devices.cmfwatchpro.samples.CmfActivitySampleProvider;
import app.superhealth.engine.devices.cmfwatchpro.samples.CmfSleepSessionProvider;
import app.superhealth.engine.devices.cmfwatchpro.workout.CmfActivityTrackProvider;
import app.superhealth.engine.devices.cmfwatchpro.workout.CmfWorkoutSummaryParser;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.ActivitySample;
import app.superhealth.engine.model.ActivitySummaryParser;
import app.superhealth.engine.model.ActivityTrackProvider;
import app.superhealth.engine.model.Spo2Sample;
import app.superhealth.engine.model.StressSample;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.devices.cmfwatchpro.CmfInstallHandler;
import app.superhealth.engine.service.devices.cmfwatchpro.CmfWatchProSupport;
import app.superhealth.engine.util.Prefs;

public class CmfWatchProCoordinator extends AbstractBLEDeviceCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Watch Pro$");
    }

    @NonNull
    @Override
    public Collection<? extends ScanFilter> createBLEScanFilters() {
        final ParcelUuid casioService = new ParcelUuid(CmfWatchProSupport.UUID_SERVICE_CMF_CMD);
        final ScanFilter filter = new ScanFilter.Builder().setServiceUuid(casioService).build();
        return Collections.singletonList(filter);
    }

    @Nullable
    @Override
    public InstallHandler findInstallHandler(final Uri uri, final Bundle options, final Context context) {
        final CmfInstallHandler handler = new CmfInstallHandler(uri, context);
        return handler.isValid() ? handler : null;
    }

    @Override
    public boolean suggestUnbindBeforePair() {
        return false;
    }

    @Override
    public String getManufacturer() {
        return "Nothing";
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_nothing_cmf_watch_pro;
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_amazfit_bip;
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(@NonNull final GBDevice device) {
        return CmfWatchProSupport.class;
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_BOND;
    }

    @Override
    public boolean requiresAuthKey() {
        return true;
    }

    @Override
    public boolean validateAuthKey(final String authKey) {
        final byte[] authKeyBytes = authKey.trim().getBytes();
        return authKeyBytes.length == 32 || (authKey.startsWith("0x") && authKeyBytes.length == 34);
    }

    @Nullable
    @Override
    public String getAuthHelp() {
        return "https://gadgetbridge.org/basics/pairing/nothing-cmf-server/";
    }

    @Override
    public int[] getSupportedDeviceSpecificAuthenticationSettings() {
        return new int[]{R.xml.devicesettings_pairingkey};
    }

    @Override
    public SampleProvider<? extends ActivitySample> getSampleProvider(final GBDevice device, DaoSession session) {
        return new CmfActivitySampleProvider(device, session);
    }

    @NonNull
    @Override
    public SleepSessionProvider getSleepSessionProvider(@NonNull final GBDevice device, @NonNull final DaoSession session) {
        return new CmfSleepSessionProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends StressSample> getStressSampleProvider(final GBDevice device, final DaoSession session) {
        return new CmfStressSampleProvider(device, session);
    }

    @Override
    public int[] getStressRanges() {
        // 1-29 = relaxed
        // 30-59 = normal
        // 60-79 = medium
        // 80-99 = high
        return new int[]{1, 30, 60, 80};
    }

    @Override
    public TimeSampleProvider<? extends Spo2Sample> getSpo2SampleProvider(final GBDevice device, final DaoSession session) {
        return new CmfSpo2SampleProvider(device, session);
    }

    @Nullable
    @Override
    public ActivitySummaryParser getActivitySummaryParser(final GBDevice device, final Context context) {
        return new CmfWorkoutSummaryParser(device, context, 1);
    }

    @Nullable
    @Override
    public ActivityTrackProvider getActivityTrackProvider(@NonNull final GBDevice device, @NonNull final Context context) {
        return new CmfActivityTrackProvider(device);
    }

    @Override
    public boolean supportsFlashing(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public int getAlarmSlotCount(final GBDevice device) {
        return 5;
    }

    @Override
    public boolean supportsAlarmTitle(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public int getAlarmTitleLimit(final GBDevice device) {
        return 8;
    }

    @Override
    public boolean supportsAppsManagement(@NonNull final GBDevice device) {
        return false; // TODO for watchface management
    }

    @Override
    public boolean supportsCachedAppManagement(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsInstalledAppManagement(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsWatchfaceManagement(@NonNull final GBDevice device) {
        return supportsAppsManagement(device);
    }

    @Override
    public Class<? extends Activity> getAppsManagementActivity(final GBDevice device) {
        return AppManagerActivity.class;
    }

    @Override
    public boolean supportsAppListFetching(@NonNull final GBDevice device) {
        return false; // TODO it does not, but we can fake it for watchfaces
    }

    @Override
    public boolean supportsDataFetching(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsActivityTracking(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsRecordedActivities(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsStressMeasurement(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsSpo2(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsMusicInfo(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public int getContactsSlotCount(final GBDevice device) {
        return 20;
    }

    @Override
    public boolean supportsHeartRateMeasurement(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsManualHeartRateMeasurement(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsRemSleep(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsWeather(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsAppNotifications(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsFindDevice(@NonNull final GBDevice device) {
        return true;
    }


    @Override
    public DeviceSpecificSettings getDeviceSpecificSettings(final GBDevice device) {
        final DeviceSpecificSettings deviceSpecificSettings = new DeviceSpecificSettings();

        final List<Integer> dateTime = deviceSpecificSettings.addRootScreen(DeviceSpecificSettingsScreen.DATE_TIME);
        dateTime.add(R.xml.devicesettings_timeformat);

        final List<Integer> display = deviceSpecificSettings.addRootScreen(DeviceSpecificSettingsScreen.DISPLAY);
        display.add(R.xml.devicesettings_cmf_activity_types);
        display.add(R.xml.devicesettings_liftwrist_display_noshed);

        final List<Integer> health = deviceSpecificSettings.addRootScreen(DeviceSpecificSettingsScreen.HEALTH);
        health.add(R.xml.devicesettings_heartrate_sleep_alert_activity_stress_spo2);
        health.add(R.xml.devicesettings_inactivity_dnd);
        health.add(R.xml.devicesettings_hydration_reminder_dnd);

        final List<Integer> notifications = deviceSpecificSettings.addRootScreen(DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS);
        notifications.add(R.xml.devicesettings_send_app_notifications);
        notifications.add(R.xml.devicesettings_bluetooth_calls);
        notifications.add(R.xml.devicesettings_transliteration);

        if (getContactsSlotCount(device) > 0) {
            deviceSpecificSettings.addRootScreen(R.xml.devicesettings_contacts);
        }

        return deviceSpecificSettings;
    }

    @Override
    public DeviceSpecificSettingsCustomizer getDeviceSpecificSettingsCustomizer(final GBDevice device) {
        return new CmfWatchProSettingsCustomizer();
    }

    @Override
    public String[] getSupportedLanguageSettings(final GBDevice device) {
        return null;
        // FIXME language setting does not seem to work from phone
        //return new String[]{
        //        "auto",
        //        "ar_SA",
        //        "de_DE",
        //        "en_US",
        //        "es_ES",
        //        "fr_FR",
        //        "hi_IN",
        //        "id_ID",
        //        "it_IT",
        //        "ja_JP",
        //        "ko_KO",
        //        "zh_CN",
        //        "zh_HK",
        //};
    }

    @Override
    public List<HeartRateCapability.MeasurementInterval> getHeartRateMeasurementIntervals() {
        return Arrays.asList(
                HeartRateCapability.MeasurementInterval.OFF,
                HeartRateCapability.MeasurementInterval.SMART
        );
    }

    protected static Prefs getPrefs(final GBDevice device) {
        return new Prefs(GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()));
    }

    public boolean supportsSunriseSunset() {
        return false;
    }

    @Override
    public DeviceKind getDeviceKind(@NonNull final GBDevice device) {
        return DeviceKind.WATCH;
    }
}
