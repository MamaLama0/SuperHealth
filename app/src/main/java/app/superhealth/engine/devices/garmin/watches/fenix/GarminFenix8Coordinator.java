package app.superhealth.engine.devices.garmin.watches.fenix;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.garmin.watches.GarminWatchCoordinator;

public class GarminFenix8Coordinator extends GarminWatchCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^fenix 8( - [\\d+]+mm)?$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_fenix_8;
    }
}
