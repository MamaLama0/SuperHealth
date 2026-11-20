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
package app.superhealth.engine.devices.sony.wena3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import app.superhealth.engine.devices.Wena3BehaviorSampleProvider;
import app.superhealth.engine.devices.Wena3HeartRateSampleProvider;
import app.superhealth.engine.entities.Wena3BehaviorSample;
import app.superhealth.engine.entities.Wena3ActivitySample;
import app.superhealth.engine.entities.Wena3HeartRateSample;
import app.superhealth.engine.model.HeartRateSample;
import app.superhealth.engine.service.devices.sony.wena3.protocol.packets.activity.BehaviorSample;

// The sole purpose of this part of code is to take the Behavior samples
// and drop them onto Steps samples (which are just your average activity data with the current
// architecture of SuperHealth)
public class SonyWena3ActivitySampleCombiner {
    private static final Logger LOG = LoggerFactory.getLogger(SonyWena3ActivitySampleCombiner.class);
    final SonyWena3ActivitySampleProvider activitySampleProvider;

    public SonyWena3ActivitySampleCombiner(SonyWena3ActivitySampleProvider activitySampleProvider) {
        this.activitySampleProvider = activitySampleProvider;
    }

    public void overlayBehaviorStartingAt(Date startDate, Wena3BehaviorSampleProvider behaviorSampleProvider) {
        List<Wena3BehaviorSample> behaviorSamples = behaviorSampleProvider.getAllSamples(startDate.getTime(), Long.MAX_VALUE);
        List<Wena3ActivitySample> alteredSamples = new ArrayList<>();
        for(Wena3BehaviorSample behaviorSample: behaviorSamples) {
            List<Wena3ActivitySample> activitySamplesForThisRange = activitySampleProvider.getAllActivitySamples((int)(behaviorSample.getTimestampFrom() / 1000L), (int)(behaviorSample.getTimestampTo() / 1000L));

            LOG.debug("Changing " + activitySamplesForThisRange.size() + " samples to behavior type: " + BehaviorSample.Type.LUT[behaviorSample.getRawKind()].name());
            for(Wena3ActivitySample activitySample: activitySamplesForThisRange) {
                activitySample.setRawKind(behaviorSample.getRawKind());
                alteredSamples.add(activitySample);
            }
        }
        activitySampleProvider.addGBActivitySamples(alteredSamples);
    }

    public void overlayHeartRateStartingAt(Date startDate, Wena3HeartRateSampleProvider heartRateSampleProvider) {
        List<Wena3HeartRateSample> heartRateSamples = heartRateSampleProvider.getAllSamples(startDate.getTime(), Long.MAX_VALUE);
        List<Wena3ActivitySample> alteredSamples = new ArrayList<>();
        for(int i = 0; i < heartRateSamples.size(); i++) {
            HeartRateSample currentSample = heartRateSamples.get(i);
            HeartRateSample nextSample = (i == heartRateSamples.size() - 1) ? null : heartRateSamples.get(i + 1);

            List<Wena3ActivitySample> activitySamplesInRange = activitySampleProvider.getAllActivitySamples(
                    (int) (currentSample.getTimestamp() / 1000L),
                    (nextSample == null ? Integer.MAX_VALUE : ((int) (nextSample.getTimestamp() / 1000L)))
            );

            LOG.debug("Changing " + activitySamplesInRange.size() + " samples to heart rate: " + currentSample.getHeartRate());
            for(Wena3ActivitySample activitySample: activitySamplesInRange) {
                activitySample.setHeartRate(currentSample.getHeartRate());
                alteredSamples.add(activitySample);
            }
        }
        activitySampleProvider.addGBActivitySamples(alteredSamples);
    }
}
