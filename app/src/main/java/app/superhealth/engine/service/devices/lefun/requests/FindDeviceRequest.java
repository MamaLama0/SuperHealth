/*  Copyright (C) 2020-2024 Yukai Li

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
package app.superhealth.engine.service.devices.lefun.requests;

import app.superhealth.engine.devices.lefun.LefunConstants;
import app.superhealth.engine.devices.lefun.commands.FindDeviceCommand;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.lefun.LefunDeviceSupport;
import app.superhealth.engine.service.devices.miband.operations.OperationStatus;

public class FindDeviceRequest extends Request {
    public FindDeviceRequest(LefunDeviceSupport support, TransactionBuilder builder) {
        super(support, builder);
    }

    @Override
    public byte[] createRequest() {
        FindDeviceCommand cmd = new FindDeviceCommand();
        return cmd.serialize();
    }

    @Override
    public int getCommandId() {
        return LefunConstants.CMD_FIND_DEVICE;
    }

    @Override
    public void handleResponse(byte[] data) {
        FindDeviceCommand cmd = new FindDeviceCommand();
        cmd.deserialize(data);

        if (!cmd.isSuccess())
            reportFailure("Could not initiate find device");

        operationStatus = OperationStatus.FINISHED;
    }
}
