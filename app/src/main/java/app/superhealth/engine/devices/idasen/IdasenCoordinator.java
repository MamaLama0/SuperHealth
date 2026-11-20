package app.superhealth.engine.devices.idasen;

import android.app.Activity;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import app.superhealth.engine.R;
import app.superhealth.engine.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator;
import app.superhealth.engine.devices.DeviceCoordinator;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.devices.idasen.IdasenDeviceSupport;

public class IdasenCoordinator extends AbstractBLEDeviceCoordinator {
    @Override
    public String getManufacturer() {
        return "IKEA";
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("Desk 1000", Pattern.CASE_INSENSITIVE);
    }
    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_idasen;
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return IdasenDeviceSupport.class;
    }

    @Override
    public DeviceSpecificSettingsCustomizer getDeviceSpecificSettingsCustomizer(final GBDevice device) {
        return new IdasenSettingsCustomizer();
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_BOND;
    }

    @Override
    public boolean supportsAppsManagement(final GBDevice device) {
        return true;
    }

    @Override
    public Class<? extends Activity> getAppsManagementActivity(final GBDevice device) {
        return IdasenControlActivity.class;
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(GBDevice device) {
        return new int[]{
            R.xml.devicesettings_idasen
        };
    }

    @Override
    public DeviceCoordinator.DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceCoordinator.DeviceKind.UNKNOWN;
    }
}
