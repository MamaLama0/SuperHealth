/*  Copyright (C) 2024 Me7c7

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

import java.util.List;

import app.superhealth.engine.devices.huawei.HuaweiPacket;
import app.superhealth.engine.devices.huawei.packets.EphemerisFileUpload;
import app.superhealth.engine.service.devices.huawei.HuaweiSupportProvider;

public class SendEphemerisDataRequestResponse extends Request {
    final int responseCode;
    final String fileName;
    final int offset;


    public SendEphemerisDataRequestResponse(HuaweiSupportProvider support, int responseCode, String fileName, int offset) {
        super(support);
        this.serviceId = EphemerisFileUpload.id;
        this.commandId = EphemerisFileUpload.FileList.id;
        this.responseCode = responseCode;
        this.fileName = fileName;
        this.offset = offset;
        this.addToResponse = false;
    }

    @Override
    protected List<byte[]> createRequest() throws Request.RequestCreationException {
        try {
            return new EphemerisFileUpload.DataRequest.DataRequestResponse(this.paramsProvider, this.responseCode, this.fileName, this.offset).serialize();
        } catch (HuaweiPacket.CryptoException e) {
            throw new Request.RequestCreationException(e);
        }
    }
}
