package app.superhealth.engine.devices;

import android.content.SharedPreferences;

import java.util.EnumSet;

import app.superhealth.engine.activities.devicesettings.DeviceSettingsPreferenceConst;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.ServiceDeviceSupport;
import app.superhealth.engine.util.preferences.DevicePrefs;

public abstract class AbstractUsbDeviceCoordinator extends AbstractDeviceCoordinator {
    @Override
    protected void applyDefaultPreferences(final DevicePrefs devicePreferences, final SharedPreferences.Editor editor) {
        editor.putBoolean(DeviceSettingsPreferenceConst.PREFS_DEVICE_SUPPORT_CAN_RECONNECT, false);
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.USB;
    }

    @Override
    public int getBondingStyle() {
        // FIXME: This only applies to BLE
        return BONDING_STYLE_NONE;
    }

    @Override
    public EnumSet<ServiceDeviceSupport.Flags> getInitialFlags() {
        return EnumSet.noneOf(ServiceDeviceSupport.Flags.class);
    }

    @Override
    public int[] getSupportedDebugSettings(final GBDevice device) {
        // stress test and busy checking bypass are not relevant for usb devices
        return new int[0];
    }
}
