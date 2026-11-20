/*  Copyright (C) 2018-2024 Andreas Shimokawa, Daniele Gobbetti

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
package app.superhealth.engine.service.devices.huami.miband2;

import android.bluetooth.BluetoothGattCharacteristic;

import androidx.annotation.NonNull;
import app.superhealth.engine.devices.miband.VibrationProfile;
import app.superhealth.engine.service.btle.BLETypeConversions;
import app.superhealth.engine.service.btle.BtLEAction;
import app.superhealth.engine.service.btle.GattCharacteristic;
import app.superhealth.engine.service.btle.TransactionBuilder;
import app.superhealth.engine.service.btle.profiles.alertnotification.AlertCategory;
import app.superhealth.engine.service.btle.profiles.alertnotification.AlertNotificationProfile;
import app.superhealth.engine.service.btle.profiles.alertnotification.NewAlert;
import app.superhealth.engine.service.btle.profiles.alertnotification.OverflowStrategy;
import app.superhealth.engine.service.devices.common.SimpleNotification;
import app.superhealth.engine.service.devices.huami.HuamiIcon;
import app.superhealth.engine.service.devices.huami.HuamiSupport;
import app.superhealth.engine.util.StringUtils;

public class Mi2TextNotificationStrategy extends Mi2NotificationStrategy {
    private final BluetoothGattCharacteristic newAlertCharacteristic;

    public Mi2TextNotificationStrategy(HuamiSupport support) {
        super(support);
        newAlertCharacteristic = support.getCharacteristic(GattCharacteristic.CHARACTERISTIC_NEW_ALERT.uuid);
    }

    @Override
    protected void sendCustomNotification(VibrationProfile vibrationProfile, SimpleNotification simpleNotification, BtLEAction extraAction, TransactionBuilder builder) {
        if (simpleNotification != null && simpleNotification.getAlertCategory() == AlertCategory.IncomingCall) {
            // incoming calls are notified solely via NewAlert including caller ID
            sendAlert(simpleNotification, builder);
            return;
        }

        // announce text messages with configured alerts first
        super.sendCustomNotification(vibrationProfile, simpleNotification, extraAction, builder);
        // and finally send the text message, if any
        if (simpleNotification != null && !StringUtils.isEmpty(simpleNotification.getMessage())) {
            sendAlert(simpleNotification, builder);
        }
    }

    @Override
    protected void startNotify(TransactionBuilder builder, int alertLevel, SimpleNotification simpleNotification) {
        builder.write(newAlertCharacteristic, getNotifyMessage(simpleNotification));
    }

    protected byte[] getNotifyMessage(SimpleNotification simpleNotification) {
        int numAlerts = 1;
        if (simpleNotification != null && simpleNotification.getNotificationType() != null && simpleNotification.getAlertCategory() != AlertCategory.SMS) {
            byte customIconId = HuamiIcon.mapToIconId(simpleNotification.getNotificationType());
            if (customIconId == HuamiIcon.EMAIL) {
                // unfortunately. the email icon breaks the notification, fall back to a standard AlertCategory
                return new byte[]{BLETypeConversions.fromUint8(AlertCategory.Email.getId()), BLETypeConversions.fromUint8(numAlerts)};
            }
            return new byte[]{BLETypeConversions.fromUint8(AlertCategory.CustomHuami.getId()), BLETypeConversions.fromUint8(numAlerts), customIconId};
        }
        return new byte[] { BLETypeConversions.fromUint8(AlertCategory.SMS.getId()), BLETypeConversions.fromUint8(numAlerts)};
    }

    protected void sendAlert(@NonNull SimpleNotification simpleNotification, TransactionBuilder builder) {
        AlertNotificationProfile<?> profile = new AlertNotificationProfile<>(getSupport());
        // override the alert category,  since only SMS and incoming call support text notification
        AlertCategory category = AlertCategory.SMS;
        if (simpleNotification.getAlertCategory() == AlertCategory.IncomingCall) {
            category = simpleNotification.getAlertCategory();
        }
        NewAlert alert = new NewAlert(category, 1, simpleNotification.getMessage());
        profile.newAlert(builder, alert, OverflowStrategy.MAKE_MULTIPLE);
    }

    @Override
    public void stopCurrentNotification(TransactionBuilder builder) {
        builder.write(GattCharacteristic.CHARACTERISTIC_NEW_ALERT.uuid, new byte[]{(byte) AlertCategory.IncomingCall.getId(), 0});
    }
}
