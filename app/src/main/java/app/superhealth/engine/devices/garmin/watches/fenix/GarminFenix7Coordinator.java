package app.superhealth.engine.devices.garmin.watches.fenix;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.garmin.watches.GarminWatchCoordinator;
import app.superhealth.engine.impl.GBDevice;

public class GarminFenix7Coordinator extends GarminWatchCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^fenix 7$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_fenix_7;
    }

    @Override
    public boolean supportsSolarCharging(@NonNull final GBDevice device) {
        // Unlike the Fenix 7 Pro models, the original Fenix 7 models use
        // the same BLE name and BLE product number for non-solar and solar models
        return true;
    }
}
