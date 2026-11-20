package app.superhealth.engine.service.devices.garmin.deviceevents;

import android.content.Context;

import java.util.List;

import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.devices.garmin.FileType;

public class SupportedFileTypesDeviceEvent extends GBDeviceEvent {
    private final List<FileType> supportedFileTypes;

    public SupportedFileTypesDeviceEvent(List<FileType> fileTypes) {
        this.supportedFileTypes = fileTypes;
    }

    public List<FileType> getSupportedFileTypes() {
        return supportedFileTypes;
    }

    @Override
    public void evaluate(final Context context, final GBDevice device) {
        // Handled in support class
    }
}
