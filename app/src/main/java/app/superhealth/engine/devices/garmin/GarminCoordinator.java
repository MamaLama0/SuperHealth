/*  Copyright (C) 2024-2026 José Rebelo, Daniele Gobbetti, kuhy, Martin.JM, a0z, k4z4n0v4, ad0z,
        Thomas Kuehne

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
package app.superhealth.engine.devices.garmin;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.R;
import app.superhealth.engine.activities.appmanager.AppManagerActivity;
import app.superhealth.engine.activities.appmanager.config.DynamicAppConfigActivity;
import app.superhealth.engine.activities.charts.DeviceChartsProvider;
import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import app.superhealth.engine.activities.devicesettings.dsl.DeviceSettingsSpec;
import app.superhealth.engine.capabilities.loyaltycards.BarcodeFormat;
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator;
import app.superhealth.engine.devices.GarminBodyEnergySampleProvider;
import app.superhealth.engine.devices.GarminSolarChargeSampleProvider;
import app.superhealth.engine.devices.GarminHeartRateRestingSampleProvider;
import app.superhealth.engine.devices.GarminHrvSummarySampleProvider;
import app.superhealth.engine.devices.GarminHrvValueSampleProvider;
import app.superhealth.engine.devices.GarminRespiratoryRateSampleProvider;
import app.superhealth.engine.devices.GarminRestingMetabolicRateSampleProvider;
import app.superhealth.engine.devices.GarminSleepStatsSampleProvider;
import app.superhealth.engine.devices.GarminSpo2SampleProvider;
import app.superhealth.engine.devices.GarminStressSampleProvider;
import app.superhealth.engine.devices.GenericTrainingLoadAcuteSampleProvider;
import app.superhealth.engine.devices.GenericTrainingLoadChronicSampleProvider;
import app.superhealth.engine.devices.HydrationSampleProvider;
import app.superhealth.engine.devices.InstallHandler;
import app.superhealth.engine.devices.SampleProvider;
import app.superhealth.engine.devices.SleepSessionProvider;
import app.superhealth.engine.devices.TimeSampleProvider;
import app.superhealth.engine.devices.WorkoutLoadSampleProvider;
import app.superhealth.engine.devices.cards.DeviceCardItem;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.GenericTrainingLoadAcuteSample;
import app.superhealth.engine.entities.GenericTrainingLoadChronicSample;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.impl.GBDeviceCandidate;
import app.superhealth.engine.install.InstallHandlerV2;
import app.superhealth.engine.model.ActivitySample;
import app.superhealth.engine.model.ActivitySummaryParser;
import app.superhealth.engine.model.ActivityTrackProvider;
import app.superhealth.engine.model.workouts.spec.WorkoutTemplateSpec;
import app.superhealth.engine.model.BodyEnergySample;
import app.superhealth.engine.model.SolarChargeSample;
import app.superhealth.engine.model.DeviceType;
import app.superhealth.engine.model.FitActivityTrackProvider;
import app.superhealth.engine.model.GpxActivityTrackProvider;
import app.superhealth.engine.model.HrvSummarySample;
import app.superhealth.engine.model.HrvValueSample;
import app.superhealth.engine.model.PaiSample;
import app.superhealth.engine.model.RespiratoryRateSample;
import app.superhealth.engine.model.RestingMetabolicRateSample;
import app.superhealth.engine.model.SleepScoreSample;
import app.superhealth.engine.model.Spo2Sample;
import app.superhealth.engine.model.StressSample;
import app.superhealth.engine.model.WorkoutLoadSample;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.btle.BluetoothCompanyIdentifiers;
import app.superhealth.engine.service.devices.garmin.GarminPrgFile;
import app.superhealth.engine.service.devices.garmin.GarminSupport;
import app.superhealth.engine.service.devices.garmin.watchapps.GarminLoyaltyCardsApp;
import app.superhealth.engine.util.Prefs;
import app.superhealth.engine.util.preferences.DevicePrefs;
import app.superhealth.engine.vendorimport.VendorDataImporter;
import app.superhealth.engine.widgets.DeviceWidgetsProvider;

public abstract class GarminCoordinator extends AbstractBLEDeviceCoordinator {
    @Override
    public boolean supports(@NonNull final GBDeviceCandidate candidate) {
        final SparseArray<byte[]> manufacturerSpecificData = candidate.getManufacturerSpecificData();
        final byte[] garminData = manufacturerSpecificData.get(BluetoothCompanyIdentifiers.GARMIN_INTERNATIONAL_INC);
        if (garminData != null && garminData.length >= 2) {
            final int partNumber = ((garminData[0] & 0xFF) << 8) | (garminData[1] & 0xFF);
            final DeviceType deviceType = GarminProductNumbers.getDeviceType(partNumber);
            return deviceType != null && deviceType.getCoordinatorClass() == this.getClass();
        }

        // Fallback to device name
        return super.supports(candidate);
    }

    @Override
    public boolean suggestUnbindBeforePair() {
        return false;
    }

    @Override
    protected void applyDefaultPreferences(final DevicePrefs devicePreferences, final SharedPreferences.Editor editor) {
        if (defaultNewSyncProtocol()) {
            // #5021 - Some new devices like Venu X1 misses a lot of files without the new sync protocol
            // TODO: check presence of GarminCapability.SYNC_2 instead
            editor.putBoolean(DeviceSettingsPreferenceConst.PREF_NEW_SYNC_PROTOCOL, true);
            // new sync protocol without MLR fails to sync large workouts
            // TODO: check DeviceInformationMessage.protocolVersion >= 150 instead
            editor.putBoolean(GarminPreferences.PREF_GARMIN_MLR, true);

            editor.apply();
        }

        if (defaultExploreSync()) {
            editor.putBoolean(GarminPreferences.PREF_GARMIN_EXPLORE_SYNC, true);
        }
    }

    public boolean defaultNewSyncProtocol() {
        return false;
    }

    public boolean defaultExploreSync() {
        return true;
    }

    @Override
    public String getManufacturer() {
        return "Garmin";
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(@NonNull final GBDevice device) {
        return GarminSupport.class;
    }

    @Override
    public DeviceChartsProvider getChartsProvider() {
        return new GarminChartsProvider();
    }

    @Override
    public DeviceWidgetsProvider getWidgetsProvider() {
        return GarminWidgetsProvider.INSTANCE;
    }

    @Nullable
    @Override
    public ActivitySummaryParser getActivitySummaryParser(final GBDevice device, final Context context) {
        return new GarminWorkoutParser(context, device);
    }

    @Override
    @Nullable
    public ActivityTrackProvider getActivityTrackProvider(@NonNull final GBDevice device, @NonNull final Context context) {
        // Pick the richer source when both are present:
        //  - FIT (rawDetailsPath) carries detailed sensor streams.
        //  - GPX (gpxTrack) is the polyline-only fallback that
        //    ExploreSync writes when no FIT has arrived yet.
        // If a later FIT delivery dedup-links onto an existing
        // ExploreSync row, both fields will be set; we should return
        // the FIT view rather than hide its richer data behind GPX.
        return summary -> {
            final ActivityTrackProvider provider = summary.getRawDetailsPath() != null
                    ? new FitActivityTrackProvider()
                    : new GpxActivityTrackProvider();

            return provider.getActivityTrack(summary);
        };
    }

    @Override
    public SampleProvider<? extends ActivitySample> getSampleProvider(final GBDevice device, DaoSession session) {
        return new GarminActivitySampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends StressSample> getStressSampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminStressSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends BodyEnergySample> getBodyEnergySampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminBodyEnergySampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends SolarChargeSample> getSolarChargeSampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminSolarChargeSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends HrvSummarySample> getHrvSummarySampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminHrvSummarySampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends HrvValueSample> getHrvValueSampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminHrvValueSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends WorkoutLoadSample> getWorkoutLoadSampleProvider(final GBDevice device, final DaoSession session) {
        return new WorkoutLoadSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends GenericTrainingLoadAcuteSample> getTrainingAcuteLoadSampleProvider(final GBDevice device, final DaoSession session) {
        return new GenericTrainingLoadAcuteSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends GenericTrainingLoadChronicSample> getTrainingChronicLoadSampleProvider(final GBDevice device, final DaoSession session) {
        return new GenericTrainingLoadChronicSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends Spo2Sample> getSpo2SampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminSpo2SampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends PaiSample> getPaiSampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminPaiSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends RespiratoryRateSample> getRespiratoryRateSampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminRespiratoryRateSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends RestingMetabolicRateSample> getRestingMetabolicRateProvider(final GBDevice device, final DaoSession session) {
        return new GarminRestingMetabolicRateSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends SleepScoreSample> getSleepScoreProvider(final GBDevice device, final DaoSession session) {
        return new GarminSleepStatsSampleProvider(device, session);
    }

    @NonNull
    @Override
    public SleepSessionProvider getSleepSessionProvider(@NonNull final GBDevice device, @NonNull final DaoSession session) {
        return new GarminSleepSessionProvider(device, session);
    }

    @Override
    public HydrationSampleProvider getHydrationSampleProvider(final GBDevice device, final DaoSession session) {
        return new HydrationSampleProvider(device, session);
    }

    @Override
    public VendorDataImporter getVendorDataImporter(@NonNull final GBDevice device) {
        return new GarminVendorDataImporter();
    }

    @Override
    public GarminHeartRateRestingSampleProvider getHeartRateRestingSampleProvider(final GBDevice device, final DaoSession session) {
        return new GarminHeartRateRestingSampleProvider(device, session);
    }

    @Override
    public int[] getSupportedDeviceSpecificAuthenticationSettings() {
        return new int[]{R.xml.devicesettings_garmin_fake_oauth};
    }

    @Override
    public int[] getSupportedDeviceSpecificExperimentalSettings(final GBDevice device) {
        return new int[]{R.xml.devicesettings_garmin_experimental};
    }

    @Override
    public int[] getSupportedDebugSettings(final GBDevice device) {
        return ArrayUtils.add(
                super.getSupportedDebugSettings(device),
                R.xml.devicesettings_debug_drop_packets
        );
    }

    @Nullable
    @Override
    public WorkoutTemplateSpec getWorkoutTemplateSpec(@NonNull final GBDevice device) {
        return GarminWorkoutTemplateSpec.INSTANCE.build(this, device);
    }

    @Override
    public DeviceSettingsSpec getDeviceSettings(@NonNull final GBDevice device) {
        return GarminDeviceSettingsKt.garminDeviceSettings(device, this);
    }

    @Override
    public List<DeviceCardItem> getCardItems(@NonNull final GBDevice device) {
        return GarminDeviceCardItemsKt.garminDeviceCardItems(device, this);
    }

    @Override
    public DeviceSpecificSettingsCustomizer getDeviceSpecificSettingsCustomizer(GBDevice device) {
        return new GarminSettingsCustomizer();
    }

    @Override
    public int getCannedRepliesSlotCount(final GBDevice device) {
        if (getPrefs(device).getBoolean(GarminPreferences.PREF_FEAT_CANNED_MESSAGES, false)) {
            return 16;
        }

        return 0;
    }

    @Override
    public int getContactsSlotCount(final GBDevice device) {
        if (getPrefs(device).getBoolean(GarminPreferences.PREF_FEAT_CONTACTS, false)) {
            return 50;
        }

        return 0;
    }

    protected static Prefs getPrefs(final GBDevice device) {
        return new Prefs(GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()));
    }

    @Override
    public boolean supportsUnicodeEmojis(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsAppNotifications(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsPhoneCalls(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsCallControl(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsInternetAccess(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsSmsReply(@NonNull final GBDevice device) {
        return getCannedRepliesSlotCount(device) > 0;
    }

    @Override
    public boolean supportsGpsSharing(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsNavigation(@NonNull final GBDevice device) {
        return supportsAppNotifications(device);
    }

    @Override
    public Set<BarcodeFormat> getSupportedBarcodeFormats(@NonNull final GBDevice device) {
        if (isAppInstalled(device, GarminLoyaltyCardsApp.APP_UUID)) {
            return EnumSet.allOf(BarcodeFormat.class);
        }
        return Collections.emptySet();
    }

    @Override
    public boolean supportsLoyaltyCardsSyncRequest(@NonNull final GBDevice device) {
        return isAppInstalled(device, GarminLoyaltyCardsApp.APP_UUID);
    }

    @Override
    public boolean supportsWatchAppMessages(@NonNull final GBDevice device) {
        return supports(device, GarminCapability.CONNECTIQ_WATCH_APP_DOWNLOAD);
    }

    @Override
    public boolean supportsAppsManagement(@NonNull final GBDevice device) {
        // FIXME: experimental until better polished
        return experimentalSettingEnabled(device, "garmin_experimental_app_management");
    }

    @Override
    public boolean supportsCachedAppManagement(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public Class<? extends Activity> getAppsManagementActivity(final GBDevice device) {
        return AppManagerActivity.class;
    }

    @Nullable
    @Override
    public Class<? extends Activity> getAppConfigurationActivity(final GBDevice device) {
        return DynamicAppConfigActivity.class;
    }

    @Override
    public boolean supportsAppListFetching(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsHydration(@NonNull final GBDevice device) {
        return getPrefs(device).getBoolean(GarminPreferences.PREF_GARMIN_HYDRATION_SUPPORTED, false);
    }

    public boolean supportsAgpsUpdates(final GBDevice device) {
        return !getPrefs(device).getString(GarminPreferences.PREF_AGPS_KNOWN_URLS, "").isEmpty();
    }

    public boolean supportsSendWaypoint(@NonNull final GBDevice device) {
        return supports(device, GarminCapability.WAYPOINT_TRANSFER)
            || supports(device, GarminCapability.EXPLORE_SYNC)
            || GBApplication.getDevicePrefs(device).installUnsupportedFiles();
    }

    public boolean isAppInstalled(final GBDevice device, final UUID app) {
        return getPrefs(device).getStringSet(GarminPreferences.PREF_GARMIN_INSTALLED_APPS, Collections.emptySet())
            .contains(app.toString());
    }

    public boolean supports(final GBDevice device, final GarminCapability capability) {
        return getPrefs(device).getStringSet(GarminPreferences.PREF_GARMIN_CAPABILITIES, Collections.emptySet())
            .contains(capability.name());
    }

    @Override
    public boolean supportsFlashing(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsMusicInfo(@NonNull GBDevice device) {
        return supports(device, GarminCapability.BASIC_MUSIC_CONTROLS)
            || supports(device, GarminCapability.ADVANCED_MUSIC_CONTROLS);
    }

    @Override
    public boolean supportsFindDevice(@NonNull GBDevice device) {
        return supports(device, GarminCapability.FIND_MY_WATCH);
    }

    @Nullable
    @Override
    public InstallHandler findInstallHandler(Uri uri, Bundle options, Context context) {
        final GarminFitFileInstallHandler fitFileInstallHandler = new GarminFitFileInstallHandler(uri, options, context);
        if (fitFileInstallHandler.isValid())
            return fitFileInstallHandler;

        final GarminGpxRouteInstallHandler garminGpxRouteInstallHandler = new GarminGpxRouteInstallHandler(uri, context);
        if (garminGpxRouteInstallHandler.isValid())
            return garminGpxRouteInstallHandler;

        return null;
    }

    @Nullable
    @Override
    public InstallHandlerV2 findInstallHandlerV2(@NonNull final List<Uri> files, @NonNull final Context context) {
        for (final Uri file : files) {
            if (GarminPrgFile.open(context, file) != null) {
                return new GarminPrgInstallHandler(context, this);
            }
        }
        return null;
    }

    @Override
    public boolean supportsTrainingLoad(@NonNull final GBDevice device) {
        // Not all devices support it
        return true;
    }

}
