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

import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.packets.Workout;
import app.superhealth.engine.devices.huawei.packets.Workout.NotifyHeartRate;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;

public class SendNotifyHeartRateCapabilityRequest extends Request {
    private static final Logger LOG = LoggerFactory.getLogger(SendNotifyHeartRateCapabilityRequest.class);

    public SendNotifyHeartRateCapabilityRequest(HuaweiSupportProvider support) {
        super(support);
        this.serviceId = Workout.id;
        this.commandId = NotifyHeartRate.id;
    }

    @Override
    protected boolean requestSupported() {
        return supportProvider.getDeviceState().supportsWorkoutsTrustHeartRate();
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        try {
            return new NotifyHeartRate.Request(paramsProvider).serialize();
        } catch (HuaweiPacket.CryptoException e) {
            throw new RequestCreationException(e);
        }
    }

    @Override
    protected void processResponse() {
        LOG.debug("handle Send Workout HeartRate Capability Request");
    }
}
