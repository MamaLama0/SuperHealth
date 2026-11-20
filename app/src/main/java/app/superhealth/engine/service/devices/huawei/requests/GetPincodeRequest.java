/*  Copyright (C) 2024 Damien Gaignon, Martin.JM

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

import app.superhealth.engine.devices.huawei.HuaweiPacket.CryptoException;
import app.superhealth.engine.devices.huawei.packets.DeviceConfig;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;

public class GetPincodeRequest extends Request {
    private static final Logger LOG = LoggerFactory.getLogger(GetPincodeRequest.class);

    public GetPincodeRequest(HuaweiSupportProvider support) {
        super(support);
        this.serviceId = DeviceConfig.id;
        this.commandId = DeviceConfig.PinCode.id;
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        try {
            return new DeviceConfig.PinCode.Request(paramsProvider).serialize();
        } catch (CryptoException e) {
            throw new RequestCreationException(e);
        }
    }

    @Override
    protected void processResponse() throws ResponseParseException {
        LOG.debug("handle Pincode");

        if (!(receivedPacket instanceof DeviceConfig.PinCode.Response))
            throw new ResponseTypeMismatchException(receivedPacket, DeviceConfig.PinCode.Response.class);

        paramsProvider.setPinCode(((DeviceConfig.PinCode.Response) receivedPacket).pinCode);
    }
}
