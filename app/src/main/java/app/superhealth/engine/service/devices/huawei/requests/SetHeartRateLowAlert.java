/*  Copyright (C) 2025 Me7c7

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
import app.superhealth.engine.devices.huawei.HuaweiConstants;
import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.packets.FitnessData;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;

public class SetHeartRateLowAlert extends Request {
    private static final Logger LOG = LoggerFactory.getLogger(SetHeartRateLowAlert.class);

    public SetHeartRateLowAlert(HuaweiSupportProvider support) {
        super(support);
        this.serviceId = FitnessData.id;
        this.commandId = FitnessData.LowHeartRateAlert.id;
        this.addToResponse = false;
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        int lowHeartRateAlert = Integer.parseInt(GBApplication
                .getDeviceSpecificSharedPrefs(supportProvider.getDevice().getAddress())
                .getString(HuaweiConstants.PREF_HUAWEI_HEART_RATE_LOW_ALERT, "0"));

        LOG.info("Attempting to set low heart rate alert: {}", lowHeartRateAlert);
        try {
            return new FitnessData.LowHeartRateAlert.Request(paramsProvider, lowHeartRateAlert > 0, (byte) lowHeartRateAlert).serialize();
        } catch (HuaweiPacket.CryptoException e) {
            throw new RequestCreationException(e);
        }
    }
}
