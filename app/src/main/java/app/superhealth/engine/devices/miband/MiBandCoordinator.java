/*  Copyright (C) 2015-2024 Andreas Shimokawa, Carsten Pfeiffer, Christian
    Fischer, Damien Gaignon, Daniel Dakhno, Daniele Gobbetti, José Rebelo,
    Petr Vaněk, Szymon Tomasz Stefanek

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
package app.superhealth.engine.devices.miband;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelUuid;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.R;
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettings;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsScreen;
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator;
import app.superhealth.engine.devices.DeviceCoordinator;
import app.superhealth.engine.devices.InstallHandler;
import app.superhealth.engine.devices.SampleProvider;
import app.superhealth.engine.entities.AbstractActivitySample;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.impl.GBDeviceCandidate;
import app.superhealth.engine.model.ActivityUser;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.ServiceDeviceSupport;
import app.superhealth.engine.service.devices.miband.MiBandSupport;
import app.superhealth.engine.util.Prefs;

import static app.superhealth.engine.model.ActivityUser.PREF_USER_NAME;

public class MiBandCoordinator extends AbstractBLEDeviceCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(MiBandCoordinator.class);

    public MiBandCoordinator() {
    }

    @NonNull
    @Override
    public Collection<? extends ScanFilter> createBLEScanFilters() {
        ParcelUuid mi1Service = new ParcelUuid(MiBandService.UUID_SERVICE_MIBAND_SERVICE);
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(mi1Service).build();
        return Collections.singletonList(filter);
    }

    @Override
    public boolean supports(GBDeviceCandidate candidate) {
        String macAddress = candidate.getMacAddress().toUpperCase();
        if (macAddress.startsWith(MiBandService.MAC_ADDRESS_FILTER_1_1A)
                || macAddress.startsWith(MiBandService.MAC_ADDRESS_FILTER_1S)) {
            return true;
        }
        if (candidate.supportsService(MiBandService.UUID_SERVICE_MIBAND_SERVICE)
                && !candidate.supportsService(MiBandService.UUID_SERVICE_MIBAND2_SERVICE)) {
            return true;
        }
        // and a heuristic
        try {
            BluetoothDevice device = candidate.getDevice();
            if (isHealthWearable(device)) {
                String name = candidate.getName();
                if (name != null && name.toUpperCase().startsWith(MiBandConst.MI_GENERAL_NAME_PREFIX.toUpperCase())) {
                    return true;
                }
            }
        } catch (Exception ex) {
            LOG.error("unable to check device support", ex);
        }
        return false;
    }

    @Override
    public Class<? extends Activity> getPairingActivity() {
        return MiBandPairingActivity.class;
    }

    @Override
    public SampleProvider<? extends AbstractActivitySample> getSampleProvider(GBDevice device, DaoSession session) {
        return new MiBandSampleProvider(device, session);
    }

    @Override
    public InstallHandler findInstallHandler(Uri uri, Bundle options, Context context) {
        MiBandFWInstallHandler handler = new MiBandFWInstallHandler(uri, context);
        return handler.isValid() ? handler : null;
    }

    @Override
    public boolean supportsDataFetching(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public int getAlarmSlotCount(GBDevice device) {
        return 3;
    }

    @Override
    public boolean supportsSmartWakeup(GBDevice device, int position) {
        return true;
    }

    @Override
    public boolean supportsActivityTracking(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public String getManufacturer() {
        return "Xiaomi";
    }

    @Override
    public boolean supportsRealtimeData(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsFindDevice(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsAppNotifications(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsCalendarEvents(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsPhoneCalls(@NonNull final GBDevice device) {
        return true;
    }

    /** @noinspection BooleanMethodIsAlwaysInverted*/
    public static boolean hasValidUserInfo() {
        String dummyMacAddress = MiBandService.MAC_ADDRESS_FILTER_1_1A + ":00:00:00";
        try {
            getConfiguredUserInfo(dummyMacAddress);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Returns the configured user info, or, if that is not available or invalid,
     * a default user info.
     *
     */
    public static UserInfo getAnyUserInfo(String miBandAddress) {
        try {
            return getConfiguredUserInfo(miBandAddress);
        } catch (Exception ex) {
            LOG.error("Error creating user info from settings, using default user instead", ex);
            return UserInfo.getDefault(miBandAddress);
        }
    }

    /**
     * Returns the user info from the user configured data in the preferences.
     *
     * @throws IllegalArgumentException when the user info can not be created
     */
    public static UserInfo getConfiguredUserInfo(String miBandAddress) throws IllegalArgumentException {
        ActivityUser activityUser = new ActivityUser();
        Prefs prefs = GBApplication.getPrefs();

        return UserInfo.create(
                miBandAddress,
                prefs.getString(PREF_USER_NAME, null),
                activityUser.getGender(),
                activityUser.getAge(),
                activityUser.getHeightCm(),
                activityUser.getWeightKg(),
                0
        );
    }

    public static int getWearLocation(String deviceAddress) throws IllegalArgumentException {
        int location = 0; //left hand
        Prefs prefs = new Prefs(GBApplication.getDeviceSpecificSharedPrefs(deviceAddress));
        if ("right".equals(prefs.getString(DeviceSettingsPreferenceConst.PREF_WEARLOCATION, "left"))) {
            location = 1; // right hand
        }
        return location;
    }

    public static int getDeviceTimeOffsetHours(String deviceAddress) throws IllegalArgumentException {
        Prefs prefs = new Prefs(GBApplication.getDeviceSpecificSharedPrefs(deviceAddress));
		return prefs.getInt(MiBandConst.PREF_MIBAND_DEVICE_TIME_OFFSET_HOURS, 0);
	}

    public static boolean getHeartrateSleepSupport(String deviceAddress) throws IllegalArgumentException {
        Prefs prefs = new Prefs(GBApplication.getDeviceSpecificSharedPrefs(deviceAddress));
        return prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_HEARTRATE_USE_FOR_SLEEP_DETECTION, false);
    }

    public static int getReservedAlarmSlots(String miBandAddress) throws IllegalArgumentException {
        Prefs prefs = new Prefs(GBApplication.getDeviceSpecificSharedPrefs(miBandAddress));
        return prefs.getInt(DeviceSettingsPreferenceConst.PREF_RESERVER_ALARMS_CALENDAR, 0);
    }

    @Override
    public boolean supportsHeartRateMeasurement(GBDevice device) {
        String hwVersion = device.getModel();
        return isMi1S(hwVersion) || isMiPro(hwVersion);
    }

    @Override
    public DeviceSpecificSettings getDeviceSpecificSettings(final GBDevice device) {
        final DeviceSpecificSettings deviceSpecificSettings = new DeviceSpecificSettings();

        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_wearlocation);
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_heartrate_sleep);
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_miband_vibrationpatterns);
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_fake_timeoffset);

        final List<Integer> calendar = deviceSpecificSettings.addRootScreen(DeviceSpecificSettingsScreen.CALENDAR);
        calendar.add(R.xml.devicesettings_sync_calendar);
        calendar.add(R.xml.devicesettings_reserve_alarms_calendar);

        final List<Integer> connection = deviceSpecificSettings.addRootScreen(DeviceSpecificSettingsScreen.CONNECTION);
        connection.add(R.xml.devicesettings_lowlatency_fwupdate);

        final List<Integer> developer = deviceSpecificSettings.addRootScreen(DeviceSpecificSettingsScreen.DEVELOPER);
        developer.add(R.xml.devicesettings_keep_activity_data_on_device);

        return deviceSpecificSettings;
    }

    @Override
    public DeviceSpecificSettingsCustomizer getDeviceSpecificSettingsCustomizer(final GBDevice device) {
        return new MiBandSettingsCustomizer(device);
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return MiBandSupport.class;
    }

    private boolean isMi1S(String hardwareVersion) {
        return MiBandConst.MI_1S.equals(hardwareVersion);
    }

    private boolean isMiPro(String hardwareVersion) {
        return MiBandConst.MI_PRO.equals(hardwareVersion);
    }

    @Override
    public EnumSet<ServiceDeviceSupport.Flags> getInitialFlags() {
        return EnumSet.of(ServiceDeviceSupport.Flags.THROTTLING, ServiceDeviceSupport.Flags.BUSY_CHECKING);
    }

    @Override
    public int getOrderPriority(){
        // all Coordinators have a priority of 0, thus get checked before this one
        // PLEASE DO NOT EXTEND THE PRIORITIES
        // PLEASE BUILD NEW COORDINATORS ORDER INDEPENDENT
        // this ordering mechanism is a temporary hack and will hopefully be gone soon...
        return 1;
    }

    @Override
    @StringRes
    public int getDeviceNameResource() {
        return R.string.devicetype_miband;
    }

    @Override
    @DrawableRes
    public int getDefaultIconResource() {
        return R.drawable.ic_device_miband;
    }

    @Override
    public DeviceCoordinator.DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceCoordinator.DeviceKind.FITNESS_BAND;
    }
}
