package app.superhealth.engine.service.devices.gatt_client;

import androidx.annotation.NonNull;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.impl.GBDeviceCandidate;
import app.superhealth.engine.service.DeviceSupport;

public class BleGattClientCoordinator extends AbstractBLEDeviceCoordinator {
    @Override
    public String getManufacturer() {
        return "Generic";
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return BleGattClientSupport.class;
    }

    @Override
    public boolean supports(@NonNull GBDeviceCandidate candidate) {
        // can only add through debug settings
        return false;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_ble_gatt_client;
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_scannable;
    }

    @Override
    public DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.UNKNOWN;
    }
}
