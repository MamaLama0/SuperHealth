package app.superhealth.engine.devices.garmin.watches.enduro;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.garmin.watches.GarminWatchCoordinator;
import app.superhealth.engine.impl.GBDevice;

public class GarminEnduroCoordinator extends GarminWatchCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Enduro$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_enduro;
    }

    @Override
    public boolean supportsSolarCharging(@NonNull final GBDevice device) {
        return true;
    }
}
