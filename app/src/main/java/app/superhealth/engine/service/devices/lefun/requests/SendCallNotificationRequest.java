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

import app.superhealth.engine.devices.lefun.commands.NotificationCommand;
import app.superhealth.engine.model.CallSpec;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.lefun.LefunDeviceSupport;

public class SendCallNotificationRequest extends AbstractSendNotificationRequest {
    public SendCallNotificationRequest(LefunDeviceSupport support, TransactionBuilder builder) {
        super(support, builder);
    }

    private CallSpec callNotification;

    public CallSpec getCallNotification() {
        return callNotification;
    }

    public void setCallNotification(CallSpec callNotification) {
        this.callNotification = callNotification;
    }

    @Override
    protected String getMessage() {
        String message = "";
        if (callNotification.getNumber() != null &&!callNotification.getNumber().isEmpty()) {
            message = callNotification.getNumber();
        }

        if (callNotification.getName() != null && !callNotification.getName().isEmpty()) {
            if (message.length() > 0) {
                message += " - ";
            }
            message += callNotification.getName();
        }

        return message;
    }

    @Override
    protected byte getNotificationType() {
        return NotificationCommand.SERVICE_TYPE_CALL;
    }

    @Override
    protected byte getExtendedNotificationType() {
        return 0;
    }
}
