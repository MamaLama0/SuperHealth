package app.superhealth.engine.devices.garmin.watches.instinct;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.garmin.watches.GarminWatchCoordinator;

public class GarminInstinct2SolTacCoordinator extends GarminWatchCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Instinct 2 SolTac$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_instinct_2_soltac;
    }
}
