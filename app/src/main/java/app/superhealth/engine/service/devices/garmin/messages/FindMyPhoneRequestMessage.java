package app.superhealth.engine.service.devices.garmin.messages;

import java.util.Collections;
import java.util.List;

import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.deviceevents.GBDeviceEventFindPhone;

public class FindMyPhoneRequestMessage extends GFDIMessage {
    private final int duration;

    public FindMyPhoneRequestMessage(GarminMessage garminMessage, int duration) {
        this.garminMessage = garminMessage;
        this.duration = duration;

        this.statusMessage = getStatusMessage();
    }

    public static FindMyPhoneRequestMessage parseIncoming(MessageReader reader, GarminMessage garminMessage) {
        final int duration = reader.readByte();

        return new FindMyPhoneRequestMessage(garminMessage, duration);
    }

    @Override
    public List<GBDeviceEvent> getGBDeviceEvent() {
        final GBDeviceEventFindPhone findPhoneEvent = new GBDeviceEventFindPhone();
        findPhoneEvent.event = GBDeviceEventFindPhone.Event.START;
        findPhoneEvent.durationSeconds = duration > 0 ? Math.min(duration, 255) : 256;
        return Collections.singletonList(findPhoneEvent);
    }

    @Override
    protected boolean generateOutgoing() {
        return false;
    }
}
