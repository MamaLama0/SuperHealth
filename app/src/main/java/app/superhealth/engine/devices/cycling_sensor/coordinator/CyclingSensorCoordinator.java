package app.superhealth.engine.devices.cycling_sensor.coordinator;

import android.app.Activity;

import androidx.annotation.NonNull;

import app.superhealth.engine.R;
import app.superhealth.engine.devices.AbstractBLEDeviceCoordinator;
import app.superhealth.engine.devices.CyclingSampleProvider;
import app.superhealth.engine.devices.DeviceCoordinator;
import app.superhealth.engine.devices.TimeSampleProvider;
import app.superhealth.engine.devices.cycling_sensor.activity.CyclingLiveDataActivity;
import app.superhealth.engine.entities.CyclingSample;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.impl.GBDeviceCandidate;
import app.superhealth.engine.service.DeviceSupport;
import app.superhealth.engine.service.devices.cycling_sensor.support.CyclingSensorSupport;

public class CyclingSensorCoordinator extends AbstractBLEDeviceCoordinator {
    @Override
    public boolean supports(GBDeviceCandidate candidate) {
        return candidate.supportsService(CyclingSensorSupport.UUID_CYCLING_SENSOR_SERVICE);
    }

    @Override
    public boolean supportsCyclingData(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public TimeSampleProvider<CyclingSample> getCyclingSampleProvider(GBDevice device, DaoSession session) {
        return new CyclingSampleProvider(device, session);
    }

    @Override
    public boolean supportsCharts(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public String getManufacturer() {
        return "Generic";
    }

    @Override
    public Class<? extends Activity> getAppsManagementActivity(final GBDevice device) {
        return CyclingLiveDataActivity.class;
    }

    @Override
    public boolean supportsRealtimeData(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_NONE;
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(GBDevice device) {
        return new int[]{
                R.xml.devicesettings_cycling_sensor
        };
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(final GBDevice device) {
        return CyclingSensorSupport.class;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_cycling_sensor;
    }

    @Override
    public boolean supportsAppsManagement(GBDevice device) {
        return true;
    }

    @Override
    public DeviceCoordinator.DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.UNKNOWN;
    }
}
