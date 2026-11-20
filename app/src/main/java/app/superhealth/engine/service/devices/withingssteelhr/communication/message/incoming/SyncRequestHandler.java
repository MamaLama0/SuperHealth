/*  Copyright (C) 2023-2024 Frank Ertl

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
package app.superhealth.engine.service.devices.withingssteelhr.communication.message.incoming;

import app.superhealth.engine.service.devices.withingssteelhr.WithingsSteelHRDeviceSupport;
import app.superhealth.engine.service.devices.withingssteelhr.communication.message.ExpectedResponse;
import app.superhealth.engine.service.devices.withingssteelhr.communication.message.Message;
import app.superhealth.engine.service.devices.withingssteelhr.communication.message.WithingsMessage;
import app.superhealth.engine.service.devices.withingssteelhr.communication.message.WithingsMessageType;

public class SyncRequestHandler implements IncomingMessageHandler {

    private final WithingsSteelHRDeviceSupport support;

    public SyncRequestHandler(WithingsSteelHRDeviceSupport support) {
        this.support = support;
    }

    @Override
    public void handleMessage(Message message) {
        support.sendToDevice(new WithingsMessage(WithingsMessageType.SYNC_RESPONSE));
        support.doSync();
    }
}
