/*  Copyright (C) 2023-2024 Frank Ertl

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
package app.superhealth.engine.service.devices.withingssteelhr.communication.conversation;

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.GregorianCalendar;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.devices.withingssteelhr.WithingsSteelHRSampleProvider;
import app.superhealth.engine.entities.WithingsSteelHRActivitySample;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.DeviceService;
import app.superhealth.engine.service.devices.withingssteelhr.WithingsSteelHRDeviceSupport;
import app.superhealth.engine.service.devices.withingssteelhr.activity.SleepActivitySampleHelper;
import app.superhealth.engine.service.devices.withingssteelhr.communication.datastructures.HeartRate;
import app.superhealth.engine.service.devices.withingssteelhr.communication.datastructures.LiveHeartRate;
import app.superhealth.engine.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import app.superhealth.engine.service.devices.withingssteelhr.communication.message.Message;

public class HeartRateHandler extends AbstractResponseHandler {
    private static final Logger logger = LoggerFactory.getLogger(HeartRateHandler.class);

    public HeartRateHandler(WithingsSteelHRDeviceSupport support) {
        super(support);
    }

    @Override
    public void handleResponse(Message response) {
        if (response.getDataStructures().size() > 0) {
            handleHeartRateData(response.getDataStructures().get(0));
        }
    }

    private void handleHeartRateData(WithingsStructure structure) {
        int heartRate = 0;
        if (structure instanceof HeartRate) {
            heartRate = ((HeartRate)structure).getHeartrate();
        } else if (structure instanceof LiveHeartRate) {
            heartRate = ((LiveHeartRate)structure).getHeartrate();
        }

        if (heartRate > 0) {
            saveHeartRateData(heartRate);
        }
    }

    private void saveHeartRateData(int heartRate) {
        WithingsSteelHRActivitySample sample = new WithingsSteelHRActivitySample();
        sample.setTimestamp((int) (GregorianCalendar.getInstance().getTimeInMillis() / 1000L));
        sample.setHeartRate(heartRate);
        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            Long userId = DBHelper.getUser(dbHandler.getDaoSession()).getId();
            Long deviceId = DBHelper.getDevice(device, dbHandler.getDaoSession()).getId();
            WithingsSteelHRSampleProvider provider = new WithingsSteelHRSampleProvider(device, dbHandler.getDaoSession());
            sample.setDeviceId(deviceId);
            sample.setUserId(userId);
            sample = SleepActivitySampleHelper.mergeIfNecessary(provider, sample);
            provider.addGBActivitySample(sample);
            Intent intent = new Intent(DeviceService.ACTION_REALTIME_SAMPLES)
                    .putExtra(GBDevice.EXTRA_DEVICE, support.getDevice())
                    .putExtra(DeviceService.EXTRA_REALTIME_SAMPLE, sample);
            LocalBroadcastManager.getInstance(support.getContext()).sendBroadcast(intent);
        } catch (Exception ex) {
            logger.warn("Error saving current heart rate: " + ex.getLocalizedMessage());
        }
    }
}
