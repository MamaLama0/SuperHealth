package app.superhealth.engine.devices.garmin.bike;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.impl.GBDevice;

public class GarminEdge540Coordinator extends GarminBikeComputerCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Edge 540$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_edge_540;
    }

    @Override
    public int getBatteryCount(final GBDevice device) {
        return 0; // does not seem to report the battery %
    }
}
