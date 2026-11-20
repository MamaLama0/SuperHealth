/*  Copyright (C) 2017-2024 Andreas Shimokawa, Carsten Pfeiffer

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
package app.superhealth.engine.service.devices.common;

import app.superhealth.engine.model.NotificationType;
import app.superhealth.engine.service.btle.profiles.alertnotification.AlertCategory;

public class SimpleNotification {
    private final String message;
    private final AlertCategory alertCategory;
    private final NotificationType notificationType;

    public SimpleNotification(String message, AlertCategory alertCategory, NotificationType notificationType) {
        this.message = message;
        this.alertCategory = alertCategory;
        this.notificationType = notificationType;
    }

    public AlertCategory getAlertCategory() {
        return alertCategory;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public String getMessage() {
        return message;
    }
}
