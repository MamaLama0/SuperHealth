/*  Copyright (C) 2018-2024 Andreas Shimokawa, José Rebelo

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

import static app.superhealth.engine.service.btle.GattCharacteristic.CHARACTERISTIC_ALERT_LEVEL;

import android.content.Context;
import android.net.Uri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import app.superhealth.engine.devices.huami.HuamiFWHelper;
import app.superhealth.engine.devices.huami.HuamiService;
import app.superhealth.engine.devices.huami.miband2.MiBand2FWHelper;
import app.superhealth.engine.devices.miband.MiBandConst;
import app.superhealth.engine.model.NotificationSpec;
import app.superhealth.engine.model.NotificationType;
import app.superhealth.engine.service.btle.BLETypeConversions;
import app.superhealth.engine.service.btle.profiles.alertnotification.AlertCategory;
import app.superhealth.engine.service.devices.common.SimpleNotification;
import app.superhealth.engine.service.devices.huami.HuamiSupport;
import app.superhealth.engine.service.devices.huami.actions.StopNotificationAction;
import app.superhealth.engine.util.NotificationUtils;

public class MiBand2Support extends HuamiSupport {
    private static final Logger LOG = LoggerFactory.getLogger(MiBand2Support.class);

    private boolean alarmClockRinging;

    @Override
    public HuamiFWHelper createFWHelper(Uri uri, Context context) throws IOException {
        return new MiBand2FWHelper(uri, context);
    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        if (notificationSpec.getType() == NotificationType.GENERIC_ALARM_CLOCK) {
            onAlarmClock(notificationSpec);
            return;
        }

        int alertLevel = HuamiService.ALERT_LEVEL_MESSAGE;
        if (notificationSpec.getType() == NotificationType.UNKNOWN) {
            alertLevel = HuamiService.ALERT_LEVEL_VIBRATE_ONLY;
        }
        String message = NotificationUtils.getPreferredTextFor(notificationSpec, 40, 40, getContext()).trim();
        String origin = notificationSpec.getType().getGenericType();
        SimpleNotification simpleNotification = new SimpleNotification(message, BLETypeConversions.toAlertCategory(notificationSpec.getType()), notificationSpec.getType());
        performPreferredNotification(origin + " received", origin, simpleNotification, alertLevel, null);
    }

    protected void onAlarmClock(NotificationSpec notificationSpec) {
        alarmClockRinging = true;
        StopNotificationAction abortAction = new StopNotificationAction(getCharacteristic(CHARACTERISTIC_ALERT_LEVEL.uuid)) {
            @Override
            protected boolean shouldAbort() {
                return !isAlarmClockRinging();
            }
        };
        String message = NotificationUtils.getPreferredTextFor(notificationSpec, 40, 40, getContext());
        SimpleNotification simpleNotification = new SimpleNotification(message, AlertCategory.HighPriorityAlert, notificationSpec.getType());
        performPreferredNotification("alarm clock ringing", MiBandConst.ORIGIN_ALARM_CLOCK, simpleNotification, HuamiService.ALERT_LEVEL_VIBRATE_ONLY, abortAction);
    }

    @Override
    public void onDeleteNotification(int id) {
        alarmClockRinging = false; // we should have the notificationtype at least to check
    }

    private boolean isAlarmClockRinging() {
        // don't synchronize, this is not really important
        return alarmClockRinging;
    }

}
