/*  Copyright (C) 2023-2024 akasaka / Genjitsu Labs

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
package app.superhealth.engine.service.devices.sony.wena3.protocol.logic.parsers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.devices.Wena3HeartRateSampleProvider;
import app.superhealth.engine.devices.sony.wena3.SonyWena3ActivitySampleCombiner;
import app.superhealth.engine.devices.sony.wena3.SonyWena3ActivitySampleProvider;
import app.superhealth.engine.entities.Wena3HeartRateSample;
import app.superhealth.engine.impl.GBDevice;

public class HeartRatePacketParser extends OneBytePerSamplePacketParser {
    private static final Logger LOG = LoggerFactory.getLogger(HeartRatePacketParser.class);
    private static final int HEART_PKT_MARKER = 0x01;
    public HeartRatePacketParser() {
        super(HEART_PKT_MARKER, ONE_MINUTE_IN_MS);
    }

    @Override
    public void finishReceiving(GBDevice device) {
        try (DBHandler db = GBApplication.acquireDB()) {
            Wena3HeartRateSampleProvider sampleProvider = new Wena3HeartRateSampleProvider(device, db.getDaoSession());
            Long userId = DBHelper.getUser(db.getDaoSession()).getId();
            Long deviceId = DBHelper.getDevice(device, db.getDaoSession()).getId();
            List<Wena3HeartRateSample> samples = new ArrayList<>();

            Date currentSampleDate = startDate;
            int i = 0;
            for(int rawSample: accumulator) {
                Wena3HeartRateSample gbSample = new Wena3HeartRateSample();
                gbSample.setDeviceId(deviceId);
                gbSample.setUserId(userId);
                gbSample.setTimestamp(currentSampleDate.getTime());
                gbSample.setHeartRate(rawSample);
                samples.add(gbSample);

                i++;
                currentSampleDate = timestampOfSampleAtIndex(i);
            }

            sampleProvider.addSamples(samples);

            if(!accumulator.isEmpty()) {
                SonyWena3ActivitySampleProvider activitySampleProvider = new SonyWena3ActivitySampleProvider(device, db.getDaoSession());
                SonyWena3ActivitySampleCombiner combiner = new SonyWena3ActivitySampleCombiner(activitySampleProvider);
                combiner.overlayHeartRateStartingAt(startDate, sampleProvider);
            }
        } catch (Exception e) {
            LOG.error("Error acquiring database for recording heart rate samples", e);
        }

        // Finally clean up the parser
        super.finishReceiving(device);
    }
}
