package app.superhealth.engine.service.devices.garmin.deviceevents;

import android.content.Context;

import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.service.devices.garmin.FileTransferHandler;

public class FileDownloadedDeviceEvent extends GBDeviceEvent {
    public boolean success = true;
    public FileTransferHandler.DirectoryEntry directoryEntry;
    public String localPath;

    @Override
    public void evaluate(final Context context, final GBDevice device) {
        // Handled in support class
    }
}
