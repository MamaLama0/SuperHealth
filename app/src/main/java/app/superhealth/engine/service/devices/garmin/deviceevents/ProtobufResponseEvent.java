package app.superhealth.engine.service.devices.garmin.deviceevents;

import android.content.Context;

import androidx.annotation.NonNull;

import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.impl.GBDevice;
import app.superhealth.engine.proto.garmin.GdiSmartProto.Smart;

public class ProtobufResponseEvent extends GBDeviceEvent {
    public Smart payload;
    public int messageId;

    public ProtobufResponseEvent(final Smart payload, final int messageId) {
        this.payload = payload;
        this.messageId = messageId;
    }

    @Override
    public void evaluate(@NonNull final Context context, @NonNull final GBDevice device) {
        // Handled in support class
    }
}
