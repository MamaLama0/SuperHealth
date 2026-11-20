package app.superhealth.engine.devices.garmin.watches.d2;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.garmin.watches.fenix.GarminFenix7ProCoordinator;

public class GarminD2Mach1ProCoordinator extends GarminFenix7ProCoordinator {
    @Override
    public boolean isExperimental() {
        // Not tested, and the supported device name below is unconfirmed
        return true;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        // TODO: unconfirmed device name
        return Pattern.compile("^D2 Mach 1 Pro( - \\d+mm)?$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_d2_mach_1_pro;
    }
}
