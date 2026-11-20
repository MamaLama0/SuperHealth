package app.superhealth.engine.devices.polar;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettings;
import app.superhealth.engine.impl.GBDevice;

public class PolarH10DeviceCoordinator extends AbstractPolarDeviceCoordinator {
    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_polarh10;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Polar H10.*");
    }

    @Override
    public DeviceSpecificSettings getDeviceSpecificSettings(final GBDevice device) {
        final DeviceSpecificSettings deviceSpecificSettings = new DeviceSpecificSettings();
        deviceSpecificSettings.addRootScreen(R.xml.devicesettings_polar);
        return deviceSpecificSettings;
    }
}
