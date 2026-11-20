package app.superhealth.engine.devices.garmin.watches.instinct;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.garmin.watches.GarminWatchCoordinator;

import java.util.regex.Pattern;

public class GarminInstinctCrossoverCoordinator extends GarminWatchCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Instinct Crossover$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_instinct_crossover;
    }
}
