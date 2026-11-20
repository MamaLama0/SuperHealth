package app.superhealth.engine.service.devices.garmin.deviceevents;

import android.content.Context;

import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.impl.GBDevice;

public class NotificationSubscriptionDeviceEvent extends GBDeviceEvent {
    public boolean enable;

    @Override
    public void evaluate(final Context context, final GBDevice device) {
        // Handled in support class
    }
}
