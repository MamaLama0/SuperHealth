package app.superhealth.engine.devices.garmin.watches.fenix;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.garmin.watches.GarminWatchCoordinator;

public class GarminFenix6SSapphireCoordinator extends GarminWatchCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^fenix 6S Sapphire$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_fenix_6s_sapphire;
    }
}
