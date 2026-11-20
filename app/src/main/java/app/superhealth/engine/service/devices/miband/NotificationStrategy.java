/*  Copyright (C) 2015-2024 Carsten Pfeiffer, Daniele Gobbetti

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
package app.superhealth.engine.service.devices.miband;

import androidx.annotation.Nullable;
import app.superhealth.engine.devices.miband.VibrationProfile;
import app.superhealth.engine.service.btle.BtLEAction;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.devices.common.SimpleNotification;

public interface NotificationStrategy {
    void sendDefaultNotification(TransactionBuilder builder, SimpleNotification simpleNotification, BtLEAction extraAction);

    /**
     * Adds a custom notification to the given transaction builder
     * @param vibrationProfile specifies how and how often the Band shall vibrate.
     * @param simpleNotification an optional notification containing a type and text message
     * @param flashTimes
     * @param flashColour
     * @param originalColour
     * @param flashDuration
     * @param extraAction      an extra action to be executed after every vibration and flash sequence. Allows to abort the repetition, for example.
     * @param builder
     */
    void sendCustomNotification(VibrationProfile vibrationProfile, @Nullable SimpleNotification simpleNotification, int flashTimes, int flashColour, int originalColour, long flashDuration, BtLEAction extraAction, TransactionBuilder builder);

    /**
     * Stops any current notification.
     * @param builder
     */
    void stopCurrentNotification(TransactionBuilder builder);
}
