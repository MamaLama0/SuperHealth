package app.superhealth.engine.devices;

import androidx.annotation.NonNull;

import app.superhealth.engine.entities.AbstractActivitySample;
import app.superhealth.engine.entities.DaoSession;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.model.ActivitySample;
import app.superhealth.engine.service.DeviceSupport;

public class FixedSampleDeviceCoordinator extends AbstractDeviceCoordinator {
    private final SampleProvider<? extends AbstractActivitySample> sampleProvider;

    public FixedSampleDeviceCoordinator(final SampleProvider<? extends AbstractActivitySample> sampleProvider) {
        this.sampleProvider = sampleProvider;
    }

    @NonNull
    @Override
    public SampleProvider<? extends ActivitySample> getSampleProvider(@NonNull final GBDevice device,
                                                                      @NonNull final DaoSession session) {
        return sampleProvider;
    }

    @NonNull
    @Override
    public String getManufacturer() {
        return "SuperHealth Tests";
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(@NonNull final GBDevice device) {
        return DeviceSupport.class;
    }

    @Override
    public int getDeviceNameResource() {
        return 0;
    }

    @NonNull
    @Override
    public DeviceCoordinator.DeviceKind getDeviceKind(@NonNull final GBDevice device) {
        return DeviceCoordinator.DeviceKind.UNKNOWN;
    }
}
