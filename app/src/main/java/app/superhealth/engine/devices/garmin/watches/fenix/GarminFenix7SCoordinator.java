package app.superhealth.engine.devices.garmin.watches.fenix;

import java.util.regex.Pattern;

import app.superhealth.engine.R;

public class GarminFenix7SCoordinator extends GarminFenix7Coordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^fenix 7S$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_fenix_7s;
    }
}
