package app.superhealth.engine.devices.garmin.watches.forerunner;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.garmin.watches.GarminWatchCoordinator;

public class GarminForerunner265SCoordinator extends GarminWatchCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        // #4131 - including variants of the name just in case
        return Pattern.compile("^(Garmin )?Forerunner 265[sS]$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_forerunner_265s;
    }
}
