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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.database.DBHandler;
import app.superhealth.engine.database.DBHelper;
import app.superhealth.engine.devices.sony.wena3.SonyWena3StressSampleProvider;
import app.superhealth.engine.entities.Wena3StressSample;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.StressSample;

public class StressPacketParser extends OneBytePerSamplePacketParser {
    private static final Logger LOG = LoggerFactory.getLogger(StressPacketParser.class);
    private static final int STRESS_PKT_MARKER = 0x04;
    public StressPacketParser() {
        super(STRESS_PKT_MARKER, ONE_MINUTE_IN_MS);
    }
    @Override
    Integer takeSampleFromBuffer(ByteBuffer buffer) {
        // In this one we actually need signed values, hence the override
        return Integer.valueOf(buffer.get());
    }
    @Override
    public void finishReceiving(GBDevice device) {
        try (DBHandler db = GBApplication.acquireDB()) {
            SonyWena3StressSampleProvider sampleProvider = new SonyWena3StressSampleProvider(device, db.getDaoSession());
            Long userId = DBHelper.getUser(db.getDaoSession()).getId();
            Long deviceId = DBHelper.getDevice(device, db.getDaoSession()).getId();
            List<Wena3StressSample> samples = new ArrayList<>();

            Date currentSampleDate = startDate;
            int i = 0;
            for(int rawSample: accumulator) {
                if(rawSample >= -100 && rawSample <= 100) {
                    Wena3StressSample gbSample = new Wena3StressSample();
                    gbSample.setDeviceId(deviceId);
                    gbSample.setUserId(userId);
                    gbSample.setTimestamp(currentSampleDate.getTime());
                    gbSample.setStress(rawSample);
                    gbSample.setTypeNum(StressSample.Type.AUTOMATIC.getNum());
                    samples.add(gbSample);
                } else {
                    LOG.debug("Discard stress value as out of range: " + rawSample);
                }

                i++;
                currentSampleDate = timestampOfSampleAtIndex(i);
            }

            sampleProvider.addSamples(samples);
        } catch (Exception e) {
            LOG.error("Error acquiring database for recording stress samples", e);
        }

        // Finally clean up the parser
        super.finishReceiving(device);
    }
}