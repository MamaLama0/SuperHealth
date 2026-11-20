/*  Copyright (C) 2025 Arjan Schrijver

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
package app.superhealth.engine.service.devices.qhybrid.parser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import app.superhealth.engine.entities.BaseActivitySummary;
import app.superhealth.engine.model.ActivitySummaryData;
import app.superhealth.engine.model.ActivitySummaryEntries;
import app.superhealth.engine.model.ActivitySummaryParser;
import app.superhealth.engine.service.btle.BLETypeConversions;
import app.superhealth.engine.util.ArrayUtils;

public class HybridHRWorkoutSummaryParser implements ActivitySummaryParser {
    @Override
    public BaseActivitySummary parseBinaryData(BaseActivitySummary summary, boolean forDetails) {
        final byte[] rawSummaryData = summary.getRawSummaryData();
        if (rawSummaryData == null) {
            return summary;
        }

        final ByteBuffer buffer = ByteBuffer.wrap(rawSummaryData).order(ByteOrder.LITTLE_ENDIAN);

        final ActivitySummaryData summaryData = new ActivitySummaryData();

        for (int i = 0; i < 14; i++) {
            byte attributeId = buffer.get();
            byte size = buffer.get();
            byte[] rawInfo = new byte[size & 0xFF];
            buffer.get(rawInfo);
            ByteBuffer info = ByteBuffer.wrap(rawInfo).order(ByteOrder.LITTLE_ENDIAN);
            if (ArrayUtils.isAllFF(info.array(), 0, size)) {
                continue;
            }
            switch (attributeId) {
                case 2:
                    int duration = info.getInt();
                    summaryData.add(ActivitySummaryEntries.ACTIVE_SECONDS, duration, ActivitySummaryEntries.UNIT_SECONDS);
                    break;
                case 4:
                    int steps = info.getInt();
                    summaryData.add(ActivitySummaryEntries.STEPS, steps, ActivitySummaryEntries.UNIT_STEPS);
                    break;
                case 5:
                    int distance = info.getInt() / 100;
                    summaryData.add(ActivitySummaryEntries.DISTANCE_METERS, distance, ActivitySummaryEntries.UNIT_METERS);
                    break;
                case 6:
                    int calories = info.getInt();
                    summaryData.add(ActivitySummaryEntries.CALORIES_BURNT, calories, ActivitySummaryEntries.UNIT_KCAL);
                    break;
                case 7:
                    int avg_hr = info.get(0) & 0xFF;
                    summaryData.add(ActivitySummaryEntries.HR_AVG, avg_hr, ActivitySummaryEntries.UNIT_BPM);
                    break;
                case 8:
                    int max_hr = info.get(0) & 0xFF;
                    summaryData.add(ActivitySummaryEntries.HR_MAX, max_hr, ActivitySummaryEntries.UNIT_BPM);
                    break;
            }
        }

        summary.setSummaryData(summaryData.toString());
        return summary;
    }
}
