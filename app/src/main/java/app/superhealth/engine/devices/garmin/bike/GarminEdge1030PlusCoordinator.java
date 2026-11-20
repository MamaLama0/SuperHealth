package app.superhealth.engine.devices.garmin.bike;

import java.util.regex.Pattern;

import app.superhealth.engine.R;

public class GarminEdge1030PlusCoordinator extends GarminBikeComputerCoordinator {
    @Override
    public boolean isExperimental() {
        // Not tested, and the supported device name below is unconfirmed
        return true;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        // TODO: unconfirmed device name
        return Pattern.compile("^Edge 1030 Plus$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_edge_1030_plus;
    }
}
