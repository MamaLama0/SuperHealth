package app.superhealth.engine.service.devices.garmin.deviceevents;

import android.content.Context;

import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.impl.GBDevice;

public class MaxPacketSizeDeviceEvent extends GBDeviceEvent {
    private final int maxPacketSize;

    public MaxPacketSizeDeviceEvent(final int maxPacketSize) {
        this.maxPacketSize = maxPacketSize;
    }

    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    @Override
    public void evaluate(final Context context, final GBDevice device) {
        // Handled in support class
    }
}
