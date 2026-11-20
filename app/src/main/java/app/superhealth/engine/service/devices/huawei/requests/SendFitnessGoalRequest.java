/*  Copyright (C) 2024 Damien Gaignon

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
package app.superhealth.engine.service.devices.huawei.requests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import app.superhealth.engine.GBApplication;
import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.packets.FitnessData;
import app.superhealth.engine.devices.huawei.packets.FitnessData.MotionGoal;
import app.superhealth.engine.model.ActivityUser;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;

public class SendFitnessGoalRequest extends Request {
    private static final Logger LOG = LoggerFactory.getLogger(SendFitnessGoalRequest.class);

    public SendFitnessGoalRequest(HuaweiSupportProvider support) {
        super(support);
        this.serviceId = FitnessData.id;
        this.commandId = MotionGoal.id;
    }

    @Override
    protected boolean requestSupported() {
        return supportProvider.getDeviceState().supportsMotionGoal();
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        try {
            // Hardcoded values till interface for goal
            int stepGoal = GBApplication.getPrefs().getInt(ActivityUser.PREF_USER_STEPS_GOAL, ActivityUser.defaultUserStepsGoal);
            int calorieGoal = 0;
            short durationGoal = 0;
            return new MotionGoal.Request(paramsProvider,
                    (byte)0x01,
                    (byte)0x00,
                    stepGoal,
                    calorieGoal,
                    durationGoal
            ).serialize();
        } catch (HuaweiPacket.CryptoException e) {
            throw new RequestCreationException(e);
        }
    }

    @Override
    protected void processResponse() {
        LOG.debug("handle Send Fitness Goal Request");
    }
}
