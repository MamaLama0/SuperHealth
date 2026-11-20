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
import app.superhealth.engine.devices.huawei.packets.DeviceConfig;
import app.superhealth.engine.devices.huawei.packets.DeviceConfig.SetUpDeviceStatusRequest;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;

public class SendSetUpDeviceStatusRequest extends Request {
    private static final Logger LOG = LoggerFactory.getLogger(SendSetUpDeviceStatusRequest.class);

    public SendSetUpDeviceStatusRequest(HuaweiSupportProvider support) {
        super(support);
        this.serviceId = DeviceConfig.id;
        this.commandId = SetUpDeviceStatusRequest.id;
    }

    @Override
    protected boolean requestSupported() {
        return supportProvider.getDeviceState().supportsMultiDevice();
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        int relationShip = 1; // Hardcoded value for now - 1 = mainDevice
        String deviceName = supportProvider.getDevice().getName(); 
        try {
            return new SetUpDeviceStatusRequest(paramsProvider, relationShip, deviceName).serialize();
        } catch (HuaweiPacket.CryptoException e) {
            throw new RequestCreationException(e);
        }
    }

    @Override
    protected void processResponse() {
        LOG.debug("handle Set Up Device Status");
    }
}
