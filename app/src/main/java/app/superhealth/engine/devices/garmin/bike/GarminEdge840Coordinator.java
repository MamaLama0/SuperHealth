package app.superhealth.engine.devices.garmin.bike;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.impl.GBDevice;

public class GarminEdge840Coordinator extends GarminBikeComputerCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Edge 840$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_edge_840;
    }

    @Override
    public int getBatteryCount(final GBDevice device) {
        return 0; // Unconfirmed: does not seem to report the battery %
    }
}
