/*  Copyright (C) 2025 Thomas Kuehne

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
package app.superhealth.engine.service.devices.garmin;

import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import app.superhealth.engine.BuildConfig;
import app.superhealth.engine.GBApplication;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.deviceevents.GBDeviceEventBatteryInfo;
import app.superhealth.engine.devices.garmin.GarminActivitySampleProvider;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.entities.Device;
import app.superhealth.engine.entities.GarminActivitySample;
import app.superhealth.engine.entities.User;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.ActivityKind;
import app.superhealth.engine.model.ActivitySample;
import app.superhealth.engine.model.BatteryState;
import app.superhealth.engine.model.DeviceService;
import app.superhealth.engine.service.btle.GattCharacteristic;
import app.superhealth.engine.service.btle.GattService;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.btle.profiles.IntentListener;
import app.superhealth.engine.service.btle.profiles.battery.BatteryInfo;
import app.superhealth.engine.service.btle.profiles.battery.BatteryInfoProfile;
import app.superhealth.engine.service.btle.profiles.heartrate.HeartRate;
import app.superhealth.engine.service.btle.profiles.heartrate.HeartRateProfile;
import app.superhealth.engine.util.GB;

public class GarminSupportHrm extends GarminSupport {
    private final BatteryInfoProfile<GarminSupportHrm> batteryInfoProfile;
    private final HeartRateProfile<GarminSupportHrm> heartRateProfile;

    private boolean newSamples = false;

    public GarminSupportHrm() {
        addSupportedService(BatteryInfoProfile.SERVICE_UUID);
        batteryInfoProfile = new BatteryInfoProfile<>(this);
        batteryInfoProfile.addListener(new BatteryListener());
        addSupportedProfile(batteryInfoProfile);

        addSupportedService(GattService.SERVICE_HEART_RATE.uuid);
        heartRateProfile = new HeartRateProfile<>(this);
        heartRateProfile.addListener(new HeartRateListener());
        addSupportedProfile(heartRateProfile);
    }

    @NonNull
    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        super.initializeDevice(builder);

        if (null != getCharacteristic(BatteryInfoProfile.UUID_CHARACTERISTIC_BATTERY_LEVEL)) {
            batteryInfoProfile.requestBatteryInfo(builder);
            batteryInfoProfile.enableNotify(builder, true);

        }

        if (null != getCharacteristic(GattCharacteristic.CHARACTERISTIC_HEART_RATE_MEASUREMENT.uuid)) {
            heartRateProfile.enableNotify(builder, true);
        }

        return builder;
    }

    @CallSuper
    @Override
    public void disconnect() {
        if (newSamples) {
            // Since we always receive samples in realtime, signal that there are new samples when we disconnect
            GB.signalActivityDataFinish(getDevice());
            newSamples = false;
        }

        super.disconnect();
    }

    @CallSuper
    @Override
    public void dispose() {
        synchronized (ConnectionMonitor) {
            if (newSamples) {
                // Since we always receive samples in realtime, signal that there are new samples when we disconnect
                GB.signalActivityDataFinish(getDevice());
                newSamples = false;
            }

            super.dispose();
        }
    }

    final class BatteryListener implements IntentListener {
        @Override
        public void notify(Intent intent) {
            BatteryInfo info = intent.getParcelableExtra(BatteryInfoProfile.EXTRA_BATTERY_INFO);
            if (info != null) {
                GBDeviceEventBatteryInfo event = new GBDeviceEventBatteryInfo();
                event.state = BatteryState.BATTERY_NORMAL;
                event.level = info.getPercentCharged();
                handleGBDeviceEvent(event);
            }
        }
    }

    @Override
    public void onHeartRateTest() {
        // noop - device publishes HR ca every second
    }

    @Override
    public void onEnableRealtimeHeartRateMeasurement(final boolean enable) {
        // noop - HR is always enabled
    }

    final class HeartRateListener implements IntentListener {
        @Override
        public void notify(Intent intent) {
            final HeartRate heartRate = intent.getParcelableExtra(HeartRateProfile.EXTRA_HEART_RATE);
            if (heartRate != null && heartRate.isValid()) {
                final GarminActivitySample sample;
                try (DBHandler handler = GBApplication.acquireDB()) {
                    final DaoSession session = handler.getDaoSession();
                    final GarminActivitySampleProvider provider = new GarminActivitySampleProvider(getDevice(), session);

                    final Device device = DBHelper.getDevice(gbDevice, session);
                    final User user = DBHelper.getUser(session);
                    sample = provider.createActivitySample();

                    sample.setActiveCalories(ActivitySample.NOT_MEASURED);
                    sample.setDevice(device);
                    sample.setDistanceCm(ActivitySample.NOT_MEASURED);
                    sample.setHeartRate(heartRate.getHeartRate());
                    sample.setRawIntensity(ActivitySample.NOT_MEASURED);
                    sample.setRawKind(ActivityKind.UNKNOWN.getCode());
                    sample.setSteps(ActivitySample.NOT_MEASURED);
                    sample.setTimestamp((int) (System.currentTimeMillis() / 1000));
                    sample.setUser(user);

                    provider.addGBActivitySample(sample);
                } catch (Exception e) {
                    GB.toast(getContext(), "Error saving activity samples", Toast.LENGTH_LONG, GB.ERROR, e);
                    return;
                }

                publish(sample);

                newSamples = true;
            }
        }

        private void publish(final GarminActivitySample sample) {
            final Intent intent = new Intent(DeviceService.ACTION_REALTIME_SAMPLES);
            intent.setPackage(BuildConfig.APPLICATION_ID);
            intent.putExtra(GBDevice.EXTRA_DEVICE, getDevice());
            intent.putExtra(DeviceService.EXTRA_REALTIME_SAMPLE, sample);
            LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
        }
    }
}
