package app.superhealth.engine.service.devices.garmin.messages;


import java.util.Collections;
import java.util.List;

import app.superhealth.engine.deviceevents.GBDeviceEvent;
import app.superhealth.engine.deviceevents.GBDeviceEventFindPhone;

public class FindMyPhoneCancelMessage extends GFDIMessage {
    public FindMyPhoneCancelMessage(GarminMessage garminMessage) {
        this.garminMessage = garminMessage;

        this.statusMessage = getStatusMessage();
    }

    public static FindMyPhoneCancelMessage parseIncoming(MessageReader reader, GarminMessage garminMessage) {
        return new FindMyPhoneCancelMessage(garminMessage);
    }

    @Override
    public List<GBDeviceEvent> getGBDeviceEvent() {
        final GBDeviceEventFindPhone findPhoneEvent = new GBDeviceEventFindPhone();
        findPhoneEvent.event = GBDeviceEventFindPhone.Event.STOP;
        return Collections.singletonList(findPhoneEvent);
    }

    @Override
    protected boolean generateOutgoing() {
        final MessageWriter writer = new MessageWriter(response);
        writer.writeShort(0); // length and CRC are added by GFDIMessage
        writer.writeShort(garminMessage.getId());
        return true;
    }
}
