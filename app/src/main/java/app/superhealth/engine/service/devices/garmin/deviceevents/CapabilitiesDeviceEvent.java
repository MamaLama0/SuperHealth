package app.superhealth.engine.service.devices.garmin.deviceevents;

import android.content.Context;

import java.util.Set;

import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.devices.garmin.GarminCapability;
import app.superhealth.engine.impl.GBDevice;

public class CapabilitiesDeviceEvent extends GBDeviceEvent {
    public Set<GarminCapability> capabilities;

    public CapabilitiesDeviceEvent(final Set<GarminCapability> capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public void evaluate(final Context context, final GBDevice device) {
        // Handled in support class
    }
}
