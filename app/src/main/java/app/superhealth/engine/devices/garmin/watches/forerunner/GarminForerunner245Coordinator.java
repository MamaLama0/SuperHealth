package app.superhealth.engine.devices.garmin.watches.forerunner;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.garmin.watches.GarminWatchCoordinator;
import app.superhealth.engine.impl.GBDevice;

public class GarminForerunner245Coordinator extends GarminWatchCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Forerunner 245$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_forerunner_245;
    }

    @Override
    public boolean supportsHrvMeasurement(@NonNull final GBDevice device) {
        return false;
    }
}
