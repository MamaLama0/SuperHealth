package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventFindPhone;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.ChecksumCalculator;
import static org.junit.Assert.*;

public class FindMyPhoneTest {
    @Test public void cancelPhoneLocatorUses5040WithEmptyPayloadAndAValidCrc() {
        byte[] bytes = new FindMyPhoneCancelMessage(GFDIMessage.GarminMessage.FIND_MY_PHONE_CANCEL).getOutgoingMessage();
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(6, bytes.length);
        assertEquals(6, Short.toUnsignedInt(b.getShort()));
        assertEquals(5040, Short.toUnsignedInt(b.getShort()));
        assertEquals(ChecksumCalculator.computeCrc(bytes, 0, 4), Short.toUnsignedInt(b.getShort()));
        assertEquals(GBDeviceEventFindPhone.Event.STOP, ((GBDeviceEventFindPhone) GFDIMessage.parseIncoming(bytes).getGBDeviceEvent().get(0)).event);
    }
    @Test public void requestRetainsTheWatchTimeoutAndZeroUsesTheReferenceDefault() {
        for (int seconds : new int[]{0, 30, 200, 255}) {
            byte[] packet = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN).putShort((short)7).putShort((short)5039).put((byte)seconds).putShort((short)0).array();
            ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).putShort(5, (short)ChecksumCalculator.computeCrc(packet,0,5));
            GBDeviceEventFindPhone event = (GBDeviceEventFindPhone)GFDIMessage.parseIncoming(packet).getGBDeviceEvent().get(0);
            assertEquals(GBDeviceEventFindPhone.Event.START, event.event);
            assertEquals(seconds == 0 ? 256 : seconds, event.durationSeconds);
        }
    }
}
